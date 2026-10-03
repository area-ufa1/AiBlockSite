package app.tabguard.core.match

import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMatcherTest {
    private val porn = Category(1, "Порно", true, listOf("порно", "porn", "sex", "only fans"), setOf("pornhub.com"))

    @Test
    fun `domain match includes subdomains`() = assertEquals(
        Verdict.Block(1, VerdictSource.DOMAIN),
        LocalMatcher.match(TabSnapshot("b", "rt.pornhub.com/v", "Video"), listOf(porn)),
    )

    @Test
    fun `domain does not match lookalike suffix`() =
        assertNull(LocalMatcher.match(TabSnapshot("b", "notpornhub.com", "Video"), listOf(porn.copy(keywords = emptyList()))))

    @Test
    fun `keyword matches inflected russian`() = assertEquals(
        VerdictSource.KEYWORD,
        LocalMatcher.match(TabSnapshot("b", "x.ru", "Смотреть порнографию"), listOf(porn))?.source,
    )

    @Test
    fun `keyword matches in url`() =
        assertNotNull(LocalMatcher.match(TabSnapshot("b", "google.com/search?q=free+porn", "Google"), listOf(porn)))

    @Test
    fun `keyword matches only at token start`() =
        assertNull(LocalMatcher.match(TabSnapshot("b", "bbc.co.uk", "Sussex and Essex news"), listOf(porn)))

    @Test
    fun `multi word keyword`() =
        assertNotNull(LocalMatcher.match(TabSnapshot("b", null, "Only Fans leaks"), listOf(porn)))

    @Test
    fun `multi word keyword needs adjacent tokens`() =
        assertNull(LocalMatcher.match(TabSnapshot("b", null, "Only the fans"), listOf(porn.copy(keywords = listOf("only fans")))))

    @Test
    fun `blank keywords are ignored`() = assertNull(
        LocalMatcher.match(
            TabSnapshot("b", "news.ru", "Новости"),
            listOf(porn.copy(keywords = listOf("", "   ", "-"), domains = setOf("", " "))),
        ),
    )

    @Test
    fun `disabled category never matches`() =
        assertNull(LocalMatcher.match(TabSnapshot("b", "pornhub.com", "porn"), listOf(porn.copy(enabled = false))))

    @Test
    fun `domains are checked before keywords across categories`() {
        val words = Category(2, "Слова", true, listOf("video"), emptySet())
        assertEquals(
            Verdict.Block(1, VerdictSource.DOMAIN),
            LocalMatcher.match(TabSnapshot("b", "pornhub.com", "video"), listOf(words, porn)),
        )
    }

    @Test
    fun `allowlisted hosts are never blocked`() {
        assertTrue(LocalMatcher.isAllowlisted(TabSnapshot("b", "claude.ai/new?q=порно", "Claude")))
        assertTrue(LocalMatcher.isAllowlisted(TabSnapshot("b", "https://www.anthropic.com/x", null)))
        assertTrue(LocalMatcher.isAllowlisted(TabSnapshot("app.tabguard", null, null)))
    }

    @Test
    fun `ordinary page is not allowlisted`() =
        assertTrue(!LocalMatcher.isAllowlisted(TabSnapshot("b", "notclaude.ai.evil.com", null)))

    @Test
    fun `large domain set matches quickly`() {
        val big = Category(3, "big", true, emptyList(), (0 until 100_000).map { "site$it.com" }.toSet() + "pornhub.com")
        val start = System.nanoTime()
        repeat(1000) { assertNotNull(LocalMatcher.match(TabSnapshot("b", "rt.pornhub.com/v", "x"), listOf(big))) }
        assertTrue((System.nanoTime() - start) < 1_000_000_000L)
    }
}
