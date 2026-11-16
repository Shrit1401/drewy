// Pictures and voice for Drewy:
//  • illustrations — drawn by the OpenAI image model as transparent "sticker" art, generated in the
//    background the moment Sol asks for one, served at /media/<id>.webp (long-polls until ready)
//  • photos        — real photos via Wikipedia → Wikimedia Commons; falls back to an illustration
//  • speech        — Drewy's voice (marin) via gpt-4o-mini-tts, cached per sentence

import crypto from "node:crypto";
import OpenAI from "openai";

const client = new OpenAI();
export const IMAGE_MODEL = process.env.DREWY_IMAGE_MODEL ?? "gpt-image-2.6-flare";
export const TTS_MODEL = process.env.DREWY_TTS_MODEL ?? "gpt-4o-mini-tts";
export const VOICE = process.env.DREWY_VOICE ?? "marin";

const STYLE =
  "Clean, modern hand-drawn study-notes illustration — like a beautiful sketch from a top student's notebook or a textbook margin. " +
  "Confident fine dark-navy (#2B2D6E) ink linework, soft pastel accents (sky blue #DCE6FF, blush pink #FFD6E8, butter yellow #FFF1A8, mint #CFF5E7) " +
  "with subtle shading. Scientifically accurate, clear structure, any labels short, neat and spelled correctly. Sophisticated and minimal — " +
  "not childish, no cartoon faces on objects. Isolated subject on a transparent background, no frame, no paper texture.";

// ---------------- illustrations ----------------
const images = new Map(); // id -> { promise, buf, error, at }

function sweep() {
  const cutoff = Date.now() - 60 * 60 * 1000;
  for (const [k, v] of images) if (v.at < cutoff) images.delete(k);
}

/** Start generating an illustration now; returns its id immediately. */
export function startIllustration(prompt) {
  sweep();
  const id = crypto.createHash("sha1").update(prompt).digest("hex").slice(0, 16);
  if (images.has(id)) return id;
  const entry = { at: Date.now(), buf: null, error: null };
  const gen = (p) => client.images.generate({ model: IMAGE_MODEL, prompt: `${p}\n\nStyle: ${STYLE}`, size: "1024x1024", quality: "low", background: "transparent", output_format: "webp" });
  entry.promise = gen(prompt)
    .catch((e) => {
      // e.g. the safety system rejected a medical/biology wording — retry as a simple, friendly educational doodle
      console.warn("illustration retry:", e.message?.slice(0, 80));
      return gen(`A simple, friendly, child-safe educational cartoon doodle of: ${prompt.replace(/blood|wound|injur\w*|dead|kill\w*|weapon\w*/gi, "").slice(0, 160)}`);
    })
    .then((r) => {
      const b64 = r.data?.[0]?.b64_json;
      if (!b64) throw new Error("no image returned");
      entry.buf = Buffer.from(b64, "base64");
      return entry.buf;
    })
    .catch((e) => {
      entry.error = e.message;
      console.warn("illustration failed:", e.message);
      return null;
    });
  images.set(id, entry);
  return id;
}

/** Wait (up to timeoutMs) for an illustration. */
export async function getIllustration(id, timeoutMs = 90_000) {
  const e = images.get(id);
  if (!e) return null;
  if (e.buf) return e.buf;
  return Promise.race([e.promise, new Promise((r) => setTimeout(() => r(null), timeoutMs))]);
}

// ---------------- photos ----------------
const UA = "DrewyApp/0.1 (Android drawing assistant)";
const photoCache = new Map();

async function wiki(params, host = "en.wikipedia.org") {
  const res = await fetch(`https://${host}/w/api.php?` + new URLSearchParams({ format: "json", ...params }), {
    headers: { "User-Agent": UA },
    signal: AbortSignal.timeout(8000),
  });
  return res.json();
}

export async function lookupPhoto(query) {
  if (!query) return null;
  const key = query.toLowerCase();
  if (photoCache.has(key)) return photoCache.get(key);
  let out = null;
  try {
    // 1) Wikipedia article lead image
    const d = await wiki({ action: "query", generator: "search", gsrsearch: query, gsrlimit: "3", prop: "pageimages|info", piprop: "thumbnail", pithumbsize: "900", inprop: "url", redirects: "1" });
    const pages = Object.values(d?.query?.pages ?? {}).sort((a, b) => a.index - b.index);
    const hit = pages.find((p) => p.thumbnail?.source);
    if (hit) out = { imageUrl: hit.thumbnail.source, sourceUrl: hit.fullurl, sourceTitle: hit.title };
    // 2) Wikimedia Commons file search
    if (!out) {
      const c = await wiki({ action: "query", generator: "search", gsrsearch: `${query} filetype:bitmap`, gsrnamespace: "6", gsrlimit: "3", prop: "imageinfo", iiprop: "url", iiurlwidth: "900" }, "commons.wikimedia.org");
      const files = Object.values(c?.query?.pages ?? {}).sort((a, b) => a.index - b.index);
      const f = files.find((p) => p.imageinfo?.[0]?.thumburl);
      if (f) out = { imageUrl: f.imageinfo[0].thumburl, sourceUrl: f.imageinfo[0].descriptionurl, sourceTitle: f.title.replace(/^File:/, "") };
    }
  } catch (e) {
    console.warn("photo lookup failed", query, e.message);
  }
  photoCache.set(key, out);
  return out;
}

/**
 * Make every picture element ready to draw:
 *  - illustration → start generation, set imageUrl to /media/<id>.webp
 *  - photo → real photo; if none exists, quietly becomes an illustration so there is ALWAYS an image
 */
export async function preparePictures(elements) {
  await Promise.all(
    (elements ?? []).map(async (el) => {
      if (el.kind === "photo") {
        const p = await lookupPhoto(el.query || el.caption);
        if (p) return Object.assign(el, p);
        el.kind = "illustration";
        el.prompt = el.prompt || `${el.query || el.caption}`;
      }
      if (el.kind === "illustration" && !el.imageUrl) {
        el.imageUrl = `/media/${startIllustration(el.prompt || el.caption || "a friendly doodle")}.webp`;
      }
    }),
  );
}

// ---------------- speech ----------------
const VOICE_STYLE =
  "You are Drewy, a calm, warm, friendly study companion talking to a college student across the desk. Natural conversational pace, " +
  "relaxed and reassuring, a little smile in your voice, genuinely curious. Clear and articulate on technical terms. Never childish, never salesy, never robotic.";

// key -> { chunks: Buffer[], done, error, waiters: Set<fn> } — lets playback start while audio is still being generated
const speech = new Map();

export function speechKey(text) {
  return crypto.createHash("sha1").update(normalize(text)).digest("hex");
}
const normalize = (t) => String(t ?? "").replace(/\s+/g, " ").trim().slice(0, 600);

/** Start generating speech for `text` (no-op if already started). Returns its key. */
export function startSpeech(text) {
  const clean = normalize(text);
  if (!clean) return null;
  const key = speechKey(clean);
  if (speech.has(key) && !speech.get(key).error) return key;
  if (speech.size > 400) speech.delete(speech.keys().next().value);
  const entry = { chunks: [], done: false, error: null, waiters: new Set() };
  speech.set(key, entry);
  const wake = () => { for (const w of entry.waiters) w(); entry.waiters.clear(); };
  (async () => {
    try {
      const r = await client.audio.speech.create({ model: TTS_MODEL, voice: VOICE, input: clean, instructions: VOICE_STYLE, response_format: "mp3" });
      const reader = r.body.getReader();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        entry.chunks.push(Buffer.from(value)); wake();
      }
    } catch (e) {
      entry.error = e.message; console.warn("speech failed:", e.message);
    }
    entry.done = true; wake();
  })();
  return key;
}

/** Yields audio chunks as they arrive (works for finished and in-progress speech). */
export async function* speechChunks(key) {
  const e = speech.get(key);
  if (!e) return;
  let i = 0;
  for (;;) {
    while (i < e.chunks.length) yield e.chunks[i++];
    if (e.done) return;
    await new Promise((r) => e.waiters.add(r));
  }
}

/** Whole clip (tests / simple clients). */
export async function speak(text) {
  const key = startSpeech(text);
  if (!key) return null;
  const parts = [];
  for await (const c of speechChunks(key)) parts.push(c);
  return parts.length ? Buffer.concat(parts) : null;
}
