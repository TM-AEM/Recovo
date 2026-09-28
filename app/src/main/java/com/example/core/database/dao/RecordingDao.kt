package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.model.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAllNewest(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAt ASC")
    fun observeAllOldest(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAllByName(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY displayName COLLATE NOCASE DESC")
    fun observeAllByNameDesc(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY durationMs DESC")
    fun observeAllByDuration(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY fileSizeBytes DESC")
    fun observeAllBySize(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE displayName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<RecordingEntity>>

    @Query("SELECT COUNT(*) FROM recordings")
    fun observeRecordingCount(): Flow<Int>

    @Query("UPDATE recordings SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE recordings SET isFavorite = :isFavorite WHERE id IN (:recordingIds)")
    suspend fun updateFavoriteStatus(recordingIds: List<Long>, isFavorite: Boolean)

    @Query("UPDATE recordings SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE recordings SET folderId = :folderId WHERE id IN (:recordingIds)")
    suspend fun updateFolderForRecordings(recordingIds: List<Long>, folderId: Long?)

    @Query("UPDATE recordings SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderIdForRecordings(folderId: Long)

    @Query("UPDATE recordings SET displayName = :displayName, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun renameRecording(id: Long, displayName: String, modifiedAt: Long)
}
