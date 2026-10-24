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
import org.junit.Assert.assertTrue
import app.tabguard.core.lock.LockMode
import app.tabguard.core.lock.LockState
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
        // DataStore is a process-wide singleton: start every test from an unconfigured lock.
        runBlocking { settings.saveLock(LockState()) }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(
        sender: (android.content.Intent) -> Unit = { throw SecurityException("no permission") },
        running: () -> Boolean = { true },
    ) = MainViewModel(repo, settings, TermuxClaudeClassifier(app, 1_000, sender), isServiceEnabled = { true }, isServiceRunning = running)

    /** Waits until the state satisfies [predicate]. */
    private fun MainViewModel.await(predicate: (MainState) -> Boolean): MainState = runBlocking {
        withTimeout(5_000) { state.first(predicate) }
    }

    @Test
    fun `state exposes categories with counts`() {
        val s = vm().await { it.categories.isNotEmpty() }
        assertEquals("Порно", s.categories.first { it.bundledList == "adult" }.category.name)
        assertEquals(0, s.categories.first { it.bundledList == "adult" }.blockCount)
        assertEquals(ServiceStatus.RUNNING, s.serviceStatus)
    }

    @Test
    fun `toggleCategory persists`() {
        val vm = vm()
        val id = vm.await { it.categories.isNotEmpty() }.categories.first { it.bundledList == "adult" }.category.id
        vm.toggleCategory(id, false)
        vm.await { s -> s.categories.first { it.bundledList == "adult" }.category.enabled.not() }
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

    @Test
    fun `enabled but not running service is reported as stuck`() {
        var running = false
        val vm = vm(running = { running })
        assertEquals(ServiceStatus.STUCK, vm.await { it.categories.isNotEmpty() }.serviceStatus)
        running = true
        vm.refresh()
        assertEquals(ServiceStatus.RUNNING, vm.await { it.serviceStatus == ServiceStatus.RUNNING }.serviceStatus)
    }

    @Test
    fun `setup is required until a mode is chosen`() {
        val vm = vm()
        assertTrue(vm.await { it.categories.isNotEmpty() }.needsSetup)
        vm.completeSetup(LockMode.PAUSE, minutes = 10, pin = null, porn = true, youtube = true)
        val s = vm.await { !it.needsSetup }
        assertEquals(LockMode.PAUSE, s.lock.mode)
        assertTrue(s.categories.first { it.bundledList == "youtube-fun" }.category.enabled)
    }

    @Test
    fun `toggle off while locked is refused with message`() {
        val vm = vm()
        vm.await { it.categories.isNotEmpty() }
        vm.completeSetup(LockMode.PAUSE, minutes = 20, pin = null, porn = true, youtube = false)
        vm.await { !it.needsSetup }
        val id = vm.state.value.categories.first { it.bundledList == "adult" }.category.id
        vm.toggleCategory(id, false)
        vm.await { it.unlockRequested }
        assertTrue(runBlocking { repo.get(id)!!.enabled })
    }

    @Test
    fun `partner pin opens changes`() {
        val vm = vm()
        vm.await { it.categories.isNotEmpty() }
        vm.completeSetup(LockMode.PARTNER, minutes = 20, pin = "4321", porn = true, youtube = false)
        vm.await { !it.needsSetup }
        vm.submitPin("0000")
        assertEquals("Неверный PIN", vm.await { it.pinError != null }.pinError)
        vm.submitPin("4321")
        vm.await { it.lockOpen }
        val id = vm.state.value.categories.first { it.bundledList == "adult" }.category.id
        vm.toggleCategory(id, false)
        vm.await { s -> s.categories.first { it.bundledList == "adult" }.category.enabled.not() }
    }
}
