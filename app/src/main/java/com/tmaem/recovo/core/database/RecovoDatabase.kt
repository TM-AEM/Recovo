package com.tmaem.recovo.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tmaem.recovo.core.database.dao.BookmarkDao
import com.tmaem.recovo.core.database.dao.FolderDao
import com.tmaem.recovo.core.database.dao.RecordingDao
import com.tmaem.recovo.core.database.dao.TagDao
import com.tmaem.recovo.core.database.model.BookmarkEntity
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity

@Database(
    entities = [
        RecordingEntity::class,
        FolderEntity::class,
        TagEntity::class,
        RecordingTagCrossRef::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RecovoDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val DATABASE_NAME = "recovo.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recordings` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_isFavorite` ON `recordings` (`isFavorite`)")
            }
        }

        @Volatile
        private var INSTANCE: RecovoDatabase? = null

        fun getInstance(context: Context): RecovoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecovoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
