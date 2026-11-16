// The "board" is the contract between the backend and the Android app.
//
// Drewy draws on a shared canvas made of PAGES. Each page is a 360 x 560 sheet (x right, y down).
// A board is a list of steps; each step is narrated and draws a few things onto a page,
// either a fresh page (newPage: true) or on top of the current one (newPage: false).
// Every element is positioned on the page, like a person sketching on paper.

export const PAGE_W = 360;
export const PAGE_H = 560;

const str = (description) => ({ type: "string", description });
const num = (description) => ({ type: "number", description });
const COLORS = ["ink", "blue", "pink", "sun", "green"];
const MOTIONS = ["none", "float", "fly", "bounce", "spin", "wiggle", "pulse"];

const element = {
  type: "object",
  description:
    "One thing Drewy draws on the page. Set `kind`, its position (x, y = top-left in page units) and only the fields that kind uses.",
  properties: {
    kind: {
      type: "string",
      enum: ["text", "illustration", "photo", "ink", "arrow", "mark", "chart", "grid", "check", "model3d"],
      description: [
        "text: handwritten words (text, x, y, size, color, w = max width, rotate).",
        "illustration: a picture Drewy DRAWS with AI image generation — the best choice for objects, animals, scenes, machines, body parts, concepts (prompt, x, y, w, h, motion). Drawn as a cute hand-drawn sticker with labels if useful.",
        "photo: a REAL photo of a specific real person/place/landmark/product (query = exact Wikipedia title, caption, x, y, w, rotate, motion).",
        "ink: hand-drawn strokes — diagrams, doodles, flow lines (paths in PAGE coordinates, color, width, anim).",
        "arrow: a hand-drawn arrow from one point to another, to connect labels to parts (from, to, bend, color).",
        "mark: hand-drawn emphasis around an area — circle, underline or box (x, y, w, h, style, color).",
        "chart: hand-drawn bar chart (x, y, w, h, caption, bars).",
        "grid: table / array of cells (x, y, cells, highlight).",
        "check: grading one line of the student's work (text, verdict, why, x, y).",
        "model3d: interactive 3D model (x, y, w, h, caption, parts).",
      ].join(" "),
    },
    x: num("left edge, 0-360"),
    y: num("top edge, 0-560"),
    w: num("width in page units"),
    h: num("height in page units"),
    rotate: num("tilt in degrees, -8..8, for a hand-placed feel"),
    text: str("text/check: the words. Short handwritten phrases, not paragraphs."),
    size: { type: "string", enum: ["s", "m", "l", "xl"], description: "text size: s 18, m 24, l 32, xl 44 units tall" },
    color: { type: "string", enum: COLORS, description: "ink colour. ink = dark navy (default)." },
    prompt: str("illustration: what to draw, concrete and visual (subject, key parts, labels to write on it)."),
    query: str("photo: exact subject to look up."),
    caption: str("photo/chart/model3d: short handwritten caption"),
    motion: { type: "string", enum: MOTIONS, description: "illustration/photo: how it comes alive after it's drawn. fly for rockets/birds/planes, float for balloons/planets/clouds, bounce for balls/animals, spin for wheels/planets, wiggle for creatures, pulse for hearts/lights." },
    paths: {
      type: "array",
      description: "ink: SVG path data strings in PAGE coordinates (M, L, Q, C, A, Z). Draw like a person with a marker.",
      items: { type: "string" },
    },
    width: num("ink: stroke width 2-8 (default 4)"),
    fill: { type: "boolean", description: "ink: lightly fill closed shapes" },
    anim: {
      type: "string",
      enum: ["none", "flow", "flicker", "pulse", "spin", "bob"],
      description: "ink: keep animating after drawn. flow = dashes run along the line (blood, current, wind, water); flicker = flames/stars; pulse = beating/glowing; spin = rotating; bob = gentle up-down.",
    },
    from: { type: "array", items: { type: "number" }, description: "arrow: [x, y] start" },
    to: { type: "array", items: { type: "number" }, description: "arrow: [x, y] end (arrowhead here)" },
    bend: num("arrow: curve amount -1..1 (0 straight)"),
    style: { type: "string", enum: ["circle", "underline", "box"], description: "mark style" },
    verdict: { type: "string", enum: ["correct", "wrong", "unclear"], description: "check only" },
    why: str("check: short note on the mistake"),
    bars: {
      type: "array",
      items: { type: "object", properties: { label: str("label"), value: num("value") }, required: ["label", "value"] },
    },
    cells: { type: "array", items: { type: "array", items: { type: "string" } } },
    highlight: { type: "array", items: { type: "integer" }, description: "grid: [row, col] to highlight" },
    parts: {
      type: "array",
      description: "model3d: 3-30 primitive parts centred near the origin, ~3 units across, y up.",
      items: {
        type: "object",
        properties: {
          name: str("part name"),
          shape: { type: "string", enum: ["box", "sphere", "cylinder", "cone", "torus"] },
          position: { type: "array", items: { type: "number" } },
          size: { type: "array", items: { type: "number" }, description: "box [w,h,d]; sphere [d]; cylinder/cone [d,h]; torus [d, thickness]" },
          rotation: { type: "array", items: { type: "number" } },
          color: str("hex colour"),
          label: { type: "boolean" },
          spin: { type: "boolean" },
        },
        required: ["name", "shape", "position", "size", "color"],
      },
    },
  },
  required: ["kind"],
};

export const boardSchema = {
  type: "object",
  properties: {
    title: str("Short title (max 6 words)."),
    mood: { type: "string", enum: ["happy", "idle", "thinking"] },
    steps: {
      type: "array",
      description:
        "Drawn and narrated in order. Each step draws 1-5 elements. newPage: true starts a fresh page (first step usually true, unless annotating the user's own drawing).",
      items: {
        type: "object",
        properties: {
          narration: str("What Drewy SAYS out loud while drawing this step: 1-2 short, warm sentences. No markdown, no lists."),
          newPage: { type: "boolean", description: "start a new blank page before drawing this step" },
          elements: { type: "array", items: element },
        },
        required: ["narration", "elements"],
      },
    },
    followUps: { type: "array", description: "2-3 short follow-up chips (max 4 words each).", items: { type: "string" } },
    score: str("check mode only: e.g. '2 of 3 steps right — so close!'"),
  },
  required: ["title", "steps", "followUps"],
};

// OpenAI Responses API function tool. Not strict: most element fields are optional by kind.
export const renderBoardTool = {
  type: "function",
  name: "render_board",
  description: "Draw your answer on the shared canvas. This is the ONLY way the user sees anything — always finish by calling it exactly once.",
  parameters: boardSchema,
  strict: false,
};
