package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class RecovoDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val DATABASE_NAME = "recovo.db"

        @Volatile
        private var INSTANCE: RecovoDatabase? = null

        fun getInstance(context: Context): RecovoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecovoDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
