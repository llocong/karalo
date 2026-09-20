package com.karalo.backend.domain

import java.security.SecureRandom

/**
 * Public, human-typeable room codes — NOT a secret (nothing secret ever goes in the QR, per this
 * feature's security requirements). The real defense against guessing/enumeration is rate
 * limiting on lookup/join (see [com.karalo.backend.plugins.installRateLimiting]), not code length.
 * Alphabet deliberately excludes visually-ambiguous characters (0/O, 1/I/L) since this is meant to
 * be readable off a phone screen or, in principle, typed by hand.
 */
object SessionCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 8
    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Normalizes user/QR input for lookup: strips a display hyphen, uppercases. */
    fun normalize(rawCode: String): String = rawCode.replace("-", "").trim().uppercase()
}
