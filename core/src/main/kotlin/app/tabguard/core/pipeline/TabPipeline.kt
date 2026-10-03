package app.tabguard.core.pipeline

import app.tabguard.core.classify.Classifier
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces tab events, skips repeats of the page that was just handled and classifies
 * in a separate coroutine, so a slow classification is not cancelled by the next tab.
 */
class TabPipeline(
    private val scope: CoroutineScope,
    private val classifier: Classifier,
    private val categories: suspend () -> List<Category>,
    private val onBlock: suspend (TabSnapshot, Verdict.Block) -> Unit,
    private val onUnknown: (String) -> Unit = {},
    private val debounceMs: Long = 500,
) {
    private val lock = Any()
    private var pending: Job? = null
    private var lastKey: String? = null

    fun submit(tab: TabSnapshot) {
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(debounceMs)
                synchronized(lock) {
                    if (tab.key == lastKey) return@launch
                    lastKey = tab.key
                }
                scope.launch { handle(tab) }
            }
        }
    }

    private suspend fun handle(tab: TabSnapshot) {
        when (val verdict = classifier.classify(tab, categories())) {
            is Verdict.Block -> onBlock(tab, verdict)
            is Verdict.Unknown -> onUnknown(verdict.reason)
            Verdict.Allow -> Unit
        }
    }
}
