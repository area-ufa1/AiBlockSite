package app.tabguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CategoryEntity::class, BlockEventEntity::class], version = 2, exportSchema = true)
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
            return builder.addCallback(SeedCallback).addMigrations(MIGRATION_1_2).build()
        }

        private const val YOUTUBE_NAME = "YouTube: развлечения"
        private const val YOUTUBE_PREFIXES = "youtube.com/shorts/"
        private const val YOUTUBE_HOSTS = "youtube.com\nyoutu.be"

        private fun insertYoutube(db: SupportSQLiteDatabase) = db.execSQL(
            "INSERT INTO categories (name, enabled, keywords, domains, bundledList, urlPrefixes, onlyHosts) " +
                "VALUES (?, 0, '', '', 'youtube-fun', ?, ?)",
            arrayOf<Any>(YOUTUBE_NAME, YOUTUBE_PREFIXES, YOUTUBE_HOSTS),
        )

        /** v2: address prefixes and host-scoped words; adds the YouTube category, disabled. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN urlPrefixes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN onlyHosts TEXT NOT NULL DEFAULT ''")
                insertYoutube(db)
            }
        }
    }

    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO categories (name, enabled, keywords, domains, bundledList) VALUES (?, 1, ?, '', 'adult')",
                arrayOf<Any>("Порно", SEED_KEYWORDS.joinToString("\n")),
            )
            insertYoutube(db)
        }
    }
}
