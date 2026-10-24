package app.tabguard.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds a database exactly as version 1 of the app created it, then opens it with the current
 * Room database: Room runs MIGRATION_1_2 and validates the resulting schema against the entities.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `migration keeps categories and adds disabled youtube`() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `keywords` TEXT NOT NULL, `domains` TEXT NOT NULL, `bundledList` TEXT)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `block_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_events_categoryId` ON `block_events` (`categoryId`)")
            db.execSQL("INSERT INTO categories (name, enabled, keywords, domains, bundledList) VALUES ('Порно', 1, 'мое слово', 'x.com', 'adult')")
            db.execSQL("INSERT INTO categories (name, enabled, keywords, domains, bundledList) VALUES ('Казино', 0, 'casino', '', NULL)")
            db.execSQL("INSERT INTO block_events (categoryId, timestamp, source) VALUES (1, 1, 'DOMAIN')")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        val repo = CategoryRepository(room, BundledLists { throw java.io.FileNotFoundException(it) })
        val rows = repo.observeWithCounts().first()
        room.close()

        assertEquals(listOf("Порно", "Казино", "YouTube: развлечения"), rows.map { it.category.name })
        assertEquals(listOf(true, false, false), rows.map { it.category.enabled })
        assertEquals(listOf("мое слово"), rows[0].category.keywords)
        assertEquals(1, rows[0].blockCount)
        assertEquals(listOf("youtube.com/shorts/"), rows[2].category.urlPrefixes)
        assertEquals(emptyList<String>(), rows[0].category.urlPrefixes)
    }
}
