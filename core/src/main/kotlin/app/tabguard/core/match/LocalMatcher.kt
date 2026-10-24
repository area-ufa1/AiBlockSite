package app.tabguard.core.match

import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import app.tabguard.core.text.extractHost
import app.tabguard.core.text.tokenize

/**
 * On-device matching: domains, then address prefixes, then keywords (scoped by [Category.onlyHosts]).
 * [Category.domains] must hold normalized hosts.
 */
object LocalMatcher {
    val ALLOWED_HOSTS = setOf("claude.ai", "anthropic.com")
    const val OWN_PACKAGE = "app.tabguard"

    fun isAllowlisted(tab: TabSnapshot): Boolean {
        if (tab.packageName == OWN_PACKAGE) return true
        val host = tab.url?.let(::extractHost) ?: return false
        return ALLOWED_HOSTS.any { host.isWithin(it) }
    }

    fun match(tab: TabSnapshot, categories: List<Category>): Verdict.Block? {
        val enabled = categories.filter { it.enabled }
        val host = tab.url?.let(::extractHost)
        if (host != null) {
            val suffixes = host.suffixes()
            enabled.firstOrNull { c -> suffixes.any(c.domains::contains) }
                ?.let { return Verdict.Block(it.id, VerdictSource.DOMAIN) }
        }
        tab.url?.let(::normalizeForPrefix)?.let { address ->
            enabled.firstOrNull { c -> c.urlPrefixes.any { p -> p.isNotBlank() && address.startsWith(normalizeForPrefix(p)) } }
                ?.let { return Verdict.Block(it.id, VerdictSource.PREFIX) }
        }
        val texts = listOfNotNull(tab.title, tab.url).map(::tokenize)
        enabled.filter { c -> c.onlyHosts.isEmpty() || (host != null && c.onlyHosts.any { host.isWithin(it) }) }.firstOrNull { c ->
            c.keywords.map(::tokenize).filter { it.isNotEmpty() }.any { kw -> texts.any { it.containsPrefixSequence(kw) } }
        }?.let { return Verdict.Block(it.id, VerdictSource.KEYWORD) }
        return null
    }

    /** Address without scheme and leading `www.`/`m.`, lowercased: `youtube.com/shorts/x`. */
    fun normalizeForPrefix(url: String): String =
        url.trim().lowercase().replace(SCHEME, "").replace(MOBILE_OR_WWW, "")

    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://")
    private val MOBILE_OR_WWW = Regex("^(www\\.|m\\.)")

    /** "a.b.com" → ["a.b.com", "b.com", "com"]. */
    private fun String.suffixes(): List<String> =
        indices.filter { it == 0 || this[it - 1] == '.' }.map { substring(it) }

    private fun String.isWithin(domain: String) = this == domain || endsWith(".$domain")

    private fun List<String>.containsPrefixSequence(kw: List<String>): Boolean =
        (0..size - kw.size).any { i -> kw.indices.all { j -> this[i + j].startsWith(kw[j]) } }
}
