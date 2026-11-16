package app.drewy.three

import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import app.drewy.R
import app.drewy.data.Part3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Tiny vector type for the software renderer. */
private data class V(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V(x * s, y * s, z * s)
    fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: V) = x * o.x + y * o.y + z * o.z
    fun norm(): V { val l = sqrt(dot(this)).takeIf { it > 1e-6f } ?: 1f; return V(x / l, y / l, z / l) }
}

private fun V.rotX(a: Float): V { val c = cos(a); val s = sin(a); return V(x, y * c - z * s, y * s + z * c) }
private fun V.rotY(a: Float): V { val c = cos(a); val s = sin(a); return V(x * c + z * s, y, -x * s + z * c) }
private fun V.rotZ(a: Float): V { val c = cos(a); val s = sin(a); return V(x * c - y * s, x * s + y * c, z) }

private class Tri(val a: V, val b: V, val c: V)

/** Unit-ish meshes for each primitive, in part-local space (already sized). */
private fun meshFor(p: Part3D): List<Tri> {
    val s = p.size
    fun sz(i: Int, d: Float) = s.getOrNull(i)?.takeIf { it > 0f } ?: d
    val out = ArrayList<Tri>()
    fun quad(a: V, b: V, c: V, d: V) { out += Tri(a, b, c); out += Tri(a, c, d) }
    when (p.shape) {
        "sphere" -> {
            val r = sz(0, 1f) / 2; val lat = 10; val lon = 16
            fun pt(i: Int, j: Int): V { val th = PI.toFloat() * i / lat; val ph = 2 * PI.toFloat() * j / lon; return V(r * sin(th) * cos(ph), r * cos(th), r * sin(th) * sin(ph)) }
            for (i in 0 until lat) for (j in 0 until lon) quad(pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1))
        }
        "cylinder", "cone" -> {
            val r = sz(0, 1f) / 2; val h = sz(1, 1f); val n = 20; val top = if (p.shape == "cone") 0f else r
            fun ring(y: Float, rr: Float, j: Int) = V(rr * cos(2 * PI.toFloat() * j / n), y, rr * sin(2 * PI.toFloat() * j / n))
            for (j in 0 until n) {
                quad(ring(-h / 2, r, j), ring(-h / 2, r, j + 1), ring(h / 2, top, j + 1), ring(h / 2, top, j))
                out += Tri(V(0f, -h / 2, 0f), ring(-h / 2, r, j + 1), ring(-h / 2, r, j))
                if (top > 0f) out += Tri(V(0f, h / 2, 0f), ring(h / 2, top, j), ring(h / 2, top, j + 1))
            }
        }
        "torus" -> {
            val d = sz(0, 1.5f); val t = sz(1, 0.3f); val big = (d - t) / 2; val small = t / 2; val n = 22; val m = 10
            fun pt(i: Int, j: Int): V {
                val u = 2 * PI.toFloat() * i / n; val v = 2 * PI.toFloat() * j / m
                return V((big + small * cos(v)) * cos(u), small * sin(v), (big + small * cos(v)) * sin(u))
            }
            for (i in 0 until n) for (j in 0 until m) quad(pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1))
        }
        else -> { // box
            val w = sz(0, 1f) / 2; val h = sz(1, 1f) / 2; val d = sz(2, 1f) / 2
            val c = listOf(V(-w, -h, -d), V(w, -h, -d), V(w, h, -d), V(-w, h, -d), V(-w, -h, d), V(w, -h, d), V(w, h, d), V(-w, h, d))
            quad(c[0], c[1], c[2], c[3]); quad(c[5], c[4], c[7], c[6]); quad(c[4], c[0], c[3], c[7])
            quad(c[1], c[5], c[6], c[2]); quad(c[3], c[2], c[6], c[7]); quad(c[4], c[5], c[1], c[0])
        }
    }
    return out
}

private fun parseColor(hex: String): Int = runCatching { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }
    .getOrDefault(0xFFC9D3F5.toInt())

private fun extentOf(p: Part3D): Float {
    val pos = V(p.position.getOrElse(0) { 0f }, p.position.getOrElse(1) { 0f }, p.position.getOrElse(2) { 0f })
    return sqrt(pos.dot(pos)) + (p.size.maxOrNull() ?: 1f) * 0.6f
}

private class Prepared(val part: Part3D, val local: List<Tri>, val color: Int, val pos: V, val rot: V)

/**
 * Interactive 3D view for a Drewy "scene recipe".
 * Drag = orbit, pinch = zoom, tap = pick a part. explode/cut are 0..1.
 */
@Composable
fun Scene3D(
    parts: List<Part3D>,
    modifier: Modifier = Modifier,
    explode: Float = 0f,
    cut: Float = 0f,
    autoRotate: Boolean = true,
    showLabels: Boolean = true,
    interactive: Boolean = true,
    onPartTap: ((Part3D) -> Unit)? = null,
    /** 0..1 — assembles the model part by part in build order (1 = complete). */
    build: Float = 1f,
) {
    val context = LocalContext.current
    val prepared = remember(parts) {
        val ext = parts.maxOfOrNull { extentOf(it) }?.coerceAtLeast(0.5f) ?: 1f
        val k = 1.6f / ext
        parts.map { p ->
            Prepared(
                p, meshFor(p).map { Tri(it.a * k, it.b * k, it.c * k) }, parseColor(p.color),
                V(p.position.getOrElse(0) { 0f }, p.position.getOrElse(1) { 0f }, p.position.getOrElse(2) { 0f }) * k,
                V(p.rotation.getOrElse(0) { 0f }, p.rotation.getOrElse(1) { 0f }, p.rotation.getOrElse(2) { 0f }) * (PI.toFloat() / 180f),
            )
        }
    }
    var yaw by remember { mutableFloatStateOf(-0.6f) }
    var pitch by remember { mutableFloatStateOf(0.42f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var touched by remember { mutableLongStateOf(0L) }
    var time by remember { mutableFloatStateOf(0f) }
    val labelCenters = remember { HashMap<Part3D, Pair<Float, Float>>() }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.nunito_bold); textSize = 30f; color = 0xFF2B2D6E.toInt()
        }
    }
    val pillPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(8f, 0f, 3f, 0x33000000) } }
    val dotPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val triPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    LaunchedEffect(prepared) {
        if (!Scene3DClock.running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                time += dt
                if (autoRotate && System.currentTimeMillis() - touched > 2500) yaw += dt * 0.35f
            }
        }
    }

    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, gZoom, _ ->
                touched = System.currentTimeMillis()
                yaw += pan.x * 0.008f
                pitch = (pitch + pan.y * 0.008f).coerceIn(-1.4f, 1.4f)
                zoom = (zoom * gZoom).coerceIn(0.5f, 3f)
            }
        }
        .pointerInput(onPartTap) {
            detectTapGestures { pt ->
                touched = System.currentTimeMillis()
                val hit = labelCenters.minByOrNull { (_, c) -> hypot(c.first - pt.x, c.second - pt.y) }
                if (hit != null && hypot(hit.value.first - pt.x, hit.value.second - pt.y) < 90f) onPartTap?.invoke(hit.key)
            }
        }

    Canvas(modifier.then(gestures)) {
        @Suppress("UNUSED_EXPRESSION") time // read so we redraw every frame
        val w = size.width; val h = size.height
        val f = min(w, h) * 1.45f * zoom
        val camD = 5.5f
        val light = V(-0.45f, 0.75f, 0.65f).norm()
        val maxX = prepared.maxOfOrNull { abs(it.pos.x) + 1f } ?: 1f
        val cutX = maxX - cut * 2 * maxX

        data class Out(val z: Float, val xy: FloatArray, val color: Int)
        val tris = ArrayList<Out>(4096)
        labelCenters.clear()
        val labelList = ArrayList<Triple<Part3D, Float, Float>>()

        fun view(p: V) = p.rotY(yaw).rotX(pitch)
        fun project(p: V): Pair<Float, Float> { val s = f / (camD - p.z); return Pair(w / 2 + p.x * s, h / 2 - p.y * s) }

        val n = prepared.size.coerceAtLeast(1)
        for ((pi, pp) in prepared.withIndex()) {
            // real-time assembly: each part drops in from above and springs into place, in build order
            val local = if (build >= 1f) 1f else (build * n - pi).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val grow = androidx.compose.animation.core.EaseOutBack.transform(local).coerceAtLeast(0.01f)
            val drop = (1f - androidx.compose.animation.core.FastOutSlowInEasing.transform(local)) * 1.4f
            val spinA = if (pp.part.spin) time * 1.4f else 0f
            val offset = pp.pos * (1f + explode * 1.3f) + (if (pp.pos.dot(pp.pos) < 1e-4f) V(0f, explode * 0.0f, 0f) else V(0f, 0f, 0f))
            fun world(v: V) = (v * grow).rotX(pp.rot.x).rotY(pp.rot.y).rotZ(pp.rot.z).rotY(spinA) + offset + V(0f, drop, 0f)
            val r = (pp.color shr 16) and 0xFF; val g = (pp.color shr 8) and 0xFF; val b = pp.color and 0xFF
            for (t in pp.local) {
                val wa = world(t.a); val wb = world(t.b); val wc = world(t.c)
                if (cut > 0f && (wa.x + wb.x + wc.x) / 3f > cutX) continue
                val a = view(wa); val bb = view(wb); val c = view(wc)
                val n = (bb - a).cross(c - a).norm()
                val lum = 0.42f + 0.58f * abs(n.dot(light))
                val col = (0xFF shl 24) or ((r * lum).toInt().coerceIn(0, 255) shl 16) or ((g * lum).toInt().coerceIn(0, 255) shl 8) or (b * lum).toInt().coerceIn(0, 255)
                val pa = project(a); val pb = project(bb); val pc = project(c)
                tris += Out((a.z + bb.z + c.z) / 3f, floatArrayOf(pa.first, pa.second, pb.first, pb.second, pc.first, pc.second), col)
            }
            val labelled = (pp.part.label || prepared.size <= 6) && local >= 1f
            if (showLabels && labelled && pp.part.name.isNotBlank()) {
                val c = project(view(offset + V(0f, (pp.part.size.getOrNull(1) ?: pp.part.size.firstOrNull() ?: 1f) * 0.0f, 0f)))
                labelList += Triple(pp.part, c.first, c.second)
            }
            labelCenters[pp.part] = project(view(offset))
        }
        tris.sortBy { it.z }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (Build.VERSION.SDK_INT >= 29) {
                val verts = FloatArray(tris.size * 6); val colors = IntArray(tris.size * 3)
                tris.forEachIndexed { i, t ->
                    System.arraycopy(t.xy, 0, verts, i * 6, 6)
                    colors[i * 3] = t.color; colors[i * 3 + 1] = t.color; colors[i * 3 + 2] = t.color
                }
                nc.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, null, 0, 0, triPaint)
            } else {
                val path = android.graphics.Path()
                for (t in tris) {
                    path.reset(); path.moveTo(t.xy[0], t.xy[1]); path.lineTo(t.xy[2], t.xy[3]); path.lineTo(t.xy[4], t.xy[5]); path.close()
                    triPaint.color = t.color; triPaint.style = Paint.Style.FILL_AND_STROKE; triPaint.strokeWidth = 1f
                    nc.drawPath(path, triPaint)
                }
            }
            for ((part, x, y) in labelList) {
                val tw = labelPaint.measureText(part.name)
                val rect = RectF(x - tw / 2 - 30f, y - 26f, x + tw / 2 + 18f, y + 22f)
                nc.drawRoundRect(rect, 22f, 22f, pillPaint)
                dotPaint.color = parseColor(part.color)
                nc.drawCircle(rect.left + 18f, y - 2f, 8f, dotPaint)
                nc.drawText(part.name, rect.left + 32f, y + 8f, labelPaint)
            }
        }
    }
}

/** Tests switch the realtime clock off so the UI can go idle. */
object Scene3DClock { @Volatile var running = true }

/** Fallback model when the AI returns none, so the 3D screen is never empty. */
val SampleModel: List<Part3D> = listOf(
    Part3D("Sun", "sphere", listOf(0f, 0f, 0f), listOf(1.2f), color = "#FFC21A", label = true),
    Part3D("Orbit", "torus", listOf(0f, 0f, 0f), listOf(3.2f, 0.04f), color = "#C9D3F5"),
    Part3D("Earth", "sphere", listOf(1.6f, 0f, 0f), listOf(0.4f), color = "#3D7BFF", label = true),
    Part3D("Moon", "sphere", listOf(1.95f, 0.2f, 0f), listOf(0.14f), color = "#E4E6F2", label = true),
)

internal fun max3(a: Float, b: Float, c: Float) = max(a, max(b, c))
