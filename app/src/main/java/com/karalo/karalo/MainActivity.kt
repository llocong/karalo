package com.karalo.karalo

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import com.karalo.core.common.mediakeys.MediaKeyRouter
import com.karalo.core.ui.theme.KaraloTheme
import com.karalo.karalo.nav.KaraloNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private val MEDIA_KEY_CODES = setOf(
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaraloTheme {
                KaraloNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
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
