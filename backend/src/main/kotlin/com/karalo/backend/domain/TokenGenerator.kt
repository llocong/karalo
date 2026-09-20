package com.karalo.backend.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * TV secrets and participant tokens: 256-bit SecureRandom values, base64url-no-padding encoded.
 * Only the SHA-256 hash is ever persisted (see [hash]) — the raw value is returned to the client
 * exactly once, at issuance, and never stored or logged in plaintext.
 */
object TokenGenerator {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Constant-time comparison against a stored hash — avoids timing side-channels. */
    fun matches(
        presentedToken: String,
        storedHash: String,
    ): Boolean = MessageDigest.isEqual(hash(presentedToken).toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))
}
