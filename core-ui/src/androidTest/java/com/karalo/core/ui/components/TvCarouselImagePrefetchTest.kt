package com.karalo.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import coil.Coil
import coil.ImageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.request.ImageResult
import com.karalo.core.ui.theme.KaraloTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val ITEM_COUNT = 20
private const val AHEAD_COUNT = 3
private val ITEM_WIDTH = 200.dp

private object FakeDisposable : Disposable {
    override val job: Deferred<ImageResult> = CompletableDeferred()
    override val isDisposed: Boolean = true

    override fun dispose() = Unit
}

/** Records every URL [enqueue] is asked to load, without performing a real fetch/decode. */
private class RecordingImageLoader(
    real: ImageLoader,
) : ImageLoader by real {
    val enqueuedUrls = mutableListOf<Any?>()

    override fun enqueue(request: ImageRequest): Disposable {
        enqueuedUrls += request.data
        return FakeDisposable
    }
}

/**
 * Verifies [TvCarousel]'s adjacent-item image-prefetch mechanism is bounded rather than
 * unconditionally warming every item's thumbnail up front -- kept narrow and mockable (a
 * recording fake [ImageLoader], swapped in via Coil's own singleton-override API) rather than
 * asserting real network/cache state, per this repo's usual testing conventions.
 */
class TvCarouselImagePrefetchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var recordingImageLoader: RecordingImageLoader

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        recordingImageLoader = RecordingImageLoader(ImageLoader.Builder(context).build())
        Coil.setImageLoader(recordingImageLoader)
    }

    @After
    fun tearDown() {
        Coil.reset()
    }

    @Test
    fun prefetchIsBoundedToTheConfiguredAheadCount() {
        composeRule.setContent {
            KaraloTheme {
                TvCarousel(
                    items = (0 until ITEM_COUNT).map { "Item $it" },
                    key = { it },
                    imagePrefetch =
                        TvCarouselImagePrefetch(
                            thumbnailUrl = { "https://example.test/$it" },
                            sizePx = IntSize(ITEM_WIDTH.value.toInt(), ITEM_WIDTH.value.toInt()),
                            aheadCount = AHEAD_COUNT,
                        ),
                ) { _, item, itemModifier ->
                    Text(
                        text = item,
                        modifier = Modifier.width(ITEM_WIDTH).then(itemModifier).clickable {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Item 0").requestFocus()
        composeRule.waitForIdle()

        assertFalse(
            "the already-visible first item should never itself be a prefetch target",
            recordingImageLoader.enqueuedUrls.contains("https://example.test/0"),
        )
        assertFalse(
            "an item far beyond aheadCount should never be prefetched",
            recordingImageLoader.enqueuedUrls.contains("https://example.test/${ITEM_COUNT - 1}"),
        )
        assertTrue(
            "prefetch should never enqueue more than aheadCount items at once",
            recordingImageLoader.enqueuedUrls.size <= AHEAD_COUNT,
        )
    }
}
