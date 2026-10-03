package app.tabguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CategoryEntity::class, BlockEventEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun events(): BlockEventDao

    companion object {
        val SEED_KEYWORDS = listOf("порно", "porn", "xxx", "секс", "sex", "хентай", "hentai", "nsfw", "onlyfans", "эротика")

        fun build(context: Context, inMemory: Boolean = false): AppDatabase = TODO()
    }
}
