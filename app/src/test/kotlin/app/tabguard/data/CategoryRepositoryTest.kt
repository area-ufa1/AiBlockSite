package app.tabguard.data

import androidx.test.core.app.ApplicationProvider
import app.tabguard.core.model.VerdictSource
import app.tabguard.core.support.PromptKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository
    private val lists = BundledLists { name ->
        when (name) {
            "adult" -> "# comment\npornhub.com\n\nxvideos.com\n".byteInputStream()
            "adult-keywords" -> "# words\nголые\nинтим фото\n".byteInputStream()
            else -> throw java.io.FileNotFoundException(name)
        }
    }

    @Before
    fun setUp() {
        db = AppDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = CategoryRepository(db, lists)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seed creates enabled porn category with bundled list`() = runTest {
        val all = repo.observeWithCounts().first()
        assertEquals(listOf("adult", "youtube-fun"), all.map { it.bundledList })
        val seed = all.first()
        assertEquals("Порно", seed.category.name)
        assertTrue(seed.category.enabled)
        assertEquals("adult", seed.bundledList)
        assertEquals(AppDatabase.SEED_KEYWORDS, seed.category.keywords)
        assertEquals(0, seed.blockCount)
    }

    @Test
    fun `save normalizes keywords and domains and drops blanks`() = runTest {
        val id = repo.save(null, "  Казино ", "Порно,\n\n XXX ,xxx", "https://www.X.com/\nне домен\n")
        val c = repo.get(id)!!
        assertEquals("Казино", c.name)
        assertEquals(listOf("порно", "xxx"), c.keywords)
        assertEquals(setOf("x.com"), c.domains)
        assertTrue(c.enabled)
    }

    @Test
    fun `save existing keeps enabled flag and bundled list`() = runTest {
        val seedId = repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.id
        repo.setEnabled(seedId, false)
        repo.save(seedId, "Порно", "porn", "")
        val row = repo.observeWithCounts().first().first { it.bundledList == "adult" }
        assertEquals(false, row.category.enabled)
        assertEquals("adult", row.bundledList)
        assertEquals(listOf("porn"), row.category.keywords)
    }

    @Test
    fun `enabledCategories merges bundled domains`() = runTest {
        val seedId = repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.id
        repo.save(seedId, "Порно", "porn", "mine.com")
        assertEquals(setOf("mine.com", "pornhub.com", "xvideos.com"), repo.enabledCategories().single().domains)
    }

    @Test
    fun `counts are per category`() = runTest {
        val first = repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.id
        val second = repo.save(null, "Казино", "casino", "")
        val third = repo.save(null, "Пусто", "", "")
        repo.recordBlock(first, VerdictSource.DOMAIN, 1)
        repo.recordBlock(first, VerdictSource.KEYWORD, 2)
        repo.recordBlock(second, VerdictSource.CLAUDE, 3)
        val counts = repo.observeWithCounts().first().filter { it.bundledList != "youtube-fun" }
            .associate { it.category.id to it.blockCount }
        assertEquals(mapOf(first to 2, second to 1, third to 0), counts)
    }

    @Test
    fun `deleting category deletes its events`() = runTest {
        val id = repo.save(null, "Казино", "casino", "")
        repo.recordBlock(id, VerdictSource.KEYWORD, 1)
        repo.delete(id)
        assertNull(repo.get(id))
        assertEquals(0, db.events().count())
    }

    @Test
    fun `disabled category excluded from enabledCategories`() = runTest {
        val seedId = repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.id
        repo.setEnabled(seedId, false)
        assertTrue(repo.enabledCategories().isEmpty())
    }

    @Test
    fun `bundled list size is reported`() = assertEquals(2, lists.domains("adult").size)

    @Test
    fun `enabledCategories adds built-in keywords to the user's own`() = runTest {
        val seedId = repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.id
        repo.save(seedId, "Порно", "мое слово", "")
        assertEquals(listOf("мое слово", "голые", "интим фото"), repo.enabledCategories().single().keywords)
    }

    @Test
    fun `built-in keywords are not shown as the user's own`() = runTest {
        assertEquals(AppDatabase.SEED_KEYWORDS, repo.observeWithCounts().first().first { it.bundledList == "adult" }.category.keywords)
    }

    @Test
    fun `new database seeds porn and a disabled youtube category`() = runTest {
        val yt = repo.observeWithCounts().first().first { it.bundledList == "youtube-fun" }
        assertEquals("YouTube: развлечения", yt.category.name)
        assertEquals(false, yt.category.enabled)
        assertEquals(listOf("youtube.com/shorts/"), yt.category.urlPrefixes)
        assertEquals(setOf("youtube.com", "youtu.be"), yt.category.onlyHosts)
        assertEquals(PromptKind.YOUTUBE, repo.promptKind(yt.category.id))
    }

    @Test
    fun `saving keeps url prefixes and only-hosts unless given`() = runTest {
        val id = repo.observeWithCounts().first().first { it.bundledList == "youtube-fun" }.category.id
        repo.save(id, "YouTube: развлечения", "мое", "")
        assertEquals(listOf("youtube.com/shorts/"), repo.get(id)!!.urlPrefixes)
        repo.save(id, "YouTube: развлечения", "мое", "", prefixesText = "https://www.vk.com/clips\n", onlyHostsText = "youtube.com")
        assertEquals(listOf("vk.com/clips"), repo.get(id)!!.urlPrefixes)
        assertEquals(setOf("youtube.com"), repo.get(id)!!.onlyHosts)
    }
}
