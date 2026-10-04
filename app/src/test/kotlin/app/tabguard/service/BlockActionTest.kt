package app.tabguard.service

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.VerdictSource
import app.tabguard.data.AppDatabase
import app.tabguard.data.BundledLists
import app.tabguard.data.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BlockActionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository
    private lateinit var action: BlockAction
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        db = AppDatabase.build(app, inMemory = true)
        repo = CategoryRepository(db, BundledLists { "".byteInputStream() })
        action = BlockAction(app, repo, clock = { 42L }, mainDispatcher = Dispatchers.Unconfined, goBack = { events += "back" })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `perform records event, copies prompt, opens claude url`() = runBlocking {
        val id = repo.observeWithCounts().first().single().category.id
        action.perform(TabSnapshot("b", "x.com", "Горячее видео"), Verdict.Block(id, VerdictSource.KEYWORD))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.dataString!!.startsWith("https://claude.ai/new?q="))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip.contains("«Порно»") && clip.contains("Горячее видео"))
        assertEquals(1, repo.observeWithCounts().first().single().blockCount)
    }

    @Test
    fun `perform for deleted category does nothing`() = runBlocking {
        action.perform(TabSnapshot("b", "x.com", "t"), Verdict.Block(999, VerdictSource.CLAUDE))
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, db.events().count())
    }

    @Test
    fun `perform presses back before opening claude`() = runBlocking {
        val id = repo.observeWithCounts().first().single().category.id
        action.perform(TabSnapshot("b", "x.com", "t"), Verdict.Block(id, VerdictSource.DOMAIN))
        assertEquals(listOf("back"), events)
        assertTrue(shadowOf(app).nextStartedActivity != null)
    }

    @Test
    fun `deleted category does not press back`() = runBlocking {
        action.perform(TabSnapshot("b", "x.com", "t"), Verdict.Block(999, VerdictSource.DOMAIN))
        assertEquals(emptyList<String>(), events)
    }
}
