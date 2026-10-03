package app.tabguard.core.pipeline

import app.tabguard.core.classify.Classifier
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TabPipelineTest {
    private val cats = listOf(Category(1, "c", true, emptyList(), emptySet()))
    private val classified = mutableListOf<String?>()
    private val blocked = mutableListOf<String?>()
    private val unknown = mutableListOf<String>()

    private fun TestScope.pipeline(scope: CoroutineScope = backgroundScope, verdict: suspend (TabSnapshot) -> Verdict) =
        TabPipeline(
            scope = scope,
            classifier = Classifier { tab, _ -> classified += tab.title; verdict(tab) },
            categories = { cats },
            onBlock = { tab, _ -> blocked += tab.title },
            onUnknown = { unknown += it },
            now = { currentTime },
        )

    private fun tab(t: String) = TabSnapshot("b", "$t.com", t)

    @Test
    fun `events within 500ms are debounced to the last one`() = runTest {
        val p = pipeline { Verdict.Allow }
        p.submit(tab("A")); advanceTimeBy(200); p.submit(tab("B")); advanceTimeBy(600)
        assertEquals(listOf("B"), classified)
    }

    @Test
    fun `nothing is classified before 500ms`() = runTest {
        val p = pipeline { Verdict.Allow }
        p.submit(tab("A")); advanceTimeBy(499)
        assertEquals(emptyList<String>(), classified)
    }

    @Test
    fun `same key twice in a row is classified once`() = runTest {
        val p = pipeline { Verdict.Allow }
        p.submit(tab("A")); advanceTimeBy(600); p.submit(tab("A")); advanceTimeBy(600)
        assertEquals(listOf("A"), classified)
    }

    @Test
    fun `blocked page blocks again after visiting another page`() = runTest {
        val p = pipeline { if (it.title == "A") Verdict.Block(1, VerdictSource.KEYWORD) else Verdict.Allow }
        for (t in listOf("A", "B", "A")) { p.submit(tab(t)); advanceTimeBy(600) }
        assertEquals(listOf("A", "A"), blocked)
    }

    @Test
    fun `slow classification is not cancelled by next tab`() = runTest {
        val p = pipeline { delay(10_000); if (it.title == "A") Verdict.Block(1, VerdictSource.CLAUDE) else Verdict.Allow }
        p.submit(tab("A")); advanceTimeBy(600); p.submit(tab("B")); advanceTimeBy(20_000)
        assertEquals(listOf("A"), blocked)
    }

    @Test
    fun `remote failure yields Unknown and pipeline continues`() = runTest {
        val p = pipeline { if (it.title == "A") Verdict.Unknown("timeout") else Verdict.Block(1, VerdictSource.CLAUDE) }
        p.submit(tab("A")); advanceTimeBy(600); p.submit(tab("B")); advanceTimeBy(600)
        assertEquals(listOf("timeout"), unknown)
        assertEquals(listOf("B"), blocked)
    }

    @Test
    fun `going back to a blocked page blocks it again`() = runTest {
        val p = pipeline { Verdict.Block(1, VerdictSource.KEYWORD) }
        p.submit(tab("A")); advanceTimeBy(600)
        advanceTimeBy(5_000)
        p.submit(tab("A")); advanceTimeBy(600)
        assertEquals(listOf("A", "A"), blocked)
    }

    @Test
    fun `repeated events right after a block do not redirect twice`() = runTest {
        val p = pipeline { Verdict.Block(1, VerdictSource.KEYWORD) }
        p.submit(tab("A")); advanceTimeBy(600)
        p.submit(tab("A")); advanceTimeBy(600)
        assertEquals(listOf("A"), blocked)
    }

    @Test
    fun `exception in classification does not stop the pipeline`() = runTest {
        val p = pipeline { if (it.title == "A") throw IllegalStateException("db closed") else Verdict.Block(1, VerdictSource.KEYWORD) }
        p.submit(tab("A")); advanceTimeBy(600); p.submit(tab("B")); advanceTimeBy(600)
        assertEquals(listOf("error: db closed"), unknown)
        assertEquals(listOf("B"), blocked)
    }
}
