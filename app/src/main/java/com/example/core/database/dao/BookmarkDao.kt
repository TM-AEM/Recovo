package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.model.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY positionMs ASC")
    fun observeBookmarksForRecording(recordingId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY positionMs ASC")
    suspend fun getBookmarksForRecording(recordingId: Long): List<BookmarkEntity>
}
