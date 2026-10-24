package app.tabguard.data

import app.tabguard.core.model.Category
import app.tabguard.core.lock.Lock
import app.tabguard.core.match.LocalMatcher
import app.tabguard.core.model.VerdictSource
import app.tabguard.core.support.PromptKind
import app.tabguard.core.text.extractHost
import app.tabguard.core.text.normalize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CategoryWithCount(val category: Category, val bundledList: String?, val blockCount: Int)

class CategoryRepository(private val db: AppDatabase, private val lists: BundledLists) {
    private val dao = db.categories()

    fun observeWithCounts(): Flow<List<CategoryWithCount>> = dao.observeWithCounts().map { rows ->
        rows.map { CategoryWithCount(it.category.toModel(), it.category.bundledList, it.blockCount) }
    }

    /** Enabled categories with bundled list domains and built-in keywords merged in, ready for matching. */
    suspend fun enabledCategories(): List<Category> = dao.enabled().map { e ->
        val own = e.toModel()
        val bundled = e.bundledList?.let(lists::domains).orEmpty()
        val builtInKeywords = e.bundledList?.let(lists::keywords).orEmpty()
        own.copy(
            domains = if (own.domains.isEmpty()) bundled else own.domains + bundled,
            keywords = (own.keywords + builtInKeywords).distinct(),
        )
    }

    suspend fun get(id: Long): Category? = dao.get(id)?.toModel()

    /** `prefixesText`/`onlyHostsText` = null keeps the stored value. */
    suspend fun save(
        id: Long?,
        name: String,
        keywordsText: String,
        domainsText: String,
        prefixesText: String? = null,
        onlyHostsText: String? = null,
    ): Long {
        val keywords = parseList(keywordsText).joinToString("\n")
        val domains = parseList(domainsText).mapNotNull(::extractHost).distinct().joinToString("\n")
        val prefixes = prefixesText?.let { t ->
            t.split('\n', ',').map(LocalMatcher::normalizeForPrefix).filter { it.isNotEmpty() }.distinct().joinToString("\n")
        }
        val hosts = onlyHostsText?.let { t -> parseList(t).mapNotNull(::extractHost).distinct().joinToString("\n") }
        val existing = id?.let { dao.get(it) }
        return if (existing == null) {
            dao.insert(
                CategoryEntity(
                    name = name.trim(), enabled = true, keywords = keywords, domains = domains, bundledList = null,
                    urlPrefixes = prefixes.orEmpty(), onlyHosts = hosts.orEmpty(),
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    name = name.trim(), keywords = keywords, domains = domains,
                    urlPrefixes = prefixes ?: existing.urlPrefixes, onlyHosts = hosts ?: existing.onlyHosts,
                ),
            )
            existing.id
        }
    }

    /** Whether [save] with these inputs (or deleting, when [deleting]) would weaken protection. */
    suspend fun wouldWeaken(
        id: Long,
        keywordsText: String? = null,
        domainsText: String? = null,
        prefixesText: String? = null,
        onlyHostsText: String? = null,
        enabled: Boolean? = null,
        deleting: Boolean = false,
    ): Boolean {
        val before = get(id) ?: return false
        if (deleting) return Lock.isWeakening(before, null)
        val after = before.copy(
            enabled = enabled ?: before.enabled,
            keywords = keywordsText?.let(::parseList) ?: before.keywords,
            domains = domainsText?.let { t -> parseList(t).mapNotNull(::extractHost).toSet() } ?: before.domains,
            urlPrefixes = prefixesText?.let { t ->
                t.split('\n', ',').map(LocalMatcher::normalizeForPrefix).filter { it.isNotEmpty() }.distinct()
            } ?: before.urlPrefixes,
            onlyHosts = onlyHostsText?.let { t -> parseList(t).mapNotNull(::extractHost).toSet() } ?: before.onlyHosts,
        )
        return Lock.isWeakening(before, after)
    }

    /** Which supportive text a block in this category uses. */
    suspend fun promptKind(id: Long): PromptKind =
        if (dao.get(id)?.bundledList == "youtube-fun") PromptKind.YOUTUBE else PromptKind.ADULT

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun recordBlock(categoryId: Long, source: VerdictSource, at: Long) =
        db.events().insert(BlockEventEntity(categoryId = categoryId, timestamp = at, source = source.name))

    private fun CategoryEntity.toModel() = Category(
        id = id,
        name = name,
        enabled = enabled,
        keywords = keywords.lines().filter { it.isNotEmpty() },
        domains = domains.lines().filter { it.isNotEmpty() }.toSet(),
        urlPrefixes = urlPrefixes.lines().filter { it.isNotEmpty() },
        onlyHosts = onlyHosts.lines().filter { it.isNotEmpty() }.toSet(),
    )

    private fun parseList(text: String): List<String> =
        text.split('\n', ',').map(::normalize).filter { it.isNotEmpty() }.distinct()
}
