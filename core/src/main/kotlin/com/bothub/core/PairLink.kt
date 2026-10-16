package com.bothub.core

import java.net.URI
import java.net.URLDecoder

/**
 * The QR code of BotHub (Settings → Mobile App):
 * bothub://pair?server=<dashboard>&user=<user name>&code=<validation key>.
 * The key is new every 30 seconds on the dashboard; scan the current one.
 */
data class PairLink(val server: String, val code: String, val user: String = "") {
    companion object {
        private val codeRe = Regex("^[0-9a-f]{48}$")

        /**
         * null when the text is no BotHub pairing link. An http address of a
         * public host becomes https: BotHub on the internet runs behind TLS,
         * and an http request would be redirected and lose its body.
         */
        fun parse(text: String): PairLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "bothub" || uri.host != "pair") return null
            val params = (uri.rawQuery ?: "").split('&').mapNotNull {
                val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                k to URLDecoder.decode(v, "UTF-8")
            }.toMap()
            var server = params["server"]?.trimEnd('/') ?: return null
            val code = params["code"] ?: return null
            val s = runCatching { URI(server) }.getOrNull() ?: return null
            if ((s.scheme != "https" && s.scheme != "http") || s.host.isNullOrBlank() || !codeRe.matches(code)) return null
            if (s.scheme == "http" && !isLocal(s.host)) server = "https://" + server.removePrefix("http://")
            return PairLink(server, code, params["user"]?.take(64).orEmpty())
        }

        /** A home-network address: localhost, a name without dot or ending in .local/.lan/.home, a private IP. */
        fun isLocal(host: String): Boolean {
            val h = host.lowercase().trim('[', ']')
            if (h == "localhost" || !h.contains('.') && !h.contains(':')) return true
            if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") || h.endsWith(".internal") || h.endsWith(".localhost")) return true
            val ip = h.split('.').mapNotNull { it.toIntOrNull() }
            if (ip.size == 4) {
                return ip[0] == 10 || ip[0] == 127 || (ip[0] == 192 && ip[1] == 168) || (ip[0] == 172 && ip[1] in 16..31) || (ip[0] == 100 && ip[1] in 64..127)
            }
            return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80") || h == "::1"
        }
    }

    /** Plain http only for local addresses (a home server in the LAN). */
    val insecure: Boolean get() = server.startsWith("http://")
}
