package app.tabguard.core.model

/** What the accessibility service saw in the active browser tab. */
data class TabSnapshot(val packageName: String, val url: String?, val title: String?)

/** Identity of a page for de-duplication and caching. */
val TabSnapshot.key: String get() = "${url.orEmpty()}|${title.orEmpty()}"

data class Category(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val keywords: List<String>,
    val domains: Set<String>,
    /** Block when the address (without scheme and leading `www.`/`m.`) starts with one of these. */
    val urlPrefixes: List<String> = emptyList(),
    /** If not empty, this category's words apply only on these hosts and their subdomains. */
    val onlyHosts: Set<String> = emptySet(),
)

enum class VerdictSource { DOMAIN, PREFIX, KEYWORD, CLAUDE }

sealed interface Verdict {
    data class Block(val categoryId: Long, val source: VerdictSource) : Verdict
    data object Allow : Verdict
    data class Unknown(val reason: String) : Verdict
}
