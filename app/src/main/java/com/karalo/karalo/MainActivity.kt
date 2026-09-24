package com.karalo.karalo

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.karalo.core.common.logging.Logger
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.common.session.SearchSessionHolder
import com.karalo.core.karaoke.domain.KaraokeRepository
import com.karalo.core.karaoke.domain.KaraokeSessionHolder
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.core.ui.theme.LocalKaraloTokens
import com.karalo.karalo.nav.KaraloNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private val MEDIA_KEY_CODES =
    setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    )

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var mediaKeyRouter: MediaKeyRouter

    @Inject
    lateinit var karaokeRepository: KaraokeRepository

    @Inject
    lateinit var karaokeSessionHolder: KaraokeSessionHolder

    @Inject
    lateinit var searchSessionHolder: SearchSessionHolder

    @Inject
    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The TV's one persistent karaoke session is ensured/restored on every launch, unconditionally
        // -- never gated on the user ever opening a drawer or player -- so the QR is available from
        // the very first frame the drawer could show it, per this feature's "session is independent
        // of the current page" requirement. A failure here (backend unreachable) is non-fatal: the
        // rest of the app -- homepage, search, local playback -- works exactly as it always has,
        // simply without a QR to show until a later retry succeeds (see KaraokeRepositoryImpl's own
        // reconnect loop, which keeps trying in the background regardless).
        lifecycleScope.launch {
            karaokeRepository.ensureSession().let { result ->
                if (result is com.karalo.core.common.result.AppResult.Failure) {
                    logger.log("Karaoke session ensure failed at launch: ${result.error}")
                }
            }
        }

        setContent {
            val sessionJoinUrl by karaokeRepository.sessionJoinUrl.collectAsState()
            val seasonalTheme by karaokeRepository.seasonalTheme.collectAsState()
            KaraloTheme(seasonalTheme = seasonalTheme) {
                KaraloNavHost(
                    karaokeSessionHolder = karaokeSessionHolder,
                    searchSessionHolder = searchSessionHolder,
                    sessionJoinUrl = sessionJoinUrl,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(LocalKaraloTokens.current.pageBackground),
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in MEDIA_KEY_CODES) {
            if (mediaKeyRouter.dispatch(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}
