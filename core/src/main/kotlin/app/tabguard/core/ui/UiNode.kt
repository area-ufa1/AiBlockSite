package app.tabguard.core.ui

/** Minimal view of an accessibility node, so tree parsing is testable without Android. */
interface UiNode {
    val viewId: String?
    val className: String?
    val text: String?
    val contentDescription: String?
    val isFocused: Boolean
    val children: List<UiNode>
}
