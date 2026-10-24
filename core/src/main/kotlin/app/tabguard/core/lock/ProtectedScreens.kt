package app.tabguard.core.lock

import app.tabguard.core.ui.UiNode

/**
 * Recognizes the system screens that would switch the blocker off: this app's info page
 * (Force stop / Uninstall), its accessibility service page, and the uninstall dialog.
 * Screens that merely mention the app (app lists, search results) are not protected.
 */
object ProtectedScreens {
    const val APP_NAME = "Страж вкладок"

    private val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
    private val ACTIONS = setOf("остановить", "принудительно остановить", "force stop", "удалить", "uninstall", "отключить", "disable")
    private val TOGGLE_HINTS = listOf("использовать", "\\buse\\b", "быстрое включение", "shortcut").map(::Regex)
    private val UNINSTALL_HINTS = listOf("удал", "uninstall")
    private const val MAX_NODES = 3000

    /** Confirmation dialogs ("Force stop?") don't name the app; they count only right after a protected screen. */
    private val CONFIRM_HINTS = listOf("остановить", "force stop", "отключить", "turn off", "удалить", "uninstall")

    /** [recentlyProtected]: a protected screen of the same settings app was left a moment ago. */
    fun isProtected(packageName: String, root: UiNode, recentlyProtected: Boolean = false): Boolean {
        val installer = packageName in INSTALLERS
        val settings = packageName == "com.android.settings" || packageName.endsWith(".settings")
        if (!installer && !settings) return false

        val nodes = flatten(root)
        val texts = nodes.mapNotNull { (it.text ?: it.contentDescription)?.trim()?.takeIf(String::isNotEmpty) }
        val lower = texts.map { it.lowercase() }

        if (installer) {
            return texts.any { it.contains(APP_NAME) } && lower.any { t -> UNINSTALL_HINTS.any { t.contains(it) } }
        }
        if (recentlyProtected && lower.any { t -> CONFIRM_HINTS.any { t.contains(it) } }) return true
        if (texts.none { it == APP_NAME }) return false
        if (lower.any { it in ACTIONS }) return true
        val hasSwitch = nodes.any { it.className?.contains("Switch") == true }
        return hasSwitch && lower.any { t -> TOGGLE_HINTS.any { it.containsMatchIn(t) } }
    }

    private fun flatten(root: UiNode): List<UiNode> {
        val out = ArrayList<UiNode>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val n = queue.removeFirst()
            out += n
            queue.addAll(n.children)
        }
        return out
    }
}
