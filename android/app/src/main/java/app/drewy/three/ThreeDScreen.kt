package app.drewy.three

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.drewy.data.Element
import app.drewy.ui.components.AskBar
import app.drewy.ui.components.ChipColor
import app.drewy.ui.components.DrewySlider
import app.drewy.ui.components.entrance
import app.drewy.ui.mascot.Drewy
import app.drewy.ui.mascot.Mood
import app.drewy.ui.components.DrewyChip
import app.drewy.ui.components.TopBar
import app.drewy.ui.components.softShadow
import app.drewy.ui.icons.DrewyIcons
import app.drewy.ui.theme.DrewyTheme

private val C = DrewyTheme.colors
private val T = DrewyTheme.type

/** Full-screen "Make it 3D" viewer with cutaway + explode sliders. onAsk sends a question back to the canvas. */
@Composable
fun ThreeDScreen(model: Element, onBack: () -> Unit, onAsk: (String) -> Unit) {
    val parts = model.parts.ifEmpty { SampleModel }
    var explode by remember { mutableFloatStateOf(0f) }
    var cut by remember { mutableFloatStateOf(0f) }
    var picked by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val explodeAnim by animateFloatAsState(explode, spring(dampingRatio = 0.6f, stiffness = 200f), label = "explode")
    val name = model.caption ?: "model"
    Box(Modifier.fillMaxSize().background(C.appGradient)) {
        Scene3D(parts, Modifier.fillMaxSize().padding(top = 120.dp, bottom = 330.dp), explode = explodeAnim, cut = cut, onPartTap = { picked = it.name })
        Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopBar(name, "live 3D", onBack)
            Row(
                Modifier.align(Alignment.CenterHorizontally).entrance(1).background(C.bgInverse.copy(alpha = 0.88f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(DrewyIcons.Rotate, null, Modifier.size(14.dp), tint = C.textOnBrand)
                Text(picked?.let { "picked: $it" } ?: "drag to spin · pinch to zoom · tap a part", style = T.caption, color = C.textOnBrand)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val shape = RoundedCornerShape(DrewyTheme.radius.xl)
            Column(
                Modifier.fillMaxWidth().entrance(2).softShadow(shape, 8.dp).background(C.bgSurface, shape).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DrewySlider("Cutaway", cut, { cut = it }, Modifier.fillMaxWidth())
                DrewySlider("Explode", explode, { explode = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrewyChip(if (explode > 0.5f) "Put back" else "Explode", DrewyIcons.Sparkle, ChipColor.Pink) { explode = if (explode > 0.5f) 0f else 1f }
                    DrewyChip(picked?.let { "Explain part" } ?: "Explain it", DrewyIcons.Book, ChipColor.Yellow) {
                        onAsk(picked?.let { "Explain the \"$it\" part of the $name." } ?: "Explain how the $name works, part by part.")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.entrance(3)) {
                Drewy(Mood.Idle, Modifier.width(64.dp))
                AskBar(
                    text = text, onTextChange = { text = it }, onSend = { if (text.isNotBlank()) onAsk("About the $name: $text") },
                    onCamera = {}, onMicPress = {}, onMicRelease = {}, listening = false, level = 0f, partial = "",
                    placeholder = picked?.let { "ask about the $it…" } ?: "ask about this model…", modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
