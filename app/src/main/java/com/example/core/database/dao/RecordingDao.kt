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

    @Query("SELECT * FROM recordings ORDER BY durationMs DESC")
    fun observeAllByDuration(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY fileSizeBytes DESC")
    fun observeAllBySize(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE displayName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<RecordingEntity>>

    @Query("SELECT COUNT(*) FROM recordings")
    fun observeRecordingCount(): Flow<Int>
}
