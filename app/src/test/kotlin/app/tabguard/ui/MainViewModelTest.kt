package app.tabguard.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.tabguard.data.AppDatabase
import app.tabguard.data.BundledLists
import app.tabguard.data.CategoryRepository
import app.tabguard.data.SettingsStore
import app.tabguard.termux.TermuxClaudeClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository
    private val settings = SettingsStore(app)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = AppDatabase.build(app, inMemory = true)
        repo = CategoryRepository(db, BundledLists { "".byteInputStream() })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(sender: (android.content.Intent) -> Unit = { throw SecurityException("no permission") }) =
        MainViewModel(repo, settings, TermuxClaudeClassifier(app, 1_000, sender), isServiceEnabled = { true })

    /** Waits until the state satisfies [predicate]. */
    private fun MainViewModel.await(predicate: (MainState) -> Boolean): MainState = runBlocking {
        withTimeout(5_000) { state.first(predicate) }
    }

    @Test
    fun `state exposes categories with counts`() {
        val s = vm().await { it.categories.isNotEmpty() }
        assertEquals("Порно", s.categories.single().category.name)
        assertEquals(0, s.categories.single().blockCount)
        assertEquals(true, s.serviceEnabled)
    }

    @Test
    fun `toggleCategory persists`() {
        val vm = vm()
        val id = vm.await { it.categories.isNotEmpty() }.categories.single().category.id
        vm.toggleCategory(id, false)
        vm.await { s -> s.categories.single().category.enabled.not() }
        assertFalse(runBlocking { repo.get(id)!!.enabled })
    }

    @Test
    fun `testClaude reports Unknown reason in status`() {
        val vm = vm()
        vm.testClaude()
        val s = vm.await { it.claudeStatus?.startsWith("Ошибка") == true }
        assertEquals("Ошибка: termux: no permission", s.claudeStatus)
    }

    @Test
    fun `claude toggle disabled state when termux missing`() {
        val vm = vm()
        vm.setClaudeEnabled(true)
        assertFalse(runBlocking { settings.claudeEnabled.first() })
        assertFalse(vm.state.value.claudeEnabled)
    }
}
