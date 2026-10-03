package app.tabguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CategoryEntity::class, BlockEventEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun events(): BlockEventDao

    companion object {
        val SEED_KEYWORDS = listOf("порно", "porn", "xxx", "секс", "sex", "хентай", "hentai", "nsfw", "onlyfans", "эротика")

        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "tabguard.db")
            }
            return builder.addCallback(SeedCallback).build()
        }
    }

    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO categories (name, enabled, keywords, domains, bundledList) VALUES (?, 1, ?, '', 'adult')",
                arrayOf<Any>("Порно", SEED_KEYWORDS.joinToString("\n")),
            )
        }
    }
}
