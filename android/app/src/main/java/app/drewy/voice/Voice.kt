package app.drewy.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Push-to-talk speech recognition with live partial text + mic level for the waveform. */
class Listener(private val context: Context) {
    var listening by mutableStateOf(false); private set
    var partial by mutableStateOf(""); private set
    var level by mutableFloatStateOf(0f); private set

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (String) -> Unit, onError: (String) -> Unit = {}) {
        if (!available) { onError("Speech recognition isn't available on this phone."); return }
        stop()
        this.onResult = onResult; this.onError = onError
        partial = ""; listening = true
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { partial }
                finish()
                if (text.isNotBlank()) this@Listener.onResult?.invoke(text) else this@Listener.onError?.invoke("I didn't catch that — try again?")
            }
            override fun onError(error: Int) {
                val text = partial
                finish()
                if (text.isNotBlank()) this@Listener.onResult?.invoke(text)
                else this@Listener.onError?.invoke(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that — try again?"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Drewy needs the microphone permission."
                        else -> "Listening stopped (error $error)."
                    },
                )
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        })
    }

    /** Stop listening and deliver whatever was heard. */
    fun release() { recognizer?.stopListening() }

    fun stop() { recognizer?.cancel(); finish() }

    private fun finish() {
        listening = false; level = 0f
        recognizer?.destroy(); recognizer = null
    }
}

/**
 * Drewy's voice: OpenAI "marin" via the backend (prefetched while he draws), played with MediaPlayer.
 * Falls back to the phone's text-to-speech if the backend voice isn't reachable.
 * `speak` suspends until the sentence has been said.
 */
class Speaker(context: Context, private val api: app.drewy.data.DrewyApi) {
    private val appContext = context.applicationContext
    private var player: android.media.MediaPlayer? = null

    var muted by mutableStateOf(false)
    var speaking by mutableStateOf(false); private set

    // ---- fallback: system TTS ----
    private var ttsReady = false
    private val waiters = HashMap<String, () -> Unit>()
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) { tts.language = Locale.getDefault(); tts.setPitch(1.12f); tts.setSpeechRate(1.02f) }
    }
    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { done(utteranceId) }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { done(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { done(utteranceId) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { done(utteranceId) }
        })
    }
    private fun done(id: String?) { speaking = false; synchronized(waiters) { id?.let { waiters.remove(it) } }?.invoke() }

    /** Kept for compatibility: the backend now starts generating voice as soon as Sol writes a line. */
    fun prefetch(@Suppress("UNUSED_PARAMETER") text: String) = Unit

    /**
     * Say a line in Drewy's marin voice. [url] is a streaming /api/speak URL (from the event stream) —
     * playback starts while the audio is still being generated. Falls back to the phone's TTS.
     */
    suspend fun speak(text: String, slow: Boolean = false, url: String? = null) {
        if (muted || text.isBlank()) return
        val ok = playStream(url ?: api.speechUrl(text), slow, text.length)
        if (!ok) speakSystem(text, slow)
    }

    private suspend fun playStream(url: String, slow: Boolean, chars: Int): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        stopPlayer()
        val mp = android.media.MediaPlayer()
        player = mp
        var started = false
        val finished = withTimeoutOrNull(9000L + chars * 120L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                runCatching {
                    mp.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    mp.setDataSource(url)
                    mp.setOnPreparedListener {
                        if (slow) runCatching { it.playbackParams = it.playbackParams.setSpeed(0.86f) }
                        started = true; speaking = true; it.start()
                    }
                    mp.setOnCompletionListener { if (cont.isActive) cont.resume(true) }
                    mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(started); true }
                    mp.prepareAsync()
                }.onFailure { if (cont.isActive) cont.resume(false) }
                cont.invokeOnCancellation { runCatching { mp.stop() } }
            }
        }
        speaking = false
        if (player === mp) { runCatching { mp.release() }; player = null }
        finished == true || started
    }

    private suspend fun speakSystem(text: String, slow: Boolean) {
        if (!ttsReady) return
        tts.setSpeechRate(if (slow) 0.82f else 1.02f)
        val id = UUID.randomUUID().toString()
        withTimeoutOrNull(2500L + text.length * 90L) {
            suspendCancellableCoroutine { cont ->
                synchronized(waiters) { waiters[id] = { if (cont.isActive) cont.resume(Unit) } }
                cont.invokeOnCancellation { tts.stop() }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            }
        } ?: run { synchronized(waiters) { waiters.remove(id) }; speaking = false }
    }

    private fun stopPlayer() { player?.let { runCatching { it.stop(); it.release() } }; player = null }

    fun stop() { stopPlayer(); tts.stop(); speaking = false }
    fun shutdown() { stop(); tts.shutdown() }
}
