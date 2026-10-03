package app.tabguard.core.claude

import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationPromptTest {
    private val cats = listOf(
        Category(1, "Порно", true, listOf("porn", "xxx"), emptySet()),
        Category(2, "Казино", false, listOf("casino"), emptySet()),
    )

    @Test
    fun `prompt lists only enabled categories`() {
        val p = ClassificationPrompt.build(TabSnapshot("b", "x.com", "Title"), cats)
        assertTrue(p.contains("1: Порно (слова: porn, xxx)"))
        assertFalse(p.contains("Казино"))
        assertTrue(p.contains("Заголовок: Title"))
        assertTrue(p.contains("Адрес: x.com"))
    }

    @Test
    fun `prompt uses нет for missing title`() =
        assertTrue(ClassificationPrompt.build(TabSnapshot("b", "x.com", null), cats).contains("Заголовок: нет"))

    @Test
    fun `prompt truncates long title to 300 chars`() {
        val p = ClassificationPrompt.build(TabSnapshot("b", null, "я".repeat(1000)), cats)
        assertTrue(p.contains("Заголовок: " + "я".repeat(300) + "\n"))
        assertFalse(p.contains("я".repeat(301)))
    }

    @Test
    fun `parse zero is Allow`() = assertEquals(Verdict.Allow, ClassificationPrompt.parse(" 0\n", setOf(1)))

    @Test
    fun `parse valid id with noise`() =
        assertEquals(Verdict.Block(2, VerdictSource.CLAUDE), ClassificationPrompt.parse("Категория 2.", setOf(1, 2)))

    @Test
    fun `parse unknown id is Unknown`() = assertTrue(ClassificationPrompt.parse("7", setOf(1)) is Verdict.Unknown)

    @Test
    fun `parse garbage is Unknown`() = assertTrue(ClassificationPrompt.parse("не знаю", setOf(1)) is Verdict.Unknown)
}
