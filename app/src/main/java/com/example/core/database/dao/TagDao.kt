package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.model.RecordingTagCrossRef
import com.example.core.database.model.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: RecordingTagCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: RecordingTagCrossRef)

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN recording_tag_cross_ref rtc ON t.id = rtc.tagId
        WHERE rtc.recordingId = :recordingId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>>
}
