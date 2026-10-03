package app.tabguard.data

import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Domain lists shipped in assets/blocklists/<name>.txt, one host per line, `#` comments. Loaded once. */
class BundledLists(private val open: (String) -> InputStream) {
    constructor(assets: AssetManager) : this({ name -> assets.open("blocklists/$name.txt") })

    private val cache = ConcurrentHashMap<String, Set<String>>()

    fun domains(name: String): Set<String> = cache.getOrPut(name) {
        try {
            open(name).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()
            }
        } catch (e: IOException) {
            emptySet()
        }
    }
}
