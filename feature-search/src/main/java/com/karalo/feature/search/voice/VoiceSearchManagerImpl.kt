package com.karalo.feature.search.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val VOICE_SEARCH_PROMPT = "Speak to search"

// On Google TV devices with both this and com.google.android.katniss (the Assistant-style
// "Google" option) installed, an implicit intent triggers a disambiguation dialog instead of
// listening immediately. "Speech Recognition & Synthesis from Google" (this package) gives
// noticeably better results for search queries, so it's preferred explicitly here -- with a
// fallback to an unrestricted intent for any device where it isn't present, per-device
// resolution never being hardcoded to a specific activity/class name.
private const val PREFERRED_RECOGNIZER_PACKAGE = "com.google.android.tts"

class VoiceSearchManagerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : VoiceSearchManager {
        override fun isAvailable(): Boolean = resolveRecognizerIntent() != null

        override fun createRecognizerIntent(): Intent = resolveRecognizerIntent() ?: baseRecognizerIntent()

        private fun resolveRecognizerIntent(): Intent? {
            val preferred = baseRecognizerIntent().setPackage(PREFERRED_RECOGNIZER_PACKAGE)
            if (context.packageManager.resolveActivity(preferred, 0) != null) return preferred
            val unrestricted = baseRecognizerIntent()
            return unrestricted.takeIf { context.packageManager.resolveActivity(it, 0) != null }
        }

        private fun baseRecognizerIntent(): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, VOICE_SEARCH_PROMPT)
            }

        override fun parseResult(
            resultCode: Int,
            data: Intent?,
        ): VoiceSearchResult {
            if (resultCode != Activity.RESULT_OK) return VoiceSearchResult.Cancelled
            val text =
                data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull { it.isNotBlank() }
            return text?.let { VoiceSearchResult.Success(it) } ?: VoiceSearchResult.NoMatch
        }
    }
