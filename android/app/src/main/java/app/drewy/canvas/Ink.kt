package app.drewy.canvas

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import app.drewy.data.StrokeData
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Tool { Pen, Marker, Eraser, Shapes, Lasso, Hand }

/** One ink stroke in canvas world coordinates. */
class InkStroke(val points: List<Offset>, val color: Color, val width: Float, val tool: Tool) {
    val bounds: Rect by lazy {
        if (points.isEmpty()) Rect.Zero else Rect(
            points.minOf { it.x } - width, points.minOf { it.y } - width,
            points.maxOf { it.x } + width, points.maxOf { it.y } + width,
        )
    }
    val path: Path by lazy { smoothPath(points) }

    fun toData() = StrokeData(points.flatMap { listOf(it.x, it.y) }, color.toArgb().toLong(), width, if (tool == Tool.Marker) "marker" else "pen")

    companion object {
        fun from(d: StrokeData): InkStroke {
            val pts = d.points.chunked(2).filter { it.size == 2 }.map { Offset(it[0], it[1]) }
            return InkStroke(pts, Color(d.color.toInt()), d.width, if (d.tool == "marker") Tool.Marker else Tool.Pen)
        }
    }
}

/** Quadratic smoothing through midpoints — makes finger ink look like a marker. */
fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) { lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f); return@apply }
    for (i in 1 until pts.size - 1) {
        val mid = (pts[i] + pts[i + 1]) / 2f
        quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
    }
    lineTo(pts.last().x, pts.last().y)
}

fun DrawScope.drawInk(s: InkStroke, alpha: Float = 1f) {
    val a = if (s.tool == Tool.Marker) 0.38f * alpha else alpha
    drawPath(s.path, s.color.copy(alpha = a), style = Stroke(s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** "Shapes" tool: turn a wobbly stroke into a clean line, rectangle or ellipse. */
fun snapShape(points: List<Offset>): List<Offset> {
    if (points.size < 4) return points
    val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    val diag = hypot(maxX - minX, maxY - minY).coerceAtLeast(1f)
    val closed = (points.first() - points.last()).getDistance() < diag * 0.25f
    if (!closed) return listOf(points.first(), points.last())
    // Rectangles pass close to all four bounding-box corners; ellipses stay ~0.3r away from them.
    val corners = listOf(Offset(minX, minY), Offset(maxX, minY), Offset(maxX, maxY), Offset(minX, maxY))
    val cornerHits = corners.count { c -> points.any { (it - c).getDistance() < diag * 0.12f } }
    return if (cornerHits >= 3) {
        listOf(Offset(minX, minY), Offset(maxX, minY), Offset(maxX, maxY), Offset(minX, maxY), Offset(minX, minY))
    } else {
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val rx = (maxX - minX) / 2; val ry = (maxY - minY) / 2
        (0..48).map { i -> val a = 2 * PI * i / 48; Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()) }
    }
}

/** Sharp corners for snapped rectangles (no smoothing). */
fun isPolyline(points: List<Offset>) = points.size in 2..5

fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y + 1e-6f) + a.x) inside = !inside
        j = i
    }
    return inside
}

fun strokesInLasso(strokes: List<InkStroke>, lasso: List<Offset>): List<InkStroke> =
    if (lasso.size < 3) emptyList() else strokes.filter { s -> s.points.count { pointInPolygon(it, lasso) } >= s.points.size * 0.5f }

fun unionBounds(strokes: List<InkStroke>): Rect? =
    strokes.map { it.bounds }.reduceOrNull { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }

/** Render strokes to a PNG (white paper background) for Claude to look at. Returns base64. */
fun strokesToPngBase64(strokes: List<InkStroke>, area: Rect? = null, maxSide: Int = 1024): String? {
    val b = area ?: unionBounds(strokes) ?: return null
    val pad = 24f
    val w = (b.width + 2 * pad).coerceAtLeast(16f); val h = (b.height + 2 * pad).coerceAtLeast(16f)
    val k = min(1f, maxSide / max(w, h))
    val bmp = Bitmap.createBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    c.scale(k, k); c.translate(pad - b.left, pad - b.top)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (s in strokes) {
        paint.color = s.color.copy(alpha = if (s.tool == Tool.Marker) 0.45f else 1f).toArgb()
        paint.strokeWidth = s.width
        val p = android.graphics.Path()
        s.points.firstOrNull()?.let { p.moveTo(it.x, it.y) }
        for (pt in s.points.drop(1)) p.lineTo(pt.x, pt.y)
        c.drawPath(p, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
