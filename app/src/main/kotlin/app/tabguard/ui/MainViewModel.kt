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
    val state: StateFlow<MainState> = MutableStateFlow(MainState())
    fun refresh(): Unit = TODO()
    fun toggleCategory(id: Long, enabled: Boolean): Unit = TODO()
    fun saveCategory(id: Long?, name: String, keywords: String, domains: String): Unit = TODO()
    fun deleteCategory(id: Long): Unit = TODO()
    fun setClaudeEnabled(v: Boolean): Unit = TODO()
    fun testClaude(): Unit = TODO()
}
