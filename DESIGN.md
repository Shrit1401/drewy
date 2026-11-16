# Drewy design system

Drewy should feel like **a friendly sketchbook that draws back**: soft pastel paper, chunky navy outlines, rounded everything, and a small cloud character who reacts to what you do. It should be playful and bouncy, but never noisy.

All tokens live in [`ui/theme/Theme.kt`](android/app/src/main/java/app/drewy/ui/theme/Theme.kt) and mirror the Figma file *"Drewy — AI Canvas App Wireframes"* (Design System + Screens v2 pages). Use `DrewyTheme.colors / .type / .spacing / .radius`. Never hard-code a hex value or a font in a screen.

```
android/app/src/main/java/app/drewy/ui/
├── theme/Theme.kt          palette, semantic colours, type, spacing, radius
├── components/Components.kt buttons, chips, ask bar, bubbles, marks, polaroid, slider
├── icons/DrewyIcons.kt     hand-built stroke icon set
└── mascot/                 Drewy the cloud (Drewy.kt) + speech bubble, confetti, intro (Buddy.kt)
```

---

## 1. Principles

1. **Paper first.** The canvas is the product. Chrome (top bar, ask bar) floats above it as rounded pills, and the paper is never boxed in.
2. **Ink outlines.** Characters and key controls use a thick navy stroke (`#2B2D6E`, ~2.5dp in UI, 5 units on the mascot) instead of grey borders. This gives the hand-drawn, comic look.
3. **Soft, not flat.** Surfaces lift off the background with a purple-tinted soft shadow, not with hard borders.
4. **Everything bounces.** Every tappable thing squishes on press and springs back, with a light haptic.
5. **Drewy shows the state.** Loading, listening, errors and success are shown through the mascot's mood, not spinners or toasts.
6. **Handwriting is for Drewy's voice.** Caveat is only for things Drewy "writes" (captions, notes, hints). The UI itself uses Nunito and Fredoka.

---

## 2. Colour

### Palette (primitives, `DrewyPalette`)

| Family | Tokens |
|---|---|
| Navy (ink + text) | `navy900 #2B2D6E` · `navy700 #4A4C78` · `navy500 #6B6D9A` · `navy400 #8A8CB3` · `navy100 #E4E6F2` · `navy50 #EEEFF7` |
| Ink (camera) | `ink950 #17182B` |
| Blue (brand) | `blue600 #2F6BF0` · `blue500 #3D7BFF` · `blue400 #5B93FF` · `blue100 #DCE6FF` |
| Background tints | `lavender100 #E9EDFF` · `blush100 #FBE9F6` |
| Yellow (sun) | `yellow400 #FFC21A` · `yellow300 #F2D24B` · `yellow100 #FFF1A8` · `yellow50 #FFFBE6` |
| Pink | `pink500 #FF5C8A` · `pink300 #FF8FBF` · `pink100 #FFD6E8` |
| Mint | `mint700 #1C7A63` · `mint500 #2EC4A0` · `mint100 #CFF5E7` |
| Paper | `paper200 #ECE8DA` · `paper50 #FFFDF7` |

### Semantic tokens (`DrewyTheme.colors`)

Always use these in screens. Only reach for the palette inside the mascot or icon drawing.

| Role | Token → value | Use |
|---|---|---|
| **Text** | `textPrimary` navy900 · `textSecondary` navy700 · `textSubtle` navy500 · `textMuted` navy400 · `textOnBrand` white · `textLink` blue500 | Headings/body · secondary · hints · placeholders · on blue · ghost buttons |
| **Backgrounds** | `bgApp` navy50 · `bgSurface` white · `bgPaper` paper50 · `bgSubtle` navy100 · `bgInverse` navy900 · `bgCamera` ink950 | App · cards/bars · canvas sheet · photo wells, inactive dots · dark chips · camera screen |
| **Brand** | `bgBrand` blue500 · `bgBrandStrong` blue600 · `bgBrandSoft` blue400 | Primary actions, user bubbles, cursor |
| **App gradient** | `appGradient` lavender100 → blush100 (top → bottom) | Full-screen backgrounds (Home) |
| **Brand gradient** | `brandGradient` blue400 → blue600 | Primary button fill |
| **Accents** (soft fills) | `accentYellow` · `accentPink` · `accentMint` · `accentSky` · `accentNote` | Chips, suggestion pills, sticky notes |
| **Pops** (loud) | `popPink` · `popSun` · `popGreen` · `popGreenInk` · `popBlush` | Marks (✗ / hint / ✓), sparkles, hearts, cheeks |
| **Borders** | `borderStrong` navy900 · `borderBrand` blue500 · `borderSubtle` navy100 · `borderNote` yellow300 · `borderGrid` paper200 | Ink outlines · focus · dividers · notes · paper grid |

**Drawing inks:** Drewy and the user draw with `colors.ink(name)`: `"blue"`, `"pink"`, `"sun"`, `"green"`, or navy by default. The backend's board schema uses the same names, so keep the two in sync.

**Feedback colours:** correct = `popGreen`, wrong = `popPink`, hint = `popSun`. Never use red. Mistakes should feel gentle.

The app is **light-only**. The window background is `#E9EDFF` (lavender) with transparent, light status and navigation bars (`res/values/themes.xml`).

---

## 3. Typography

Three families, bundled in `res/font/`:

| Family | Personality | Used for |
|---|---|---|
| **Fredoka** (Medium/SemiBold/Bold) | Round, chunky, friendly | Display, titles, buttons |
| **Nunito** (Regular → ExtraBold) | Soft, very readable | Body, labels, captions |
| **Caveat** (Regular/Bold) | Handwriting | Anything Drewy *writes* |

| Style | Font | Size / line height | Use |
|---|---|---|---|
| `display` | Fredoka Bold | 36sp / 1.2 | Hero greeting |
| `title` | Fredoka SemiBold | 22sp / 1.25 | Screen titles |
| `heading` | Fredoka SemiBold | 17sp / 1.25 | Section labels, top-bar title |
| `button` | Fredoka SemiBold | 20sp / 1.2 | Button labels |
| `body` | Nunito SemiBold | 15sp / 1.45 | Default text, inputs |
| `bodyRegular` | Nunito Regular | 15sp / 1.5 | Long-form explanations |
| `label` | Nunito Bold | 14sp / 1.3 | Chips, suggestions, slider labels |
| `caption` | Nunito SemiBold | 12sp / 1.3 | Meta text |
| `overline` | Nunito Bold | 10sp / 1.2, +6% tracking, UPPERCASE | Kicker above top-bar titles |
| `handXL` / `handL` / `handM` | Caveat Bold | 34 / 28 / 22sp | Board headings, polaroid captions, speech bubbles, "hold to talk" hint |

The default body is SemiBold, not Regular, because it reads warmer and holds up against pastel backgrounds.

---

## 4. Spacing, radius, elevation

**Spacing (`DrewyTheme.spacing`):** a 4-point scale: `4 · 8 · 12 · 16 · 20 · 24 · 32` dp. Screen gutters are 16–20dp. Gaps inside a control are 8–12dp.

**Radius (`DrewyTheme.radius`):**

| Token | dp | Typical use |
|---|---|---|
| `sm` | 12 | Small tiles |
| `md` | 16 | Icon buttons |
| `lg` | 22 | Chips, suggestions, narration cards |
| `xl` | 28 | Large cards |
| `xxl` | 40 | Sheets |
| pill (`50%`) | — | Buttons, ask bar, top bar |

The canvas sheet is 18dp. Photos and polaroids stay nearly square at 4–6dp, so they read as real prints.

**Elevation:** two shadow helpers in `Components.kt`:

- `softShadow(shape, elevation)`: purple-tinted (`#3D338C` @ 25%). It is the default lift for every surface. Use 3dp for chips and buttons, 6–8dp for the ask bar and narration, 10dp for paper and polaroids.
- `brandGlow(shape)`: a 14dp blue glow (`#2E6BF0`). Use it only on the primary CTA and the brand (send / mic) button.

---

## 5. Components

| Component | Look | Notes |
|---|---|---|
| `DrewyButton` | Pill, 60dp tall. **Primary:** brand gradient + glow, white Fredoka. **Secondary:** white + 2.5dp navy outline. **Ghost:** blue text only. | One primary per screen. |
| `DrewyIconButton` | 46dp rounded square (`md`). **Default:** white. **Active:** sky. **Ghost:** transparent. **Brand:** 56dp blue circle + glow. | Background colour cross-fades over 220ms. |
| `DrewyChip` | `lg`-rounded pill in an accent colour (Yellow / Pink / Mint / Sky / White), icon + `label`. | Mode chips on Home. |
| `AskBar` | White pill, 2.5dp navy outline, 8dp shadow: camera · text field · mic/send. | Switches to a live `Waveform` + transcript while listening. Send scales in once there is text. Sending hides the keyboard so you can watch Drewy draw. |
| `TopBar` | White pill: back · overline + title · trailing actions. | Floats over the canvas. |
| `UserBubble` | Brand blue, white text, 20dp corners with a 6dp "tail" corner at bottom-right. | Max 280dp wide. |
| `Narration` | White `lg` card with a small talking Drewy and a quoted line. | Drewy's spoken words. |
| `SpeechBubble` | Comic bubble with a tail, Caveat text, springs in. | Drewy's quick reactions. |
| `Suggestion` | Sky (or inverse navy) pill with a sparkle icon. | Follow-up prompts. |
| `StepDots` | 8dp dots, the active one stretches to 20dp, brand blue / subtle. | Lesson and step progress. |
| `Mark` | 32dp circle: ✓ green, ✗ pink, 💡 sun, white icon. | "Check my work" grading. |
| `Polaroid` | White print, 6dp corners, tilted −3°, yellow tape strip, Caveat caption. | Real photos on the board. |
| `DrewySlider` | 6dp rounded track, blue fill, white knob with a 3dp blue ring. | 3D Cutaway / Explode. |
| `ConfettiBurst` | Sparkle confetti in sun, pink, green, blush and brand blues. | Celebrations (correct answer, finished lesson). |

### Icons (`DrewyIcons`)

These are custom vectors on a 24-unit grid with **2-unit round-capped, round-joined strokes**, drawn in black and tinted with `Icon(tint = …)`. They are kept stroke-based to match the ink-outline style. Default sizes are 20dp in chips, 22dp in buttons, and 26dp in brand buttons.

---

## 6. The canvas

- **Paper:** `bgPaper` (#FFFDF7, warm off-white) with a faint `borderGrid` square grid every 20 units, 18dp corners, and a 10dp soft shadow. Pages stack and get page dots at the bottom (active dot 20dp, others 8dp).
- **Drewy draws live:** handwriting appears with a pen cursor, sketches are stroked in order, charts grow, and photos drop in as polaroids. Placement follows Drewy's pencil, so the eye always knows where to look.
- **User ink:** pen, highlighter and eraser use the same ink palette. Shapes auto-snap to clean lines, boxes and ellipses.
- **Board content** comes from the backend as JSON (`backend/board-schema.mjs` ↔ `data/Board.kt`). Colours in that JSON are ink *names*, never hex values.

---

## 7. Drewy, the mascot

Drewy is a fluffy cloud drawn entirely in code (`ui/mascot/Drewy.kt`, 220×200 viewbox). It has no image assets.

- **Body:** a union of a rounded rect and five circles. It is filled with a white → `#F1F4FF` → `#CFD9FF` vertical gradient and outlined in 5-unit navy, with a glossy white highlight, little feet, and a soft ground shadow.
- **Face:** tall navy oval eyes with blue irises and two white catch-lights, pink (`pink300`) blush cheeks, and a navy mouth with a pink tongue.
- **Moods** (`Mood`): the first four match the Figma component set.

| Mood | Face | Extras | When |
|---|---|---|---|
| `Idle` | Open eyes, small smile | — | Default |
| `Listening` | Bigger eyes, "o" mouth, gentle squish pulse | Blue sound arcs + sparkles | Mic is on |
| `Thinking` | Eyes look up-right, wavy mouth, raised brow | Thought bubbles pop in | Waiting on the brain |
| `Happy` | ^ ^ closed eyes, open grin | Spinning sparkles, bounce | Answer done / correct |
| `Oops` | Worried brows, wobbly mouth | Sliding sweat drop | Errors, wrong answer |
| `Sleepy` | Closed curved eyes | Drifting Zs | Idle for a long time |
| `Surprised` | Huge eyes, round mouth | Yellow burst lines | Unexpected results |

- **Alive by default:** Drewy bobs (2.4s), breathes (3.2s), blinks at random 2.2–5.2s gaps (sometimes double-blinks), and squashes and stretches on every mood change. Its eyes can follow a point (`lookAt`), and its mouth flaps while TTS is speaking (`talking`).
- **Tap Drewy:** it hops, waves, bursts hearts, and gives a haptic.
- **App icon** (`res/drawable/ic_launcher_foreground.xml`) is a simplified Drewy: white cloud, navy outline, eyes, blush and smile on lavender `#E9EDFF`.

---

## 8. Motion

| Pattern | Spec |
|---|---|
| Press | `bouncyClick`: scale to 0.92 with a medium-bouncy spring, plus a light haptic. Use it on every tappable element. |
| Colour changes | `tween(220)` |
| Mood change | Squash to 0.86 (1.18 for Surprised), then a spring back (damping 0.42, stiffness 420) |
| Eye tracking | Low-bouncy, low-stiffness spring |
| Wave | Arm springs up (0.5 / 300), holds 1.3s, settles (0.7 / 200) |
| Enter | Scale-in + fade-in. Exit: fade-out. |
| Speech bubbles, confetti | Spring pop-in |
| Launch | `DrewyIntro`: Drewy floats up with a bounce, waves, and the wordmark writes itself |

Rules: use springs for anything physical and tweens for colour and opacity. Animate transforms and opacity, not layout. Any looping animation must stop when its `animate = false` flag is set.

---

## 9. Voice and copy

- Speak as Drewy: short, warm, lowercase-friendly ("hold to talk · tap to type", "Ask Drewy anything…").
- Teach, don't dump. "Check my work" points at the mistake and explains *why*, rather than just giving the answer.
- Use the ellipsis character (…) for in-progress states ("Listening…").

## 10. Adding something new: checklist

- [ ] Colours come from `DrewyTheme.colors` (add a semantic token if needed, and mirror it in Figma).
- [ ] Text uses a `DrewyType` style. Caveat is only for Drewy's handwriting.
- [ ] Radius comes from `DrewyTheme.radius` or a pill. Spacing is on the 4-point scale.
- [ ] Lift with `softShadow`. Use `brandGlow` only for the main action.
- [ ] Tappable → `bouncyClick`.
- [ ] State shown through a Drewy mood where it makes sense.
- [ ] Icons are 2-unit round strokes on a 24 grid.
