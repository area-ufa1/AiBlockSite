package app.tabguard.core.ui

import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.text.extractHost

/** Finds the address bar and page title in a browser's node tree. */
object TabExtractor {
    val URL_BAR_ID_SUFFIXES = listOf(
        ":id/url_bar",
        ":id/mozac_browser_toolbar_url_view",
        ":id/location_bar_edit_text",
        ":id/url_field",
        ":id/address_bar_edit_text",
        ":id/bro_omnibar_address_title_text",
        ":id/search_box_text",
    )
    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 3000
    private const val WEB_VIEW = "android.webkit.WebView"

    fun extract(packageName: String, root: UiNode): TabSnapshot? {
        var urlBar: UiNode? = null
        var urlLikeEdit: UiNode? = null
        var title: String? = null

        val queue = ArrayDeque(listOf(root to 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (urlBar == null && node.viewId?.let { id -> URL_BAR_ID_SUFFIXES.any(id::endsWith) } == true) urlBar = node
            if (urlLikeEdit == null && node.className?.contains("EditText") == true && node.text.isUrlLike()) urlLikeEdit = node
            if (title == null && node.className == WEB_VIEW) {
                title = node.text?.takeIf { it.isNotBlank() } ?: node.contentDescription?.takeIf { it.isNotBlank() }
            }
            if (urlBar != null && title != null) break
            // Page content lives under the WebView: never search it (cost, and form fields look like URLs).
            if (depth < MAX_DEPTH && node.className != WEB_VIEW) node.children.forEach { queue.addLast(it to depth + 1) }
        }

        val url = urlBar ?: urlLikeEdit ?: return null
        if (url.isFocused) return null
        val text = url.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return TabSnapshot(packageName, text, title?.trim())
    }

    private fun String?.isUrlLike() = this != null && !trim().contains(' ') && extractHost(this) != null
}
