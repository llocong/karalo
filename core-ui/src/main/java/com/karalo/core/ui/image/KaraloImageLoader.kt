package com.karalo.core.ui.image

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.Collections

// Tuned for the ~2GB-RAM reference Android TV device this app was profiled on this session --
// generous enough to hold several rows' worth of decoded thumbnails without competing too hard
// with the rest of a low-RAM device's memory budget.
private const val MEMORY_CACHE_SIZE_PERCENT = 0.15

private const val PERF_LOG_TAG = "TvCarouselPerf"

/**
 * The single [ImageLoader] shared by every `AsyncImage` in the app (via [coil.ImageLoaderFactory],
 * see `KaraloApplication`) and by [com.karalo.core.ui.components.TvCarousel]'s adjacent-item
 * prefetching -- sharing one instance means prefetched thumbnails and the real, on-screen
 * `AsyncImage` requests hit the same memory cache.
 */
fun buildKaraloImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .memoryCache {
            MemoryCache
                .Builder(context)
                .maxSizePercent(MEMORY_CACHE_SIZE_PERCENT)
                .build()
        }
        // Coil's own default -- kept explicit as documentation: nothing in this app needs pixel
        // access (e.g. palette extraction), so there's no reason to force the slower ARGB_8888 path.
        .allowHardware(true)
        .eventListener(karaloImageEventListener(context))
        .build()

/**
 * Logs each image request's load time to Logcat under [PERF_LOG_TAG] -- purely a debug-build
 * diagnostic (see the demo screen's own on-screen use of the same tag), a no-op in release builds.
 * Gated on the runtime `FLAG_DEBUGGABLE` bit rather than a Gradle `BuildConfig.DEBUG` field, since
 * neither `app` nor `core-ui` enables `buildFeatures.buildConfig` today and this doesn't need it.
 */
private fun karaloImageEventListener(context: Context): EventListener {
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isDebuggable) return EventListener.NONE

    val startTimesMs = Collections.synchronizedMap(mutableMapOf<ImageRequest, Long>())
    return object : EventListener {
        override fun onStart(request: ImageRequest) {
            startTimesMs[request] = SystemClock.elapsedRealtime()
        }

        override fun onSuccess(
            request: ImageRequest,
            result: SuccessResult,
        ) {
            val startedAt = startTimesMs.remove(request) ?: return
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            Log.d(PERF_LOG_TAG, "loaded in ${elapsedMs}ms: ${request.data}")
        }

        override fun onError(
            request: ImageRequest,
            result: ErrorResult,
        ) {
            startTimesMs.remove(request)
            Log.d(PERF_LOG_TAG, "load failed: ${request.data}", result.throwable)
        }
    }
}
