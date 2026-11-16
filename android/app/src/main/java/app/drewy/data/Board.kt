package app.drewy.data

import kotlinx.serialization.Serializable

/** Page size in page units — mirrors backend/board-schema.mjs. */
const val PAGE_W = 360f
const val PAGE_H = 560f

/** What Drewy draws in answer to one question — mirrors backend/board-schema.mjs. */
@Serializable
data class Board(
    val title: String = "",
    val mood: String? = null,
    val steps: List<Step> = emptyList(),
    val followUps: List<String> = emptyList(),
    val score: String? = null,
)

@Serializable
data class Step(
    val narration: String = "",
    val newPage: Boolean? = null,
    val elements: List<Element> = emptyList(),
)

/** One thing drawn on a page, positioned in page units (360 x 560). */
@Serializable
data class Element(
    val kind: String = "text",
    val x: Float? = null,
    val y: Float? = null,
    val w: Float? = null,
    val h: Float? = null,
    val rotate: Float? = null,
    val text: String? = null,
    val size: String? = null,
    val color: String? = null,
    val prompt: String? = null,
    val query: String? = null,
    val caption: String? = null,
    val motion: String? = null,
    val paths: List<String> = emptyList(),
    val width: Float? = null,
    val fill: Boolean = false,
    val anim: String? = null,
    val from: List<Float> = emptyList(),
    val to: List<Float> = emptyList(),
    val bend: Float? = null,
    val style: String? = null,
    val verdict: String? = null,
    val why: String? = null,
    val bars: List<Bar> = emptyList(),
    val cells: List<List<String>> = emptyList(),
    val highlight: List<Int> = emptyList(),
    val parts: List<Part3D> = emptyList(),
    // filled in by the backend for pictures
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
)

@Serializable data class Bar(val label: String = "", val value: Double = 0.0)

@Serializable
data class Part3D(
    val name: String = "",
    val shape: String = "box",
    val position: List<Float> = listOf(0f, 0f, 0f),
    val size: List<Float> = listOf(1f, 1f, 1f),
    val rotation: List<Float> = emptyList(),
    val color: String = "#C9D3F5",
    val label: Boolean = false,
    val spin: Boolean = false,
)

/** Streamed from the backend while Sol is still writing. speech = a /api/speak URL already generating. */
sealed interface BoardEvent {
    data class Ack(val text: String, val speech: String?) : BoardEvent
    data class Meta(val title: String) : BoardEvent
    data class StepStart(val step: Int, val newPage: Boolean, val narration: String, val speech: String?) : BoardEvent
    data class ElementReady(val step: Int, val index: Int, val element: Element) : BoardEvent
    data class Done(val board: Board) : BoardEvent
    data class Failed(val message: String) : BoardEvent
}

/** One earlier turn of the conversation, sent so follow-ups make sense. */
@Serializable data class Turn(val q: String, val title: String? = null, val said: String? = null)

@Serializable
data class StrokeData(
    val points: List<Float>, // x0,y0,x1,y1,… in page units
    val color: Long,
    val width: Float,
    val tool: String, // pen | marker
)

/** One sheet of the shared canvas: your ink + everything Drewy drew on it. */
@Serializable
data class PageData(
    val id: String,
    val strokes: List<StrokeData> = emptyList(),
    val drawn: List<Element> = emptyList(),
    /** The question this page answers (shown in the top bar). */
    val question: String? = null,
    val title: String? = null,
    val mode: String? = null,
    val snapPath: String? = null,
    val followUps: List<String> = emptyList(),
    val score: String? = null,
    /** What Drewy said on this page (for conversation memory). */
    val said: String? = null,
)

@Serializable
data class CanvasDoc(
    val id: String,
    val title: String = "my doodle",
    val updatedAt: Long = System.currentTimeMillis(),
    val pages: List<PageData> = emptyList(),
)
