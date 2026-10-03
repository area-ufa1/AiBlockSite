package app.tabguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query(
        "SELECT c.*, (SELECT COUNT(*) FROM block_events e WHERE e.categoryId = c.id) AS blockCount " +
            "FROM categories c ORDER BY c.id",
    )
    fun observeWithCounts(): Flow<List<CategoryCountRow>>

    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Query("SELECT COUNT(*) FROM block_events")
    suspend fun count(): Int
}
