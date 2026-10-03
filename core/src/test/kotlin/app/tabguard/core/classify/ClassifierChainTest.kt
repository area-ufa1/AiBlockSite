package app.tabguard.core.classify

import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ClassifierChainTest {
    private val cat = Category(1, "Порно", true, listOf("porn"), setOf("pornhub.com"))
    private var calls = 0
    private fun remote(v: Verdict) = Classifier { _, _ -> calls++; v }

    @Test
    fun `local block short-circuits remote`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Allow) })
        assertEquals(Verdict.Block(1, VerdictSource.KEYWORD), chain.classify(TabSnapshot("b", "x.com", "porn"), listOf(cat)))
        assertEquals(0, calls)
    }

    @Test
    fun `no remote yields Allow`() = runTest {
        assertEquals(Verdict.Allow, ClassifierChain({ null }).classify(TabSnapshot("b", "x.com", "news"), listOf(cat)))
    }

    @Test
    fun `no enabled categories skips remote`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Block(1, VerdictSource.CLAUDE)) })
        assertEquals(Verdict.Allow, chain.classify(TabSnapshot("b", "x.com", "news"), listOf(cat.copy(enabled = false))))
        assertEquals(0, calls)
    }

    @Test
    fun `remote exception yields Unknown`() = runTest {
        val chain = ClassifierChain({ Classifier { _, _ -> throw IOException("boom") } })
        assertEquals(Verdict.Unknown("boom"), chain.classify(TabSnapshot("b", "x.com", "news"), listOf(cat)))
    }

    @Test
    fun `claude url is allowed even if remote says block`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Block(1, VerdictSource.CLAUDE)) })
        assertEquals(Verdict.Allow, chain.classify(TabSnapshot("b", "claude.ai/new?q=porn", "Claude"), listOf(cat)))
    }

    @Test
    fun `returning to a page uses cache not remote`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Allow) })
        val a = TabSnapshot("b", "a.com", "A")
        chain.classify(a, listOf(cat)); chain.classify(TabSnapshot("b", "b.com", "B"), listOf(cat)); chain.classify(a, listOf(cat))
        assertEquals(2, calls)
    }

    @Test
    fun `cache is invalidated when categories change`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Allow) })
        val a = TabSnapshot("b", "a.com", "A")
        assertEquals(Verdict.Allow, chain.classify(a, listOf(cat)))
        assertEquals(Verdict.Block(1, VerdictSource.KEYWORD), chain.classify(a, listOf(cat.copy(keywords = listOf("a")))))
        chain.classify(a, listOf(cat.copy(keywords = listOf("zzz"))))
        assertEquals(2, calls)
    }

    @Test
    fun `Unknown is not cached`() = runTest {
        val chain = ClassifierChain({ remote(Verdict.Unknown("timeout")) })
        val a = TabSnapshot("b", "a.com", "A")
        chain.classify(a, listOf(cat)); chain.classify(a, listOf(cat))
        assertEquals(2, calls)
    }
}
