package com.eithonx.rejoin

internal const val RELEASE_REPO = "EithonX/captive"
internal const val RELEASES_PAGE = "https://github.com/$RELEASE_REPO/releases"
internal const val UPDATE_METADATA = "rejoin-update.json"
internal const val UPDATE_APK = "Rejoin.apk"

internal data class ReleaseUpdate(
    val version: String,
    val versionCode: Int,
    val minSdk: Int,
    val size: Long,
    val sha256: String
) {
    val tag get() = "v$version"
    val notesUrl get() = "$RELEASES_PAGE/tag/$tag"
    val apkUrl get() = "$RELEASES_PAGE/download/$tag/$UPDATE_APK"

    fun isNewerAndCompatible(installedVersion: Int, sdk: Int) =
        versionCode > installedVersion && minSdk <= sdk

    companion object {
        private val versionPattern = Regex("""[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?""")
        private val digestPattern = Regex("[a-f0-9]{64}")

        fun validTag(tag: String): Boolean =
            tag.startsWith("v") && tag.length <= 65 && versionPattern.matches(tag.drop(1))

        fun parse(tag: String, code: Int, sdk: Int, size: Long, digest: String): ReleaseUpdate? {
            if (!validTag(tag) || code <= 0 || sdk < 23 || size !in 1..50_000_000L ||
                !digestPattern.matches(digest)) return null
            return ReleaseUpdate(tag.drop(1), code, sdk, size, digest)
        }
    }
}
