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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<RecordingTagCrossRef>)

    @Delete
    suspend fun deleteCrossRef(crossRef: RecordingTagCrossRef)

    @Query("SELECT * FROM recording_tag_cross_ref")
    fun observeAllCrossRefs(): Flow<List<RecordingTagCrossRef>>

    @Query("DELETE FROM recording_tag_cross_ref WHERE recordingId = :recordingId")
    suspend fun deleteCrossRefsForRecording(recordingId: Long)

    @Query("DELETE FROM recording_tag_cross_ref WHERE tagId = :tagId")
    suspend fun deleteCrossRefsForTag(tagId: Long)

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN recording_tag_cross_ref rtc ON t.id = rtc.tagId
        WHERE rtc.recordingId = :recordingId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN recording_tag_cross_ref rtc ON t.id = rtc.tagId
        WHERE rtc.recordingId = :recordingId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    suspend fun getTagsForRecording(recordingId: Long): List<TagEntity>
}
