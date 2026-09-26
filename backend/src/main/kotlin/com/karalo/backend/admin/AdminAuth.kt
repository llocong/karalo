package com.karalo.backend.admin

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The admin password, stored as `pbkdf2-sha256$<iterations>$<salt>$<hash>` (base64) in
 * KARALO_ADMIN_PASSWORD_HASH. `karalo-backend hash-admin-password` makes one (see
 * deploy/set-admin-password.sh), so the password itself is never written anywhere.
 */
object AdminPassword {
    private const val PREFIX = "pbkdf2-sha256"
    private const val DEFAULT_ITERATIONS = 600_000 // OWASP's current minimum for PBKDF2-HMAC-SHA256
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun hash(
        password: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val encoder = Base64.getEncoder()
        return "$PREFIX\$$iterations\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(derive(password, salt, iterations))}"
    }

    fun matches(
        password: String,
        encoded: String,
    ): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray =
        SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
}

sealed interface LoginResult {
    data class Success(
        val token: String,
    ) : LoginResult

    data object WrongPassword : LoginResult

    data class Locked(
        val retryAfter: Duration,
    ) : LoginResult
}

/**
 * Sign-in for the admin dashboard. After [MAX_FAILURES] wrong passwords from one IP within
 * [LOCKOUT], that IP is locked out for [LOCKOUT]; [MAX_GLOBAL_FAILURES] from all IPs together
 * lock everyone out, so guessing from many addresses doesn't help either.
 *
 * Signed-in sessions live in memory for [SESSION_LIFETIME]: a restart (every deploy) signs the
 * owner out, which is a fair price for not having a signing key to manage. Only a hash of each
 * session token is kept.
 */
class AdminAuth(
    private val passwordHash: String?,
    private val clock: () -> Instant = Instant::now,
) {
    val enabled: Boolean get() = passwordHash != null

    private val failuresByIp = ConcurrentHashMap<String, MutableList<Instant>>()
    private val allFailures = mutableListOf<Instant>()
    private val lockedUntilByIp = ConcurrentHashMap<String, Instant>()

    @Volatile private var globalLockedUntil: Instant? = null
    private val sessions = ConcurrentHashMap<String, Instant>()
    private val random = SecureRandom()

    @Synchronized
    fun login(
        ip: String,
        password: String,
    ): LoginResult {
        val now = clock()
        lockedFor(ip, now)?.let { return LoginResult.Locked(it) }
        val hash = passwordHash ?: return LoginResult.WrongPassword
        if (AdminPassword.matches(password, hash)) {
            failuresByIp.remove(ip)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
            sessions[tokenHash(token)] = now.plus(SESSION_LIFETIME)
            return LoginResult.Success(token)
        }
        val since = now.minus(LOCKOUT)
        val mine = failuresByIp.getOrPut(ip) { mutableListOf() }.apply { removeAll { it.isBefore(since) }; add(now) }
        allFailures.apply { removeAll { it.isBefore(since) }; add(now) }
        if (mine.size >= MAX_FAILURES) {
            lockedUntilByIp[ip] = now.plus(LOCKOUT)
            failuresByIp.remove(ip)
        }
        if (allFailures.size >= MAX_GLOBAL_FAILURES) {
            globalLockedUntil = now.plus(LOCKOUT)
            allFailures.clear()
        }
        return lockedFor(ip, now)?.let { LoginResult.Locked(it) } ?: LoginResult.WrongPassword
    }

    /** How much longer [ip] must wait before trying again, or null if it may try now. */
    fun lockedFor(
        ip: String,
        now: Instant = clock(),
    ): Duration? {
        val until = listOfNotNull(lockedUntilByIp[ip], globalLockedUntil).maxOrNull() ?: return null
        return if (until.isAfter(now)) Duration.between(now, until) else null
    }

    fun isSignedIn(token: String?): Boolean {
        if (token.isNullOrEmpty() || !enabled) return false
        val key = tokenHash(token)
        val expiry = sessions[key] ?: return false
        if (expiry.isBefore(clock())) {
            sessions.remove(key)
            return false
        }
        return true
    }

    fun logout(token: String?) {
        if (!token.isNullOrEmpty()) sessions.remove(tokenHash(token))
    }

    private fun tokenHash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_FAILURES = 5
        const val MAX_GLOBAL_FAILURES = 20
        val LOCKOUT: Duration = Duration.ofMinutes(15)
        val SESSION_LIFETIME: Duration = Duration.ofHours(12)
        private const val TOKEN_BYTES = 32
    }
}
