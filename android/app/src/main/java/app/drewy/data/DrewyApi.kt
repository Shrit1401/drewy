package app.drewy.data

import android.content.Context
import app.drewy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

val DrewyJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

class DrewyApiException(message: String) : Exception(message)

/** Talks to the Drewy backend (which holds the OpenAI key). */
class DrewyApi(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "DrewyApp/0.1 (Android)").build()) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var baseUrl: String
        get() = prefs().getString(KEY_URL, null) ?: BuildConfig.BACKEND_URL
        set(v) = prefs().edit().putString(KEY_URL, v.trim().trimEnd('/')).apply()

    private fun prefs() = context.getSharedPreferences("drewy", Context.MODE_PRIVATE)

    /** mode: ask | teach | 3d | lasso. image: base64 PNG of the canvas/selection. */
    suspend fun ask(prompt: String, mode: String, image: String? = null): Board =
        post("/api/ask", prompt, mode, image)

    /** mode: solve | explain | check. image: base64 JPEG from the camera. */
    suspend fun snap(image: String, mode: String, prompt: String = ""): Board =
        post("/api/snap", prompt, mode, image)

    /** Stream a board: ack → meta → stepStart/element… → done. */
    fun askStream(prompt: String, mode: String, image: String? = null, history: List<Turn> = emptyList()) = stream("/api/ask", prompt, mode, image, history)

    fun snapStream(image: String, mode: String, prompt: String = "", history: List<Turn> = emptyList()) = stream("/api/snap", prompt, mode, image, history)

    private fun prim(o: kotlinx.serialization.json.JsonObject, k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content

    private fun stream(path: String, prompt: String, mode: String, image: String?, history: List<Turn>): kotlinx.coroutines.flow.Flow<BoardEvent> = kotlinx.coroutines.flow.flow {
        ensureReachable()
        val body = buildJsonObject {
            put("prompt", prompt); put("mode", mode)
            if (image != null) put("image", image)
            put("history", DrewyJson.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Turn.serializer()), history))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl$path?stream=1").post(body).build()
        val res = try { http.newCall(req).execute() } catch (e: IOException) {
            throw DrewyApiException("Can't reach Drewy's server — is the backend running?")
        }
        res.use {
            if (!it.isSuccessful) throw DrewyApiException(errorOf(it.body?.string().orEmpty()) ?: "Drewy's server said ${it.code}")
            val src = it.body?.source() ?: throw DrewyApiException("Empty reply")
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val o = DrewyJson.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject ?: continue
                when (prim(o, "type")) {
                    "ack" -> emit(BoardEvent.Ack(prim(o, "text").orEmpty(), resolve(prim(o, "speech"))))
                    "meta" -> emit(BoardEvent.Meta(prim(o, "title").orEmpty()))
                    "stepStart" -> emit(BoardEvent.StepStart(prim(o, "step")?.toIntOrNull() ?: 0, prim(o, "newPage") == "true", prim(o, "narration").orEmpty(), resolve(prim(o, "speech"))))
                    "element" -> emit(BoardEvent.ElementReady(prim(o, "step")?.toIntOrNull() ?: 0, prim(o, "index")?.toIntOrNull() ?: 0, DrewyJson.decodeFromJsonElement(Element.serializer(), o["element"]!!)))
                    "done" -> emit(BoardEvent.Done(DrewyJson.decodeFromJsonElement(Board.serializer(), o["board"]!!)))
                    "error" -> emit(BoardEvent.Failed(prim(o, "error") ?: "Something went wrong"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Find a backend that answers: the saved one, the build default (USB tunnel 127.0.0.1), or the laptop on Wi-Fi.
     * Remembers the first that works, so a dropped USB tunnel doesn't silently kill Drewy.
     */
    suspend fun ensureReachable() = withContext(Dispatchers.IO) {
        if (ping(baseUrl)) return@withContext
        val candidates = listOfNotNull(prefs().getString(KEY_URL, null), BuildConfig.BACKEND_URL, BuildConfig.BACKEND_LAN_URL.takeIf { it.isNotBlank() }).distinct()
        candidates.firstOrNull { it != baseUrl && ping(it) }?.let { prefs().edit().putString(KEY_URL, it).apply() }
    }

    private fun ping(url: String) = runCatching {
        quick.newCall(Request.Builder().url("$url/health").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private val quick by lazy { http.newBuilder().connectTimeout(1500, TimeUnit.MILLISECONDS).readTimeout(2, TimeUnit.SECONDS).build() }

    /** Streaming voice URL for any text (plays while it's still being generated). */
    fun speechUrl(text: String) = "$baseUrl/api/speak?text=" + java.net.URLEncoder.encode(text, "UTF-8")

    /** Drewy's voice (marin) as mp3 bytes. */
    suspend fun speak(text: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject { put("text", text) }.toString().toRequestBody("application/json".toMediaType())
            http.newCall(Request.Builder().url("$baseUrl/api/speak").post(body).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    /** Backend media paths ("/media/x.webp") → absolute URLs. */
    fun resolve(url: String?): String? = when {
        url == null -> null
        url.startsWith("/") -> baseUrl + url
        else -> url
    }

    private fun errorOf(text: String) = runCatching {
        (DrewyJson.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject)?.get("error")?.toString()?.trim('"')
    }.getOrNull()

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("$baseUrl/health").build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun post(path: String, prompt: String, mode: String, image: String?): Board = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("prompt", prompt)
            put("mode", mode)
            if (image != null) put("image", image)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(baseUrl + path).post(body).build()
        try {
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching {
                        DrewyJson.parseToJsonElement(text).let { (it as? kotlinx.serialization.json.JsonObject)?.get("error")?.toString()?.trim('"') }
                    }.getOrNull()
                    throw DrewyApiException(msg ?: "Drewy's server said ${res.code}")
                }
                DrewyJson.decodeFromString(Board.serializer(), text)
            }
        } catch (e: IOException) {
            throw DrewyApiException("Can't reach Drewy's server at $baseUrl — is the backend running?")
        }
    }

    companion object { private const val KEY_URL = "backend_url" }
}
