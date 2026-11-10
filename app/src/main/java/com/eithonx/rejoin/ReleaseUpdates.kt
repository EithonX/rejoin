package com.eithonx.rejoin

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

internal object ReleaseUpdates {
    enum class Check { IDLE, CHECKING, CURRENT, INCOMPATIBLE, AVAILABLE, NO_RELEASE, UNAVAILABLE }
    enum class DownloadError { NETWORK, VERIFICATION }

    var available by mutableStateOf<ReleaseUpdate?>(null)
        private set
    var check by mutableStateOf(Check.IDLE)
        private set
    var downloading by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var loaded = false

    fun refresh(context: Context, manual: Boolean = false) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (!loaded) {
            loaded = true
            available = prefs.getString("cached", null)?.let { json ->
                runCatching { metadata(JSONObject(json)) }.getOrNull()
            }?.takeIf { it.isNewerAndCompatible(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT) }
            if (available != null) check = Check.AVAILABLE
        }
        if (check == Check.CHECKING || downloading) return
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("attempted", 0L) in 0L until 86_400_000L) return
        prefs.edit().putLong("attempted", now).apply()
        check = Check.CHECKING
        worker.execute {
            val result = runCatching(::fetchLatest)
            main.post {
                if (result.isSuccess) {
                    val release = result.getOrNull()
                    val compatible = release?.takeIf {
                        it.isNewerAndCompatible(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
                    }
                    available = compatible
                    prefs.edit().putString("cached", compatible?.let(::json)?.toString()).apply()
                    check = when {
                        compatible != null -> Check.AVAILABLE
                        release == null -> Check.NO_RELEASE
                        release.versionCode > BuildConfig.VERSION_CODE -> Check.INCOMPATIBLE
                        else -> Check.CURRENT
                    }
                } else check = if (result.exceptionOrNull() is FeedUnavailable) Check.NO_RELEASE
                    else Check.UNAVAILABLE
            }
        }
    }

    fun download(context: Context, update: ReleaseUpdate, complete: (File?, DownloadError?) -> Unit) {
        if (downloading || !update.isNewerAndCompatible(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) return
        downloading = true
        val app = context.applicationContext
        worker.execute {
            val result = runCatching { verifiedApk(app, update) }
            main.post {
                downloading = false
                complete(result.getOrNull(), when (result.exceptionOrNull()) {
                    null -> null
                    is InvalidApk -> DownloadError.VERIFICATION
                    else -> DownloadError.NETWORK
                })
            }
        }
    }

    private fun fetchLatest(): ReleaseUpdate? {
        val releases = get("https://api.github.com/repos/$RELEASE_REPO/releases?per_page=20", feed = true) {
            JSONArray(readText(it, 1_000_000))
        }
        val includePrerelease = '-' in BuildConfig.VERSION_NAME
        val candidates = (0 until releases.length()).mapNotNull { releases.optJSONObject(it) }
            .filter { release ->
                !release.optBoolean("draft") &&
                    (includePrerelease || !release.optBoolean("prerelease")) &&
                    ReleaseUpdate.validTag(release.optString("tag_name")) &&
                    hasUpdateAssets(release.optJSONArray("assets"))
            }.sortedByDescending { it.optString("published_at") }

        // Only check a bounded number of release descriptors, including fallback for newer SDKs.
        var latest: ReleaseUpdate? = null
        for (release in candidates.take(3)) {
            val tag = release.optString("tag_name")
            val update = get("$RELEASES_PAGE/download/$tag/$UPDATE_METADATA") {
                metadata(JSONObject(readText(it, 4096)))
            } ?: throw IOException("Invalid update metadata")
            if (update.tag != tag) throw IOException("Release tag mismatch")
            if (latest == null || update.versionCode > latest.versionCode) latest = update
            if (update.isNewerAndCompatible(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) return update
        }
        return latest
    }

    private fun hasUpdateAssets(assets: JSONArray?): Boolean {
        if (assets == null) return false
        val names = (0 until assets.length()).mapNotNull { assets.optJSONObject(it)?.optString("name") }
        return UPDATE_APK in names && UPDATE_METADATA in names
    }

    private fun metadata(json: JSONObject) = ReleaseUpdate.parse(
        json.optString("tag"), json.optInt("versionCode"), json.optInt("minSdk"),
        json.optLong("size"), json.optString("sha256")
    )

    private fun json(update: ReleaseUpdate) = JSONObject()
        .put("tag", update.tag).put("versionCode", update.versionCode).put("minSdk", update.minSdk)
        .put("size", update.size).put("sha256", update.sha256)

    private fun verifiedApk(context: Context, update: ReleaseUpdate): File {
        val folder = File(context.cacheDir, "updates")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Update cache unavailable")
        val apk = File(folder, UPDATE_APK)
        val partial = File(folder, "download.part.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val count = get(update.apkUrl) { input ->
                partial.outputStream().use { output ->
                    transfer(input, update.size) { bytes, size ->
                        output.write(bytes, 0, size)
                        digest.update(bytes, 0, size)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (count != update.size || hash != update.sha256 || !matchesInstalled(context, partial, update)) {
                throw InvalidApk()
            }
            if (!partial.renameTo(apk)) throw IOException("Could not save verified update")
            return apk
        } finally {
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun matchesInstalled(context: Context, apk: File, update: ReleaseUpdate): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else info.versionCode.toLong()
        fun signatures(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners.orEmpty().toSet()
            else info.signatures.orEmpty().toSet()
        val signer = signatures(installed)
        return archive.packageName == installed.packageName && code(archive) == update.versionCode.toLong() &&
            code(archive) > code(installed) && archive.versionName == update.version &&
            signer.isNotEmpty() && signatures(archive) == signer
    }

    private fun readText(input: InputStream, maxSize: Long): String {
        val output = ByteArrayOutputStream()
        transfer(input, maxSize) { bytes, size -> output.write(bytes, 0, size) }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun transfer(input: InputStream, limit: Long, consume: (ByteArray, Int) -> Unit): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        val deadline = System.nanoTime() + 90_000_000_000L
        while (true) {
            if (System.nanoTime() > deadline) throw IOException("Transfer timed out")
            val size = input.read(buffer)
            if (size < 0) return total
            total += size
            if (total > limit) throw IOException("Transfer too large")
            consume(buffer, size)
        }
    }

    private val hosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    private fun <T> get(address: String, feed: Boolean = false, read: (InputStream) -> T): T {
        var url = URL(address)
        repeat(5) {
            if (url.protocol != "https" || url.host !in hosts || url.userInfo != null ||
                url.port !in listOf(-1, 443)) throw IOException("Untrusted release host")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "Rejoin-Android")
                if (url.host == "api.github.com") connection.setRequestProperty("Accept", "application/vnd.github+json")
                when (connection.responseCode) {
                    200 -> return connection.inputStream.use(read)
                    301, 302, 303, 307, 308 -> url = URL(url,
                        connection.getHeaderField("Location") ?: throw IOException("Missing release redirect"))
                    404 -> if (feed) throw FeedUnavailable() else throw IOException("Release asset unavailable")
                    else -> throw IOException("Release request failed")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many release redirects")
    }

    private class FeedUnavailable : IOException()
    private class InvalidApk : IOException()
}
