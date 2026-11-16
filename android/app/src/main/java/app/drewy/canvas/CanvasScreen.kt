package app.drewy.canvas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.drewy.DrewyApp
import app.drewy.Handoff
import app.drewy.data.Element
import app.drewy.data.PAGE_H
import app.drewy.data.PAGE_W
import app.drewy.ui.components.AskBar
import app.drewy.ui.components.ChipColor
import app.drewy.ui.components.DrewyChip
import app.drewy.ui.components.DrewyIconButton
import app.drewy.ui.components.IconButtonStyle
import app.drewy.ui.components.Narration
import app.drewy.ui.components.Suggestion
import app.drewy.ui.components.TopBar
import app.drewy.ui.components.bouncyClick
import app.drewy.ui.components.entrance
import app.drewy.ui.components.softShadow
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.mascot.ConfettiBurst
import app.drewy.ui.mascot.DrewyBuddy
import app.drewy.ui.mascot.Mood
import app.drewy.ui.mascot.watchPointer
import app.drewy.ui.theme.DrewyTheme
import app.drewy.voice.Listener
import kotlinx.coroutines.delay
import kotlin.math.abs

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

data class CanvasArgs(val id: String, val prompt: String? = null, val mode: String = "ask", val hint: String? = null)

/** The shared canvas: paper pages you and Drewy draw on together. */
@Composable
fun CanvasScreen(args: CanvasArgs, onBack: () -> Unit, onSnap: () -> Unit, onOpen3D: (Element) -> Unit) {
    val app = LocalContext.current.applicationContext as DrewyApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(args.id) { PageState(app, scope, args.id) }
    val listener = remember { Listener(context) }
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(args.mode) }
    var toolsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lassoOpen by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val live = remember { mutableStateListOf<Offset>() }
    var liveTool by remember { mutableStateOf(Tool.Pen) }
    var lassoPoly by remember { mutableStateOf<List<Offset>>(emptyList()) }

    DisposableEffect(Unit) { onDispose { state.dispose(); listener.stop() } }
    LaunchedEffect(args.id) { state.load(); args.prompt?.takeIf { it.isNotBlank() }?.let { state.ask(it, args.mode) } }
    LaunchedEffect(state.loaded, Handoff.pendingSnap) {
        val snap = Handoff.pendingSnap
        if (state.loaded && snap != null) { Handoff.pendingSnap = null; state.snap(snap.path, snap.mode) }
    }
    LaunchedEffect(state.loaded, Handoff.pendingAsk) {
        val q = Handoff.pendingAsk
        if (state.loaded && q != null) { Handoff.pendingAsk = null; state.ask(q, "ask") }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) toast = "Drewy needs the mic to listen." }
    fun send(prompt: String) {
        if (prompt.isBlank()) return
        state.ask(prompt.trim(), detectMode(prompt, mode)); text = ""; mode = "ask"
    }
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        app.speaker.stop(); listener.start(onResult = { send(it) }, onError = { toast = it })
    }

    val pager = rememberPagerState { state.pages.size.coerceAtLeast(1) }
    LaunchedEffect(state.current, state.pages.size) {
        if (state.pages.isNotEmpty() && pager.currentPage != state.current) pager.animateScrollToPage(state.current, animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f))
    }

    Box(Modifier.fillMaxSize().background(C.appGradient).watchPointer { pointer = it }) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar ----
            val p = state.page
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val overline = when {
                    state.thinking -> "drewy is thinking…"
                    state.drawing -> "drewy is drawing…"
                    p?.question != null -> "you asked · ${p.question}"
                    else -> "canvas · page ${state.current + 1}"
                }
                TopBar(p?.title ?: state.title, overline, onBack) {
                    val toolIcon = when (state.tool) { Tool.Marker -> DrewyIcons.Marker; Tool.Eraser -> DrewyIcons.Eraser; Tool.Lasso -> DrewyIcons.Lasso; Tool.Shapes -> DrewyIcons.Shapes; Tool.Hand -> DrewyIcons.Hand; else -> DrewyIcons.Pen }
                    DrewyIconButton(
                        toolIcon, "Drawing tools", style = if (toolsOpen) IconButtonStyle.Active else IconButtonStyle.Ghost,
                        tint = if (state.tool in listOf(Tool.Pen, Tool.Marker, Tool.Shapes)) state.color else null, onClick = { toolsOpen = !toolsOpen },
                    )
                    Box {
                        DrewyIconButton(DrewyIcons.More, "More", style = IconButtonStyle.Ghost, onClick = { menuOpen = true })
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }, containerColor = C.bgSurface) {
                            DropdownMenuItem(text = { Text("New page", style = T.label) }, onClick = { state.newBlankPage(); menuOpen = false })
                            DropdownMenuItem(text = { Text(if (app.speaker.muted) "Unmute Drewy" else "Mute Drewy", style = T.label) }, onClick = { app.speaker.muted = !app.speaker.muted; menuOpen = false })
                            DropdownMenuItem(text = { Text("Clear this page", style = T.label, color = C.popPink) }, onClick = { state.clearPage(); menuOpen = false })
                        }
                    }
                }
                AnimatedVisibility(toolsOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ToolStrip(state) { toolsOpen = false }
                }
            }

            // ---- pages ----
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val side = 18.dp
                val maxW = maxWidth - side * 2
                val pageW = minOf(maxW, (maxHeight - 16.dp) * (PAGE_W / PAGE_H))
                val u = with(density) { pageW.toPx() } / PAGE_W
                val pad = (maxWidth - pageW) / 2
                HorizontalPager(
                    state = pager, userScrollEnabled = false, contentPadding = PaddingValues(horizontal = pad), pageSpacing = 14.dp,
                    beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top,
                ) { i ->
                    val pg = state.pages.getOrNull(i) ?: return@HorizontalPager
                    val offset = (pager.currentPage - i) + pager.currentPageOffsetFraction
                    Box(
                        Modifier.fillMaxWidth().graphicsLayer {
                            // a gentle page turn
                            rotationY = offset * 14f; val k = 1f - abs(offset).coerceAtMost(1f) * 0.06f; scaleX = k; scaleY = k
                            cameraDistance = 18f * density.density
                        }.pointerInput(pg, u, state.tool) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = down.position
                                var multi = false; var dx = 0f; var moved = 0f
                                val tool = state.tool
                                val drawingNow = !state.drawing && tool != Tool.Hand
                                if (drawingNow) { live.clear(); liveTool = tool; live += start / u; if (tool == Tool.Eraser) state.eraseAt(pg, start / u, 10f) }
                                lassoOpen = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2 || tool == Tool.Hand) {
                                        if (!multi) { multi = true; live.clear() }
                                        dx += pressed.map { it.position.x - it.previousPosition.x }.average().toFloat()
                                        ev.changes.forEach { it.consume() }
                                    } else if (drawingNow && !multi) {
                                        val ch = pressed.first(); val w = ch.position / u
                                        moved += (ch.position - ch.previousPosition).getDistance()
                                        when (tool) {
                                            Tool.Eraser -> state.eraseAt(pg, w, 10f)
                                            else -> if ((live.lastOrNull()?.let { (it - w).getDistance() } ?: 99f) > 0.8f) live += w
                                        }
                                        ch.consume()
                                    } else {
                                        moved += pressed.first().let { (it.position - it.previousPosition).getDistance() }
                                    }
                                }
                                when {
                                    multi && abs(dx) > 80f -> state.goTo(state.current + if (dx < 0) 1 else -1)
                                    state.drawing && moved < 12f -> state.hurry()
                                    drawingNow && !multi && live.size > 1 -> when (tool) {
                                        Tool.Pen, Tool.Marker -> state.addStroke(pg, InkStroke(live.toList(), state.color, if (tool == Tool.Marker) 14f else 3.2f, tool))
                                        Tool.Shapes -> state.addStroke(pg, InkStroke(snapShape(live.toList()), state.color, 3.2f, Tool.Pen))
                                        Tool.Lasso -> {
                                            val sel = strokesInLasso(pg.strokes, live.toList())
                                            state.lassoSelection = sel
                                            if (sel.isNotEmpty()) { lassoPoly = live.toList(); lassoOpen = true } else toast = "Circle something you drew"
                                        }
                                        else -> Unit
                                    }
                                    else -> Unit
                                }
                                live.clear()
                            }
                        },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        PageView(
                            pg, u, state, active = i == state.current, live = if (i == state.current) live else emptyList(), liveTool = liveTool,
                            lasso = if (i == state.current && lassoOpen) lassoPoly else if (i == state.current && liveTool == Tool.Lasso) live else emptyList(),
                            onOpen3D = onOpen3D, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // page dots
                if (state.pages.size > 1) {
                    Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.pages.forEachIndexed { i, _ ->
                            val w by animateDpAsState(if (i == state.current) 20.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
                            Box(Modifier.size(w, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i == state.current) C.bgBrand else C.bgSubtle).bouncyClick { state.goTo(i) })
                        }
                    }
                }
            }

            // ---- dock ----
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(toast != null || state.error != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
                    Suggestion(toast ?: state.error.orEmpty(), inverse = true)
                }
                val chips = state.page?.followUps.orEmpty()
                AnimatedContent(
                    targetState = when { state.drawing && state.caption != null -> 1; lassoOpen -> 2; chips.isNotEmpty() && !state.thinking -> 3; else -> 0 },
                    transitionSpec = { (fadeIn(tween(240)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(140)) },
                    label = "dockTop",
                ) { which ->
                    when (which) {
                        1 -> Narration(state.caption.orEmpty(), Mood.Happy, Modifier.fillMaxWidth(), talking = app.speaker.speaking)
                        2 -> LassoActions { prompt, m -> lassoOpen = false; state.ask(prompt, m, state.lassoSelection) }
                        3 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.page?.score?.takeIf { it.isNotBlank() }?.let { DrewyChip(it, DrewyIcons.Sparkle, ChipColor.Yellow, Modifier.entrance(0)) {} }
                            val colors = listOf(ChipColor.Yellow, ChipColor.Pink, ChipColor.Sky, ChipColor.Mint)
                            val icons = listOf(DrewyIcons.Bulb, DrewyIcons.Sparkle, DrewyIcons.Hand, DrewyIcons.Book)
                            chips.take(4).forEachIndexed { i, f -> DrewyChip(f, icons[i % 4], colors[i % 4], Modifier.entrance(i + 1)) { state.followUp(f) } }
                        }
                        else -> if (state.strokesSinceAsk >= 3 && !state.thinking) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Suggestion("Want me to take a look at this?", onClick = { state.ask("What did I draw? Make it real.", "lasso") })
                            }
                        } else Spacer(Modifier.height(0.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(64.dp)) {
                        // Drewy leaves the dock while he's on the page drawing
                        androidx.compose.animation.AnimatedVisibility(visible = !state.drawing && !state.thinking, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            DrewyBuddy(
                                if (listener.listening) Mood.Listening else state.mood, 64.dp,
                                say = state.buddySay.takeIf { chips.isEmpty() || state.thinking }, lookAtRoot = pointer,
                                onTap = { state.say(listOf("hey!", "i'm here", "ready when you are", "hehe, hi").random(), 1500) },
                            )
                        }
                    }
                    AskBar(
                        text = text, onTextChange = { text = it }, onSend = { send(text) }, onCamera = onSnap,
                        onMicPress = { if (listener.listening) listener.release() else startListening() },
                        onMicRelease = { held -> if (held > 450 && listener.listening) listener.release() },
                        listening = listener.listening, level = listener.level, partial = listener.partial,
                        placeholder = when {
                            lassoOpen -> "ask about what you circled…"
                            state.drawing -> "tap the page to hurry Drewy"
                            state.pages.all { it.isBlank } && args.hint != null -> args.hint
                            mode == "teach" -> "What should I explain?"
                            mode == "3d" -> "What should I build in 3D?"
                            else -> "Ask Drewy anything…"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ConfettiBurst(state.celebrate, Modifier.fillMaxSize(), originFraction = Offset(0.5f, 0.4f))
    }
}

/** Drawing tools, tucked away until you need them. */
@Composable
private fun ToolStrip(state: PageState, onPicked: () -> Unit) {
    val shape = RoundedCornerShape(DrewyTheme.radius.lg)
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 6.dp).background(C.bgSurface, shape).padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val tools: List<Triple<Tool, ImageVector, String>> = listOf(
            Triple(Tool.Pen, DrewyIcons.Pen, "Pen"), Triple(Tool.Marker, DrewyIcons.Marker, "Highlighter"), Triple(Tool.Shapes, DrewyIcons.Shapes, "Shapes"),
            Triple(Tool.Eraser, DrewyIcons.Eraser, "Eraser"), Triple(Tool.Lasso, DrewyIcons.Lasso, "Lasso"),
        )
        for ((t, icon, label) in tools) DrewyIconButton(icon, label, size = 42.dp, style = if (state.tool == t) IconButtonStyle.Active else IconButtonStyle.Ghost) { state.tool = t; if (t != Tool.Lasso) state.lassoSelection = emptyList(); onPicked() }
        DrewyIconButton(DrewyIcons.Undo, "Undo", size = 42.dp, style = IconButtonStyle.Ghost) { state.undoLast() }
        Box(Modifier.padding(horizontal = 4.dp).size(2.dp, 26.dp).background(C.bgSubtle))
        for (c in InkColors) {
            val sel = state.color == c
            val ring by animateDpAsState(if (sel) 3.dp else 0.dp, spring(dampingRatio = 0.5f), label = "ring")
            Box(
                Modifier.padding(3.dp).size(28.dp).bouncyClick { state.color = c; if (state.tool !in listOf(Tool.Pen, Tool.Marker, Tool.Shapes)) state.tool = Tool.Pen; onPicked() }
                    .border(ring, C.accentSky, CircleShape).padding(ring).clip(CircleShape).background(c),
            )
        }
    }
}

/** Figma 06 lasso actions, as chips in the dock. */
@Composable
private fun LassoActions(onAction: (String, String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrewyChip("Label it", DrewyIcons.Brush, ChipColor.Yellow, Modifier.entrance(0)) { onAction("What did I draw? Name it and label its parts with arrows right on my page.", "lasso") }
        DrewyChip("Make it 3D", DrewyIcons.Cube, ChipColor.Mint, Modifier.entrance(1)) { onAction("Build a 3D model of what I drew.", "3d") }
        DrewyChip("Explain it", DrewyIcons.Book, ChipColor.Pink, Modifier.entrance(2)) { onAction("Explain what I drew and teach me how it works.", "teach") }
        DrewyChip("Make it real", DrewyIcons.Image, ChipColor.Sky, Modifier.entrance(3)) { onAction("Draw a proper illustration of what I drew, and show a real photo if it's a real thing.", "lasso") }
    }
}

/** "teach me…" → lesson, "…in 3D" → 3D model, otherwise keep the current mode. */
fun detectMode(text: String, current: String): String {
    val t = text.lowercase().trim()
    return when {
        Regex("\\b(3d|3-d|three[ -]d)\\b").containsMatchIn(t) || t.contains("model of") -> "3d"
        t.startsWith("teach me") || t.startsWith("explain") || t.startsWith("how does") || t.startsWith("how do") || t.startsWith("why does") -> "teach"
        else -> current
    }
}
