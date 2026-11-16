// End-to-end smoke test over the STREAMING API: every mode, validated against what the app can draw.
//   node test/smoke.mjs [baseUrl]
// Saves each final board to test/out/<case>.json (also used by the Android tests).

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] ?? "http://localhost:8787";
const fx = (f) => fs.readFileSync(path.join(here, "fixtures", f)).toString("base64");
fs.mkdirSync(path.join(here, "out"), { recursive: true });

const els = (b) => b.steps.flatMap((s) => s.elements ?? []);
const has = (b, k) => els(b).some((e) => e.kind === k);
const pages = (b) => 1 + b.steps.slice(1).filter((s) => s.newPage).length;
const text = (b) => JSON.stringify(b);

const cases = [
  { name: "ask-richest", route: "/api/ask", body: { prompt: "who's the richest guy rn?", mode: "ask" }, expect: (b) => has(b, "photo") || "expected a real photo" },
  { name: "ask-rocket", route: "/api/ask", body: { prompt: "show me a rocket launching into space", mode: "ask" }, expect: (b) => els(b).some((e) => e.kind === "illustration" && e.motion && e.motion !== "none") || "expected an animated illustration" },
  { name: "ask-chart", route: "/api/ask", body: { prompt: "compare the populations of India, China, USA and Indonesia", mode: "ask" }, expect: (b) => has(b, "chart") || "expected a chart" },
  { name: "teach-heart", route: "/api/ask", body: { prompt: "teach me how the heart pumps blood", mode: "teach" }, expect: (b) => (pages(b) >= 2 && (has(b, "illustration") || has(b, "ink"))) || `expected 2+ pages with pictures (got ${pages(b)} pages)` },
  { name: "teach-numpy", route: "/api/ask", body: { prompt: "teach me numpy arrays", mode: "teach" }, expect: (b) => b.steps.length >= 4 || `lesson had only ${b.steps.length} steps` },
  { name: "3d-transformer", route: "/api/ask", body: { prompt: "show me inside an electrical transformer", mode: "3d" }, expect: (b) => has(b, "model3d") || "expected a model3d" },
  { name: "lasso-doodle", route: "/api/ask", body: { prompt: "What did I draw? Name it and label its parts with arrows right on my page.", mode: "lasso", image: fx("doodle.png") }, expect: (b) => (has(b, "arrow") || has(b, "text")) || "expected labels" },
  { name: "snap-solve", route: "/api/snap", body: { mode: "solve", image: fx("question.jpg"), prompt: "question 3" }, expect: (b) => (/x\s*=\s*1/.test(text(b)) && /3/.test(text(b))) || "expected x = 1 and x = 3" },
  { name: "snap-check", route: "/api/snap", body: { mode: "check", image: fx("work.jpg") }, expect: (b) => (els(b).some((e) => e.kind === "check" && e.verdict === "wrong") && !!b.score) || "expected a wrong-marked line and a score" },
];

const KINDS = new Set(["text", "illustration", "photo", "ink", "arrow", "mark", "chart", "grid", "check", "model3d"]);

function validate(b) {
  const errs = [];
  if (!Array.isArray(b.steps) || !b.steps.length) return ["no steps"];
  if (/[-]/.test(JSON.stringify(b))) errs.push("citation characters leaked");
  b.steps.forEach((s, i) => { if (!s.narration?.trim()) errs.push(`step ${i}: empty narration`); });
  if (!els(b).some((e) => ["illustration", "photo", "ink", "chart", "grid", "model3d"].includes(e.kind))) errs.push("no visual at all");
  els(b).forEach((e, i) => {
    const at = `el ${i} (${e.kind})`;
    if (!KINDS.has(e.kind)) return errs.push(`${at}: unknown kind`);
    if (e.kind === "arrow") { if (e.from?.length !== 2 || e.to?.length !== 2) errs.push(`${at}: bad from/to`); return; }
    if (e.kind === "ink") {
      if (!e.paths?.length) errs.push(`${at}: no paths`);
      for (const d of e.paths ?? []) if (!/^\s*[Mm]/.test(d) || /[^MmLlHhVvCcSsQqTtAaZz0-9eE.,\-+\s]/.test(d)) errs.push(`${at}: bad path "${d.slice(0, 30)}"`);
      return;
    }
    if (typeof e.x !== "number" || typeof e.y !== "number") errs.push(`${at}: no position`);
    else if (e.x < -5 || e.x > 360 || e.y < -5 || e.y > 560) errs.push(`${at}: off the page (${e.x},${e.y})`);
    if (["text", "check"].includes(e.kind) && !e.text) errs.push(`${at}: no text`);
    if (["illustration", "photo"].includes(e.kind) && !e.imageUrl) errs.push(`${at}: no imageUrl`);
    if (e.kind === "chart" && !(e.bars?.length >= 2)) errs.push(`${at}: bad bars`);
    if (e.kind === "model3d" && !(e.parts?.length >= 2)) errs.push(`${at}: too few parts`);
  });
  return errs;
}

async function run(c) {
  const t0 = Date.now();
  let firstStep = null, board = null, err = null;
  try {
    const res = await fetch(`${BASE}${c.route}?stream=1`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(c.body), signal: AbortSignal.timeout(300_000) });
    const reader = res.body.getReader(); const dec = new TextDecoder(); let buf = "";
    for (;;) {
      const { value, done } = await reader.read(); if (done) break;
      buf += dec.decode(value, { stream: true });
      let nl; while ((nl = buf.indexOf("\n")) >= 0) {
        const line = buf.slice(0, nl); buf = buf.slice(nl + 1); if (!line.trim()) continue;
        const ev = JSON.parse(line);
        if (ev.type === "step" && firstStep == null) firstStep = Date.now() - t0;
        if (ev.type === "done") board = ev.board;
        if (ev.type === "error") err = ev.error;
      }
    }
  } catch (e) { err = e.message; }
  if (!board) return { c, ok: false, ms: Date.now() - t0, errs: [err ?? "no board"] };
  fs.writeFileSync(path.join(here, "out", `${c.name}.json`), JSON.stringify(board, null, 1));
  const errs = validate(board);
  const exp = c.expect(board); if (exp !== true) errs.push(exp);
  // every illustration must actually get drawn
  for (const e of els(board).filter((e) => e.kind === "illustration" && e.imageUrl?.startsWith("/media/"))) {
    const r = await fetch(BASE + e.imageUrl, { signal: AbortSignal.timeout(120_000) }).catch(() => null);
    if (!r?.ok) errs.push(`illustration ${e.imageUrl} failed (${r?.status})`);
  }
  return { c, ok: errs.length === 0, ms: Date.now() - t0, firstStep, errs, board };
}

const results = await Promise.all(cases.map(run));
let fails = 0;
for (const r of results) {
  if (!r.ok) fails++;
  const b = r.board;
  const kinds = b ? [...new Set(els(b).map((e) => e.kind))].join(",") : "";
  console.log(`${r.ok ? "PASS" : "FAIL"}  ${r.c.name.padEnd(15)} first page ${String(r.firstStep ?? "-").padStart(5)}ms · total ${String(r.ms).padStart(6)}ms  ${b ? `"${b.title}" pages=${pages(b)} steps=${b.steps.length} [${kinds}]` : ""}`);
  for (const e of r.errs) console.log(`      - ${e}`);
}
console.log(`\n${results.length - fails}/${results.length} passed`);
process.exit(fails ? 1 : 0);
