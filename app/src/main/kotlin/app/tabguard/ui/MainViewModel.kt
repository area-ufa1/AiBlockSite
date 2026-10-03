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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainState(
    val categories: List<CategoryWithCount> = emptyList(),
    val serviceEnabled: Boolean = false,
    val termuxInstalled: Boolean = false,
    val claudeEnabled: Boolean = false,
    val claudeStatus: String? = null,
)

class MainViewModel(
    private val repo: CategoryRepository,
    private val settings: SettingsStore,
    private val termux: TermuxClaudeClassifier,
    private val isServiceEnabled: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val serviceEnabled = MutableStateFlow(isServiceEnabled())
    private val termuxInstalled = MutableStateFlow(termux.isTermuxInstalled())
    private val testStatus = MutableStateFlow<String?>(null)

    val state: StateFlow<MainState> = combine(
        repo.observeWithCounts(),
        settings.claudeEnabled,
        settings.lastClaudeError,
        combine(serviceEnabled, termuxInstalled, ::Pair),
        testStatus,
    ) { categories, claudeEnabled, lastError, (service, installed), status ->
        MainState(
            categories = categories,
            serviceEnabled = service,
            termuxInstalled = installed,
            claudeEnabled = claudeEnabled && installed,
            claudeStatus = status ?: lastError?.let { "Последняя ошибка: $it" },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    /** Re-checks things the user may have changed in system settings. */
    fun refresh() {
        serviceEnabled.value = isServiceEnabled()
        termuxInstalled.value = termux.isTermuxInstalled()
    }

    fun toggleCategory(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(id, enabled) }
    }

    fun saveCategory(id: Long?, name: String, keywords: String, domains: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.save(id, name, keywords, domains) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

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
