package app.drewy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import app.drewy.ui.mascot.DrewyIntro
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.drewy.camera.SnapScreen
import app.drewy.canvas.CanvasArgs
import app.drewy.canvas.CanvasScreen
import app.drewy.data.Element
import app.drewy.home.HomeScreen
import app.drewy.three.ThreeDScreen

sealed interface Screen {
    data object Home : Screen
    data class Canvas(val args: CanvasArgs) : Screen
    /** Camera. canvasId = the canvas to answer on (null = make a new one). Drawn as an overlay. */
    data class Snap(val canvasId: String?) : Screen
    /** Full-screen 3D viewer, drawn as an overlay over its canvas. */
    data class ThreeD(val model: Element) : Screen
}

private val Screen.isOverlay get() = this is Screen.Snap || this is Screen.ThreeD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { DrewyNav() }
    }
}

@Composable
private fun DrewyNav() {
    val app = LocalContext.current.applicationContext as DrewyApp
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(stack.size > 1) { pop() }

    // Base screen (Home or a Canvas) stays composed under overlays so in-flight answers keep going.
    val base = stack.last { !it.isOverlay }
    val overlay = stack.last().takeIf { it.isOverlay }
    var showIntro by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            base,
            transitionSpec = {
                // forward (into a canvas) slides in from the right; back slides out to the right
                val forward = targetState is Screen.Canvas && initialState !is Screen.Canvas
                val spec = tween<IntOffset>(380, easing = FastOutSlowInEasing)
                if (forward) (slideInHorizontally(spec) { it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(spec) { -it / 6 } + fadeOut(tween(220)))
                else (slideInHorizontally(spec) { -it / 6 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(spec) { it / 3 } + fadeOut(tween(220)))
            },
            label = "base",
        ) { screen ->
            when (screen) {
                is Screen.Canvas -> CanvasScreen(
                    screen.args,
                    onBack = ::pop,
                    onSnap = { push(Screen.Snap(screen.args.id)) },
                    onOpen3D = { push(Screen.ThreeD(it)) },
                )
                else -> HomeScreen(
                    onOpenCanvas = { id, prompt, mode, hint -> push(Screen.Canvas(CanvasArgs(id ?: app.store.newId(), prompt, mode, hint))) },
                    onSnap = { push(Screen.Snap(null)) },
                )
            }
        }
        AnimatedVisibility(overlay != null, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
            when (val o = overlay ?: return@AnimatedVisibility) {
                is Screen.Snap -> SnapScreen(
                    onClose = ::pop,
                    onCaptured = { path, mode ->
                        Handoff.pendingSnap = PendingSnap(path, mode)
                        pop()
                        if (o.canvasId == null) push(Screen.Canvas(CanvasArgs(app.store.newId())))
                    },
                )
                is Screen.ThreeD -> ThreeDScreen(o.model, onBack = ::pop, onAsk = { q -> Handoff.pendingAsk = q; pop() })
                else -> Unit
            }
        }
        if (showIntro) DrewyIntro(onDone = { showIntro = false })
    }
}
