package app.drewy.canvas

import android.graphics.PathMeasure
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.drewy.DrewyApp
import app.drewy.data.Element
import app.drewy.data.PAGE_H
import app.drewy.data.PAGE_W
import app.drewy.three.Scene3D
import app.drewy.ui.components.Mark
import app.drewy.ui.components.MarkKind
import app.drewy.ui.components.softShadow
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.mascot.Drewy
import app.drewy.ui.mascot.Mood
import app.drewy.ui.theme.DrewyTheme
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val C = DrewyTheme.colors
private val T = DrewyTheme.type
private fun ease(x: Float) = EaseOutBack.transform(x.coerceIn(0f, 1f))

/** Size names → glyph height in page units. */
private fun sizeUnits(s: String?) = when (s) { "s" -> 18f; "l" -> 32f; "xl" -> 44f; else -> 24f }

/**
 * One sheet of paper: grid, Drewy's drawings, your ink, and Drewy himself drawing with his pencil.
 * @param u pixels per page unit.
 */
@Composable
fun PageView(
    page: PageUi,
    u: Float,
    state: PageState,
    active: Boolean,
    live: List<Offset>,
    liveTool: Tool,
    lasso: List<Offset>,
    onOpen3D: (Element) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val wDp = with(density) { (PAGE_W * u).toDp() }
    val hDp = with(density) { (PAGE_H * u).toDp() }
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.size(wDp, hDp).softShadow(shape, 10.dp).clip(shape).background(C.bgPaper)) {
        // paper grid
        Canvas(Modifier.fillMaxSize()) {
            var x = 20f; while (x < PAGE_W) { drawLine(C.borderGrid, Offset(x * u, 0f), Offset(x * u, size.height), 1f); x += 20f }
            var y = 20f; while (y < PAGE_H) { drawLine(C.borderGrid, Offset(0f, y * u), Offset(size.width, y * u), 1f); y += 20f }
        }
        // Drewy's drawings
        for (d in page.drawn) {
            androidx.compose.runtime.key(d) {
                DrawnElement(d, u, onPen = { if (active) state.pen = it }, onOpen3D = onOpen3D)
            }
        }
        // your ink (on top, so you can draw over anything)
        Canvas(Modifier.fillMaxSize()) {
            scale(u, u, pivot = Offset.Zero) {
                for (s in page.strokes) drawInk(s, if (state.lassoSelection.isNotEmpty() && s !in state.lassoSelection) 0.35f else 1f)
                if (live.size > 1 && liveTool != Tool.Lasso && liveTool != Tool.Eraser) {
                    drawInk(InkStroke(live.toList(), state.color, if (liveTool == Tool.Marker) 14f else 3.2f, if (liveTool == Tool.Marker) Tool.Marker else Tool.Pen))
                }
                if (lasso.size > 2) {
                    val p = Path().apply { moveTo(lasso[0].x, lasso[0].y); lasso.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(p, C.bgBrand.copy(alpha = 0.06f))
                    drawPath(p, C.borderBrand, style = Stroke(2.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }
            }
        }
        // Drewy thinking on the page while Sol plans the drawing (his spoken reaction in a bubble)
        androidx.compose.animation.AnimatedVisibility(
            visible = active && state.thinking,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f, stiffness = 300f), initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 0.3f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                app.drewy.ui.mascot.SpeechBubble(state.caption ?: "hmm…", tailLeft = false)
                Drewy(Mood.Thinking, Modifier.width(150.dp), talking = (LocalContext.current.applicationContext as DrewyApp).speaker.speaking)
            }
        }
        // Drewy holding the pencil, flying to wherever he's drawing
        AnimatedVisibility(
            active && state.pen != null && state.penPage == state.pages.indexOf(page),
            enter = fadeIn() + scaleIn(initialScale = 0.4f), exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            DrewyPen(state.pen ?: Offset(PAGE_W / 2, PAGE_H / 2), u, talking = (LocalContext.current.applicationContext as DrewyApp).speaker.speaking)
        }
    }
}

/** Drewy + his pencil. The pencil tip sits exactly at [at] (page units) and glides there with a soft spring. */
@Composable
private fun DrewyPen(at: Offset, u: Float, talking: Boolean) {
    val tip by animateOffsetAsState(Offset(at.x * u, at.y * u), spring(dampingRatio = 0.7f, stiffness = 260f), label = "pen")
    val t = rememberInfiniteTransition(label = "scribble")
    val wob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "wob")
    val density = LocalDensity.current
    val boxW = with(density) { 96.dp.toPx() }
    Box(Modifier.offset { IntOffset((tip.x - 4).roundToInt(), (tip.y - boxW + 4).roundToInt()) }.size(96.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val tipP = Offset(4f, size.height - 4f)
            rotate(-42f + wob * 3f, tipP) {
                val len = size.width * 0.62f; val w = size.width * 0.11f
                // graphite + wood
                drawPath(Path().apply { moveTo(tipP.x, tipP.y); lineTo(tipP.x + len * 0.16f, tipP.y - w / 2); lineTo(tipP.x + len * 0.16f, tipP.y + w / 2); close() }, Color(0xFFF3D9B1))
                drawPath(Path().apply { moveTo(tipP.x, tipP.y); lineTo(tipP.x + len * 0.06f, tipP.y - w * 0.2f); lineTo(tipP.x + len * 0.06f, tipP.y + w * 0.2f); close() }, C.textPrimary)
                // body
                drawRect(C.popSun, Offset(tipP.x + len * 0.16f, tipP.y - w / 2), Size(len * 0.68f, w))
                drawRect(Color(0xFFE0A400), Offset(tipP.x + len * 0.16f, tipP.y - w * 0.08f), Size(len * 0.68f, w * 0.16f))
                // eraser
                drawRoundRect(C.popBlush, Offset(tipP.x + len * 0.84f, tipP.y - w / 2), Size(len * 0.16f, w), CornerRadius(w * 0.3f))
                drawRoundRect(C.textPrimary, Offset(tipP.x + len * 0.16f, tipP.y - w / 2), Size(len * 0.84f, w), CornerRadius(w * 0.3f), style = Stroke(2.2f))
            }
        }
        Drewy(Mood.Happy, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp).width(54.dp), talking = talking)
    }
}

@Composable
private fun DrawnElement(d: DrawnUi, u: Float, onPen: (Offset) -> Unit, onOpen3D: (Element) -> Unit) {
    val el = d.el
    val p = d.progress.value
    val pen: (Offset) -> Unit = { if (p < 1f) onPen(it) }
    when (el.kind) {
        "text" -> Placed(el, u) { WrittenText(el.text.orEmpty(), sizeUnits(el.size), C.ink(el.color), el.w ?: 300f, p, u, rotate = el.rotate ?: 0f) { pen(Offset(el.x!! + it.x, el.y!! + it.y)) } }
        "ink" -> InkEl(el, p, u, pen)
        "arrow" -> ArrowEl(el, p, u, pen)
        "mark" -> MarkEl(el, p, u, pen)
        "illustration" -> Picture(d, u, pen, photo = false)
        "photo" -> Picture(d, u, pen, photo = true)
        "chart" -> Placed(el, u) { ChartEl(el, p, u) }.also { SideEffect { pen(Offset(el.x!! + (el.w ?: 300f) * p, el.y!! + (el.h ?: 180f) * 0.75f)) } }
        "grid" -> Placed(el, u) { GridEl(el, p, u) }.also { SideEffect { pen(Offset(el.x!! + (el.w ?: 300f) * p, el.y!! + 30f)) } }
        "check" -> Placed(el, u) { CheckEl(el, p, u) { pen(Offset(el.x!! + it.x, el.y!! + it.y)) } }
        "model3d" -> Placed(el, u) { Model3DEl(el, p, u, onOpen3D) }.also {
            // Drewy hovers over the model, "placing" each part as it assembles
            SideEffect { pen(Offset((el.x ?: 0f) + (el.w ?: 320f) * (0.35f + 0.3f * sin(p * 18f)), (el.y ?: 0f) + (el.h ?: 300f) * (0.85f - 0.6f * p))) }
        }
        else -> Placed(el, u) { WrittenText(el.text ?: el.caption.orEmpty(), 24f, C.ink(el.color), el.w ?: 300f, p, u) { pen(Offset(el.x!! + it.x, el.y!! + it.y)) } }
    }
}

@Composable
private fun Placed(el: Element, u: Float, content: @Composable () -> Unit) {
    Box(Modifier.offset { IntOffset(((el.x ?: 0f) * u).roundToInt(), ((el.y ?: 0f) * u).roundToInt()) }) { content() }
}

// ---------------------------------------------------------------- handwriting

/** Writes text letter by letter, line by line, like a pen. Reports the pen tip (units, relative to the text). */
@Composable
fun WrittenText(
    text: String, units: Float, color: Color, widthUnits: Float, progress: Float, u: Float,
    rotate: Float = 0f, align: TextAlign = TextAlign.Start, onPen: (Offset) -> Unit = {},
) {
    val density = LocalDensity.current
    val style = TextStyle(
        fontFamily = app.drewy.ui.theme.Caveat, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = color,
        fontSize = with(density) { (units * u * 0.9f).toSp() }, lineHeight = with(density) { (units * u * 1.05f).toSp() }, textAlign = align,
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val n = (progress * text.length).coerceIn(0f, text.length.toFloat())
    Text(
        text, style = style, onTextLayout = { layout = it },
        modifier = Modifier.width(with(density) { (widthUnits * u).toDp() }).rotate(rotate).drawWithContent {
            val l = layout
            if (progress >= 1f || l == null) { if (progress > 0f || l == null) drawContent(); return@drawWithContent }
            val idx = n.toInt()
            for (line in 0 until l.lineCount) {
                val s = l.getLineStart(line); val e = l.getLineEnd(line, visibleEnd = true)
                val top = l.getLineTop(line); val bottom = l.getLineBottom(line)
                when {
                    idx >= e -> clipRect(0f, top, size.width, bottom) { this@drawWithContent.drawContent() }
                    idx > s || (idx == s && n > s) -> {
                        val x0 = l.getHorizontalPosition(idx, true)
                        val x1 = if (idx + 1 <= e) l.getHorizontalPosition((idx + 1).coerceAtMost(e), true) else x0
                        val x = x0 + (x1 - x0) * (n - idx)
                        clipRect(0f, top, x, bottom) { this@drawWithContent.drawContent() }
                        break
                    }
                    else -> break
                }
            }
        },
    )
    val l = layout
    if (l != null && progress > 0f && progress < 1f) {
        val idx = n.toInt().coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val line = l.getLineForOffset(idx)
        val x = l.getHorizontalPosition(idx, true)
        val y = l.getLineBaseline(line) - (l.getLineBottom(line) - l.getLineTop(line)) * 0.15f
        SideEffect { onPen(Offset(x / u, y / u)) }
    }
}

// ---------------------------------------------------------------- ink, arrows, marks

private class ParsedInk(val path: Path, val length: Float, val bounds: Rect)

private fun parseInk(d: String): ParsedInk? = runCatching {
    val path = PathParser().parsePathString(d).toPath()
    val pm = PathMeasure(path.asAndroidPath(), false)
    var len = 0f; do { len += pm.length } while (pm.nextContour())
    val r = android.graphics.RectF(); path.asAndroidPath().computeBounds(r, true)
    ParsedInk(path, len, Rect(r.left, r.top, r.right, r.bottom))
}.getOrNull()

/** Draw a path up to [len] (units); returns the pen tip. */
private fun DrawScope.drawPathPart(path: Path, len: Float, color: Color, width: Float, effect: PathEffect? = null): Offset? {
    val pm = PathMeasure(path.asAndroidPath(), false)
    val seg = android.graphics.Path()
    var left = len; var tip: Offset? = null
    val pos = FloatArray(2)
    do {
        val l = pm.length
        val take = left.coerceAtMost(l)
        if (take > 0f) { pm.getSegment(0f, take, seg, true); pm.getPosTan(take, pos, null); tip = Offset(pos[0], pos[1]) }
        left -= l
    } while (left > 0f && pm.nextContour())
    drawPath(seg.asComposePath(), color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    return tip
}

@Composable
private fun InkEl(el: Element, progress: Float, u: Float, onPen: (Offset) -> Unit) {
    val parsed = remember(el) { el.paths.mapNotNull(::parseInk) }
    val total = parsed.sumOf { it.length.toDouble() }.toFloat().coerceAtLeast(1f)
    val bounds = remember(parsed) { parsed.map { it.bounds }.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) } ?: Rect.Zero }
    val color = C.ink(el.color)
    val width = (el.width ?: 4f).coerceIn(1.5f, 10f)
    val alive = progress >= 1f && el.anim != null && el.anim != "none"
    val t = rememberInfiniteTransition(label = "ink")
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "c")
    var tip by remember { mutableStateOf<Offset?>(null) }
    Canvas(Modifier.fillMaxSize()) {
        scale(u, u, pivot = Offset.Zero) {
            val c = bounds.center
            val k = if (!alive) 1f else when (el.anim) { "pulse" -> 1f + 0.07f * sin(clock * 2 * PI.toFloat() * 2); "flicker" -> 1f + 0.04f * sin(clock * 2 * PI.toFloat() * 9); else -> 1f }
            val rot = if (alive && el.anim == "spin") clock * 360f else 0f
            val dy = if (alive && el.anim == "bob") sin(clock * 2 * PI.toFloat()) * 4f else 0f
            val alpha = if (alive && el.anim == "flicker") 0.78f + 0.22f * sin(clock * 2 * PI.toFloat() * 7) else 1f
            translate(0f, dy) { rotate(rot, c) { scale(k, k, c) {
                var used = 0f
                val drawLen = total * progress
                for (pi in parsed) {
                    val rem = drawLen - used
                    if (rem <= 0f) break
                    if (rem >= pi.length) {
                        if (el.fill && progress >= 1f) drawPath(pi.path, color.copy(alpha = 0.16f))
                        if (alive && el.anim == "flow") {
                            drawPath(pi.path, color.copy(alpha = 0.3f), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(pi.path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f), -clock * 88f)))
                        } else drawPath(pi.path, color.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else tip = drawPathPart(pi.path, rem, color, width)
                    used += pi.length
                }
            } } }
        }
    }
    tip?.let { SideEffect { onPen(it) } }
}

@Composable
private fun ArrowEl(el: Element, progress: Float, u: Float, onPen: (Offset) -> Unit) {
    val from = Offset(el.from.getOrElse(0) { 0f }, el.from.getOrElse(1) { 0f })
    val to = Offset(el.to.getOrElse(0) { 0f }, el.to.getOrElse(1) { 0f })
    val path = remember(el) {
        val mid = (from + to) / 2f; val d = to - from; val len = hypot(d.x, d.y).coerceAtLeast(1f)
        val perp = Offset(-d.y / len, d.x / len) * ((el.bend ?: 0.25f) * len * 0.5f)
        Path().apply { moveTo(from.x, from.y); quadraticTo(mid.x + perp.x, mid.y + perp.y, to.x, to.y) }
    }
    val color = C.ink(el.color)
    var tip by remember { mutableStateOf<Offset?>(null) }
    Canvas(Modifier.fillMaxSize()) {
        scale(u, u, pivot = Offset.Zero) {
            val pm = PathMeasure(path.asAndroidPath(), false); val len = pm.length
            tip = drawPathPart(path, len * (progress / 0.78f).coerceAtMost(1f), color, 3.4f)
            if (progress > 0.78f) {
                val tan = FloatArray(2); pm.getPosTan(len, null, tan)
                val ang = atan2(tan[1], tan[0]); val hl = 12f * ((progress - 0.78f) / 0.22f).coerceIn(0f, 1f)
                for (s in listOf(-1f, 1f)) {
                    val a = ang + PI.toFloat() + s * 0.5f
                    drawLine(color, to, Offset(to.x + cos(a) * hl, to.y + sin(a) * hl), 3.4f, StrokeCap.Round)
                }
            }
        }
    }
    if (progress < 1f) tip?.let { SideEffect { onPen(it) } }
}

@Composable
private fun MarkEl(el: Element, progress: Float, u: Float, onPen: (Offset) -> Unit) {
    val r = Rect(el.x ?: 0f, el.y ?: 0f, (el.x ?: 0f) + (el.w ?: 100f), (el.y ?: 0f) + (el.h ?: 30f))
    val path = remember(el) {
        when (el.style) {
            "underline" -> Path().apply { moveTo(r.left, r.bottom); cubicTo(r.left + r.width * 0.3f, r.bottom + 6f, r.left + r.width * 0.65f, r.bottom + 7f, r.right, r.bottom - 3f) }
            "box" -> Path().apply { moveTo(r.left - 4f, r.top - 2f); lineTo(r.right + 6f, r.top - 5f); lineTo(r.right + 3f, r.bottom + 5f); lineTo(r.left - 6f, r.bottom + 3f); lineTo(r.left - 2f, r.top - 8f) }
            else -> Path().apply {
                val w = r.width + 22f; val h = r.height + 18f; val x = r.left - 11f; val y = r.top - 9f
                moveTo(x + w * 0.08f, y + h * 0.62f)
                cubicTo(x - w * 0.04f, y + h * 0.05f, x + w * 0.55f, y - h * 0.22f, x + w * 0.86f, y + h * 0.06f)
                cubicTo(x + w * 1.08f, y + h * 0.24f, x + w * 1.06f, y + h * 0.92f, x + w * 0.62f, y + h * 1.06f)
                cubicTo(x + w * 0.22f, y + h * 1.16f, x - w * 0.02f, y + h * 0.92f, x + w * 0.12f, y + h * 0.38f)
            }
        }
    }
    var tip by remember { mutableStateOf<Offset?>(null) }
    Canvas(Modifier.fillMaxSize()) {
        scale(u, u, pivot = Offset.Zero) {
            val len = PathMeasure(path.asAndroidPath(), false).length
            tip = drawPathPart(path, len * progress, C.ink(el.color ?: "pink"), 3.6f)
        }
    }
    if (progress < 1f) tip?.let { SideEffect { onPen(it) } }
}

// ---------------------------------------------------------------- pictures that come alive

/**
 * Illustration / photo: Drewy first scribbles a pencil sketch in its spot, then paints the real picture in with
 * brush strokes, then it comes alive (fly / float / bounce / spin / wiggle / pulse).
 */
@Composable
private fun Picture(d: DrawnUi, u: Float, onPen: (Offset) -> Unit, photo: Boolean) {
    val el = d.el
    val app = LocalContext.current.applicationContext as DrewyApp
    val density = LocalDensity.current
    val x = el.x ?: 0f; val y = el.y ?: 0f; val w = el.w ?: 260f
    val h = if (photo) w * 1.18f else (el.h ?: w)
    val reveal = remember { Animatable(if (d.restored) 1f else 0f) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, failed) {
        if (loaded || failed) d.ready = true
        if (loaded && reveal.value < 1f) reveal.animateTo(1f, tween(1500, easing = LinearEasing))
        if (loaded || failed) d.settled = true
    }
    val t = rememberInfiniteTransition(label = "alive")
    val clock by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "clock")
    val fast by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "fast")
    val alive = reveal.value >= 1f
    val tau = 2 * PI.toFloat()
    // motion (in page units)
    val (mx, my, mr, ms) = if (!alive) listOf(0f, 0f, 0f, 1f) else when (el.motion) {
        "fly" -> listOf(sin(clock * tau) * w * 0.12f, -sin(clock * tau * 2) * 14f - 6f, cos(clock * tau) * 6f + 4f, 1f)
        "float" -> listOf(0f, sin(clock * tau) * 7f, sin(clock * tau) * 2f, 1f)
        "bounce" -> listOf(0f, -abs(sin(clock * tau * 2)) * 18f, 0f, 1f + 0.03f * cos(clock * tau * 4))
        "spin" -> listOf(0f, 0f, clock * 360f, 1f)
        "wiggle" -> listOf(0f, 0f, sin(fast * tau) * 5f, 1f)
        "pulse" -> listOf(0f, 0f, 0f, 1f + 0.06f * sin(clock * tau * 3))
        else -> listOf(0f, 0f, 0f, 1f)
    }
    val wDp = with(density) { (w * u).toDp() }; val hDp = with(density) { (h * u).toDp() }
    Box(Modifier.offset { IntOffset((x * u).roundToInt(), (y * u).roundToInt()) }.size(wDp, hDp)) {
        // soft ground shadow for things that hover
        if (alive && el.motion in listOf("fly", "float", "bounce")) {
            Canvas(Modifier.fillMaxSize()) {
                val k = (1f + my / 40f).coerceIn(0.6f, 1.1f)
                drawOval(C.textPrimary.copy(alpha = 0.08f * k), Offset(size.width * (0.5f - 0.3f * k) + mx * u, size.height * 0.92f), Size(size.width * 0.6f * k, size.height * 0.06f))
            }
        }
        // fly trail sparkles
        if (alive && el.motion == "fly") {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..6) {
                    val c2 = clock - i * 0.025f
                    val px = size.width / 2 + sin(c2 * tau) * w * 0.12f * u
                    val py = size.height * 0.78f - (sin(c2 * tau * 2) * 14f + 6f) * u + i * 10f * u
                    drawCircle(listOf(C.popSun, C.popPink, C.bgBrandSoft)[i % 3].copy(alpha = 0.55f * (1f - i / 7f)), (5f - i * 0.6f) * u, Offset(px, py))
                }
            }
        }
        // pencil sketch while the picture is being made
        if (!alive) Scribble(d.progress.value, reveal.value, u, w, h) { onPen(Offset(x + it.x, y + it.y)) }
        // the picture, painted in with brush strokes
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = mx * u; translationY = my * u; rotationZ = mr + (el.rotate ?: if (photo) -3f else 0f); scaleX = ms; scaleY = ms
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                if (reveal.value >= 1f) { drawContent(); return@drawWithContent }
                drawContent()
                val bp = brushPath(size.width, size.height)
                val len = PathMeasure(bp.asAndroidPath(), false).length
                val seg = android.graphics.Path(); PathMeasure(bp.asAndroidPath(), false).getSegment(0f, len * reveal.value, seg, true)
                drawPath(seg.asComposePath(), Color.Black, style = Stroke(size.height / 5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.DstIn)
            },
        ) {
            if (photo) PolaroidFrame(el, u) { Img(app.api.resolve(el.imageUrl), { loaded = true }, { failed = true }) }
            else Img(app.api.resolve(el.imageUrl), { loaded = true }, { failed = true })
        }
        if (failed) Text("couldn't draw this one", style = T.caption, color = C.textMuted, modifier = Modifier.align(Alignment.Center))
    }
    // pen follows the brush while painting
    if (loaded && reveal.value in 0.001f..0.999f) {
        val bp = remember(w, h) { brushPath(w, h) }
        val pm = remember(bp) { PathMeasure(bp.asAndroidPath(), false) }
        val pos = FloatArray(2); pm.getPosTan(pm.length * reveal.value, pos, null)
        SideEffect { onPen(Offset(x + pos[0], y + pos[1])) }
    }
}

@Composable
private fun Img(url: String?, onOk: () -> Unit, onFail: () -> Unit) {
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
        onSuccess = { onOk() }, onError = { onFail() },
    )
}

@Composable
private fun PolaroidFrame(el: Element, u: Float, photo: @Composable () -> Unit) {
    val density = LocalDensity.current
    Column(
        Modifier.fillMaxSize().softShadow(RoundedCornerShape(6.dp), 8.dp).background(C.bgSurface, RoundedCornerShape(6.dp)).padding(with(density) { (8f * u).toDp() }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(C.bgSubtle)) { photo() }
        val cap = el.caption ?: el.query.orEmpty()
        if (cap.isNotBlank()) WrittenText(cap, 22f, C.textPrimary, (el.w ?: 200f) - 16f, 1f, u, align = TextAlign.Center)
    }
}

/** Zig-zag brush strokes covering a w x h box, top to bottom. */
private fun brushPath(w: Float, h: Float): Path = Path().apply {
    val rows = 6
    moveTo(w * 0.04f, h * 0.06f)
    for (r in 0 until rows) {
        val yy = h * (0.08f + 0.86f * r / (rows - 1))
        if (r % 2 == 0) { lineTo(w * 0.96f, yy); lineTo(w * 0.96f, yy + h * 0.17f / 2) } else { lineTo(w * 0.04f, yy); lineTo(w * 0.04f, yy + h * 0.17f / 2) }
    }
}

/** A loose pencil sketch Drewy scribbles while the real picture is being made. */
@Composable
private fun Scribble(progress: Float, reveal: Float, u: Float, w: Float, h: Float, onPen: (Offset) -> Unit) {
    val t = rememberInfiniteTransition(label = "scrib")
    val loop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "l")
    val path = remember(w, h) {
        Path().apply {
            val cx = w / 2; val cy = h / 2; val rx = w * 0.32f; val ry = h * 0.3f
            moveTo(cx + rx, cy)
            for (i in 1..72) { val a = i / 72f * 2 * PI.toFloat() * 3; val k = 1f - i / 72f * 0.45f; lineTo(cx + cos(a) * rx * k + sin(a * 3) * 4f, cy + sin(a) * ry * k + cos(a * 2) * 4f) }
        }
    }
    var tip by remember { mutableStateOf<Offset?>(null) }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - reveal).coerceIn(0f, 1f) }) {
        scale(u, u, pivot = Offset.Zero) {
            val len = PathMeasure(path.asAndroidPath(), false).length
            val k = if (progress < 1f) progress else 0.6f + 0.4f * loop
            tip = drawPathPart(path, len * k, C.textMuted.copy(alpha = 0.55f), 2.2f)
        }
    }
    if (reveal <= 0f) tip?.let { SideEffect { onPen(it) } }
}

// ---------------------------------------------------------------- charts, grids, checks, 3D

@Composable
private fun ChartEl(el: Element, progress: Float, u: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val w = el.w ?: 300f; val h = el.h ?: 180f
    val colors = listOf(C.bgBrand, C.popBlush, C.popSun, C.popGreen, C.bgBrandSoft, C.popPink)
    Column(Modifier.width(with(density) { (w * u).toDp() })) {
        el.caption?.let { WrittenText(it, 22f, C.textPrimary, w, (progress * 3f).coerceAtMost(1f), u) }
        Canvas(Modifier.fillMaxWidth().height(with(density) { (h * u).toDp() })) {
            val bars = el.bars.take(6); if (bars.isEmpty()) return@Canvas
            val maxV = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
            val base = size.height - 26f * u
            drawLine(C.borderStrong, Offset(0f, base), Offset(size.width * (progress * 4f).coerceAtMost(1f), base), 3f * u, StrokeCap.Round)
            val slot = size.width / bars.size
            bars.forEachIndexed { i, b ->
                val local = ((progress * (bars.size + 1)) - i - 0.6f).coerceIn(0f, 1f)
                val bh = ((b.value / maxV) * (base - 24f * u)).toFloat() * ease(local)
                val bx = i * slot + slot * 0.18f
                if (bh > 0f) drawRoundRect(colors[i % colors.size], Offset(bx, base - bh), Size(slot * 0.64f, bh), CornerRadius(8f * u))
                val lbl = measurer.measure(b.label, T.handM.copy(fontSize = with(density) { (15f * u).toSp() }, color = C.textSecondary.copy(alpha = local), textAlign = TextAlign.Center), constraints = Constraints(maxWidth = slot.toInt()))
                drawText(lbl, topLeft = Offset(i * slot + (slot - lbl.size.width) / 2, base + 3f * u))
            }
        }
    }
}

@Composable
private fun GridEl(el: Element, progress: Float, u: Float) {
    val density = LocalDensity.current
    val cols = el.cells.maxOfOrNull { it.size }?.coerceIn(1, 6) ?: 1
    val cell = ((el.w ?: 300f) / cols - 4f).coerceAtMost(62f)
    val total = el.cells.take(6).sumOf { it.size.coerceAtMost(6) }.coerceAtLeast(1)
    var n = 0
    Column(verticalArrangement = Arrangement.spacedBy(with(density) { (4f * u).toDp() })) {
        el.cells.take(6).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(with(density) { (4f * u).toDp() })) {
                row.take(6).forEachIndexed { c, v ->
                    val hl = el.highlight.size == 2 && el.highlight[0] == r && el.highlight[1] == c
                    val local = if (progress >= 1f) 1f else ((progress * (total + 2)) - n - 1).coerceIn(0f, 1f); n++
                    Box(
                        Modifier.graphicsLayer { val k = ease(local); scaleX = k; scaleY = k; alpha = local }
                            .size(with(density) { (cell * u).toDp() }, with(density) { (cell * 0.82f * u).toDp() })
                            .background(if (hl) C.accentSky else C.bgSubtle).border(if (hl) 3.dp else 1.2.dp, if (hl) C.borderBrand else C.borderStrong),
                        contentAlignment = Alignment.Center,
                    ) { WrittenText(v, 22f, C.textPrimary, cell, 1f, u, align = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun CheckEl(el: Element, progress: Float, u: Float, onPen: (Offset) -> Unit) {
    val density = LocalDensity.current
    val kind = when (el.verdict) { "correct" -> MarkKind.Correct; "wrong" -> MarkKind.Wrong; else -> MarkKind.Hint }
    val w = (el.w ?: 300f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(with(density) { (10f * u).toDp() })) {
            Box {
                WrittenText(el.text.orEmpty(), 30f, C.textPrimary, w - 50f, (progress / 0.55f).coerceAtMost(1f), u, onPen = onPen)
                if (kind == MarkKind.Wrong) Canvas(Modifier.matchParentSize()) {
                    val pr = ((progress - 0.55f) / 0.2f).coerceIn(0f, 1f)
                    if (pr > 0f) {
                        val line = Path().apply { moveTo(0f, size.height - 4f); cubicTo(size.width * 0.3f, size.height + 4f, size.width * 0.65f, size.height + 5f, size.width * 0.9f, size.height - 6f) }
                        drawPathPart(line, PathMeasure(line.asAndroidPath(), false).length * pr, C.popPink, 3.5f * u)
                    }
                }
            }
            if (progress > 0.6f) Mark(kind, Modifier.graphicsLayer { val k = ease((progress - 0.6f) / 0.2f); scaleX = k; scaleY = k })
        }
        if (kind != MarkKind.Correct && !el.why.isNullOrBlank() && progress > 0.75f) {
            WrittenText(el.why, 20f, C.popPink, w - 10f, ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f), u)
        }
    }
}

@Composable
private fun Model3DEl(el: Element, progress: Float, u: Float, onOpen3D: (Element) -> Unit) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.size(with(density) { ((el.w ?: 320f) * u).toDp() }, with(density) { ((el.h ?: 280f) * u).toDp() })
            .graphicsLayer { val a = (progress * 8f).coerceAtMost(1f); alpha = a; val s = 0.94f + 0.06f * ease(a); scaleX = s; scaleY = s }
            .clip(shape).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.bgGradientTop, C.bgGradientBottom))),
    ) {
        Scene3D(el.parts, Modifier.fillMaxSize(), build = progress, autoRotate = true)
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(C.bgSurface)
                .androidx_click { onOpen3D(el) }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { Icon(DrewyIcons.Cube, null, Modifier.size(16.dp), tint = C.textPrimary); Text("open in 3D", style = T.caption, color = C.textPrimary) }
    }
}

@Composable
private fun Modifier.androidx_click(onClick: () -> Unit): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, onClick = onClick)
}
