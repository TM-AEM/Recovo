package com.tmaem.recovo.data.repository

import com.tmaem.recovo.core.database.model.BookmarkEntity
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    // Recordings
    fun observeRecordings(sortOrder: SortOrder = SortOrder.NEWEST): Flow<List<RecordingEntity>>
    fun observeFavorites(): Flow<List<RecordingEntity>>
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>
    fun observeRecordingById(id: Long): Flow<RecordingEntity?>
    fun observeRecordingsByFolder(folderId: Long): Flow<List<RecordingEntity>>
    fun observeRecordingCount(): Flow<Int>
    suspend fun getRecordingById(id: Long): RecordingEntity?
    suspend fun insertRecording(recording: RecordingEntity): Long
    suspend fun updateRecording(recording: RecordingEntity)
    suspend fun deleteRecording(recording: RecordingEntity): StorageResult<Boolean>
    suspend fun deleteRecordings(recordings: List<RecordingEntity>): List<StorageResult<Boolean>>
    suspend fun toggleFavorite(recordingId: Long, isFavorite: Boolean)
    suspend fun setFavorites(recordingIds: List<Long>, isFavorite: Boolean)
    suspend fun renameRecording(recordingId: Long, newName: String): Result<Unit>
    suspend fun moveRecordingToFolder(recordingId: Long, folderId: Long?)
    suspend fun moveRecordingsToFolder(recordingIds: List<Long>, folderId: Long?)

    // Folders
    fun observeFolders(): Flow<List<FolderEntity>>
    suspend fun getFolderById(id: Long): FolderEntity?
    suspend fun createFolder(name: String): Long
    suspend fun createFolderSafely(name: String): Result<Long>
    suspend fun updateFolder(folder: FolderEntity)
    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit>
    suspend fun deleteFolder(folder: FolderEntity)

    // Tags
    fun observeTags(): Flow<List<TagEntity>>
    fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>>
    fun observeAllCrossRefs(): Flow<List<RecordingTagCrossRef>>
    suspend fun createTag(name: String): Long
    suspend fun createTagSafely(name: String): Result<Long>
    suspend fun renameTag(tagId: Long, newName: String): Result<Unit>
    suspend fun deleteTag(tag: TagEntity)
    suspend fun addTagToRecording(recordingId: Long, tagId: Long)
    suspend fun removeTagFromRecording(recordingId: Long, tagId: Long)
    suspend fun setTagsForRecording(recordingId: Long, tagIds: Set<Long>)

    // Bookmarks
    fun observeBookmarks(recordingId: Long): Flow<List<BookmarkEntity>>
    suspend fun getBookmarks(recordingId: Long): List<BookmarkEntity>
    suspend fun addBookmark(recordingId: Long, positionMs: Long, label: String): Long
    suspend fun updateBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
