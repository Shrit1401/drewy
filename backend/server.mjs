// Drewy backend — keeps the OpenAI API key off the phone.
// Brain: Decisions API (gpt-6-luna) routes, Sol (gpt-6.1-sol) draws. Pictures: gpt-image. Voice: marin.
//
//   POST /api/ask    { prompt, mode?: "ask"|"teach"|"3d"|"lasso", image?: base64 PNG of the page }
//   POST /api/snap   { image: base64 JPEG from the camera, mode: "solve"|"explain"|"check", prompt? }
//        → JSON board, or with ?stream=1 an NDJSON stream of {type: meta|step|done|error} events
//   POST /api/speak  { text }  → audio/mpeg (Drewy's voice)
//   GET  /media/<id>.webp      → AI illustration (waits until it's drawn)
//   GET  /health

import http from "node:http";
import { drawStream, OpenAIErrors as OpenAI, ROUTER_MODEL, SOL_MODEL, warmAcks } from "./brain.mjs";
import { getIllustration, IMAGE_MODEL, speak, speechChunks, speechKey, startSpeech, VOICE } from "./media.mjs";

const PORT = Number(process.env.PORT ?? 8787);

function sniffImage(b64) {
  if (b64.startsWith("/9j/")) return "image/jpeg";
  if (b64.startsWith("iVBOR")) return "image/png";
  if (b64.startsWith("UklGR")) return "image/webp";
  return "image/jpeg";
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > 15 * 1024 * 1024) throw Object.assign(new Error("Image too large"), { status: 413 });
    chunks.push(c);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

function friendly(e) {
  if (e instanceof OpenAI.AuthenticationError) return "Backend has no valid OPENAI_API_KEY.";
  if (e instanceof OpenAI.RateLimitError) return "Drewy is a bit busy — try again in a moment.";
  if (e instanceof OpenAI.APIConnectionError) return "Backend can't reach OpenAI right now.";
  return e.message;
}

const server = http.createServer(async (req, res) => {
  const started = Date.now();
  const url = new URL(req.url, "http://x");
  try {
    if (req.method === "GET" && url.pathname === "/health") return send(res, 200, { ok: true, model: SOL_MODEL, router: ROUTER_MODEL, images: IMAGE_MODEL, voice: VOICE });

    if (req.method === "GET" && url.pathname.startsWith("/media/")) {
      const id = url.pathname.slice(7).replace(/\.webp$/, "");
      const buf = await getIllustration(id);
      if (!buf) return send(res, 404, { error: "not ready" });
      res.writeHead(200, { "Content-Type": "image/webp", "Cache-Control": "public, max-age=86400" });
      return res.end(buf);
    }

    // streaming voice: GET /api/speak?k=<key> (from an event) or ?text=… — starts playing while still being generated
    if (req.method === "GET" && url.pathname === "/api/speak") {
      const key = url.searchParams.get("k") ?? startSpeech(url.searchParams.get("text"));
      if (!key) return send(res, 400, { error: "text required" });
      res.writeHead(200, { "Content-Type": "audio/mpeg", "Cache-Control": "no-cache", "Transfer-Encoding": "chunked" });
      let n = 0;
      for await (const chunk of speechChunks(key)) { res.write(chunk); n += chunk.length; }
      console.log(`/api/speak stream voice=${VOICE} ${Date.now() - started}ms ${n}B`);
      return res.end();
    }

    if (req.method === "POST" && url.pathname === "/api/speak") {
      const body = await readJson(req);
      const audio = await speak(body.text);
      if (!audio) return send(res, 400, { error: "text required" });
      console.log(`/api/speak voice=${VOICE} ${Date.now() - started}ms ${audio.length}B "${String(body.text).slice(0, 40)}"`);
      res.writeHead(200, { "Content-Type": "audio/mpeg", "Content-Length": audio.length });
      return res.end(audio);
    }

    if (req.method !== "POST" || !["/api/ask", "/api/snap"].includes(url.pathname)) return send(res, 404, { error: "not found" });

    const body = await readJson(req);
    const image = typeof body.image === "string" && body.image.length > 0 ? body.image.replace(/^data:[^,]+,/, "") : null;
    let mode = String(body.mode ?? "ask");
    if (url.pathname === "/api/snap") {
      if (!image) return send(res, 400, { error: "image required" });
      if (!["solve", "explain", "check"].includes(mode)) mode = "solve";
    } else if (!["ask", "teach", "3d", "lasso"].includes(mode)) mode = "ask";
    const args = { mode, prompt: body.prompt, image, mediaType: image ? sniffImage(image) : null, history: Array.isArray(body.history) ? body.history : [] };

    if (url.searchParams.get("stream") === "1") {
      res.writeHead(200, { "Content-Type": "application/x-ndjson; charset=utf-8", "Cache-Control": "no-cache", "X-Accel-Buffering": "no" });
      const write = (ev) => res.write(JSON.stringify(ev) + "\n");
      try {
        const { board, routing } = await drawStream(args, write);
        console.log(`${url.pathname} stream mode=${mode} ack=${routing.ack} fresh=${routing.fresh} ${Date.now() - started}ms "${board.title}" steps=${board.steps.length}`);
      } catch (e) {
        console.error(e);
        write({ type: "error", error: friendly(e) });
      }
      return res.end();
    }

    const { board, routing } = await drawStream(args);
    console.log(`${url.pathname} mode=${mode} ack=${routing.ack} fresh=${routing.fresh} ${Date.now() - started}ms "${board.title}"`);
    send(res, 200, board);
  } catch (e) {
    console.error(e);
    const status = e.status && e.status < 600 ? e.status : 500;
    if (!res.headersSent) send(res, status, { error: friendly(e) });
    else res.end();
  }
});

warmAcks();
server.listen(PORT, "0.0.0.0", () => console.log(`Drewy backend on http://0.0.0.0:${PORT} (router ${ROUTER_MODEL}, drawer ${SOL_MODEL}, images ${IMAGE_MODEL}, voice ${VOICE})`));
