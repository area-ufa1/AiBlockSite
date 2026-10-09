package app.tabguard.data

import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Lists shipped in assets/blocklists, one entry per line, `#` comments, loaded once:
 * `<name>.txt` holds domains, `<name>-keywords.txt` built-in keywords for the same category.
 */
class BundledLists(private val open: (String) -> InputStream) {
    constructor(assets: AssetManager) : this({ name -> assets.open("blocklists/$name.txt") })

    private val cache = ConcurrentHashMap<String, Set<String>>()
    private val keywordCache = ConcurrentHashMap<String, List<String>>()

    fun domains(name: String): Set<String> = cache.getOrPut(name) { readLines(name).toHashSet() }

    /** Built-in keywords, in file order. */
    fun keywords(name: String): List<String> = keywordCache.getOrPut(name) { readLines("$name-keywords") }

    private fun readLines(file: String): List<String> = try {
        open(file).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
    } catch (e: IOException) {
        emptyList()
    }
}
