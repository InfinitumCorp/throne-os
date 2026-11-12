package app.infinitum.nether.core

import java.net.URI

/**
 * Decides which saved logins autofill may offer.
 *
 * A web page matches a login when its host is the saved host or a subdomain of it
 * (a leading "www." on the saved host is ignored). An app matches only a login saved
 * as androidapp://<package>. Nothing else matches, and there is no fallback list.
 */
object AutofillMatch {
    const val APP_SCHEME = "androidapp://"

    fun matches(entryUrl: String, webDomain: String?, packageName: String?): Boolean {
        val url = entryUrl.trim()
        if (url.isEmpty()) return false
        val domain = webDomain?.trim()?.trimEnd('.')?.lowercase()
        if (!domain.isNullOrEmpty()) {
            if (url.startsWith(APP_SCHEME, ignoreCase = true)) return false
            val saved = host(url)?.removePrefix("www.") ?: return false
            if (saved.isEmpty()) return false
            return domain == saved || domain.endsWith(".$saved")
        }
        val pkg = packageName?.trim()?.lowercase()
        if (pkg.isNullOrEmpty()) return false
        if (!url.startsWith(APP_SCHEME, ignoreCase = true)) return false
        return url.substring(APP_SCHEME.length).trimEnd('/').lowercase() == pkg
    }

    fun host(url: String): String? {
        val withScheme = if (url.contains("://")) url else "https://$url"
        return try {
            URI(withScheme).host?.trimEnd('.')?.lowercase()
        } catch (_: Exception) {
            null
        }
    }
}
