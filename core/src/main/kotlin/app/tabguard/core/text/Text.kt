package app.tabguard.core.text

import java.util.Locale

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val HOST = Regex("^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+$")

fun normalize(s: String): String = s.lowercase(Locale.ROOT).replace('ё', 'е').trim()

fun tokenize(s: String): List<String> = normalize(s).split(NON_WORD).filter { it.isNotEmpty() }

/** Host of [url] (scheme optional), lowercased, without `www.`, port or userinfo; null if it is not a host. */
fun extractHost(url: String): String? {
    val rest = url.trim().replace(SCHEME, "")
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = normalize(authority.substringAfterLast('@').substringBefore(':')).removePrefix("www.")
    return host.takeIf { HOST.matches(it) }
}
