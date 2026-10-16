package com.bothub.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class User(val id: Long, val username: String)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val platform: String = "android",
    val appVersion: String = "",
    val push: Boolean = false,
    val notify: List<String> = emptyList(),
    val guard: Boolean = false,
)

/** Answer of POST /api/v1/mobile/pair: the token and guard secret come only here. */
@Serializable
data class Pairing(val token: String, val guardSecret: String, val device: Device, val user: User)

@Serializable
data class Me(val device: Device, val user: User, val permissions: List<String> = emptyList())

@Serializable
data class Bot(
    val id: Long,
    val name: String,
    val status: String,
    val avatarUrl: String? = null,
    val guildCount: Int = 0,
    val startedAt: String? = null,
    val statusErrorKey: String? = null,
    val access: String? = null,
) {
    val running: Boolean get() = status == "running"
}

@Serializable
data class LogEntry(
    val id: Long,
    val time: String,
    val level: String,
    val key: String,
    val code: String? = null,
    val params: Map<String, JsonElement>? = null,
)

@Serializable
data class ModuleState(val key: String, val enabled: Boolean)

@Serializable
data class PluginState(val id: String, val name: String, val version: String = "", val icon: String = "", val enabled: Boolean = false)

@Serializable
data class Approval(val id: String, val ip: String, val userAgent: String, val createdAt: String, val expiresAt: String)

@Serializable
data class Overview(val bots: Counts) {
    @Serializable
    data class Counts(val total: Int, val online: Int)
}

@Serializable
internal data class Items<T>(val items: List<T> = emptyList())

@Serializable
internal data class ApiErrorBody(val error: ApiErrorKey? = null)

@Serializable
internal data class ApiErrorKey(val key: String = "error.internal")

/** An error answer of BotHub: [key] is a text key (e.g. "error.mobile.unpaired"). */
class ApiException(val status: Int, val key: String) : Exception("$status $key") {
    /** The device was unpaired (or its user deleted): pair again. */
    val unpaired: Boolean get() = status == 401 && key == "error.mobile.unpaired"
}
