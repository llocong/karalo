package com.karalo.feature.search.voice

import android.content.Intent

/**
 * Bridges to the system's own speech-recognition activity (launched via
 * [RecognizerIntent][android.speech.RecognizerIntent]) rather than driving
 * [android.speech.SpeechRecognizer] in-process -- no RECORD_AUDIO permission needed here, and no
 * recognizer instance in this app's process to leak.
 */
interface VoiceSearchManager {
    /** Whether a system activity can handle [createRecognizerIntent]. */
    fun isAvailable(): Boolean

    /** The intent to launch via an Activity Result API to start listening. */
    fun createRecognizerIntent(): Intent

    /** Interprets the [android.app.Activity.RESULT_OK]/data pair returned by that activity. */
    fun parseResult(
        resultCode: Int,
        data: Intent?,
    ): VoiceSearchResult
}
