package com.bothub.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Auto-update from the GitHub releases of the app repo. Only published
 * releases count (/releases/latest leaves out drafts and pre-releases); the
 * tag is the version ("v1.2.0"), the asset the signed APK.
 */
object Updates {
    const val REPO = "Kljub/BotHubAndroitApp"
    const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val page: String = "",
        val assets: List<Asset> = emptyList(),
    ) {
        val version: String get() = tag.removePrefix("v")
        val apk: Asset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
    }

    @Serializable
    data class Asset(val name: String, val size: Long = 0, @SerialName("browser_download_url") val url: String)

    /** "1.10.0" > "1.9.2"; parts missing count as 0, a suffix like "-beta" is ignored. */
    fun newer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The newest release when it is newer than [current] and has an APK; else null. */
    suspend fun check(current: String, http: OkHttpClient = BotHubClient.defaultHttp, url: String = LATEST): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            val r = BotHubClient.json.decodeFromString(Release.serializer(), res.body?.string().orEmpty())
            r.takeIf { !it.draft && !it.prerelease && it.apk != null && newer(it.version, current) }
        }
    }
}
