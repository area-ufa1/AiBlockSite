package app.tabguard.core.ui

/**
 * Recognizes the Shorts player in the YouTube app by its progress bar, the element only that
 * player has (same signal as the open-source Shorts Blocker, github.com/atick-faisal/Shorts-Blocker).
 * Shorts shelves in the feed and ordinary videos do not have it.
 */
object YouTubeShorts {
    const val PACKAGE = "com.google.android.youtube"
    private const val PLAYER_MARK = "reel_progress_bar"
    private const val MAX_NODES = 3000

    fun isShortsPlayer(packageName: String, root: UiNode): Boolean {
        if (packageName != PACKAGE) return false
        val queue = ArrayDeque(listOf(root))
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.viewId?.contains(PLAYER_MARK) == true) return true
            queue.addAll(node.children)
        }
        return false
    }
}
