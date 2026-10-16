package com.bothub.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The app against a real BotHub (Docker): BOTHUB_E2E_URL is the dashboard
 * address, BOTHUB_E2E_USER / BOTHUB_E2E_PASSWORD a user without 2FA. Plays
 * the browser (sign-in, QR code, Guard switch) and the phone (pairing,
 * signed calls, approving a sign-in).
 */
class EndToEndTest {
    private val url = System.getenv("BOTHUB_E2E_URL").orEmpty()
    private val user = System.getenv("BOTHUB_E2E_USER") ?: "admin"
    private val password = System.getenv("BOTHUB_E2E_PASSWORD").orEmpty()

    /** A browser: cookie session + CSRF token. */
    private inner class Browser {
        private val jar = object : CookieJar {
            val cookies = mutableListOf<Cookie>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies.removeAll { c -> cookies.any { it.name == c.name } }; this.cookies += cookies }
            override fun loadForRequest(url: HttpUrl) = cookies
        }
        val http = OkHttpClient.Builder().cookieJar(jar).build()
        var csrf = ""

        fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
            val b = Request.Builder().url(url + path).header("X-CSRF-Token", csrf)
            b.method(method, body?.toRequestBody("application/json".toMediaType()))
            return http.newCall(b.build()).execute().use { it.code to (it.body?.string() ?: "") }
        }

        fun login(): Pair<Int, String> {
            val r = call("POST", "/api/v1/auth/login", """{"username":"$user","password":"$password"}""")
            if (r.first == 200) csrf = Json.parseToJsonElement(call("GET", "/api/v1/auth/me").second).jsonObject["csrfToken"]!!.jsonPrimitive.content
            return r
        }
    }

    @Test
    fun `pair, control bots and approve a sign-in with BotHub Guard`() = runTest {
        assumeTrue("BOTHUB_E2E_URL not set", url.isNotEmpty())
        val browser = Browser()
        assertEquals(200, browser.login().first)

        // Dashboard: QR code → the phone pairs.
        val qr = Json.parseToJsonElement(browser.call("POST", "/api/v1/account/devices/pairing", "{}").second).jsonObject
        val code = qr["code"]!!.jsonPrimitive.content
        assertEquals(user, qr["user"]!!.jsonPrimitive.content)
        val link = PairLink.parse("bothub://pair?server=" + java.net.URLEncoder.encode(url, "UTF-8") + "&user=$user&code=$code")!!
        val phone = BotHubClient(link.server, JvmSigner())
        val pairing = phone.pair(link.code, "E2E Phone", "0.0.0-test", link.user)
        assertEquals(user, pairing.user.username)
        assertEquals("E2E Phone", phone.me().device.name)
        phone.bots() // the user's bots (may be none)
        phone.overview()
        assertTrue(phone.texts("de").containsKey("error.mobile.unpaired"))
        // The module groups of the dashboard, and admin stays web-only.
        val catalog = phone.catalog()
        assertTrue(catalog.groups.any { g -> g.key == "security" && g.color == "#f87171" && g.modules.any { it.key == "anticontrol" } })
        assertTrue("modules list their commands", catalog.groups.flatMap { it.modules }.any { it.commands.isNotEmpty() })
        phone.bots().firstOrNull()?.let { bot ->
            // Switch a module command copy off and on again (like the dashboard).
            phone.commandCopies(bot.id).values.firstOrNull()?.let { c ->
                phone.setCommand(bot.id, c.id, !c.enabled)
                assertEquals(!c.enabled, phone.commandCopies(bot.id)[c.preset]!!.enabled)
                phone.setCommand(bot.id, c.id, c.enabled)
            }
        }

        // Guard on (Security tab), then a new sign-in waits for the phone.
        assertEquals(200, browser.call("PATCH", "/api/v1/account/devices/${pairing.device.id}", """{"guard":true}""").first)
        val second = Browser()
        val (status, body) = second.login()
        assertEquals(401, status)
        val params = Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["params"]!!.jsonObject
        val ticket = params["ticket"]!!.jsonPrimitive.content
        assertEquals(202, second.call("POST", "/api/v1/auth/login/guard", """{"ticket":"$ticket"}""").first)
        val pending = phone.approvals()
        assertEquals(1, pending.size)
        phone.decide(pending.single().id, approve = true)
        assertEquals(200, second.call("POST", "/api/v1/auth/login/guard", """{"ticket":"$ticket"}""").first)

        // The Guard code works too (third sign-in).
        val third = Browser()
        val t3 = Json.parseToJsonElement(third.login().second).jsonObject["error"]!!.jsonObject["params"]!!.jsonObject["ticket"]!!.jsonPrimitive.content
        val guardCode = GuardCode.code(pairing.guardSecret, System.currentTimeMillis() / 1000)
        assertEquals(200, third.call("POST", "/api/v1/auth/login/totp", """{"ticket":"$t3","code":"$guardCode"}""").first)

        // Unpair: the phone loses access; Guard is gone with it.
        phone.unpair()
        val after = runCatching { phone.me() }.exceptionOrNull() as ApiException
        assertTrue(after.unpaired)
    }
}
