package app.tabguard.data

import app.tabguard.core.model.Category
import app.tabguard.core.model.VerdictSource
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

    /** Enabled categories with bundled list domains merged in, ready for matching. */
    suspend fun enabledCategories(): List<Category> = dao.enabled().map { e ->
        val own = e.toModel()
        val bundled = e.bundledList?.let(lists::domains).orEmpty()
        own.copy(domains = if (own.domains.isEmpty()) bundled else own.domains + bundled)
    }

    suspend fun get(id: Long): Category? = dao.get(id)?.toModel()

    suspend fun save(id: Long?, name: String, keywordsText: String, domainsText: String): Long {
        val keywords = parseList(keywordsText).joinToString("\n")
        val domains = parseList(domainsText).mapNotNull(::extractHost).distinct().joinToString("\n")
        val existing = id?.let { dao.get(it) }
        return if (existing == null) {
            dao.insert(CategoryEntity(name = name.trim(), enabled = true, keywords = keywords, domains = domains, bundledList = null))
        } else {
            dao.update(existing.copy(name = name.trim(), keywords = keywords, domains = domains))
            existing.id
        }
    }

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
    )

    private fun parseList(text: String): List<String> =
        text.split('\n', ',').map(::normalize).filter { it.isNotEmpty() }.distinct()
}
