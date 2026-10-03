package app.tabguard.data

import android.content.res.AssetManager
import java.io.InputStream

/** Domain lists shipped in assets/blocklists/<name>.txt, one host per line, `#` comments. */
class BundledLists(private val open: (String) -> InputStream) {
    constructor(assets: AssetManager) : this({ name -> assets.open("blocklists/$name.txt") })

    fun domains(name: String): Set<String> = TODO()
}
