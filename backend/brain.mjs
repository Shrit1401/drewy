// Drewy's brain, in two parts:
//
//  1. ROUTER — OpenAI Decisions API (gpt-6-luna). A fast classifier: which visual fits,
//     does it need live web facts, how hard is it.
//  2. DRAWER — Sol (gpt-6.1-sol) via the Responses API. Composes pages on the shared canvas by
//     calling `render_board`. Streamed: each finished step is sent to the phone as soon as it's
//     written, so Drewy starts drawing page 1 while Sol is still planning page 3.

import OpenAI from "openai";
import { PAGE_H, PAGE_W, renderBoardTool } from "./board-schema.mjs";
import { preparePictures, startIllustration, startSpeech } from "./media.mjs";

export const SOL_MODEL = process.env.DREWY_MODEL ?? "gpt-6.1-sol";
export const ROUTER_MODEL = process.env.DREWY_ROUTER_MODEL ?? "gpt-6-luna";

const client = new OpenAI(); // reads OPENAI_API_KEY

const SYSTEM = `You are Drewy — a calm, warm, genuinely smart study companion for a college student. You share a drawing canvas with them and explain things by SKETCHING on paper pages while you talk them through it, like the best TA they've ever had sitting next to them with a pen.

WHO YOU'RE TALKING TO
- A university student. Pitch explanations at undergraduate level: correct terminology, real formulas and units, derivations when they help, worked examples, common exam pitfalls. Never dumb it down or sound like a kids' show — be clear, precise and friendly, like a peer who really gets it.

THE CANVAS
- Pages are ${PAGE_W} wide x ${PAGE_H} tall (x right, y down). Keep everything inside with ~14 units of margin.
- Compose each page like a beautiful study note: a short handwritten headline, one clear hero visual, labels beside it with hand-drawn arrows to the exact parts, key equations written out, and a takeaway line. Leave breathing room; never overlap things.
- Typical layout: headline at y≈16 (size l); hero visual ~240-300 wide in the middle; labels (size s/m) beside it with arrows in; equations / takeaway near the bottom (y≈460-520).
- Text sizes are heights: s 18, m 24, l 32, xl 44. A line of size m holds ~22 characters per 300 units width — set w and keep lines short. Write equations in plain text (e.g. "F = ma", "dQ/dt = −kQ", "λ₁ + λ₂ = tr(A)").

HOW TO DRAW
- Every answer needs at least one real visual. For objects, machines, anatomy, chemistry, physics setups, places or concepts use an ILLUSTRATION — and make it the FIRST element of the first step so it starts rendering immediately. Write a precise prompt: subject, the parts that matter, any short labels to letter on it, viewpoint, "clean scientific notebook sketch".
- Use PHOTO only for a specific real person, landmark or product.
- When the question is about a REAL PERSON: page 1 = their photo (w≈200, top-left area) + a handwritten headline + 4-6 short key facts beside/below it (born, what they do, companies/roles, the thing they're known for, the headline number like net worth with its date). Page 2 (newPage) = a short hand-drawn timeline of their biggest milestones (ink line + dots + labels) and one takeaway.
- Use INK for things you'd draw by hand: graphs and axes, free-body diagrams, circuits, flowcharts, number lines, vectors, curves, underlines. Paths are in page coordinates. Add anim "flow" to lines that carry something (current, fluid, signal), "pulse" to things that oscillate or beat.
- Give pictures a subtle motion when it adds meaning: fly for rockets/planes, float for orbiting bodies, spin for rotating parts, pulse for hearts/oscillators. Otherwise "none".
- ARROWS (bend 0.2-0.4) connect labels to parts. MARK (circle) the answer or the key term.
- Steps are what you say while drawing. Build the page progressively: step 1 = headline + hero visual; next steps add labels, equations, details. Use newPage: true for a new idea. Explanations: 2-4 pages. Quick answers: 1 page, 2-3 steps.
- Narration is spoken aloud: natural, conversational, 1-2 sentences per step, like you're talking across the desk. No markdown, lists or emojis. Don't just read the page — add the intuition.

OTHER RULES
- For facts that change over time (prices, rankings, news, "latest", "rn"), use web search first if it's available.
- If an image of the user's page is attached, its coordinates match the page (${PAGE_W}x${PAGE_H}). When annotating THEIR drawing, set newPage: false on the first step and place labels and arrows around what they drew without covering it.
- Earlier turns of the conversation may be included — build on them naturally ("so, following on from the heart…").
- Be encouraging but honest about mistakes. Never put citations or links inside the drawing.
- Always finish by calling render_board exactly once.`;

const MODE_HINT = {
  ask: "Answer the user's question on the canvas. Usually 1 page, 2-3 steps.",
  teach: "EXPLAIN-IT MODE: a college-level walkthrough over 2-4 pages and 5-9 steps that build up the picture piece by piece: intuition first, then the formal idea (equations), then a quick worked example or exam tip. End with a one-line takeaway.",
  "3d": "3D MODE: build an accurate interactive 3D model. Step 1: a short headline (y≈14, size l) and ONE model3d element (x≈10, y≈62, w≈340, h≈400). Its parts array is the BUILD ORDER — list parts bottom-up / inside-out so it assembles like the real thing (e.g. engines → first-stage tank → interstage → second stage → payload fairing → fins). Use 10-24 primitives with realistic colours and proportions, every part with a clear real name; set label=true on the 6-9 most important ones (names short, e.g. 'Merlin engines', 'LOX tank', 'Interstage'). Steps 2-3: narrate what each major part does while you write 2-4 short handwritten callouts below the model (y≈470-540). If the user says 'it'/'that', it means the subject of the previous turn.",
  lasso:
    "The attached image is the user's page; they circled something on it. Work out what it is and do what they ask — by default name it and label its parts with arrows right on their page (newPage: false), then add a cleaner illustration on a new page.",
  solve:
    "SOLVE MODE: the photo shows a question (pick the clearest/most central, or the one the user names). Page 1: write the question as a headline, then one step per line of working as handwritten text lines going down the page (a small grey 'why' label above each), finish with the answer in xl blue text with a pink circle mark around it. Add an ink graph if it helps.",
  explain: "EXPLAIN MODE: the photo shows a question. Teach the idea behind it over 2-3 pages with pictures, then show how it applies to this question.",
  check:
    "CHECK MODE: the photo shows the student's OWN handwritten working. Write each of their lines down the page as check elements (verdict + short note), with an underline mark under the mistake. Then a step with a note on what to fix — WITHOUT giving the final answer. Fill `score` like '2 of 3 steps right — so close!'. Encouraging first.",
};

// Instant spoken reactions, picked by the Decisions API in ~0.4s and pre-voiced at startup (so they play immediately).
export const ACKS = {
  person: ["Good question — let me check.", "Let me look that up real quick."],
  draw: ["Ooh, nice. Let me sketch it.", "Okay, let me draw that out."],
  lesson: ["Great question. Let me sketch it out for you.", "Okay, let's walk through this together."],
  math: ["Alright, let's work through it.", "Okay, let's take this one step at a time."],
  check: ["Let me look over your working.", "Okay, let's see how you did."],
  sketch: ["Ooh, let me see what you've got.", "Okay, let me look at your sketch."],
  model3d: ["Let me build that in 3D for you.", "Okay, building it now."],
  chat: ["Hey! I'm here.", "Hi! What are we working on?"],
  fact: ["Good question. Let me think.", "Hmm, let me sketch that out."],
};
const ACK_CHOICES = [
  { value: "person", description: "a question about a real person, place, company, ranking or current fact" },
  { value: "draw", description: "asks to show or draw an object, animal, machine or scene" },
  { value: "lesson", description: "asks to learn, teach or explain how something works" },
  { value: "math", description: "a maths or homework problem to solve" },
  { value: "check", description: "asks to check or mark their own work" },
  { value: "sketch", description: "about something the user drew" },
  { value: "model3d", description: "asks for a 3D model" },
  { value: "chat", description: "a greeting, feelings or small talk" },
  { value: "fact", description: "any other question" },
];
const FORCED = { solve: "math", check: "check", explain: "lesson", lasso: "sketch", "3d": "model3d" };
export function warmAcks() { for (const list of Object.values(ACKS)) for (const t of list) startSpeech(t); }

/** Step 1 (~0.4s): Decisions API picks the instant reaction and whether we need live web facts. Never throws. */
export async function route({ mode, prompt, imageUrl }) {
  const fallback = { ack: FORCED[mode] ?? "fact", fresh: false, source: "fallback" };
  const content = [{ type: "input_text", text: `Drawing-app request (mode: ${mode}): ${prompt || "(see image)"}` }];
  if (imageUrl) content.push({ type: "input_image", image_url: imageUrl, detail: "low" });
  try {
    const d = await client.decisions.create({
      model: ROUTER_MODEL,
      input: [{ role: "user", content }],
      questions: [
        { name: "kind", type: "choice", instructions: "What kind of request is this?", choices: ACK_CHOICES },
        { name: "fresh", type: "predicate", instructions: "A correct answer needs current, up-to-date facts from the web (news, prices, rankings like 'richest', 'latest', today's events)." },
      ],
    });
    const by = Object.fromEntries(d.answers.map((a) => [a.name, a]));
    return {
      ack: FORCED[mode] ?? (by.kind?.type === "choice" ? String(by.kind.choice) : "fact"),
      fresh: by.fresh?.type === "predicate" ? by.fresh.probability > 0.5 : false,
      source: "decisions",
    };
  } catch (e) {
    console.warn("router (Decisions API) failed, using defaults:", e.message);
    return fallback;
  }
}

// low effort keeps the first stroke fast; lessons and homework get more thought
const effortFor = (mode) => (["solve", "check", "explain"].includes(mode) ? "medium" : "low");

/** Remove web-search citation markers (private-use … spans) and tidy whitespace in every string. */
export function cleanBoard(v) {
  if (typeof v === "string") return v.replace(/[^]*/g, "").replace(/[-]/g, "").replace(/[ \t]{2,}/g, " ").trim();
  if (Array.isArray(v)) return v.map(cleanBoard);
  if (v && typeof v === "object") return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, cleanBoard(x)]));
  return v;
}

/**
 * Parse a JSON prefix that may be cut off mid-way ("{"title":"Hi","steps":[{"narr").
 * Closes open strings/arrays/objects; drops a dangling key or partial number. Returns null if hopeless.
 */
export function parsePartialJson(src) {
  let s = src.trim();
  for (let attempt = 0; attempt < 6 && s.length; attempt++) {
    const stack = [];
    let inStr = false, esc = false;
    for (const ch of s) {
      if (inStr) { if (esc) esc = false; else if (ch === "\\") esc = true; else if (ch === '"') inStr = false; continue; }
      if (ch === '"') inStr = true;
      else if (ch === "{" || ch === "[") stack.push(ch);
      else if (ch === "}" || ch === "]") stack.pop();
    }
    let t = s;
    if (inStr) t += '"';
    t = t.replace(/[,:\s]+$/, "").replace(/,\s*"[^"]*"\s*$/, "").replace(/\{\s*"[^"]*"\s*$/, "{");
    const closers = stack.reverse().map((c) => (c === "{" ? "}" : "]")).join("");
    try { return JSON.parse(t + closers); } catch { /* trim back to the last structural char and retry */ }
    const cut = Math.max(s.lastIndexOf(","), s.lastIndexOf("{"), s.lastIndexOf("["));
    if (cut <= 0) return null;
    s = s.slice(0, cut + (s[cut] === "," ? 0 : 1));
  }
  return null;
}

const speechUrl = (text) => {
  const key = startSpeech(text);
  return key ? `/api/speak?k=${key}` : null;
};

/**
 * Stream a board, element by element. Calls onEvent with:
 *   { type: "ack", text, speech }                 instantly — Drewy's spoken reaction
 *   { type: "meta", title }
 *   { type: "stepStart", step, newPage, narration, speech }   narration voice already generating
 *   { type: "element", step, index, element }     each element as soon as Sol finishes writing it
 *   { type: "done", board, routing }
 * history: earlier turns [{ q, title, said }] so follow-ups feel like a conversation.
 */
export async function drawStream({ mode, prompt, image, mediaType, history = [] }, onEvent = () => {}) {
  const imageUrl = image ? `data:${mediaType};base64,${image}` : null;
  const routing = await route({ mode, prompt, imageUrl });
  const ackList = ACKS[routing.ack] ?? ACKS.fact;
  const ack = ackList[Math.floor(Math.random() * ackList.length)];
  onEvent({ type: "ack", text: ack, speech: speechUrl(ack) });

  const tools = [renderBoardTool];
  if (routing.fresh) tools.unshift({ type: "web_search" });

  const past = (history ?? []).slice(-4).flatMap((h) => [
    { role: "user", content: String(h.q ?? "").slice(0, 300) },
    { role: "assistant", content: `(I drew a page called "${String(h.title ?? "").slice(0, 80)}" and said: ${String(h.said ?? "").slice(0, 400)})` },
  ]);
  const content = [];
  if (imageUrl) content.push({ type: "input_image", image_url: imageUrl, detail: "high" });
  content.push({ type: "input_text", text: `${MODE_HINT[mode] ?? MODE_HINT.ask}${routing.fresh ? "\n\n(This needs current facts — search the web first.)" : ""}\n\nUser: ${prompt?.trim() || (image ? "(see image)" : "Say hi!")}` });

  let input = [...past, { role: "user", content }];
  let previous;
  let sentTitle = false;
  const sent = []; // per step: { started, elements }
  const emitStepStart = (i, step) => {
    sent[i] = sent[i] ?? { started: false, elements: 0 };
    if (sent[i].started) return;
    sent[i].started = true;
    const narration = cleanBoard(step.narration ?? "");
    onEvent({ type: "stepStart", step: i, newPage: step.newPage === true || (i === 0 && step.newPage !== false), narration, speech: speechUrl(narration) });
  };
  const emitElement = async (i, j, el) => {
    const e = cleanBoard(el);
    await preparePictures([e]);
    onEvent({ type: "element", step: i, index: j, element: e });
    return e;
  };
  /** Emit everything in `steps` that's complete. final = the board is finished. */
  const flush = async (steps, final) => {
    for (let i = 0; i < steps.length; i++) {
      const st = steps[i] ?? {};
      const stepDone = final || i < steps.length - 1;
      sent[i] = sent[i] ?? { started: false, elements: 0 };
      const narrationKnown = typeof st.narration === "string" && (st.elements !== undefined || stepDone);
      if (!sent[i].started) { if (narrationKnown) emitStepStart(i, st); else break; }
      const list = Array.isArray(st.elements) ? st.elements : [];
      const ready = stepDone ? list.length : Math.max(0, list.length - 1);
      // an illustration still being written: start drawing it as soon as its prompt is finished (a later key exists)
      const tail = list[list.length - 1];
      if (!stepDone && tail?.kind === "illustration" && typeof tail.prompt === "string") {
        const keys = Object.keys(tail);
        if (keys.indexOf("prompt") < keys.length - 1) startIllustration(tail.prompt);
      }
      while (sent[i].elements < ready) { const j = sent[i].elements++; list[j] = await emitElement(i, j, list[j]); }
      if (!stepDone) break;
    }
  };

  for (let turn = 0; turn < 4; turn++) {
    const stream = await client.responses.create({
      model: SOL_MODEL, instructions: SYSTEM, input, previous_response_id: previous, tools, tool_choice: "auto",
      reasoning: { effort: effortFor(mode) }, max_output_tokens: 32000, stream: true,
    });
    let args = "", inBoard = false, final = null;
    for await (const ev of stream) {
      if (ev.type === "response.output_item.added" && ev.item?.type === "function_call" && ev.item.name === "render_board") { inBoard = true; args = ""; }
      else if (ev.type === "response.function_call_arguments.delta" && inBoard) {
        args += ev.delta;
        const partial = parsePartialJson(args);
        if (!partial) continue;
        if (!sentTitle && typeof partial.title === "string" && /"title"\s*:\s*"[^"]*"\s*[,}]/.test(args)) { sentTitle = true; onEvent({ type: "meta", title: cleanBoard(partial.title) }); }
        if (Array.isArray(partial.steps)) await flush(partial.steps, false);
      } else if (ev.type === "response.completed" || ev.type === "response.incomplete") final = ev.response;
      else if (ev.type === "response.failed") throw new Error(ev.response?.error?.message ?? "Sol failed");
    }
    const call = final?.output?.find((o) => o.type === "function_call" && o.name === "render_board");
    let board = null;
    if (call) { try { board = cleanBoard(JSON.parse(call.arguments)); } catch { board = null; } }
    if (board) {
      board.steps = board.steps ?? [];
      if (!sentTitle) onEvent({ type: "meta", title: board.title });
      // send whatever the partial stream didn't, using the already-prepared elements where we have them
      await flush(board.steps, true);
      onEvent({ type: "done", board, routing });
      return { board, routing };
    }
    if (final?.status === "incomplete" && final.incomplete_details?.reason === "max_output_tokens") break;
    previous = final?.id;
    input = [
      ...(call ? [{ type: "function_call_output", call_id: call.call_id, output: "Invalid JSON — call render_board again with valid JSON." }] : []),
      { role: "user", content: "Now draw it: call render_board." },
    ];
  }
  const board = sorryBoard("I got a bit tangled up — can you ask again?");
  await flush(board.steps, true);
  onEvent({ type: "done", board, routing });
  return { board, routing };
}

/** Non-streaming convenience wrapper (tests). */
export const draw = (args) => drawStream(args);

export function sorryBoard(text) {
  return {
    title: "Oops", mood: "thinking", followUps: [],
    steps: [{ narration: text, newPage: true, elements: [{ kind: "text", text, x: 24, y: 40, w: 312, size: "m", color: "pink" }] }],
  };
}

export const OpenAIErrors = OpenAI;
