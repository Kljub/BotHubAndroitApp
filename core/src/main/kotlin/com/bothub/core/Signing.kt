package com.bothub.core

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Signs app requests with the phone's P-256 key. On Android the key lives in
 * the AndroidKeyStore and never leaves it; [publicKeySpki] goes to BotHub at
 * pairing. [signDer] returns the DER signature of SHA256withECDSA.
 */
interface RequestSigner {
    val publicKeySpki: ByteArray
    fun signDer(data: ByteArray): ByteArray
}

/** A software key (tests, and the end-to-end test against Docker). */
class JvmSigner : RequestSigner {
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val private: PrivateKey = pair.private
    override val publicKeySpki: ByteArray = pair.public.encoded
    override fun signDer(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(private); update(data); sign() }
}

object Signing {
    const val PREFIX = "bothub-mobile:"

    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun base64Url(bytes: ByteArray): String = b64.encodeToString(bytes)

    /** What a request signs: "bothub-mobile:<time>:<METHOD>:<path?query>". */
    fun payload(unixSeconds: Long, method: String, pathAndQuery: String): ByteArray =
        "$PREFIX$unixSeconds:${method.uppercase()}:$pathAndQuery".toByteArray(Charsets.UTF_8)

    /** DER ECDSA signature (SEQUENCE of two INTEGERs) to raw r||s, 32 bytes each (WebCrypto format). */
    fun derToRaw(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "not a DER signature" }
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i = 2 + (der[1].toInt() and 0x7f)
        fun integer(): ByteArray {
            require(der[i] == 0x02.toByte()) { "bad DER integer" }
            val len = der[i + 1].toInt() and 0xff
            val value = der.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
            val trimmed = value.dropWhile { it == 0.toByte() }.toByteArray()
            require(trimmed.size <= 32) { "integer too long" }
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return integer() + integer()
    }
}
