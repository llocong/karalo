package com.karalo.youtubeclient.internal

import com.karalo.youtubeclient.internal.potoken.WebViewPoTokenProvider
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.concurrent.atomic.AtomicBoolean

/** NewPipe.init() is process-global and must only run once. */
internal object NewPipeBootstrap {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(
        downloader: Downloader,
        poTokenProvider: WebViewPoTokenProvider,
    ) {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
            YoutubeStreamExtractor.setPoTokenProvider(poTokenProvider)
        }
    }
}
