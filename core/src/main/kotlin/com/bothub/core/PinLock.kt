package com.bothub.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock: a 4-digit PIN (optionally with biometrics in the app). The PIN is
 * kept only as PBKDF2 hash with its own salt. Wrong PINs cost time: after 5
 * wrong ones the lock waits 30 s, then twice as long each time (max. 1 h);
 * the count ends with the right PIN.
 *
 * [store] keeps the values (the app encrypts them with the Android Keystore);
 * [clock] is in milliseconds.
 */
class PinLock(private val store: Store, private val clock: () -> Long = System::currentTimeMillis) {
    interface Store {
        fun get(name: String): String?
        fun put(name: String, value: String?)
    }

    companion object {
        const val FREE_TRIES = 5
        private const val ROUNDS = 120_000
        private val pinRe = Regex("^\\d{4}$")
        fun validPin(pin: String) = pinRe.matches(pin)
    }

    /** When the app locks after it went to the background. */
    enum class Timeout(val ms: Long) { IMMEDIATELY(0), MINUTE(60_000), FIVE_MINUTES(300_000) }

    val enabled: Boolean get() = store.get("lock.hash") != null
    var biometric: Boolean
        get() = enabled && store.get("lock.bio") == "1"
        set(v) = store.put("lock.bio", if (v) "1" else null)
    var timeout: Timeout
        get() = runCatching { Timeout.valueOf(store.get("lock.timeout") ?: "") }.getOrDefault(Timeout.IMMEDIATELY)
        set(v) = store.put("lock.timeout", v.name)

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ROUNDS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(key)
    }

    fun setPin(pin: String) {
        require(validPin(pin)) { "4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        store.put("lock.salt", Base64.getEncoder().encodeToString(salt))
        store.put("lock.hash", hash(pin, salt))
        store.put("lock.fails", null)
        store.put("lock.until", null)
    }

    fun disable() {
        listOf("lock.hash", "lock.salt", "lock.bio", "lock.fails", "lock.until").forEach { store.put(it, null) }
    }

    /** Milliseconds until the next try is allowed (0: now). */
    fun waitMs(): Long = maxOf(0, (store.get("lock.until")?.toLongOrNull() ?: 0) - clock())

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Wait(val ms: Long) : Result
    }

    fun check(pin: String): Result {
        if (!enabled) return Result.Ok
        waitMs().takeIf { it > 0 }?.let { return Result.Wait(it) }
        val salt = Base64.getDecoder().decode(store.get("lock.salt") ?: "")
        if (validPin(pin) && java.security.MessageDigest.isEqual(hash(pin, salt).toByteArray(), (store.get("lock.hash") ?: "").toByteArray())) {
            store.put("lock.fails", null)
            store.put("lock.until", null)
            return Result.Ok
        }
        val fails = (store.get("lock.fails")?.toIntOrNull() ?: 0) + 1
        store.put("lock.fails", fails.toString())
        if (fails < FREE_TRIES) return Result.Wrong(FREE_TRIES - fails)
        val wait = minOf(3_600_000L, 30_000L shl minOf(fails - FREE_TRIES, 7))
        store.put("lock.until", (clock() + wait).toString())
        return Result.Wait(wait)
    }

    /** A successful biometric unlock also ends the wrong-PIN count. */
    fun biometricOk() {
        store.put("lock.fails", null)
        store.put("lock.until", null)
    }

    /** Lock again after the app was [backgroundMs] in the background? */
    fun shouldLock(backgroundMs: Long): Boolean = enabled && backgroundMs >= timeout.ms
}
