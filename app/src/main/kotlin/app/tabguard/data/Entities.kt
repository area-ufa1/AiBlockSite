package app.tabguard.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    /** One normalized keyword per line. */
    val keywords: String,
    /** One normalized host per line. */
    val domains: String,
    /** Name of a file in assets/blocklists, without extension. */
    val bundledList: String?,
    /** One normalized address prefix per line (`youtube.com/shorts/`). */
    @ColumnInfo(defaultValue = "") val urlPrefixes: String = "",
    /** One host per line; if any, this category's words apply only there. */
    @ColumnInfo(defaultValue = "") val onlyHosts: String = "",
)

@Entity(
    tableName = "block_events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val timestamp: Long,
    val source: String,
)

data class CategoryCountRow(
    @Embedded val category: CategoryEntity,
    val blockCount: Int,
)
