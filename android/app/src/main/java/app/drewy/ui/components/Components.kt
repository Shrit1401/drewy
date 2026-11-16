package app.drewy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.mascot.Drewy
import app.drewy.ui.mascot.Mood
import app.drewy.ui.theme.DrewyTheme
import androidx.compose.foundation.clickable

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

/** Soft-shadow helpers that match the Figma effect styles. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 6.dp) =
    shadow(elevation, shape, ambientColor = Color(0xFF3D338C).copy(alpha = 0.25f), spotColor = Color(0xFF3D338C).copy(alpha = 0.25f))

fun Modifier.brandGlow(shape: Shape) =
    shadow(14.dp, shape, ambientColor = Color(0xFF2E6BF0), spotColor = Color(0xFF2E6BF0))

/** Bouncy press: every tappable thing squishes a little (the "poppy" feel). */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return this.scale(s).clickable(source, indication = null, enabled = enabled, role = Role.Button) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

enum class ChipColor { Yellow, Pink, Mint, Sky, White }

fun ChipColor.bg() = when (this) {
    ChipColor.Yellow -> C.accentYellow; ChipColor.Pink -> C.accentPink; ChipColor.Mint -> C.accentMint
    ChipColor.Sky -> C.accentSky; ChipColor.White -> C.bgSurface
}

@Composable
fun DrewyChip(label: String, icon: ImageVector?, color: ChipColor, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DrewyTheme.radius.lg)
    Row(
        modifier.bouncyClick(onClick = onClick).softShadow(shape, 3.dp).background(color.bg(), shape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = C.textPrimary)
        Text(label, style = T.label, color = C.textPrimary, maxLines = 1)
    }
}

enum class IconButtonStyle { Default, Active, Ghost, Brand }

@Composable
fun DrewyIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Default,
    size: Dp = if (style == IconButtonStyle.Brand) 56.dp else 46.dp,
    tint: Color? = null,
    background: Color? = null,
    onClick: () -> Unit,
) {
    val shape = if (style == IconButtonStyle.Brand) CircleShape else RoundedCornerShape(DrewyTheme.radius.md)
    val target = background ?: when (style) {
        IconButtonStyle.Default -> C.bgSurface; IconButtonStyle.Active -> C.accentSky
        IconButtonStyle.Ghost -> Color.Transparent; IconButtonStyle.Brand -> C.bgBrand
    }
    val bg by animateColorAsState(target, tween(220), label = "ib")
    Box(
        modifier.size(size).bouncyClick(onClick = onClick)
            .then(if (style == IconButtonStyle.Brand) Modifier.brandGlow(shape) else Modifier)
            .clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(if (size > 50.dp) 26.dp else 22.dp), tint = tint ?: if (style == IconButtonStyle.Brand) C.textOnBrand else C.textPrimary)
    }
}

enum class ButtonStyle { Primary, Secondary, Ghost }

@Composable
fun DrewyButton(label: String, icon: ImageVector?, style: ButtonStyle, modifier: Modifier = Modifier, height: Dp = 60.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier.height(height).bouncyClick(onClick = onClick)
    val styled = when (style) {
        ButtonStyle.Primary -> base.brandGlow(shape).background(C.brandGradient, shape)
        ButtonStyle.Secondary -> base.softShadow(shape, 3.dp).background(C.bgSurface, shape).border(2.5.dp, C.borderStrong, shape)
        ButtonStyle.Ghost -> base
    }
    val fg = when (style) { ButtonStyle.Primary -> C.textOnBrand; ButtonStyle.Secondary -> C.textPrimary; ButtonStyle.Ghost -> C.textLink }
    Row(styled.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = fg)
        Text(label, style = T.button, color = fg)
    }
}

/** Live mic waveform. */
@Composable
fun Waveform(level: Float, modifier: Modifier = Modifier, color: Color = C.bgBrand) {
    val heights = listOf(0.3f, 0.6f, 1f, 0.7f, 0.4f, 0.8f, 0.5f)
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    val smooth by animateFloatAsState(level, tween(120), label = "lvl")
    Canvas(modifier.size(44.dp, 22.dp)) {
        val w = size.width / (heights.size * 1.8f)
        heights.forEachIndexed { i, h ->
            val wobble = 0.55f + 0.45f * kotlin.math.sin(phase + i * 0.9f)
            val hh = size.height * (0.18f + 0.82f * h * wobble * (0.35f + 0.65f * smooth))
            drawRoundRect(color, Offset(i * w * 1.8f, (size.height - hh) / 2), Size(w, hh), CornerRadius(w / 2))
        }
    }
}

/**
 * Docked at the bottom of every canvas: camera · type · mic.
 * Tap the mic to start/stop listening, or hold it and let go to send.
 */
@Composable
fun AskBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCamera: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: (heldMs: Long) -> Unit,
    listening: Boolean,
    level: Float,
    partial: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask Drewy anything…",
) {
    val shape = RoundedCornerShape(50)
    // sending closes the keyboard so you can watch Drewy draw
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val sendAndClose = { onSend(); focus.clearFocus(); keyboard?.hide(); Unit }
    Row(
        modifier.softShadow(shape, 8.dp).background(C.bgSurface, shape).border(2.5.dp, C.borderStrong, shape).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrewyIconButton(DrewyIcons.Camera, "Snap a question", style = IconButtonStyle.Ghost, onClick = onCamera)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (listening) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Waveform(level)
                    Text(partial.ifBlank { "Listening…" }, style = T.body, color = C.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                BasicTextField(
                    value = text, onValueChange = onTextChange, textStyle = T.body.copy(color = C.textPrimary),
                    singleLine = true, cursorBrush = SolidColor(C.bgBrand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) sendAndClose() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(placeholder, style = T.body, color = C.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
            }
        }
        AnimatedContent(text.isNotBlank() && !listening, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "mic") { canSend ->
            if (canSend) {
                DrewyIconButton(DrewyIcons.Send, "Send", style = IconButtonStyle.Brand, size = 50.dp, onClick = sendAndClose)
            } else {
                val shapeC = CircleShape
                val ring = rememberInfiniteTransition(label = "ring")
                val r by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "r")
                val micBg by animateColorAsState(if (listening) C.popPink else C.bgBrand, tween(220), label = "mic")
                Box(
                    Modifier.size(50.dp)
                        .then(if (listening) Modifier.drawBehind {
                            drawCircle(C.popPink.copy(alpha = 0.35f * (1f - r)), radius = size.minDimension / 2f * (1f + 0.55f * r))
                        } else Modifier)
                        .brandGlow(shapeC).clip(shapeC).background(micBg)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                val t0 = System.currentTimeMillis()
                                onMicPress()
                                tryAwaitRelease()
                                onMicRelease(System.currentTimeMillis() - t0)
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (listening) DrewyIcons.Stop else DrewyIcons.Mic, if (listening) "Stop" else "Talk", Modifier.size(24.dp), tint = C.textOnBrand) }
            }
        }
    }
}

@Composable
fun TopBar(title: String, overline: String?, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().softShadow(shape, 3.dp).background(C.bgSurface, shape).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrewyIconButton(DrewyIcons.Back, "Back", style = IconButtonStyle.Ghost, onClick = onBack)
        Column(Modifier.weight(1f)) {
            if (!overline.isNullOrBlank()) Text(overline.uppercase(), style = T.overline, color = C.textMuted, maxLines = 1)
            Text(title, style = T.heading, color = C.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = T.body, color = C.textOnBrand,
        modifier = modifier.widthIn(max = 280.dp).then(Modifier)
            .background(C.bgBrand, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun Narration(text: String, mood: Mood, modifier: Modifier = Modifier, talking: Boolean = false) {
    val shape = RoundedCornerShape(DrewyTheme.radius.lg)
    Row(
        modifier.softShadow(shape, 6.dp).background(C.bgSurface, shape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Drewy(mood, Modifier.width(56.dp), talking = talking)
        Text("“$text”", style = T.body, color = C.textPrimary, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun Suggestion(text: String, inverse: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(DrewyTheme.radius.lg)
    Row(
        modifier.then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)
            .background(if (inverse) C.bgInverse else C.accentSky, shape).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(DrewyIcons.Sparkle, null, Modifier.size(18.dp), tint = if (inverse) C.textOnBrand else C.textPrimary)
        Text(text, style = T.label, color = if (inverse) C.textOnBrand else C.textPrimary)
    }
}

@Composable
fun StepDots(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            Box(Modifier.height(8.dp).width(if (i == step) 20.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= step) C.bgBrand else C.bgSubtle))
        }
    }
}

enum class MarkKind { Correct, Wrong, Hint }

@Composable
fun Mark(kind: MarkKind, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(
            when (kind) { MarkKind.Correct -> C.popGreen; MarkKind.Wrong -> C.popPink; MarkKind.Hint -> C.popSun },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(when (kind) { MarkKind.Correct -> DrewyIcons.Check; MarkKind.Wrong -> DrewyIcons.Close; MarkKind.Hint -> DrewyIcons.Bulb }, kind.name, Modifier.size(18.dp), tint = C.textOnBrand)
    }
}

@Composable
fun Polaroid(caption: String, modifier: Modifier = Modifier, rotation: Float = -3f, photo: @Composable () -> Unit) {
    Box(modifier.rotate(rotation)) {
        Column(
            Modifier.padding(top = 10.dp).softShadow(RoundedCornerShape(6.dp), 10.dp).background(C.bgSurface, RoundedCornerShape(6.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(210.dp).clip(RoundedCornerShape(4.dp)).background(C.bgSubtle), contentAlignment = Alignment.Center) { photo() }
            Text(caption, style = T.handL, color = C.textPrimary, modifier = Modifier.widthIn(max = 210.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Box(Modifier.align(Alignment.TopCenter).rotate(6f).size(64.dp, 20.dp).background(C.popSun.copy(alpha = 0.55f)))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(text, style = T.heading, color = C.textPrimary, modifier = modifier)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))


/** Figma "Slider": 6px rounded track, blue fill, white knob with a 3px blue ring. */
@Composable
fun DrewySlider(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = T.label, color = C.textPrimary)
        Canvas(
            Modifier.fillMaxWidth().height(24.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume(); onValueChange((ch.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            val knobR = 10.dp.toPx(); val trackH = 6.dp.toPx(); val cy = size.height / 2
            val x = knobR + (size.width - 2 * knobR) * value
            drawRoundRect(C.bgSubtle, Offset(0f, cy - trackH / 2), Size(size.width, trackH), CornerRadius(trackH / 2))
            drawRoundRect(C.bgBrand, Offset(0f, cy - trackH / 2), Size(x, trackH), CornerRadius(trackH / 2))
            drawCircle(Color.Black.copy(alpha = 0.10f), knobR + 1.5f, Offset(x, cy + 2f))
            drawCircle(C.bgSurface, knobR, Offset(x, cy))
            drawCircle(C.borderBrand, knobR - 1.5.dp.toPx(), Offset(x, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
    }
}


/** Lesson player: prev step · play/pause (Brand 68) · next step. */
@Composable
fun LessonControls(playing: Boolean, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        DrewyIconButton(DrewyIcons.Back, "Previous step", Modifier.clip(CircleShape), onClick = onPrev)
        DrewyIconButton(if (playing) DrewyIcons.Pause else DrewyIcons.Play, if (playing) "Pause" else "Play", style = IconButtonStyle.Brand, size = 68.dp, onClick = onToggle)
        DrewyIconButton(DrewyIcons.Next, "Next step", Modifier.clip(CircleShape), onClick = onNext)
    }
}

/** A loop drawn around content by hand (Solve answers). progress 0..1 draws the stroke. */
fun Modifier.handCircle(progress: Float, color: Color) = this.drawBehind {
    if (progress <= 0f) return@drawBehind
    val w = size.width; val h = size.height
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.08f, h * 0.62f)
        cubicTo(-w * 0.04f, h * 0.05f, w * 0.55f, -h * 0.22f, w * 0.86f, h * 0.06f)
        cubicTo(w * 1.08f, h * 0.24f, w * 1.06f, h * 0.92f, w * 0.62f, h * 1.06f)
        cubicTo(w * 0.22f, h * 1.16f, -w * 0.02f, h * 0.92f, w * 0.12f, h * 0.38f)
    }
    drawPartialPath(p, progress, color, 3.5.dp.toPx())
}

/** A wobbly hand-drawn underline (Check my work mistakes). */
fun Modifier.handUnderline(progress: Float, color: Color) = this.drawBehind {
    if (progress <= 0f) return@drawBehind
    val w = size.width; val y = size.height - 2.dp.toPx()
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, y); cubicTo(w * 0.25f, y + 5.dp.toPx(), w * 0.6f, y + 6.dp.toPx(), w, y - 3.dp.toPx())
    }
    drawPartialPath(p, progress, color, 3.5.dp.toPx())
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPartialPath(p: androidx.compose.ui.graphics.Path, progress: Float, color: Color, width: Float) {
    val pm = android.graphics.PathMeasure(p.asAndroidPathCompat(), false)
    val seg = android.graphics.Path()
    pm.getSegment(0f, pm.length * progress.coerceIn(0f, 1f), seg, true)
    drawPath(androidx.compose.ui.graphics.Path().apply { addPath(seg.asComposePathCompat()) }, color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.Path.asAndroidPathCompat() = this.asAndroidPath()
private fun android.graphics.Path.asComposePathCompat() = this.asComposePath()

/** Staggered entrance: fades + slides up after `index * step` ms once `visible` flips true. */
@Composable
fun Modifier.entrance(index: Int, step: Int = 70, visible: Boolean = true): Modifier {
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) { kotlinx.coroutines.delay(index * step.toLong()); a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 260f)) } else a.snapTo(0f)
    }
    return this.graphicsLayerCompat(a.value)
}

private fun Modifier.graphicsLayerCompat(v: Float) = this.then(Modifier.graphicsLayer {
    alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 26.dp.toPx(); scaleX = 0.96f + 0.04f * v; scaleY = 0.96f + 0.04f * v
})
