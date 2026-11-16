package app.drewy

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.drewy.data.CanvasStore
import app.drewy.data.DrewyApi
import app.drewy.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DrewyApp : Application(), coil.ImageLoaderFactory {
    lateinit var api: DrewyApi; private set
    lateinit var store: CanvasStore; private set
    lateinit var speaker: Speaker; private set
    /** Outlives screens — used for saves so nothing is lost when you navigate away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        api = DrewyApi(this)
        store = CanvasStore(this)
        speaker = Speaker(this, api)
    }

    /** Wikimedia rejects generic user agents, so every image request identifies the app. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .okHttpClient {
            okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "DrewyApp/0.1 (Android drawing assistant)").build()) }
                .build()
        }
        .crossfade(350)
        .build()
}

/** A photo taken on the Snap screen, waiting to be answered on a canvas. */
data class PendingSnap(val path: String, val mode: String)

/** Cross-screen handoff (Snap -> Canvas). */
object Handoff {
    var pendingSnap by mutableStateOf<PendingSnap?>(null)
    /** A question asked from another screen (e.g. the 3D viewer) for the open canvas. */
    var pendingAsk by mutableStateOf<String?>(null)
}
