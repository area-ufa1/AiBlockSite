package app.tabguard

import android.app.Application
import app.tabguard.data.AppDatabase
import app.tabguard.data.BundledLists
import app.tabguard.data.CategoryRepository
import app.tabguard.data.SettingsStore
import app.tabguard.termux.TermuxClaudeClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container, one per process. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(app)
    val lists = BundledLists(app.assets)
    val repository = CategoryRepository(db, lists)
    val settings = SettingsStore(app)
    val termux = TermuxClaudeClassifier(app)
}
