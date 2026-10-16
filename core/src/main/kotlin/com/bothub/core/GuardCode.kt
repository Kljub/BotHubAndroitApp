package com.bothub.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * BotHub Guard codes: TOTP (RFC 6238, SHA-1, 6 digits, 30 s) of the guard
 * secret BotHub gave the phone at pairing; the same as the gateway's
 * totpCode, so a code also works without network.
 */
object GuardCode {
    const val PERIOD = 30L

    fun code(secretBase32: String, unixSeconds: Long): String {
        val key = base32(secretBase32)
        val counter = unixSeconds / PERIOD
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val hash = Mac.getInstance("HmacSHA1").run { init(SecretKeySpec(key, "HmacSHA1")); doFinal(msg) }
        val o = hash.last().toInt() and 0x0f
        val bin = ((hash[o].toInt() and 0x7f) shl 24) or ((hash[o + 1].toInt() and 0xff) shl 16) or
            ((hash[o + 2].toInt() and 0xff) shl 8) or (hash[o + 3].toInt() and 0xff)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** Seconds until the next code. */
    fun secondsLeft(unixSeconds: Long): Long = PERIOD - unixSeconds % PERIOD

    private fun base32(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (c in s.uppercase().filter { it != '=' && !it.isWhitespace() }) {
            val v = alphabet.indexOf(c)
            require(v >= 0) { "not base32" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add((buffer shr bits).toByte())
            }
        }
        return out.toByteArray()
    }
}
