package com.bothub.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bothub.core.BotHubClient
import com.bothub.core.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Auto-update: only published GitHub releases of the app (Updates.check).
 * The APK is downloaded into the cache and handed to the system installer;
 * Android installs it only when it is signed with the same key as the app.
 */
object Updater {
    /** Checks at most every 6 hours unless [force]. */
    suspend fun check(session: Session, force: Boolean = false): Updates.Release? {
        val now = System.currentTimeMillis()
        if (!force && now - session.lastUpdateCheck < 6 * 3600_000L) return null
        session.lastUpdateCheck = now
        return runCatching { Updates.check(BuildConfig.VERSION_NAME) }.getOrNull()
    }

    /** Downloads the APK with progress (0-100); the file in the cache. */
    suspend fun download(context: Context, release: Updates.Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val asset = release.apk ?: error("no APK")
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "BotHub-${release.version}.apk")
        BotHubClient.defaultHttp.newCall(Request.Builder().url(asset.url).build()).execute().use { res ->
            require(res.isSuccessful) { "HTTP ${res.code}" }
            val body = res.body ?: error("empty")
            val total = body.contentLength().takeIf { it > 0 } ?: asset.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        }
        file
    }

    /** May the app install packages? Else the system page to allow it. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
