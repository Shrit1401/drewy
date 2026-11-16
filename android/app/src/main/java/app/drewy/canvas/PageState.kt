package app.drewy.canvas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.drewy.DrewyApp
import app.drewy.data.BoardEvent
import app.drewy.data.CanvasDoc
import app.drewy.data.Element
import app.drewy.data.PAGE_H
import app.drewy.data.PAGE_W
import app.drewy.data.PageData
import app.drewy.data.Step
import app.drewy.data.Turn
import app.drewy.ui.mascot.Mood
import app.drewy.ui.theme.DrewyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Something Drewy drew (or is drawing) on a page. progress 0..1 while it's being drawn. */
class DrawnUi(val el: Element, drawn: Boolean = true) {
    val progress = Animatable(if (drawn) 1f else 0f)
    /** pictures: image loaded (or failed) */
    var ready by mutableStateOf(drawn)
    /** pictures: fully painted in */
    var settled by mutableStateOf(drawn)
    /** true for things that were already on the page when it was opened (no entrance animation). */
    val restored = drawn
}

/** One sheet of the shared canvas. */
class PageUi(data: PageData) {
    val id = data.id
    val strokes = mutableStateListOf<InkStroke>().apply { addAll(data.strokes.map(InkStroke::from)) }
    val drawn = mutableStateListOf<DrawnUi>().apply { addAll(data.drawn.map { DrawnUi(it) }) }
    var question by mutableStateOf(data.question)
    var title by mutableStateOf(data.title)
    var mode by mutableStateOf(data.mode)
    var snapPath by mutableStateOf(data.snapPath)
    var followUps by mutableStateOf(data.followUps)
    var score by mutableStateOf(data.score)
    var said by mutableStateOf(data.said)

    val isBlank get() = strokes.isEmpty() && drawn.isEmpty()

    fun toData() = PageData(id, strokes.map { it.toData() }, drawn.map { it.el }, question, title, mode, snapPath, followUps, score, said)
}

private sealed interface Edit {
    data class AddStroke(val page: PageUi, val s: InkStroke) : Edit
    data class RemoveStrokes(val page: PageUi, val s: List<InkStroke>) : Edit
}

/** The shared canvas: pages of your ink + Drewy's drawings, and Drewy drawing live. */
class PageState(private val app: DrewyApp, private val scope: CoroutineScope, val id: String) {
    var title by mutableStateOf("my doodle")
    val pages = mutableStateListOf<PageUi>()
    var current by mutableIntStateOf(0)
    private val undo = ArrayDeque<Edit>()

    var tool by mutableStateOf(Tool.Pen)
    var color by mutableStateOf(DrewyTheme.colors.textPrimary)

    // Drewy
    var mood by mutableStateOf(Mood.Idle)
    var buddySay by mutableStateOf<String?>(null)
    var celebrate by mutableIntStateOf(0)
    var thinking by mutableStateOf(false)
    var drawing by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var slow by mutableStateOf(false)
    /** Caption of what Drewy is saying while he draws. */
    var caption by mutableStateOf<String?>(null)
    /** Where Drewy's pen is (page units) and on which page; null when he isn't drawing. */
    var pen by mutableStateOf<Offset?>(null)
    var penPage by mutableIntStateOf(-1)
    var error by mutableStateOf<String?>(null)
    var strokesSinceAsk by mutableIntStateOf(0)
    var lassoSelection by mutableStateOf<List<InkStroke>>(emptyList())
    private var fastForward = false

    var loaded by mutableStateOf(false); private set
    private var job: Job? = null
    private var saveJob: Job? = null
    private var sayJob: Job? = null

    val page: PageUi? get() = pages.getOrNull(current)
    val canUndo get() = undo.isNotEmpty()

    suspend fun load() {
        val doc = app.store.load(id)
        if (doc != null) { title = doc.title; pages.addAll(doc.pages.map { PageUi(it) }) }
        if (pages.isEmpty()) pages += PageUi(PageData(app.store.newId()))
        current = pages.lastIndex
        loaded = true
    }

    private fun doc() = CanvasDoc(id, title, System.currentTimeMillis(), pages.map { it.toData() })

    fun scheduleSave(now: Boolean = false) {
        saveJob?.cancel()
        val d = doc()
        saveJob = app.appScope.launch { if (!now) delay(500); app.store.save(d) }
    }

    fun say(text: String, ms: Long = 1900) {
        sayJob?.cancel(); buddySay = text
        sayJob = scope.launch { delay(ms); buddySay = null }
    }

    // ---------------- pages ----------------
    fun goTo(i: Int) { current = i.coerceIn(0, pages.lastIndex) }

    fun newBlankPage() {
        if (page?.isBlank == true) return
        pages += PageUi(PageData(app.store.newId())); current = pages.lastIndex; scheduleSave()
    }

    // ---------------- ink ----------------
    fun addStroke(p: PageUi, s: InkStroke) {
        p.strokes += s; undo.addLast(Edit.AddStroke(p, s)); strokesSinceAsk++
        if (p.strokes.size % 7 == 0 && !drawing && !thinking) say(listOf("nice", "looking good", "ooh, clean", "keep going").random(), 1400)
        scheduleSave()
    }

    fun eraseAt(p: PageUi, at: Offset, radius: Float) {
        val hit = p.strokes.filter { s -> s.bounds.inflate(radius).contains(at) && s.points.any { (it - at).getDistance() < radius + s.width / 2 } }
        if (hit.isEmpty()) return
        p.strokes.removeAll(hit); undo.addLast(Edit.RemoveStrokes(p, hit)); scheduleSave()
    }

    fun undoLast() {
        when (val e = undo.removeLastOrNull() ?: return) {
            is Edit.AddStroke -> e.page.strokes.remove(e.s)
            is Edit.RemoveStrokes -> e.page.strokes.addAll(e.s)
        }
        scheduleSave()
    }

    fun clearPage() { page?.let { it.strokes.clear(); it.drawn.clear() }; undo.clear(); scheduleSave() }

    // ---------------- asking ----------------
    /** Ask Drewy. If the current page has your ink, he sees it (and may draw right on it). */
    fun ask(prompt: String, mode: String = "ask", selection: List<InkStroke>? = null) {
        val p = page
        val ink = selection ?: p?.strokes?.toList().orEmpty()
        val image = if (ink.isNotEmpty()) pageSnapshot(ink) else null
        val question = prompt.ifBlank { if (mode == "lasso") "What did I draw?" else "Hi Drewy!" }
        run(question, mode, null, canAnnotate = ink.isNotEmpty()) { app.api.askStream(question, mode, image, history()) }
        strokesSinceAsk = 0; lassoSelection = emptyList()
    }

    fun snap(path: String, mode: String) {
        val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: run { error = "Couldn't read the photo."; return }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val question = when (mode) { "check" -> "Check my work"; "explain" -> "Explain this question"; else -> "Solve this" }
        run(question, mode, path, canAnnotate = false) { app.api.snapStream(b64, mode, history = history()) }
    }

    /** The last few things you asked and what Drewy said — so follow-ups feel like a conversation. */
    private fun history(): List<Turn> = pages.filter { it.question != null }
        .distinctBy { it.question + it.title }.takeLast(4)
        .map { Turn(it.question!!, it.title, it.said) }

    fun followUp(chip: String) {
        val t = page?.title
        ask(if (!t.isNullOrBlank()) "About \"$t\": $chip" else chip, if (page?.mode == "teach") "teach" else "ask")
    }

    private fun run(question: String, mode: String, snapPath: String?, canAnnotate: Boolean, open: () -> Flow<BoardEvent>) {
        stop()
        error = null; thinking = true; mood = Mood.Thinking; paused = false; caption = null
        job = scope.launch {
            val events = Channel<BoardEvent>(Channel.UNLIMITED)
            val reader = launch {
                try { open().collect { events.send(it) } } catch (e: Exception) { events.send(BoardEvent.Failed(e.message ?: "Something went wrong")) }
                events.close()
            }
            var target: PageUi? = null
            var speech: kotlinx.coroutines.Deferred<Unit>? = null
            var boardTitle: String? = null
            var failed: String? = null
            var followUps = emptyList<String>(); var score: String? = null
            val said = StringBuilder()
            var started = false
            try {
                for (ev in events) when (ev) {
                    is BoardEvent.Ack -> {
                        // Drewy answers out loud straight away, then thinks
                        buddySay = ev.text; caption = ev.text
                        speech = async { app.speaker.speak(ev.text, url = ev.speech) }
                    }
                    is BoardEvent.Meta -> { boardTitle = ev.title; target?.title = ev.title }
                    is BoardEvent.StepStart -> {
                        speech?.await()                      // finish the previous sentence first
                        snapshotFlow { paused }.first { !it }
                        val annotateHere = !started && !ev.newPage && canAnnotate && page != null
                        if (target == null || (started && ev.newPage)) {
                            target = if (annotateHere) page!! else newAnswerPage(question, mode, snapPath, boardTitle)
                            if (started) delay(380)        // let the page turn
                        }
                        val tp = target!!
                        goTo(pages.indexOf(tp)); penPage = current
                        thinking = false; drawing = true; mood = Mood.Happy; buddySay = null
                        caption = ev.narration
                        if (ev.narration.isNotBlank()) said.append(ev.narration).append(' ')
                        fastForward = false
                        speech = async { app.speaker.speak(ev.narration, slow, ev.speech) }
                        started = true
                    }
                    is BoardEvent.ElementReady -> {
                        val tp = target ?: continue
                        snapshotFlow { paused }.first { !it }
                        val d = DrawnUi(layout(ev.element), drawn = false)
                        tp.drawn += d
                        val ms = if (fastForward) 120 else (durationFor(d.el) * if (slow) 1.5f else 1f).toInt()
                        d.progress.animateTo(1f, tween(ms, easing = if (d.el.kind in listOf("ink", "arrow", "mark")) LinearEasing else FastOutSlowInEasing))
                        // pictures: Drewy keeps sketching until the image is ready, then paints it in
                        if (d.el.kind in listOf("illustration", "photo") && !fastForward) {
                            kotlinx.coroutines.withTimeoutOrNull(25_000) { snapshotFlow { d.settled }.first { it } }
                        }
                        pen = null
                    }
                    is BoardEvent.Done -> { followUps = ev.board.followUps; score = ev.board.score; if (ev.board.title.isNotBlank()) boardTitle = ev.board.title }
                    is BoardEvent.Failed -> failed = ev.message
                }
                speech?.await()
                reader.join()
            } finally {
                drawing = false; pen = null; caption = null; thinking = false
            }
            val tp = target
            if (tp == null) {
                error = failed ?: "Drewy couldn't draw that — try again?"; mood = Mood.Oops; say("uh oh…", 2200)
                delay(3500); if (mood == Mood.Oops) mood = Mood.Idle
                return@launch
            }
            tp.title = boardTitle ?: tp.title
            tp.followUps = followUps; tp.score = score; tp.said = said.toString().trim()
            scheduleSave(now = true)
            mood = Mood.Happy
            val checks = tp.drawn.map { it.el }.filter { it.kind == "check" }
            val allRight = checks.isNotEmpty() && checks.all { it.verdict == "correct" }
            when {
                mode == "solve" || allRight -> { celebrate++; say(if (allRight) "all correct — nice!" else "solved it", 2200) }
                mode == "check" -> say("so close — you've got this", 2200)
                else -> say(listOf("there you go", "make sense?", "hope that helps").random(), 1800)
            }
            delay(3500); if (!drawing && !thinking) mood = Mood.Idle
        }
    }

    private fun newAnswerPage(question: String, mode: String, snapPath: String?, title: String?): PageUi {
        // reuse the current page if it's still empty
        val reuse = page?.takeIf { it.isBlank && it.question == null }
        val p = reuse ?: PageUi(PageData(app.store.newId())).also { pages += it }
        p.question = question; p.mode = mode; p.snapPath = snapPath; p.title = title
        return p
    }

    /** Tap the page while Drewy draws → finish this step quickly. */
    fun hurry() { fastForward = true }

    fun togglePause() { paused = !paused; if (paused) app.speaker.stop() }

    fun stop() {
        job?.cancel(); job = null
        app.speaker.stop()
        pages.forEach { p -> p.drawn.forEach { d -> if (d.progress.value < 1f) scope.launch { d.progress.snapTo(1f) } } }
        drawing = false; thinking = false; pen = null; caption = null
    }

    fun dispose() { stop(); scheduleSave(now = true) }

    companion object {
        /** Keep everything on the page and give sensible default sizes. */
        fun layout(e: Element): Element {
            val x = (e.x ?: 20f).coerceIn(4f, PAGE_W - 40f)
            val y = (e.y ?: 20f).coerceIn(4f, PAGE_H - 30f)
            val defW = when (e.kind) { "illustration" -> 260f; "photo" -> 210f; "chart", "model3d" -> 320f; "grid" -> 300f; else -> PAGE_W - x - 14f }
            val w = (e.w ?: defW).coerceIn(30f, PAGE_W - x - 6f)
            val defH = when (e.kind) { "illustration" -> w; "chart" -> 190f; "model3d" -> 280f; else -> e.h ?: 0f }
            val h = (e.h ?: defH).coerceIn(0f, PAGE_H - y - 4f)
            return e.copy(x = x, y = y, w = w, h = if (h > 0f) h else e.h)
        }

        fun durationFor(el: Element): Int = when (el.kind) {
            "text" -> (420 + (el.text?.length ?: 0) * 55).coerceAtMost(2600)
            "check" -> 1300
            "ink" -> (500 + el.paths.size * 320).coerceAtMost(4200)
            "arrow" -> 650
            "mark" -> 700
            "illustration" -> 2200
            "photo" -> 900
            "chart" -> 1500
            "grid" -> (400 + el.cells.sumOf { it.size } * 90).coerceAtMost(2600)
            "model3d" -> (900 + el.parts.size * 420).coerceAtMost(9000) // assembled part by part
            else -> 800
        }
    }
}

/** The page's ink as a PNG covering exactly the 360x560 page (so coordinates match for Drewy). */
fun pageSnapshot(strokes: List<InkStroke>, scale: Float = 2f): String {
    val bmp = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    c.scale(scale, scale)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (s in strokes) {
        paint.color = s.color.copy(alpha = if (s.tool == Tool.Marker) 0.45f else 1f).toArgb(); paint.strokeWidth = s.width
        val p = android.graphics.Path(); s.points.firstOrNull()?.let { p.moveTo(it.x, it.y) }; s.points.drop(1).forEach { p.lineTo(it.x, it.y) }
        c.drawPath(p, paint)
    }
    val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun loadSnapBitmap(path: String, maxSide: Int = 900) = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (max(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

val InkColors = listOf(
    DrewyTheme.colors.textPrimary, DrewyTheme.colors.bgBrand, DrewyTheme.colors.popPink,
    DrewyTheme.colors.popSun, DrewyTheme.colors.popGreen,
)

@Suppress("unused") private val keep = listOf(Color.Unspecified, mutableFloatStateOf(0f))
