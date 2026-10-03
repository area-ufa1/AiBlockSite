package app.tabguard.data

import app.tabguard.core.model.Category
import app.tabguard.core.model.VerdictSource
import kotlinx.coroutines.flow.Flow

data class CategoryWithCount(val category: Category, val bundledList: String?, val blockCount: Int)

class CategoryRepository(private val db: AppDatabase, private val lists: BundledLists) {
    fun observeWithCounts(): Flow<List<CategoryWithCount>> = TODO()
    suspend fun enabledCategories(): List<Category> = TODO()
    suspend fun get(id: Long): Category? = TODO()
    suspend fun save(id: Long?, name: String, keywordsText: String, domainsText: String): Long = TODO()
    suspend fun setEnabled(id: Long, enabled: Boolean): Unit = TODO()
    suspend fun delete(id: Long): Unit = TODO()
    suspend fun recordBlock(categoryId: Long, source: VerdictSource, at: Long): Unit = TODO()
}
