package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.database.dao.BookmarkDao
import com.example.core.database.dao.FolderDao
import com.example.core.database.dao.RecordingDao
import com.example.core.database.dao.TagDao
import com.example.core.database.model.BookmarkEntity
import com.example.core.database.model.FolderEntity
import com.example.core.database.model.RecordingEntity
import com.example.core.database.model.RecordingTagCrossRef
import com.example.core.database.model.TagEntity

@Database(
    entities = [
        RecordingEntity::class,
        FolderEntity::class,
        TagEntity::class,
        RecordingTagCrossRef::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
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
