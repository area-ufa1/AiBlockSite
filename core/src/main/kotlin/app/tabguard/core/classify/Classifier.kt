package app.tabguard.core.classify

import app.tabguard.core.match.LocalMatcher
import app.tabguard.core.model.Category
import app.tabguard.core.model.TabSnapshot
import app.tabguard.core.model.Verdict
import app.tabguard.core.model.key
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

fun interface Classifier {
    suspend fun classify(tab: TabSnapshot, categories: List<Category>): Verdict
}

/**
 * Allow-list → on-device match → (cached) remote classifier.
 * Only remote verdicts are cached, keyed by the page and the enabled categories,
 * so editing categories re-checks pages immediately. Remote calls run one at a time;
 * a call still waiting when a newer one arrives is dropped (the user has moved on).
 * Pages without a title are not sent to the remote classifier.
 */
class ClassifierChain(
    private val remote: () -> Classifier?,
    private val cacheSize: Int = 500,
) : Classifier {
    private val mutex = Mutex()
    private val remoteLock = Mutex()
    private val latestTicket = AtomicLong()
    private val cache = object : LinkedHashMap<String, Verdict>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Verdict>?) = size > cacheSize
    }

    override suspend fun classify(tab: TabSnapshot, categories: List<Category>): Verdict {
        if (LocalMatcher.isAllowlisted(tab)) return Verdict.Allow
        LocalMatcher.match(tab, categories)?.let { return it }
        val enabled = categories.filter { it.enabled }
        val classifier = remote()
        if (classifier == null || enabled.isEmpty() || tab.title.isNullOrBlank()) return Verdict.Allow

        val cacheKey = "${tab.key}#${enabled.hashCode()}"
        mutex.withLock { cache[cacheKey] }?.let { return it }
        val ticket = latestTicket.incrementAndGet()
        return remoteLock.withLock {
            // Superseded by a newer page while waiting: not cached, nothing to do.
            if (ticket != latestTicket.get()) return@withLock Verdict.Allow
            val verdict = try {
                classifier.classify(tab, enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Verdict.Unknown(e.message ?: "error")
            }
            if (verdict !is Verdict.Unknown) mutex.withLock { cache[cacheKey] = verdict }
            verdict
        }
    }
}
