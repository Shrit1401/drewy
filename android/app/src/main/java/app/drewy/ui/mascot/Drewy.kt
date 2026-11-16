package app.drewy.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import app.drewy.ui.theme.DrewyPalette as P

/** Drewy's moods. The first four mirror the Figma "Drewy" component set; the rest add personality. */
enum class Mood { Idle, Listening, Thinking, Happy, Oops, Sleepy, Surprised }

private const val VW = 220f
private const val VH = 200f

/**
 * Drewy, the fluffy cloud.
 *
 * @param lookAt where Drewy is looking, each axis -1..1 (null = straight ahead / wandering).
 * @param talking flaps the mouth (drive it from text-to-speech).
 * @param wave bump this number to make Drewy wave hello.
 * @param onTap tap Drewy → giggle bounce + hearts, then this callback.
 */
@Composable
fun Drewy(
    mood: Mood,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    lookAt: Offset? = null,
    talking: Boolean = false,
    wave: Int = 0,
    onTap: (() -> Unit)? = null,
) {
    val t = rememberInfiniteTransition(label = "drewy")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "drift")

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }          // squash on mood change
    val blink = remember { Animatable(1f) }        // 1 open … 0 closed
    val mouth = remember { Animatable(0f) }        // talking mouth openness
    val jump = remember { Animatable(0f) }         // tap / hop
    val arm = remember { Animatable(0f) }          // 0 rest … 1 waving
    val hearts = remember { Animatable(1f) }       // tap hearts burst (1 = done)
    var waveKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // squash-and-stretch whenever the mood changes
    LaunchedEffect(mood) {
        if (!animate) return@LaunchedEffect
        pop.snapTo(if (mood == Mood.Surprised) 1.18f else 0.86f)
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
    }
    // natural blinking: random gaps, sometimes a double blink
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (isActive) {
            delay(Random.nextLong(2200, 5200))
            repeat(if (Random.nextFloat() < 0.25f) 2 else 1) {
                blink.animateTo(0.05f, tween(70)); blink.animateTo(1f, tween(110)); delay(90)
            }
        }
    }
    // lip-flap while speaking
    LaunchedEffect(talking, animate) {
        if (!talking || !animate) { mouth.animateTo(0f, tween(120)); return@LaunchedEffect }
        while (isActive) {
            mouth.animateTo(Random.nextFloat() * 0.75f + 0.25f, tween(Random.nextInt(70, 130)))
            mouth.animateTo(Random.nextFloat() * 0.25f, tween(Random.nextInt(60, 110)))
        }
    }
    // wave hello (external trigger or tap)
    LaunchedEffect(wave, waveKey, animate) {
        if (!animate || (wave == 0 && waveKey == 0)) return@LaunchedEffect
        arm.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
        delay(1300)
        arm.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 200f))
    }
    val eyesTarget = lookAt ?: if (mood == Mood.Thinking) Offset(0.55f, -0.8f) else Offset.Zero
    val look by animateOffsetAsState(
        Offset(eyesTarget.x.coerceIn(-1f, 1f), eyesTarget.y.coerceIn(-1f, 1f)),
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "look",
    )

    val tap = if (onTap == null || !animate) Modifier else Modifier.pointerInput(onTap) {
        detectTapGestures {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { jump.snapTo(0f); jump.animateTo(1f, tween(520, easing = LinearEasing)); jump.snapTo(0f) }
            scope.launch { hearts.snapTo(0f); hearts.animateTo(1f, tween(1100, easing = LinearEasing)) }
            waveKey++
            onTap()
        }
    }

    Canvas(modifier.aspectRatio(VW / VH).then(tap)) {
        val s = size.width / VW
        val a = if (animate) 1f else 0f
        val hop = if (jump.value > 0f) -sin(jump.value * PI.toFloat()) * 22f else 0f
        val dy = hop + when (mood) {
            Mood.Happy -> -kotlin.math.abs(sin(drift * 2 * PI.toFloat())) * 10f * a
            Mood.Listening, Mood.Surprised -> 0f
            Mood.Sleepy -> (breathe - 0.5f) * 3f * a
            else -> (bob - 0.5f) * 8f * a
        }
        val br = 1f + (breathe - 0.5f) * 0.02f * a
        val squishX = (if (mood == Mood.Listening) 1f + 0.035f * pulse * a else 1f) * br
        val squishY = (if (mood == Mood.Listening) 1f - 0.035f * pulse * a else 1f) / br
        val p = pop.value
        scale(s, s, pivot = Offset.Zero) {
            drawGroundShadow(1f + dy.coerceAtMost(0f) / 40f)
            translate(0f, dy) {
                scale(squishX * (2f - p), squishY * p, pivot = Offset(110f, 180f)) {
                    // the arm only comes out to wave — at rest it's tucked away behind the fluff
                    if (arm.value > 0.02f) drawArms(arm.value, sin(drift * 2 * PI.toFloat() * 4f), cheer = false)
                    drawBody()
                    drawFace(mood, if (animate) blink.value else 1f, look, mouth.value, talking)
                }
                drawExtras(mood, pulse * a, spin * a, drift * a)
            }
            if (hearts.value < 1f) drawHearts(hearts.value)
        }
    }
}

private val bodyPath: Path by lazy {
    var p = Path().apply { addRoundRect(RoundRect(22f, 98f, 198f, 180f, CornerRadius(41f))) }
    for ((x, y, d) in listOf(Triple(30f, 68f, 80f), Triple(58f, 30f, 108f), Triple(120f, 70f, 78f), Triple(10f, 112f, 54f), Triple(160f, 116f, 50f))) {
        val c = Path().apply { addOval(Rect(x, y, x + d, y + d)) }
        p = Path().apply { op(p, c, PathOperation.Union) }
    }
    p
}

private val ink = P.navy900
private val bodyFill = Color(0xFFEEF2FF)

private fun DrawScope.drawGroundShadow(strength: Float) {
    val k = strength.coerceIn(0.4f, 1f)
    drawOval(ink.copy(alpha = 0.10f * k), topLeft = Offset(110f - 75f * k, 182f), size = Size(150f * k, 14f))
}

/** Stubby arms tucked behind the body. up: 0 rest … 1 raised. wiggle: -1..1 while waving. cheer: both arms up. */
private fun DrawScope.drawArms(up: Float, wiggle: Float, cheer: Boolean) {
    fun armAt(pivot: Offset, restDeg: Float, upDeg: Float, raise: Float, extra: Float) {
        rotate(restDeg + (upDeg - restDeg) * raise + extra, pivot) {
            val r = Rect(pivot.x - 12f, pivot.y - 52f, pivot.x + 12f, pivot.y + 2f)
            drawRoundRect(bodyFill, r.topLeft, r.size, CornerRadius(12f))
            drawRoundRect(ink, r.topLeft, r.size, CornerRadius(12f), style = Stroke(4.5f))
            // tiny highlight on the mitten
            drawOval(Color.White.copy(alpha = 0.9f), Offset(r.left + 6f, r.top + 7f), Size(7f, 10f))
        }
    }
    // right arm waves; the left one only joins in to cheer
    if (cheer) armAt(Offset(28f, 136f), -150f, -40f, up, -wiggle * 10f)
    armAt(Offset(192f, 136f), 150f, 40f, up, wiggle * 18f)
}

private fun DrawScope.drawBody() {
    for (fx in listOf(72f, 118f)) {
        drawOval(Color(0xFFE3E9FF), Offset(fx, 166f), Size(30f, 22f))
        drawOval(ink, Offset(fx, 166f), Size(30f, 22f), style = Stroke(4.5f))
    }
    drawPath(bodyPath, Brush.verticalGradient(0f to Color.White, 0.55f to Color(0xFFF1F4FF), 1f to Color(0xFFCFD9FF), startY = 30f, endY = 180f))
    drawPath(bodyPath, ink, style = Stroke(5f, join = StrokeJoin.Round))
    rotate(18f, Offset(95f, 57f)) { drawOval(Color.White.copy(alpha = 0.95f), Offset(72f, 48f), Size(46f, 18f)) }
    drawOval(Color.White.copy(alpha = 0.9f), Offset(50f, 86f), Size(12f, 8f))
}

private fun DrawScope.eye(cx: Float, cy: Float, look: Offset, ry: Float = 19f, open: Float = 1f) {
    val h = (ry * open).coerceAtLeast(2.2f)
    val ex = cx + look.x * 2.5f; val ey = cy + look.y * 2f
    drawOval(ink, Offset(ex - 15f, ey - h), Size(30f, 2 * h))
    if (open < 0.45f) return
    val dx = look.x * 4.5f; val dy = look.y * 5f
    drawCircle(P.blue500, 10f, Offset(ex + 2 + dx, ey + 4 + dy))
    drawCircle(Color(0xFF6FA6FF), 6f, Offset(ex + 2 + dx, ey + 7 + dy))
    drawCircle(Color.White, 5.5f, Offset(ex - 5 + dx * 0.6f, ey - 7 + dy * 0.6f))
    drawCircle(Color.White, 2.2f, Offset(ex + 6 + dx * 0.6f, ey + 7 + dy * 0.6f))
}

private fun DrawScope.blush(alpha: Float) {
    drawOval(P.pink300.copy(alpha = alpha), Offset(51f, 135f), Size(26f, 14f))
    drawOval(P.pink300.copy(alpha = alpha), Offset(143f, 135f), Size(26f, 14f))
}

private fun DrawScope.line(d: Path, w: Float = 4.5f, c: Color = ink) = drawPath(d, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun path(block: Path.() -> Unit) = Path().apply(block)

private fun DrawScope.talkingMouth(open: Float) {
    val h = 4f + 12f * open
    drawOval(ink, Offset(103f, 141f - h / 4), Size(14f, h))
    if (h > 8f) drawOval(P.pink500, Offset(106f, 141f + h * 0.35f), Size(8f, h * 0.35f))
}

private fun DrawScope.drawFace(mood: Mood, blink: Float, look: Offset, mouthOpen: Float, talking: Boolean) {
    when (mood) {
        Mood.Idle -> {
            eye(88f, 120f, look, open = blink); eye(132f, 120f, look, open = blink); blush(0.7f)
            if (talking) talkingMouth(mouthOpen) else line(path { moveTo(102f, 142f); quadraticTo(110f, 150f, 118f, 142f) })
        }
        Mood.Listening -> {
            eye(88f, 118f, look, ry = 21f, open = blink); eye(132f, 118f, look, ry = 21f, open = blink); blush(0.8f)
            drawOval(ink, Offset(104f, 140f), Size(12f, 14f))
            drawOval(P.pink500, Offset(106.5f, 147.5f), Size(7f, 5f))
        }
        Mood.Thinking -> {
            eye(88f, 120f, look, open = blink); eye(132f, 120f, look, open = blink); blush(0.55f)
            line(path { moveTo(101f, 145f); relativeQuadraticTo(5f, -3f, 9f, 0f); relativeQuadraticTo(5f, 3f, 9f, 0f) }, 4.2f)
            line(path { moveTo(76f, 92f); relativeQuadraticTo(10f, -6f, 20f, -2f) }, 4f)
        }
        Mood.Happy -> {
            line(path { moveTo(73f, 124f); quadraticTo(88f, 104f, 103f, 124f) }, 5.5f)
            line(path { moveTo(117f, 124f); quadraticTo(132f, 104f, 147f, 124f) }, 5.5f)
            blush(0.95f)
            if (talking) talkingMouth(0.5f + mouthOpen * 0.5f) else {
                val m = path { moveTo(95f, 136f); quadraticTo(110f, 160f, 125f, 136f); close() }
                drawPath(m, ink); drawPath(m, ink, style = Stroke(3f, join = StrokeJoin.Round))
                drawPath(path { moveTo(103f, 147f); quadraticTo(110f, 142f, 117f, 147f); quadraticTo(110f, 154f, 103f, 147f); close() }, P.pink500)
            }
        }
        Mood.Oops -> {
            eye(88f, 122f, look, ry = 17f, open = blink); eye(132f, 122f, look, ry = 17f, open = blink); blush(0.5f)
            line(path { moveTo(72f, 98f); lineTo(96f, 92f) }, 4f); line(path { moveTo(124f, 92f); lineTo(148f, 98f) }, 4f)
            line(path { moveTo(100f, 148f); relativeQuadraticTo(5f, -5f, 10f, 0f); relativeQuadraticTo(5f, 5f, 10f, 0f) }, 4.2f)
        }
        Mood.Sleepy -> {
            line(path { moveTo(76f, 124f); quadraticTo(88f, 132f, 100f, 124f) }, 5f)
            line(path { moveTo(120f, 124f); quadraticTo(132f, 132f, 144f, 124f) }, 5f)
            blush(0.6f)
            drawOval(ink, Offset(106f, 142f), Size(9f, 7f))
        }
        Mood.Surprised -> {
            eye(88f, 116f, look, ry = 23f); eye(132f, 116f, look, ry = 23f); blush(0.85f)
            drawOval(ink, Offset(102f, 140f), Size(16f, 18f))
            drawOval(P.pink500, Offset(105f, 150f), Size(10f, 6f))
        }
    }
}

private fun DrawScope.sparkle(x: Float, y: Float, s: Float, c: Color) {
    drawPath(path {
        moveTo(x, y - s); quadraticTo(x, y, x + s, y); quadraticTo(x, y, x, y + s)
        quadraticTo(x, y, x - s, y); quadraticTo(x, y, x, y - s); close()
    }, c)
}

private fun heartPath(cx: Float, cy: Float, s: Float) = path {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.4f)
    cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f); close()
}

private fun DrawScope.drawHearts(t: Float) {
    val a = (1f - t).coerceIn(0f, 1f)
    val list = listOf(Triple(40f, -30f, P.pink500), Triple(110f, -55f, P.pink300), Triple(180f, -28f, P.pink500), Triple(150f, -70f, P.yellow400))
    list.forEachIndexed { i, (x, rise, c) ->
        val tt = (t * 1.15f - i * 0.06f).coerceIn(0f, 1f)
        drawPath(heartPath(x + sin(tt * 6f + i) * 6f, 60f + rise * tt * 1.6f, 7f + 3f * tt), c.copy(alpha = a))
    }
}

private fun DrawScope.drawExtras(mood: Mood, pulse: Float, spin: Float, drift: Float) {
    when (mood) {
        Mood.Listening -> {
            val a = 0.5f + 0.5f * pulse
            line(path { moveTo(196f, 72f); relativeQuadraticTo(10f, 12f, 0f, 24f) }, 4f, P.blue500.copy(alpha = a))
            line(path { moveTo(206f, 64f); relativeQuadraticTo(16f, 20f, 0f, 40f) }, 4f, P.blue500.copy(alpha = 1f - a * 0.6f))
            sparkle(176f, 30f, 8f + 3f * pulse, P.yellow400)
            sparkle(196f, 48f, 5f + 2f * (1 - pulse), P.yellow400)
            sparkle(38f, 44f, 6f + 2f * pulse, P.pink300)
        }
        Mood.Thinking -> {
            val a1 = ((spin / 120f) % 3f).let { if (it < 1f) it else 1f }
            drawCircle(Color.White, 5f, Offset(184f, 40f), alpha = a1); drawCircle(ink, 5f, Offset(184f, 40f), alpha = a1, style = Stroke(3f))
            val a2 = (((spin - 60f) / 120f) % 3f).coerceIn(0f, 1f)
            drawCircle(Color.White, 8f, Offset(198f, 22f), alpha = a2); drawCircle(ink, 8f, Offset(198f, 22f), alpha = a2, style = Stroke(3f))
        }
        Mood.Happy -> {
            rotate(spin, Offset(182f, 34f)) { sparkle(182f, 34f, 11f, P.yellow400) }
            sparkle(40f, 40f, 9f, P.pink300)
            rotate(-spin, Offset(200f, 64f)) { sparkle(200f, 64f, 6f, P.blue400) }
        }
        Mood.Oops -> {
            // sweat drop sliding down
            val y = 70f + 14f * drift
            drawPath(path { moveTo(168f, y); quadraticTo(176f, y + 14f, 168f, y + 18f); quadraticTo(160f, y + 14f, 168f, y); close() }, P.blue400.copy(alpha = 1f - drift * 0.6f))
        }
        Mood.Sleepy -> {
            // three Zs drifting up
            for (i in 0..2) {
                val tt = (drift + i / 3f) % 1f
                val x = 170f + 14f * i + sin(tt * 6f) * 4f; val y = 70f - 60f * tt; val z = 8f + 5f * i
                line(path { moveTo(x, y); lineTo(x + z, y); lineTo(x, y + z); lineTo(x + z, y + z) }, 3.2f, ink.copy(alpha = (1f - tt) * 0.9f))
            }
        }
        Mood.Surprised -> {
            line(path { moveTo(30f, 54f); lineTo(20f, 42f) }, 4f, P.yellow400); line(path { moveTo(190f, 54f); lineTo(200f, 42f) }, 4f, P.yellow400)
            line(path { moveTo(110f, 16f); lineTo(110f, 2f) }, 4f, P.yellow400)
        }
        Mood.Idle -> Unit
    }
}

