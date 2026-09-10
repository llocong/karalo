// Adapted from TeamNewPipe/NewPipe (GPL-3.0), app/src/main/java/org/schabi/newpipe/util/potoken/JavaScriptUtil.kt
//
// Deviates from upstream in two ways to avoid new dependencies of unclear license/necessity:
// - org.json (built into Android) instead of com.grack:nanojson for JSON parsing/writing.
// - android.util.Base64 instead of okio.ByteString for base64 (en/de)coding.
package com.karalo.youtubeclient.internal.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the raw challenge data obtained from the Create endpoint and returns a JSON object
 * (valid as a JS object literal) that can be embedded in a JavaScript snippet.
 */
internal fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val challengeData =
        if (scrambled.length() > 1 && scrambled.opt(1) is String) {
            JSONArray(descramble(scrambled.getString(1)))
        } else {
            scrambled.getJSONArray(0)
        }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.getString(7)

    val safeScriptValue = challengeData.optJSONArray(1).firstStringOrNull()
    val trustedResourceUrlValue = challengeData.optJSONArray(2).firstStringOrNull()

    val interpreterJavascript =
        JSONObject()
            .put("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScriptValue ?: JSONObject.NULL)
            .put(
                "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                trustedResourceUrlValue ?: JSONObject.NULL,
            )

    return JSONObject()
        .put("messageId", messageId)
        .put("interpreterJavascript", interpreterJavascript)
        .put("interpreterHash", interpreterHash)
        .put("program", program)
        .put("globalName", globalName)
        .put("clientExperimentsStateBlob", clientExperimentsStateBlob)
        .toString()
}

/**
 * Parses the raw integrity token data obtained from the GenerateIT endpoint to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code, and a [Long] representing the
 * duration of this token in seconds.
 */
internal fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JSONArray(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

/**
 * Converts a string (usually the identifier used as input to `obtainPoToken`) to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code.
 */
internal fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

/**
 * Takes a poToken encoded as a sequence of bytes represented as integers separated by commas
 * (e.g. "97,98,99" would be "abc"), which is the output of `Uint8Array::toString()` in JavaScript,
 * and converts it to the specific base64 representation for poTokens.
 */
internal fun u8ToBase64(poToken: String): String {
    val bytes =
        poToken
            .split(",")
            .map { it.toUByte().toByte() }
            .toByteArray()
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
}

/**
 * Takes the scrambled challenge, decodes it from base64, adds 97 to each byte.
 */
private fun descramble(scrambledChallenge: String): String =
    base64ToByteArray(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube, and
 * returns a JavaScript `Uint8Array` that can be embedded directly in JavaScript code.
 */
private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteArray(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube
 * (URL-safe alphabet, with `.` used instead of `=` for padding).
 */
private fun base64ToByteArray(base64: String): ByteArray {
    val base64Mod =
        base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
    return try {
        Base64.decode(base64Mod, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        throw PoTokenException("Cannot base64 decode")
    }
}

private fun JSONArray?.firstStringOrNull(): String? {
    if (this == null) return null
    for (i in 0 until length()) {
        val value = opt(i)
        if (value is String) return value
    }
    return null
}
