package com.bothub.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The BotHub API as the app sees it. [server] is the dashboard address of
 * the QR code (it forwards /api/v1 to BotHub). Every call after pairing
 * carries the device token and a signature of time, method and path.
 */
class BotHubClient(
    val server: String,
    private val signer: RequestSigner,
    var token: String? = null,
    private val http: OkHttpClient = defaultHttp,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val jsonType = "application/json".toMediaType()
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    /** Trades the validation key of the QR code (and its user) for a device token. */
    suspend fun pair(code: String, name: String, appVersion: String, user: String = ""): Pairing {
        val body = buildJsonObject {
            put("code", code); put("user", user); put("name", name); put("platform", "android"); put("appVersion", appVersion)
            put("publicKey", Signing.base64Url(signer.publicKeySpki))
        }
        val p: Pairing = call("POST", "/api/v1/mobile/pair", body, signed = false)
        token = p.token
        return p
    }

    suspend fun me(): Me = call("GET", "/api/v1/mobile/me")
    suspend fun bots(): List<Bot> = call<Items<Bot>>("GET", "/api/v1/bots").items
    suspend fun bot(id: Long): Bot = call("GET", "/api/v1/bots/$id")
    suspend fun start(id: Long) = action("POST", "/api/v1/bots/$id/start")
    suspend fun stop(id: Long) = action("POST", "/api/v1/bots/$id/stop")
    suspend fun restart(id: Long) = action("POST", "/api/v1/bots/$id/restart")
    suspend fun logs(id: Long, level: String? = null): List<LogEntry> =
        call<Items<LogEntry>>("GET", "/api/v1/bots/$id/logs?limit=200" + (level?.let { "&level=$it" } ?: "")).items
    suspend fun modules(id: Long): List<ModuleState> = call<Items<ModuleState>>("GET", "/api/v1/bots/$id/modules").items
    suspend fun setModule(id: Long, key: String, enabled: Boolean) =
        action("PUT", "/api/v1/bots/$id/modules/$key", buildJsonObject { put("enabled", enabled) })
    suspend fun moduleCommands(id: Long, key: String): List<ModuleCommand> =
        call<Items<ModuleCommand>>("GET", "/api/v1/bots/$id/modules/$key/commands").items.sortedBy { it.name }
    suspend fun setModuleCommand(id: Long, key: String, name: String, enabled: Boolean) =
        action("PUT", "/api/v1/bots/$id/modules/$key/commands/$name", buildJsonObject { put("enabled", enabled) })
    suspend fun plugins(id: Long): List<PluginState> = call<Items<PluginState>>("GET", "/api/v1/bots/$id/plugins").items
    suspend fun setPlugin(id: Long, plugin: String, enabled: Boolean) =
        action("PATCH", "/api/v1/bots/$id/plugins/$plugin", buildJsonObject { put("enabled", enabled) })
    suspend fun overview(): Overview = call("GET", "/api/v1/stats/overview")
    suspend fun approvals(): List<Approval> = call<Items<Approval>>("GET", "/api/v1/mobile/approvals").items
    suspend fun decide(approval: String, approve: Boolean) =
        action("POST", "/api/v1/mobile/approvals/$approval", buildJsonObject { put("approve", approve) })
    suspend fun setPush(token: String, notify: List<String>): Device = call("PUT", "/api/v1/mobile/push", buildJsonObject {
        put("token", token); put("platform", if (token.isEmpty()) "" else "fcm")
        putJsonArray("notify") { notify.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    })
    suspend fun unpair() = action("DELETE", "/api/v1/mobile/device")

    /** The dashboard's texts for log lines, errors and states (public). */
    suspend fun texts(lang: String): Map<String, String> = call("GET", "/mobile/texts/$lang", signed = false)

    /** The module groups of the dashboard (public). */
    suspend fun catalog(): Catalog = call("GET", "/mobile/catalog", signed = false)

    private suspend fun action(method: String, path: String, body: JsonObject? = null) {
        call<JsonObject?>(method, path, body ?: if (method == "GET" || method == "DELETE") null else JsonObject(emptyMap()))
    }

    private suspend inline fun <reified T> call(method: String, path: String, body: JsonObject? = null, signed: Boolean = true): T =
        withContext(Dispatchers.IO) {
            val url = (server.trimEnd('/') + path).toHttpUrl()
            val b = Request.Builder().url(url)
            val payload = body?.toString()?.toRequestBody(jsonType)
            b.method(method, payload ?: if (method in setOf("POST", "PUT", "PATCH")) "{}".toRequestBody(jsonType) else null)
            if (signed) {
                val t = token ?: throw ApiException(401, "error.mobile.unpaired")
                val now = clock()
                val target = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
                val sig = Signing.derToRaw(signer.signDer(Signing.payload(now, method, target)))
                b.header("Authorization", "Bearer $t").header("X-BotHub-Time", now.toString()).header("X-BotHub-Signature", Signing.base64Url(sig))
            }
            http.newCall(b.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val key = runCatching { json.decodeFromString<ApiErrorBody>(text).error?.key }.getOrNull() ?: "error.http.${res.code}"
                    throw ApiException(res.code, key)
                }
                @Suppress("UNCHECKED_CAST")
                if (text.isBlank()) null as T else json.decodeFromString(serializer<T>(), text)
            }
        }
}
