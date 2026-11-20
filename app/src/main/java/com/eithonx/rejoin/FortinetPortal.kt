package com.eithonx.rejoin

import java.net.URI

internal object FortinetPortal {
    const val HOST = "172.16.2.1"
    const val PORT = 1000

    private val routes = setOf("/login", "/fgtauth", "/portal")
    private val embeddedLink = Regex(
        """http://172\.16\.2\.1:1000/[^\s"'<>\\]+""", RegexOption.IGNORE_CASE
    )
    private val pathCharacters = Regex("[a-zA-Z0-9/._-]+")
    private val hiddenBlocks = Regex("""<script\b[^>]*>.*?</script\s*>|<style\b[^>]*>.*?</style\s*>|<!--.*?-->""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val htmlTags = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")
    private val rejectionMessages = listOf(
        "invalid password", "invalid username", "authentication failed", "login failed",
        "incorrect password", "incorrect username", "invalid credentials", "incorrect credentials"
    )

    fun rejectsCredentials(html: String): Boolean {
        val text = html.replace(hiddenBlocks, " ").replace(htmlTags, " ")
            .replace("&nbsp;", " ").replace(whitespace, " ")
        return rejectionMessages.any { text.contains(it, ignoreCase = true) }
    }

    fun requestPath(link: String, fromGateway: Boolean = false): String? {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        if (uri.rawFragment != null || uri.rawUserInfo != null) return null
        if (uri.isAbsolute) {
            if (!uri.scheme.equals("http", true) || uri.host != HOST || uri.port != PORT) return null
        } else if (!fromGateway || uri.rawAuthority != null || !link.trimStart().startsWith("/")) {
            return null
        }
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        if (!pathCharacters.matches(path) || path.startsWith("//") || uri.normalize().rawPath != uri.rawPath)
            return null
        if (path != "/" && routes.none { path == it || path.startsWith("$it/") }) return null
        return path + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun redirectPath(location: String?, body: String): String? {
        if (!location.isNullOrBlank()) return requestPath(location)
        return embeddedLink.find(body)?.value?.let { requestPath(it.replace("&amp;", "&")) }
    }

    fun isSecureRedirect(location: String?): Boolean {
        val uri = runCatching { URI(location?.trim() ?: return false) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && uri.host == HOST && uri.rawUserInfo == null
    }
}
