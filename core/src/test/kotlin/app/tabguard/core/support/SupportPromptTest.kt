package app.tabguard.core.support

import app.tabguard.core.model.TabSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptTest {
    @Test
    fun `support prompt names category and title`() {
        val p = SupportPrompt.build(TabSnapshot("b", "x.com", "Видео"), "Порно")
        assertTrue(p.startsWith("Я только что чуть не открыл страницу из категории «Порно»: «Видео»."))
        assertTrue(p.contains("помоги справиться с тягой"))
    }

    @Test
    fun `support prompt falls back to url when title null`() =
        assertTrue(SupportPrompt.build(TabSnapshot("b", "x.com/a", null), "Порно").contains("«x.com/a»"))

    @Test
    fun `claude url encodes spaces and cyrillic`() =
        assertEquals("https://claude.ai/new?q=%D0%B0%20b", SupportPrompt.claudeUrl("а b"))
}
