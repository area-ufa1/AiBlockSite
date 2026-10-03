package app.tabguard.service

import android.view.accessibility.AccessibilityNodeInfo
import app.tabguard.core.ui.UiNode

/** [UiNode] over a live accessibility node; children are fetched lazily. */
class AccessibilityUiNode(private val node: AccessibilityNodeInfo) : UiNode {
    override val viewId: String? get() = node.viewIdResourceName
    override val className: String? get() = node.className?.toString()
    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val isFocused: Boolean get() = node.isFocused
    override val children: List<UiNode> by lazy {
        (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.let(::AccessibilityUiNode) }
    }
}
