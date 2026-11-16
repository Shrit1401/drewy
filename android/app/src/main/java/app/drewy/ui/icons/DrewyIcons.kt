package app.drewy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The 24px icon set from the Figma "Icons" section, ported 1:1 from the same SVG paths.
 * Tint with Icon(tint = …) — strokes and fills are drawn in black and recoloured.
 */
object DrewyIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y H${x + w - r} Q${x + w},$y ${x + w},${y + r} V${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} H${x + r} Q$x,${y + h} $x,${y + h - r} V${y + r} Q$x,$y ${x + r},$y Z"

    private fun build(name: String, strokes: List<String> = emptyList(), fills: List<String> = emptyList(), width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in fills) addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
            for (d in strokes) addPath(
                PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    val Pen = build("pen", listOf("M4 20l4-1 11-11-3-3L5 16l-1 4z", "M14 6l3 3"))
    val Marker = build("marker", listOf("M6 18l2-6 8-8 4 4-8 8-6 2z", "M4 21h16"))
    val Eraser = build("eraser", listOf("M8 20h12M4 15l9-10 7 7-8 8H8l-4-5z"))
    val Shapes = build("shapes", listOf(rect(3f, 12f, 8f, 8f, 1.5f), circle(16.5f, 7.5f, 4.5f)))
    val Lasso = build("lasso", listOf("M12 4c5 0 8 2.5 8 5.5S16 15 12 15 4 12.5 4 9.5 7 4 12 4z", "M7 14c-1 2 0 4 2 5"))
    val Undo = build("undo", listOf("M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3"))
    val Image = build("image", listOf(rect(3f, 4f, 18f, 16f, 3f), "M4 18l5-5 4 4 3-3 4 4"), listOf(circle(9f, 10f, 2f)))
    val Cube = build("cube", listOf("M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z", "M4 7.5l8 4.5 8-4.5M12 12v9"))
    val Book = build("book", listOf("M4 5c3-1.5 6-1.5 8 .5 2-2 5-2 8-.5v14c-3-1.5-6-1.5-8 .5-2-2-5-2-8-.5V5z", "M12 5.5v14"))
    val Sparkle = build("sparkle", listOf("M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6z", "M19 3v3M17.5 4.5h3"))
    val Bulb = build("bulb", listOf("M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z"))
    val Brush = build("brush", listOf("M14 4l6 6-7 7-6-6 7-7z", "M7 11c-3 0-4 3-4 6v3h3c3 0 6-1 6-4"))
    val Play = build("play", fills = listOf("M8 5l11 7-11 7V5z"))
    val Hand = build("hand", listOf("M8 12V6a1.5 1.5 0 0 1 3 0v5M11 11V4.5a1.5 1.5 0 0 1 3 0V11M14 11V6a1.5 1.5 0 0 1 3 0v7c0 4-2.5 7-6 7-2.5 0-4-1.5-5.5-3.5L4 13.5a1.5 1.5 0 0 1 2.5-1.5L8 14"))
    val Home = build("home", listOf("M4 11l8-7 8 7v9h-5v-6H9v6H4v-9z"))
    val More = build("more", fills = listOf(circle(5f, 12f, 2f), circle(12f, 12f, 2f), circle(19f, 12f, 2f)))
    val Back = build("back", listOf("M15 5l-7 7 7 7"), width = 2.4f)
    val Next = build("next", listOf("M9 5l7 7-7 7"), width = 2.4f)
    val Rotate = build("rotate", listOf("M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4"))
    val Camera = build("camera", listOf("M4 8.5A2.5 2.5 0 0 1 6.5 6h1.8l1.4-2h4.6l1.4 2h1.8A2.5 2.5 0 0 1 20 8.5v9a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5v-9z", circle(12f, 13f, 3.6f)))
    val Mic = build("mic", listOf("M5 11a7 7 0 0 0 14 0M12 18v3"), listOf(rect(9f, 3f, 6f, 11f, 3f)), width = 2.2f)
    val Flash = build("flash", listOf("M13 3L5 14h6l-1 7 8-11h-6l1-7z"))
    val Close = build("close", listOf("M6 6l12 12M18 6L6 18"), width = 2.4f)
    val Check = build("check", listOf("M5 12.5l4.5 4.5L19 7.5"), width = 2.6f)
    val Gallery = build("gallery", listOf(rect(3f, 3f, 18f, 18f, 4f), "M3 16l5-5 4 4 3-3 6 6"))
    val Pause = build("pause", fills = listOf(rect(6f, 4f, 4.5f, 16f, 2f), rect(13.5f, 4f, 4.5f, 16f, 2f)))
    val Stop = build("stop", fills = listOf(rect(6f, 6f, 12f, 12f, 3f)))
    val Wand = build("wand", listOf("M4 20L15 9M13 7l4 4", "M18 2v4M16 4h4M20 10v3M18.5 11.5h3M8 3v3M6.5 4.5h3"))
    val Search = build("search", listOf(circle(11f, 11f, 6.5f), "M16 16l4.5 4.5"), width = 2.2f)
    val Plus = build("plus", listOf("M12 5v14M5 12h14"), width = 2.4f)
    val Send = build("send", listOf("M5 12h13M13 6l6 6-6 6"), width = 2.4f)
    val Keyboard = build("keyboard", listOf(rect(2.5f, 6f, 19f, 12f, 3f), "M6 10h1M10 10h1M14 10h1M18 10h0M7 14h10"))
    val Trash = build("trash", listOf("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"))
}
