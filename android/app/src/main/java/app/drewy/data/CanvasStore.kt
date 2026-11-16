package app.drewy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Saves each canvas as a JSON file in app storage. */
class CanvasStore(context: Context) {
    private val dir = File(context.filesDir, "canvases").apply { mkdirs() }
    val snapsDir = File(context.filesDir, "snaps").apply { mkdirs() }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    suspend fun load(id: String): CanvasDoc? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else runCatching { DrewyJson.decodeFromString(CanvasDoc.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun save(doc: CanvasDoc) = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${doc.id}.json.tmp")
        tmp.writeText(DrewyJson.encodeToString(CanvasDoc.serializer(), doc))
        tmp.renameTo(File(dir, "${doc.id}.json"))
    }

    suspend fun recent(limit: Int = 12): List<CanvasDoc> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .mapNotNull { runCatching { DrewyJson.decodeFromString(CanvasDoc.serializer(), it.readText()) }.getOrNull() }
            .filter { d -> d.pages.any { it.strokes.isNotEmpty() || it.drawn.isNotEmpty() } }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { File(dir, "$id.json").delete() }
}
