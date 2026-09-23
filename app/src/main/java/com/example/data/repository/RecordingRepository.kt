package com.example.data.repository

import com.example.core.database.model.BookmarkEntity
import com.example.core.database.model.FolderEntity
import com.example.core.database.model.RecordingEntity
import com.example.core.database.model.TagEntity
import com.example.core.storage.StorageResult
import com.example.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    // Recordings
    fun observeRecordings(sortOrder: SortOrder = SortOrder.NEWEST): Flow<List<RecordingEntity>>
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>
    fun observeRecordingById(id: Long): Flow<RecordingEntity?>
    fun observeRecordingsByFolder(folderId: Long): Flow<List<RecordingEntity>>
    fun observeRecordingCount(): Flow<Int>
    suspend fun getRecordingById(id: Long): RecordingEntity?
    suspend fun insertRecording(recording: RecordingEntity): Long
    suspend fun updateRecording(recording: RecordingEntity)
    suspend fun deleteRecording(recording: RecordingEntity): StorageResult<Boolean>

    // Folders
    fun observeFolders(): Flow<List<FolderEntity>>
    suspend fun getFolderById(id: Long): FolderEntity?
    suspend fun createFolder(name: String): Long
    suspend fun updateFolder(folder: FolderEntity)
    suspend fun deleteFolder(folder: FolderEntity)

    // Tags
    fun observeTags(): Flow<List<TagEntity>>
    fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>>
    suspend fun createTag(name: String): Long
    suspend fun deleteTag(tag: TagEntity)
    suspend fun addTagToRecording(recordingId: Long, tagId: Long)
    suspend fun removeTagFromRecording(recordingId: Long, tagId: Long)

    // Bookmarks
    fun observeBookmarks(recordingId: Long): Flow<List<BookmarkEntity>>
    suspend fun getBookmarks(recordingId: Long): List<BookmarkEntity>
    suspend fun addBookmark(recordingId: Long, positionMs: Long, label: String): Long
    suspend fun updateBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
