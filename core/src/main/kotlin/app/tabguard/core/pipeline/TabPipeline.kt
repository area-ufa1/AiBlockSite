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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Debounces tab events, skips repeats of the page that was just handled and classifies
 * in a separate coroutine, so a slow classification is not cancelled by the next tab.
 * A blocked page is blocked again when the user returns to it (e.g. with Back), except
 * immediately repeated within [BLOCK_COOLDOWN_MS] of the previous block, so one visit redirects only once.
 */
class TabPipeline(
    private val scope: CoroutineScope,
    private val classifier: Classifier,
    private val categories: suspend () -> List<Category>,
    private val onBlock: suspend (TabSnapshot, Verdict.Block) -> Unit,
    private val onUnknown: (String) -> Unit = {},
    private val debounceMs: Long = 500,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var pending: Job? = null
    private var lastKey: String? = null
    private var lastBlockKey: String? = null
    private var lastBlockAt = 0L

    fun submit(tab: TabSnapshot) {
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(debounceMs)
                synchronized(lock) {
                    val justBlocked = tab.key == lastBlockKey && now() - lastBlockAt < BLOCK_COOLDOWN_MS
                    if (tab.key == lastKey || justBlocked) return@launch
                    lastKey = tab.key
                    if (tab.key != lastBlockKey) lastBlockKey = null
                }
                scope.launch { handle(tab) }
            }
        }
    }

    private suspend fun handle(tab: TabSnapshot) {
        try {
            when (val verdict = classifier.classify(tab, categories())) {
                is Verdict.Block -> {
                    synchronized(lock) {
                        lastBlockKey = tab.key
                        lastBlockAt = now()
                        if (lastKey == tab.key) lastKey = null
                    }
                    onBlock(tab, verdict)
                }
                is Verdict.Unknown -> onUnknown(verdict.reason)
                Verdict.Allow -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onUnknown("error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val BLOCK_COOLDOWN_MS = 2_000L
    }
}
