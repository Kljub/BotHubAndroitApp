package com.bothub.app.security

import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.bothub.core.RequestSigner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The phone's P-256 key in the AndroidKeyStore (StrongBox when the phone has
 * it). It never leaves the keystore; BotHub gets the public key at pairing
 * and checks the signature of every request with it.
 */
class KeystoreSigner(context: Context) : RequestSigner {
    private companion object {
        const val ALIAS = "bothub-device"
        const val STORE = "AndroidKeyStore"
    }

    private val ks = KeyStore.getInstance(STORE).apply { load(null) }

    init {
        if (!ks.containsAlias(ALIAS)) create(context)
    }

    private fun create(context: Context) {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE)
        val strongBox = Build.VERSION.SDK_INT >= 28 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        try {
            gen.initialize(spec(strongBox)); gen.generateKeyPair()
        } catch (e: Exception) {
            if (!strongBox) throw e
            gen.initialize(spec(false)); gen.generateKeyPair()
        }
    }

    override val publicKeySpki: ByteArray get() = ks.getCertificate(ALIAS).publicKey.encoded

    override fun signDer(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(ks.getKey(ALIAS, null) as PrivateKey); update(data); sign() }

    /** A new key (after unpairing): the old pairing can never sign again. */
    fun reset(context: Context) {
        ks.deleteEntry(ALIAS)
        create(context)
    }
}
