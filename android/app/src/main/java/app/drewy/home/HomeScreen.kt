package app.drewy.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.drewy.DrewyApp
import app.drewy.canvas.InkStroke
import app.drewy.canvas.drawInk
import app.drewy.canvas.unionBounds
import app.drewy.data.CanvasDoc
import app.drewy.ui.components.ButtonStyle
import app.drewy.ui.components.ChipColor
import app.drewy.ui.components.DrewyButton
import app.drewy.ui.components.DrewyChip
import app.drewy.ui.components.DrewyIconButton
import app.drewy.ui.components.Suggestion
import app.drewy.ui.components.Waveform
import app.drewy.ui.components.bouncyClick
import app.drewy.ui.components.entrance
import app.drewy.ui.components.brandGlow
import app.drewy.ui.components.softShadow
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.mascot.Drewy
import app.drewy.ui.mascot.DrewyBuddy
import app.drewy.ui.mascot.watchPointer
import app.drewy.ui.mascot.Mood
import app.drewy.ui.theme.DrewyTheme
import app.drewy.voice.Listener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

@Composable
fun HomeScreen(
    onOpenCanvas: (id: String?, prompt: String?, mode: String, hint: String?) -> Unit,
    onSnap: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DrewyApp
    val scope = rememberCoroutineScope()
    val listener = remember { Listener(context) }
    var recents by remember { mutableStateOf<List<CanvasDoc>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { recents = app.store.recent() }

    // ---- Drewy's personality on Home ----
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var sleepy by remember { mutableStateOf(false) }
    var surprised by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(0) }
    var say by remember { mutableStateOf<String?>(null) }
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "morning! what are we studying?"; in 12..16 -> "hey! what are we working on?"; in 17..21 -> "evening! need a hand?"; else -> "late night study? i'm here"
        }
    }
    val tips = remember { listOf("snap a problem set — I'll walk you through it", "try: explain eigenvectors visually", "try: how does a transistor work?", "circle a diagram and ask me about it", "try: show me a rocket in 3D").shuffled() }
    LaunchedEffect(Unit) {
        delay(700); wave++; say = greeting
        delay(2600); say = null
        var i = 0
        while (true) {
            delay(6500)
            if (!sleepy && !listener.listening) { say = tips[i++ % tips.size]; delay(3200); say = null }
        }
    }
    LaunchedEffect(lastTouch) {
        sleepy = false
        delay(25_000)
        if (!listener.listening) { sleepy = true; say = null }
    }
    fun touched(p: Offset) {
        pointer = p
        if (sleepy) { sleepy = false; surprised = true; say = "oh! hi!" }
        lastTouch = System.currentTimeMillis()
    }
    LaunchedEffect(surprised) { if (surprised) { delay(1200); surprised = false; delay(800); say = null } }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) toast = "Drewy needs the mic to listen." }

    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        listener.start(onResult = { onOpenCanvas(null, it, "ask", null) }, onError = { toast = it })
    }

    Box(Modifier.fillMaxSize().background(C.appGradient).watchPointer { touched(it) }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().entrance(0), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DrewyIconButton(DrewyIcons.Sparkle, "Settings", onClick = { settings = true })
                DrewyChip("Your space", DrewyIcons.Home, ChipColor.White) { onOpenCanvas(null, null, "ask", null) }
            }
            Row(
                Modifier.entrance(1).background(C.bgSurface.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(DrewyIcons.Sparkle, null, Modifier.size(14.dp), tint = C.textPrimary)
                Text(if (listener.listening) "I'm listening…" else "Time for a quick study session?", style = T.caption, color = C.textSubtle)
            }
            Text("Hey, I'm here.", style = T.display, color = C.textPrimary, modifier = Modifier.entrance(2))
            Text(
                if (listener.listening) listener.partial.ifBlank { "I'm listening…" } else "What are we working on today?",
                style = T.body, color = C.textMuted, textAlign = TextAlign.Center, modifier = Modifier.entrance(3),
            )
            Box(Modifier.fillMaxWidth().height(210.dp).entrance(4), contentAlignment = Alignment.Center) {
                Box(Modifier.size(300.dp, 210.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(C.bgSurface.copy(alpha = 0.95f), C.bgSurface.copy(alpha = 0f)))))
                DrewyBuddy(
                    when { listener.listening -> Mood.Listening; surprised -> Mood.Surprised; sleepy -> Mood.Sleepy; else -> Mood.Idle },
                    230.dp, say = say, lookAtRoot = if (sleepy) null else pointer, wave = wave, bubbleAt = Offset(0.5f, -0.06f),
                    onTap = { say = listOf("hey!", "ready when you are", "what are we studying?", "i'm here", "hehe, hi").random(); lastTouch = System.currentTimeMillis() },
                )
            }
            // Hold-to-talk CTA — breathes gently; pink with ripples while listening
            val shape = RoundedCornerShape(50)
            val breath = rememberInfiniteTransition(label = "cta")
            val b by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
            val ripple by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "r")
            Row(
                Modifier.entrance(5).width(300.dp).height(64.dp)
                    .graphicsLayer { val k = if (listener.listening) 1f else 1f + 0.018f * b; scaleX = k; scaleY = k }
                    .drawBehind {
                        if (listener.listening) {
                            val grow = 14.dp.toPx() * ripple
                            drawRoundRect(C.popPink.copy(alpha = 0.3f * (1f - ripple)), topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
                                size = androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                        }
                    }
                    .brandGlow(shape).background(if (listener.listening) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.popPink, C.popPink)) else C.brandGradient, shape)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            val t0 = System.currentTimeMillis()
                            if (listener.listening) { listener.release(); return@detectTapGestures }
                            listen()
                            tryAwaitRelease()
                            if (System.currentTimeMillis() - t0 > 450) listener.release()
                        })
                    },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                if (listener.listening) Waveform(listener.level, color = C.textOnBrand) else Icon(DrewyIcons.Mic, null, Modifier.size(24.dp), tint = C.textOnBrand)
                Text(if (listener.listening) "Tap when done" else "I'm listening", style = T.button, color = C.textOnBrand)
            }
            Text("hold to talk · tap to type", style = T.handM, color = C.textSubtle, modifier = Modifier.entrance(6).bouncyClick { onOpenCanvas(null, null, "ask", null) })

            Column(Modifier.entrance(7), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DrewyChip("Explain it", DrewyIcons.Book, ChipColor.Yellow) { onOpenCanvas(null, null, "teach", "What should I explain?") }
                    DrewyChip("Sketch it out", DrewyIcons.Brush, ChipColor.Pink) { onOpenCanvas(null, null, "ask", "Draw something, then ask me about it") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DrewyChip("See it in 3D", DrewyIcons.Cube, ChipColor.Mint) { onOpenCanvas(null, null, "3d", "What should I build in 3D?") }
                    DrewyChip("Look it up", DrewyIcons.Search, ChipColor.Sky) { onOpenCanvas(null, null, "ask", "What should I look up?") }
                }
            }
            DrewyButton("Snap a problem", DrewyIcons.Camera, ButtonStyle.Secondary, Modifier.entrance(8).width(300.dp), height = 56.dp, onClick = onSnap)

            if (recents.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().entrance(9), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recent canvases", style = T.heading, color = C.textPrimary)
                        Text("+ New", style = T.label, color = C.textLink, modifier = Modifier.bouncyClick { onOpenCanvas(null, null, "ask", null) })
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recents, key = { it.id }) { doc -> RecentCard(doc) { onOpenCanvas(doc.id, null, "ask", null) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        toast?.let { Suggestion(it, inverse = true, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) }
    }

    if (settings) {
        var url by remember { mutableStateOf(app.api.baseUrl) }
        var status by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Drewy's server", style = T.title, color = C.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Where the backend runs (your laptop's IP on the same Wi-Fi, port 8787).", style = T.bodyRegular, color = C.textSecondary)
                    OutlinedTextField(url, { url = it }, singleLine = true, textStyle = T.body)
                    status?.let { Text(it, style = T.label, color = if (it.startsWith("Connected")) C.popGreenInk else C.popPink) }
                }
            },
            confirmButton = { TextButton({ app.api.baseUrl = url; settings = false }) { Text("Save", style = T.label, color = C.textLink) } },
            dismissButton = {
                TextButton({
                    app.api.baseUrl = url
                    status = "Checking…"
                    scope.launch { status = if (app.api.health()) "Connected — Drewy is awake!" else "Can't reach it — is the backend running?" }
                }) { Text("Test", style = T.label, color = C.textSecondary) }
            },
            containerColor = C.bgSurface,
        )
    }
}

@Composable
private fun RecentCard(doc: CanvasDoc, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DrewyTheme.radius.md)
    val strokes = remember(doc.id, doc.updatedAt) { doc.pages.firstOrNull { it.strokes.isNotEmpty() }?.strokes.orEmpty().take(300).map(InkStroke::from) }
    val pic = remember(doc.id, doc.updatedAt) { doc.pages.flatMap { it.drawn }.firstOrNull { it.kind in listOf("illustration", "photo") && it.imageUrl != null }?.imageUrl }
    val app = (androidx.compose.ui.platform.LocalContext.current.applicationContext as DrewyApp)
    val title = doc.pages.lastOrNull { it.title != null }?.title ?: doc.pages.lastOrNull { it.question != null }?.question ?: doc.title
    Column(
        Modifier.width(118.dp).bouncyClick(onClick = onClick).softShadow(shape, 3.dp).background(C.bgSurface, shape).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(10.dp)).background(C.bgPaper)) {
            if (pic != null) {
                coil.compose.AsyncImage(app.api.resolve(pic), null, Modifier.fillMaxSize().padding(4.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            } else if (strokes.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    val b: Rect = unionBounds(strokes) ?: return@Canvas
                    val k = min(size.width / b.width.coerceAtLeast(1f), size.height / b.height.coerceAtLeast(1f)) * 0.85f
                    translate(size.width / 2 - b.center.x * k, size.height / 2 - b.center.y * k) {
                        scale(k, k, pivot = Offset.Zero) { strokes.forEach { drawInk(it) } }
                    }
                }
            } else {
                Drewy(Mood.Happy, Modifier.align(Alignment.Center).width(52.dp), animate = false)
            }
        }
        Text(title, style = T.handM, color = C.textPrimary, maxLines = 1)
    }
}

