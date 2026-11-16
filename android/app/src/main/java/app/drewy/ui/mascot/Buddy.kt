package app.drewy.ui.mascot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.drewy.ui.components.softShadow
import app.drewy.ui.theme.DrewyTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

/** Comic speech bubble with a little tail, pops in with a spring. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, tailLeft: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        Text(
            text, style = T.handM, color = C.textPrimary,
            modifier = Modifier.widthIn(max = 210.dp).softShadow(shape, 5.dp).background(C.bgSurface, shape).padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Canvas(Modifier.align(if (tailLeft) Alignment.Start else Alignment.CenterHorizontally).padding(start = if (tailLeft) 22.dp else 0.dp).size(16.dp, 10.dp).offset(y = (-1).dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.25f, size.height); close() }, C.bgSurface)
        }
    }
}

/**
 * Drewy + an optional speech bubble. Eyes follow [lookAtRoot] (a pointer position in root coordinates).
 */
@Composable
fun DrewyBuddy(
    mood: Mood,
    width: Dp,
    modifier: Modifier = Modifier,
    say: String? = null,
    talking: Boolean = false,
    lookAtRoot: Offset? = null,
    wave: Int = 0,
    bubbleAbove: Boolean = true,
    /** Where the bubble sits, as fractions of Drewy's width (x from his left edge, y from his top; negative = above). */
    bubbleAt: Offset = Offset(0.55f, -0.42f),
    onTap: (() -> Unit)? = null,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val look = lookAtRoot?.let { p ->
        val d = p - center; val len = hypot(d.x, d.y).coerceAtLeast(1f)
        val k = (len / 260f).coerceAtMost(1f)
        Offset(d.x / len * k, d.y / len * k)
    }
    Box(modifier.size(width, width * (200f / 220f))) {
        Drewy(
            mood, Modifier.size(width, width * (200f / 220f)).onGloballyPositioned { c ->
                center = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)
            },
            lookAt = look, talking = talking, wave = wave, onTap = onTap,
        )
        AnimatedVisibility(
            visible = !say.isNullOrBlank(),
            modifier = Modifier.align(if (bubbleAbove) Alignment.TopStart else Alignment.TopEnd)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset { IntOffset((width * bubbleAt.x).roundToPx(), (width * bubbleAt.y).roundToPx()) },
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f, transformOrigin = TransformOrigin(0f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.6f, transformOrigin = TransformOrigin(0f, 1f)),
        ) { SpeechBubble(say.orEmpty()) }
    }
}

/** Tracks the latest pointer position anywhere inside this node (root coords), without consuming events. */
fun Modifier.watchPointer(onMove: (Offset) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        var origin = Offset.Zero
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull()?.let { onMove(it.position) }
        }
    }
}

private data class Bit(val angle: Float, val speed: Float, val spin: Float, val color: Color, val star: Boolean, val size: Float)

/** Sparkle confetti burst. Bump [trigger] to fire. Draws over its whole box. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, originFraction: Offset = Offset(0.5f, 0.55f)) {
    val t = remember { Animatable(1f) }
    val bits = remember(trigger) {
        val colors = listOf(C.popSun, C.popPink, C.bgBrand, C.popGreen, C.popBlush, C.bgBrandSoft)
        List(34) { Bit(Random.nextFloat() * 2 * PI.toFloat(), 0.45f + Random.nextFloat() * 0.75f, Random.nextFloat() * 720f - 360f, colors.random(), Random.nextBoolean(), 7f + Random.nextFloat() * 9f) }
    }
    LaunchedEffect(trigger) { if (trigger > 0) { t.snapTo(0f); t.animateTo(1f, tween(1500, easing = LinearEasing)) } }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val o = Offset(size.width * originFraction.x, size.height * originFraction.y)
        val v = t.value
        val dist = size.minDimension * 0.55f
        for (b in bits) {
            val e = FastOutSlowInEasing.transform(v)
            val x = o.x + cos(b.angle) * dist * b.speed * e
            val y = o.y + sin(b.angle) * dist * b.speed * e + v * v * size.height * 0.25f // gravity
            val a = (1f - v).coerceIn(0f, 1f)
            rotate(b.spin * v, Offset(x, y)) {
                if (b.star) {
                    val s = b.size
                    drawPath(Path().apply {
                        moveTo(x, y - s); quadraticTo(x, y, x + s, y); quadraticTo(x, y, x, y + s); quadraticTo(x, y, x - s, y); quadraticTo(x, y, x, y - s); close()
                    }, b.color.copy(alpha = a))
                } else drawRoundRect(b.color.copy(alpha = a), Offset(x - b.size / 2, y - b.size / 4), androidx.compose.ui.geometry.Size(b.size, b.size / 2), androidx.compose.ui.geometry.CornerRadius(3f))
            }
        }
    }
}

/** Launch intro: Drewy floats up with a bounce, waves, and the wordmark writes itself. */
@Composable
fun DrewyIntro(onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    var wave by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f))
        wave++
        word.animateTo(1f, tween(700, easing = LinearEasing))
        kotlinx.coroutines.delay(500)
        out.animateTo(0f, tween(320))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = out.value }.background(C.appGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Drewy(
                if (word.value > 0.5f) Mood.Happy else Mood.Idle, wave = wave,
                modifier = Modifier.size(200.dp, 182.dp).graphicsLayer {
                    translationY = (1f - rise.value) * 500f; val s = 0.6f + 0.4f * rise.value; scaleX = s; scaleY = s
                },
            )
            Box(Modifier.padding(top = 10.dp)) {
                app.drewy.canvas.WrittenText("Drewy", 64f, C.textPrimary, 160f, word.value, with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text("the canvas that draws back", style = T.caption, color = C.textMuted, modifier = Modifier.graphicsLayer { alpha = word.value })
        }
    }
}
