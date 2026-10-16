package com.bothub.app

import android.content.Context
import com.bothub.app.security.KeystoreSigner
import com.bothub.app.security.SecureStore
import com.bothub.core.ApiException
import com.bothub.core.BotHubClient
import com.bothub.core.LogEntry
import com.bothub.core.PairLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/** What the app knows about its pairing (server, user, device; Guard secret encrypted). */
data class Pairing(val server: String, val user: String, val deviceId: String, val deviceName: String)

/**
 * The one session of the app: the pairing, the signed client and the
 * dashboard texts. Unpairing (here or in the dashboard) clears everything
 * and makes a new device key.
 */
class Session(private val context: Context) {
    private val store = SecureStore(context)
    private val signer = KeystoreSigner(context)
    private val _pairing = MutableStateFlow(load())
    val pairing: StateFlow<Pairing?> = _pairing

    var client: BotHubClient? = _pairing.value?.let { BotHubClient(it.server, signer, store.secret("token")) }
        private set

    /** Dashboard texts of log lines and errors (loaded once per start). */
    var texts: Map<String, String> = emptyMap()
        private set

    private fun load(): Pairing? {
        val server = store.get("server") ?: return null
        if (store.secret("token") == null) return null
        return Pairing(server, store.get("user") ?: "", store.get("deviceId") ?: "", store.get("deviceName") ?: "")
    }

    val guardSecret: String? get() = store.secret("guardSecret")

    suspend fun pair(link: PairLink, name: String) {
        val c = BotHubClient(link.server, signer)
        val p = c.pair(link.code, name, BuildConfig.VERSION_NAME)
        store.put("server", link.server)
        store.put("user", p.user.username)
        store.put("deviceId", p.device.id)
        store.put("deviceName", p.device.name)
        store.putSecret("token", p.token)
        store.putSecret("guardSecret", p.guardSecret)
        client = c
        _pairing.value = load()
        loadTexts()
    }

    suspend fun loadTexts() {
        val lang = if (Locale.getDefault().language == "de") "de" else "en"
        texts = runCatching { client?.texts(lang) }.getOrNull() ?: texts
    }

    /** A dashboard text with {params} filled; the key itself when unknown. */
    fun text(key: String, params: Map<String, Any?> = emptyMap()): String {
        var t = texts[key] ?: return key
        for ((k, v) in params) t = t.replace("{$k}", v?.toString() ?: "")
        return t
    }

    fun logText(e: LogEntry): String =
        text(e.key, e.params?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() } ?: emptyMap())

    /** An error for the user: BotHub's text, or "cannot be reached". */
    fun errorText(e: Throwable): String = when (e) {
        is ApiException -> if (e.unpaired) context.getString(R.string.error_unpaired) else text(e.key)
        else -> context.getString(R.string.error_network)
    }

    /** Runs a call; an unpaired device ends the session. */
    suspend fun <T> call(block: suspend (BotHubClient) -> T): Result<T> {
        val c = client ?: return Result.failure(ApiException(401, "error.mobile.unpaired"))
        return runCatching { block(c) }.onFailure { if (it is ApiException && it.unpaired) forget() }
    }

    suspend fun unpair() {
        runCatching { client?.unpair() }
        forget()
    }

    private fun forget() {
        store.clear()
        signer.reset(context)
        client = null
        _pairing.value = null
    }

    var lastUpdateCheck: Long
        get() = store.getLong("updateCheck")
        set(v) = store.putLong("updateCheck", v)
}
