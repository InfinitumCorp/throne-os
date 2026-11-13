package app.infinitum.nether.core

/**
 * Applies a login that Android autofill asked Nether to save.
 *
 * The login is keyed by where it was typed: https://<site> for web pages, or
 * androidapp://<package> for apps (the same form AutofillMatch fills from).
 * If a login for that place and username already exists, its password is updated
 * (the old one goes to history). Otherwise a new login is added.
 */
object AutofillSave {
    fun savedUrl(webDomain: String?, packageName: String?): String? {
        val domain = webDomain?.trim()?.trimEnd('.')?.lowercase()
        if (!domain.isNullOrEmpty()) return "https://$domain"
        val pkg = packageName?.trim()?.lowercase()
        if (!pkg.isNullOrEmpty()) return AutofillMatch.APP_SCHEME + pkg
        return null
    }

    fun apply(
        document: VaultDocument,
        webDomain: String?,
        packageName: String?,
        username: String,
        password: String,
        now: Long = System.currentTimeMillis(),
    ): VaultDocument? {
        if (password.isEmpty()) return null
        val url = savedUrl(webDomain, packageName) ?: return null
        val user = username.trim()
        val existing = document.entries.indexOfFirst { entry ->
            entry.type == EntryTypes.LOGIN &&
                entry.username.equals(user, ignoreCase = true) &&
                AutofillMatch.matches(entry.url, webDomain, packageName)
        }
        if (existing >= 0) {
            val entry = document.entries[existing]
            if (entry.secret == password) return null
            val updated = document.entries.toMutableList()
            updated[existing] = EntryEdits.withNewSecret(entry, password, now)
            return document.copy(entries = updated)
        }
        val title = webDomain?.trim()?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
            ?: packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: "Login"
        val entry = NetherEntry(
            type = EntryTypes.LOGIN,
            title = title,
            username = user,
            secret = password,
            url = url,
            createdAt = now,
            updatedAt = now,
        )
        return document.copy(entries = document.entries + entry)
    }
}
