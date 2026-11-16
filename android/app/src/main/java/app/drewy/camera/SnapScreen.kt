package app.drewy.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.drewy.DrewyApp
import app.drewy.ui.components.DrewyButton
import app.drewy.ui.components.ButtonStyle
import app.drewy.ui.components.DrewyIconButton
import app.drewy.ui.components.IconButtonStyle
import app.drewy.ui.components.Suggestion
import app.drewy.ui.components.bouncyClick
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.mascot.Drewy
import app.drewy.ui.mascot.Mood
import app.drewy.ui.theme.DrewyTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

/** Camera: snap a question → Solve / Explain / Check my work. Calls onCaptured(path, mode). */
@Composable
fun SnapScreen(onClose: () -> Unit, onCaptured: (path: String, mode: String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DrewyApp
    val lifecycle = LocalLifecycleOwner.current
    var mode by remember { mutableStateOf("solve") }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    // light status-bar icons over the dark camera, restored when leaving
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = false
        onDispose { ctl?.isAppearanceLightStatusBars = true }
    }
    LaunchedEffect(Unit) { if (!hasPermission) permission.launch(Manifest.permission.CAMERA) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val out = saveUri(context, uri, app.store.snapsDir)
            if (out != null) onCaptured(out, mode) else error = "Couldn't open that photo."
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    Box(Modifier.fillMaxSize().background(C.bgCamera)) {
        if (hasPermission) {
            val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            AndroidView({ preview }, Modifier.fillMaxSize())
            DisposableEffect(lifecycle) {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val provider = future.get()
                    val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                    provider.unbindAll()
                    camera = runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, p, capture) }
                        .onFailure { error = "Camera unavailable: ${it.message}" }.getOrNull()
                }, ContextCompat.getMainExecutor(context))
                onDispose { runCatching { future.get().unbindAll() } }
            }
            // Figma 07 framing guide: blue corner brackets that gently breathe
            val t = rememberInfiniteTransition(label = "guide")
            val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
            Canvas(Modifier.align(Alignment.Center).padding(bottom = 120.dp).size(330.dp, 230.dp)) {
                val inset = 6.dp.toPx() * pulse
                val l = inset; val tp = inset; val r = size.width - inset; val b = size.height - inset
                val arm = 34.dp.toPx(); val rad = 14.dp.toPx(); val w = 4.dp.toPx()
                val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(C.bgBrand.copy(alpha = 0.10f + 0.06f * pulse), Offset(l, tp), Size(r - l, b - tp), CornerRadius(rad))
                fun corner(x: Float, y: Float, sx: Float, sy: Float) = drawPath(Path().apply {
                    moveTo(x, y + sy * arm); lineTo(x, y + sy * rad); quadraticTo(x, y, x + sx * rad, y); lineTo(x + sx * arm, y)
                }, C.bgBrandSoft, style = st)
                corner(l, tp, 1f, 1f); corner(r, tp, -1f, 1f); corner(l, b, 1f, -1f); corner(r, b, -1f, -1f)
            }
            Suggestion("Fit one question inside the corners", modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp))
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Drewy(Mood.Thinking, Modifier.width(150.dp))
                Text("Drewy needs the camera to read your question.", style = T.body, color = C.textOnBrand)
                DrewyButton("Allow camera", DrewyIcons.Camera, ButtonStyle.Primary) { permission.launch(Manifest.permission.CAMERA) }
            }
        }

        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        // top
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DrewyIconButton(DrewyIcons.Close, "Close", style = IconButtonStyle.Ghost, tint = C.textOnBrand, onClick = onClose)
            Text("Snap a question", style = T.heading, color = C.textOnBrand, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            DrewyIconButton(DrewyIcons.Flash, "Torch", style = if (torch) IconButtonStyle.Active else IconButtonStyle.Ghost, tint = if (torch) C.textPrimary else C.textOnBrand, onClick = { torch = !torch })
        }

        // bottom
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(C.bgCamera.copy(alpha = 0.85f)).navigationBarsPadding().padding(top = 14.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Suggestion(it, inverse = true) }
            Text(
                when (mode) { "check" -> "Snap YOUR working — Drewy will mark each line"; "explain" -> "Drewy will teach the idea behind it"; else -> "Point at any question — Drewy solves it step by step" },
                style = T.caption, color = C.textOnBrand.copy(alpha = 0.75f),
            )
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((m, label) in listOf("solve" to "Solve", "explain" to "Explain", "check" to "Check my work")) {
                    val bg by animateColorAsState(if (mode == m) C.bgSurface else Color.White.copy(alpha = 0f), tween(240), label = "tab")
                    val fg by animateColorAsState(if (mode == m) C.textPrimary else C.textOnBrand, tween(240), label = "tabfg")
                    Text(
                        label, style = T.label, color = fg,
                        modifier = Modifier.clip(RoundedCornerShape(50)).bouncyClick { mode = m }
                            .background(bg).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.bgPaper).bouncyClick {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentAlignment = Alignment.Center) { Icon(DrewyIcons.Gallery, "Gallery", Modifier.size(24.dp), tint = C.textPrimary) }
                // shutter
                Box(
                    Modifier.size(84.dp).border(5.dp, C.bgSurface, CircleShape).padding(9.dp).clip(CircleShape)
                        .background(if (busy) C.bgBrand else C.bgSurface)
                        .bouncyClick(enabled = hasPermission && !busy) {
                            busy = true
                            scope.launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(380)) }
                            capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val path = saveProxy(image, app.store.snapsDir)
                                    image.close(); busy = false
                                    if (path != null) onCaptured(path, mode) else error = "Couldn't save the photo."
                                }
                                override fun onError(exception: ImageCaptureException) { busy = false; error = "Capture failed: ${exception.message}" }
                            })
                        },
                )
                Drewy(if (busy) Mood.Thinking else Mood.Listening, Modifier.width(60.dp))
            }
        }
    }
}

private fun scaleDown(b: Bitmap, maxSide: Int = 1600): Bitmap {
    val k = maxSide.toFloat() / max(b.width, b.height)
    return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
}

private fun writeJpeg(b: Bitmap, dir: File): String? = runCatching {
    val f = File(dir, "snap_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }
    f.absolutePath
}.getOrNull()

private fun saveProxy(image: ImageProxy, dir: File): String? = runCatching {
    var bmp = image.toBitmap()
    val rot = image.imageInfo.rotationDegrees
    if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
    writeJpeg(scaleDown(bmp), dir)
}.getOrNull()

private fun saveUri(context: Context, uri: Uri, dir: File): String? = runCatching {
    val bmp = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: return null
    writeJpeg(scaleDown(bmp), dir)
}.getOrNull()
