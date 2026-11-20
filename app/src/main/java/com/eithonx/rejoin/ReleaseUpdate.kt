package com.eithonx.rejoin

internal const val RELEASE_REPO = "EithonX/rejoin"
internal const val RELEASES_PAGE = "https://github.com/$RELEASE_REPO/releases"
internal const val UPDATE_APK = "Rejoin.apk"

internal data class ReleaseUpdate(
    val version: String,
    val size: Long,
    val sha256: String
) {
    val tag get() = "v$version"
    val notesUrl get() = "$RELEASES_PAGE/tag/$tag"
    val apkUrl get() = "$RELEASES_PAGE/download/$tag/$UPDATE_APK"

    fun isNewerThan(installedVersion: String) =
        versionPattern.matches(installedVersion) && compareVersions(version, installedVersion) > 0

    companion object {
        private val versionPattern = Regex("""\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+(?:[.-][a-zA-Z0-9]+)*)?""")
        private val digestPattern = Regex("sha256:[a-f0-9]{64}")
        private val versionToken = Regex("""\d+|[a-zA-Z]+""")

        fun parse(tag: String, size: Long, digest: String): ReleaseUpdate? {
            if (!tag.startsWith("v") || tag.length > 65 || !versionPattern.matches(tag.drop(1)) ||
                size !in 1..50_000_000L || !digestPattern.matches(digest)) return null
            return ReleaseUpdate(tag.drop(1), size, digest.removePrefix("sha256:"))
        }

        fun latest(updates: List<ReleaseUpdate>): ReleaseUpdate? =
            updates.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }

        private fun compareVersions(a: String, b: String): Int {
            val av = a.substringBefore('-').split('.')
            val bv = b.substringBefore('-').split('.')
            for (index in av.indices) {
                val number = compareNumbers(av[index], bv[index])
                if (number != 0) return number
            }
            val aSuffix = a.substringAfter('-', "")
            val bSuffix = b.substringAfter('-', "")
            if (aSuffix.isEmpty() || bSuffix.isEmpty()) return aSuffix.isEmpty().compareTo(bSuffix.isEmpty())
            val at = versionToken.findAll(aSuffix).map { it.value }.toList()
            val bt = versionToken.findAll(bSuffix).map { it.value }.toList()
            for (index in 0 until minOf(at.size, bt.size)) {
                val x = at[index]
                val y = bt[index]
                val order = when {
                    x.first().isDigit() && y.first().isDigit() -> compareNumbers(x, y)
                    else -> x.compareTo(y, ignoreCase = true)
                }
                if (order != 0) return order
            }
            return at.size.compareTo(bt.size)
        }

        private fun compareNumbers(a: String, b: String): Int {
            val x = a.trimStart('0')
            val y = b.trimStart('0')
            return x.length.compareTo(y.length).takeUnless { it == 0 } ?: x.compareTo(y)
        }
    }
}
