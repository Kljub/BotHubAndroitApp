package com.bothub.core

import java.net.URI
import java.net.URLDecoder

/** The QR code of BotHub (Settings → Mobile App): bothub://pair?server=<dashboard>&code=<one-time code>. */
data class PairLink(val server: String, val code: String) {
    companion object {
        private val codeRe = Regex("^[0-9a-f]{48}$")

        /** null when the text is no BotHub pairing link. */
        fun parse(text: String): PairLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "bothub" || uri.host != "pair") return null
            val params = (uri.rawQuery ?: "").split('&').mapNotNull {
                val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                k to URLDecoder.decode(v, "UTF-8")
            }.toMap()
            val server = params["server"]?.trimEnd('/') ?: return null
            val code = params["code"] ?: return null
            val s = runCatching { URI(server) }.getOrNull() ?: return null
            if ((s.scheme != "https" && s.scheme != "http") || s.host.isNullOrBlank() || !codeRe.matches(code)) return null
            return PairLink(server, code)
        }
    }

    /** Plain http only for local addresses (a home server in the LAN). */
    val insecure: Boolean get() = server.startsWith("http://")
}
