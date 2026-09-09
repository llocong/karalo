package com.karalo.youtubeclient.internal

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.atomic.AtomicBoolean

/** NewPipe.init() is process-global and must only run once. */
internal object NewPipeBootstrap {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(downloader: Downloader) {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
        }
    }
}
