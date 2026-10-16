package com.bothub.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class CoreTest {
    /** Verifies a raw r||s signature like the gateway (Go ecdsa.Verify). */
    private fun verifyRaw(spki: ByteArray, data: ByteArray, raw: ByteArray): Boolean {
        fun der(x: ByteArray): ByteArray {
            val v = BigInteger(1, x).toByteArray()
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val body = der(raw.copyOfRange(0, 32)) + der(raw.copyOfRange(32, 64))
        val sig = byteArrayOf(0x30, body.size.toByte()) + body
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(data); verify(sig) }
    }

    @Test
    fun `signatures convert from DER to raw and verify`() {
        val signer = JvmSigner()
        repeat(20) {
            val data = Signing.payload(1_700_000_000L + it, "get", "/api/v1/bots?x=1")
            val raw = Signing.derToRaw(signer.signDer(data))
            assertEquals(64, raw.size)
            assertTrue(verifyRaw(signer.publicKeySpki, data, raw))
        }
        assertEquals("bothub-mobile:5:POST:/a", String(Signing.payload(5, "post", "/a")))
    }

    @Test
    fun `pairing links`() {
        val code = "a".repeat(48)
        assertEquals(PairLink("https://bothub.example", code), PairLink.parse("bothub://pair?server=https%3A%2F%2Fbothub.example%2F&code=$code"))
        assertTrue(PairLink.parse("bothub://pair?server=http%3A%2F%2F192.168.1.5%3A8080&code=$code")!!.insecure)
        assertNull(PairLink.parse("https://evil.example/pair?code=$code"))
        assertNull(PairLink.parse("bothub://pair?server=ftp%3A%2F%2Fx&code=$code"))
        assertNull(PairLink.parse("bothub://pair?server=https%3A%2F%2Fx&code=short"))
    }

    @Test
    fun `guard codes are RFC 6238 TOTP`() {
        // RFC 6238 SHA-1 secret "12345678901234567890"; T = 59 → 94287082 → last 6 digits.
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", GuardCode.code(secret, 59))
        assertEquals("081804", GuardCode.code(secret, 1111111109))
        assertEquals(1L, GuardCode.secondsLeft(59))
    }

    @Test
    fun `versions and releases`() = runTest {
        assertTrue(Updates.newer("1.10.0", "1.9.2"))
        assertTrue(Updates.newer("v2.0", "1.99.99"))
        assertFalse(Updates.newer("1.0.0", "1.0.0"))
        assertFalse(Updates.newer("1.0.0-beta", "1.0.0"))
        MockWebServer().use { web ->
            web.enqueue(MockResponse().setBody("""{"tag_name":"v1.2.0","draft":false,"prerelease":false,"html_url":"h","assets":[{"name":"BotHub-1.2.0.apk","size":5,"browser_download_url":"https://x/a.apk"}]}"""))
            web.enqueue(MockResponse().setBody("""{"tag_name":"v1.2.0","assets":[]}"""))
            web.enqueue(MockResponse().setBody("""{"tag_name":"v1.0.0","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
            val url = web.url("/latest").toString()
            assertEquals("https://x/a.apk", Updates.check("1.1.0", url = url)!!.apk!!.url)
            assertNull(Updates.check("1.1.0", url = url), "no APK asset")
            assertNull(Updates.check("1.1.0", url = url), "older release")
        }
    }

    private fun assertNull(v: Any?, msg: String) = org.junit.Assert.assertNull(msg, v)

    @Test
    fun `client signs requests and maps errors`() = runTest {
        MockWebServer().use { web ->
            val signer = JvmSigner()
            val client = BotHubClient(web.url("/").toString().trimEnd('/'), signer, clock = { 1_800_000_000L })
            web.enqueue(MockResponse().setResponseCode(201).setBody("""{"token":"bhm_x","guardSecret":"GEZDGNBV","device":{"id":"d1","name":"Pixel"},"user":{"id":2,"username":"ann"}}"""))
            val p = client.pair("c".repeat(48), "Pixel", "1.0.0")
            assertEquals("bhm_x", client.token)
            assertEquals("ann", p.user.username)
            val pairReq = web.takeRequest()
            assertNull(pairReq.getHeader("Authorization"))
            assertTrue(pairReq.body.readUtf8().contains(Base64.getUrlEncoder().withoutPadding().encodeToString(signer.publicKeySpki)))

            web.enqueue(MockResponse().setBody("""{"items":[{"id":7,"name":"Njetflix","status":"running","extra":1}]}"""))
            val bots = client.bots()
            assertTrue(bots.single().running)
            val req = web.takeRequest()
            assertEquals("Bearer bhm_x", req.getHeader("Authorization"))
            assertEquals("1800000000", req.getHeader("X-BotHub-Time"))
            val raw = Base64.getUrlDecoder().decode(req.getHeader("X-BotHub-Signature"))
            assertTrue(verifyRaw(signer.publicKeySpki, Signing.payload(1_800_000_000L, "GET", "/api/v1/bots"), raw))

            web.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"key":"error.mobile.unpaired"}}"""))
            val e = runCatching { client.start(7) }.exceptionOrNull() as ApiException
            assertTrue(e.unpaired)
            assertEquals("POST", web.takeRequest().method)
        }
    }
}
