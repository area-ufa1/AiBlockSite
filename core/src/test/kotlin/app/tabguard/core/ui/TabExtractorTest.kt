package app.tabguard.core.ui

import app.tabguard.core.model.TabSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

data class FakeNode(
    override val viewId: String? = null,
    override val className: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val isFocused: Boolean = false,
    override val children: List<UiNode> = emptyList(),
) : UiNode

class TabExtractorTest {
    private fun webView(title: String?, desc: String? = null) =
        FakeNode(className = "android.webkit.WebView", text = title, contentDescription = desc)

    @Test
    fun `chrome url bar and webview title`() {
        val root = FakeNode(children = listOf(
            FakeNode(viewId = "com.android.chrome:id/url_bar", className = "android.widget.EditText", text = "pornhub.com"),
            FakeNode(children = listOf(webView("Free videos"))),
        ))
        assertEquals(TabSnapshot("com.android.chrome", "pornhub.com", "Free videos"), TabExtractor.extract("com.android.chrome", root))
    }

    @Test
    fun `firefox url bar id`() {
        val root = FakeNode(children = listOf(
            FakeNode(viewId = "org.mozilla.firefox:id/mozac_browser_toolbar_url_view", className = "android.widget.TextView", text = "example.com/a"),
        ))
        assertEquals(TabSnapshot("org.mozilla.firefox", "example.com/a", null), TabExtractor.extract("org.mozilla.firefox", root))
    }

    @Test
    fun `unknown browser falls back to url-like EditText`() {
        val root = FakeNode(children = listOf(
            FakeNode(className = "android.widget.EditText", text = "https://news.example.org/x"),
            webView("News"),
        ))
        assertEquals("https://news.example.org/x", TabExtractor.extract("some.browser", root)?.url)
    }

    @Test
    fun `edit text with search query is not a url`() {
        val root = FakeNode(children = listOf(FakeNode(className = "android.widget.EditText", text = "как приготовить борщ")))
        assertNull(TabExtractor.extract("some.browser", root))
    }

    @Test
    fun `focused url bar yields null`() {
        val root = FakeNode(children = listOf(
            FakeNode(viewId = "com.android.chrome:id/url_bar", text = "porn", isFocused = true),
        ))
        assertNull(TabExtractor.extract("com.android.chrome", root))
    }

    @Test
    fun `title from contentDescription when text blank`() {
        val root = FakeNode(children = listOf(
            FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com"),
            webView(" ", desc = "Example Domain"),
        ))
        assertEquals("Example Domain", TabExtractor.extract("com.android.chrome", root)?.title)
    }

    @Test
    fun `deep tree is bounded`() {
        var node: UiNode = webView("deep")
        repeat(10_000) { node = FakeNode(children = listOf(node)) }
        val root = FakeNode(children = listOf(FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com"), node))
        val start = System.nanoTime()
        val tab = TabExtractor.extract("com.android.chrome", root)
        assertNotNull(tab)
        assertTrue((System.nanoTime() - start) < 1_000_000_000L)
    }
}
