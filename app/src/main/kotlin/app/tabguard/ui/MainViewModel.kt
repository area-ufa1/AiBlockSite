package app.tabguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.data.CategoryRepository
import app.tabguard.data.CategoryWithCount
import app.tabguard.data.SettingsStore
import app.tabguard.termux.TermuxClaudeClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import app.tabguard.core.lock.Lock
import app.tabguard.core.lock.LockMode
import app.tabguard.core.lock.LockState
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ServiceStatus {
    /** Off in system settings. */
    OFF,
    /** On in settings but Android did not start it (happens after app updates on some firmwares). */
    STUCK,
    RUNNING,
}

data class MainState(
    val categories: List<CategoryWithCount> = emptyList(),
    val serviceStatus: ServiceStatus = ServiceStatus.OFF,
    val termuxInstalled: Boolean = false,
    val claudeEnabled: Boolean = false,
    val claudeStatus: String? = null,
    val lock: LockState = LockState(),
    /** Weakening changes are allowed right now. */
    val lockOpen: Boolean = true,
    val countdownLeftMs: Long? = null,
    /** The first-run wizard must be shown: no protection mode chosen yet. */
    val needsSetup: Boolean = false,
    /** A weakening change was refused: show the unlock dialog. */
    val unlockRequested: Boolean = false,
    val pinError: String? = null,
)

class MainViewModel(
    private val repo: CategoryRepository,
    private val settings: SettingsStore,
    private val termux: TermuxClaudeClassifier,
    private val isServiceEnabled: () -> Boolean,
    private val isServiceRunning: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val serviceStatus = MutableStateFlow(currentServiceStatus())
    private val termuxInstalled = MutableStateFlow(termux.isTermuxInstalled())
    private val testStatus = MutableStateFlow<String?>(null)
    private val now = MutableStateFlow(clock())
    private val unlockRequested = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)
    private val lockFlow = combine(settings.lock, now) { lock, t -> Lock.settle(lock, t) to t }

    val state: StateFlow<MainState> = combine(
        repo.observeWithCounts(),
        settings.claudeEnabled,
        settings.lastClaudeError,
        combine(serviceStatus, termuxInstalled, ::Pair),
        combine(testStatus, lockFlow, unlockRequested, pinError) { a, b, c, d -> listOf(a, b, c, d) },
    ) { categories, claudeEnabled, lastError, (service, installed), rest ->
        @Suppress("UNCHECKED_CAST")
        val (lock, t) = rest[1] as Pair<LockState, Long>
        val open = Lock.isUnlocked(lock, t)
        MainState(
            categories = categories,
            serviceStatus = service,
            termuxInstalled = installed,
            claudeEnabled = claudeEnabled && installed,
            claudeStatus = (rest[0] as String?) ?: lastError?.let { "Последняя ошибка: $it" },
            lock = lock,
            lockOpen = open,
            countdownLeftMs = Lock.remainingCountdownMs(lock, t),
            needsSetup = lock.mode == null,
            unlockRequested = (rest[2] as Boolean) && !open,
            pinError = rest[3] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    /** Moves the clock the protection state is evaluated against; the UI calls it every second. */
    fun tick() {
        now.value = clock()
    }

    private suspend fun currentLock(): LockState = Lock.settle(settings.lock.first(), clock())

    /** Runs [change] unless it weakens protection while changes are closed; then asks to unlock. */
    private fun guarded(weakens: suspend () -> Boolean, change: suspend () -> Unit) {
        viewModelScope.launch {
            if (weakens() && !Lock.isUnlocked(currentLock(), clock())) {
                unlockRequested.value = true
                tick()
            } else {
                change()
            }
        }
    }

    fun requestUnlock() {
        unlockRequested.value = true
        tick()
    }

    fun dismissUnlock() {
        unlockRequested.value = false
        pinError.value = null
    }

    fun startCountdown() {
        viewModelScope.launch {
            settings.saveLock(Lock.startCountdown(currentLock(), clock()))
            tick()
        }
    }

    fun cancelCountdown() {
        viewModelScope.launch { settings.saveLock(Lock.cancelCountdown(currentLock())) }
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            val t = clock()
            val result = Lock.tryPin(currentLock(), pin, t)
            settings.saveLock(result.state)
            pinError.value = when {
                result.ok -> null
                t < result.state.pinLockedUntil -> "Слишком много попыток. Попробуйте через ${(result.state.pinLockedUntil - t + 59_999) / 60_000} мин"
                else -> "Неверный PIN"
            }
            if (result.ok) unlockRequested.value = false
            tick()
        }
    }

    /** First-run wizard (also used to change the mode while changes are open). */
    fun completeSetup(mode: LockMode, minutes: Int, pin: String?, porn: Boolean, youtube: Boolean) {
        viewModelScope.launch {
            val lock = currentLock()
            if (!Lock.isUnlocked(lock, clock())) return@launch
            val next = when (mode) {
                LockMode.PAUSE -> Lock.setPauseMode(lock, minutes)
                LockMode.PARTNER -> Lock.setPartnerMode(lock, pin.orEmpty(), randomSalt())
            }
            repo.observeWithCounts().first().forEach { row ->
                when (row.bundledList) {
                    "adult" -> repo.setEnabled(row.category.id, porn)
                    "youtube-fun" -> repo.setEnabled(row.category.id, youtube)
                }
            }
            settings.saveLock(next)
            tick()
        }
    }

    private fun randomSalt(): String =
        ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Re-checks things the user may have changed in system settings. */
    fun refresh() {
        serviceStatus.value = currentServiceStatus()
        termuxInstalled.value = termux.isTermuxInstalled()
    }

    private fun currentServiceStatus() = when {
        !isServiceEnabled() -> ServiceStatus.OFF
        !isServiceRunning() -> ServiceStatus.STUCK
        else -> ServiceStatus.RUNNING
    }

    fun toggleCategory(id: Long, enabled: Boolean) =
        guarded({ repo.wouldWeaken(id, enabled = enabled) }) { repo.setEnabled(id, enabled) }

    fun saveCategory(
        id: Long?,
        name: String,
        keywords: String,
        domains: String,
        prefixes: String? = null,
        onlyHosts: String? = null,
    ) {
        if (name.isBlank()) return
        guarded({ id != null && repo.wouldWeaken(id, keywords, domains, prefixes, onlyHosts) }) {
            repo.save(id, name, keywords, domains, prefixes, onlyHosts)
        }
    }

    fun deleteCategory(id: Long) = guarded({ repo.wouldWeaken(id, deleting = true) }) { repo.delete(id) }

    fun setClaudeEnabled(v: Boolean) {
        if (v && !termux.isTermuxInstalled()) return
        viewModelScope.launch { settings.setClaudeEnabled(v) }
    }

    fun testClaude() {
        viewModelScope.launch {
            testStatus.value = "Проверяю…"
            val categories = repo.enabledCategories().ifEmpty { listOf(Category(1, "Тест", true, listOf("тест"), emptySet())) }
            val start = clock()
            val verdict = termux.classify(TabSnapshot("test", "example.com", "Пример"), categories)
            val seconds = (clock() - start) / 1000.0
            testStatus.value = when (verdict) {
                is Verdict.Unknown -> "Ошибка: ${verdict.reason}"
                else -> "OK, ответ за %.1f с".format(seconds)
            }
            if (verdict !is Verdict.Unknown) settings.setLastClaudeError(null)
        }
    }
}
