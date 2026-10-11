package com.tmaem.recovo.data.repository

import com.tmaem.recovo.core.database.dao.BookmarkDao
import com.tmaem.recovo.core.database.dao.FolderDao
import com.tmaem.recovo.core.database.dao.RecordingDao
import com.tmaem.recovo.core.database.dao.TagDao
import com.tmaem.recovo.core.database.model.BookmarkEntity
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.core.storage.StorageErrorType
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.domain.model.SortOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecordingRepositoryImpl(
    private val recordingDao: RecordingDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val bookmarkDao: BookmarkDao,
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecordingRepository {

    override fun observeRecordings(sortOrder: SortOrder): Flow<List<RecordingEntity>> {
        return when (sortOrder) {
            SortOrder.NEWEST -> recordingDao.observeAllNewest()
            SortOrder.OLDEST -> recordingDao.observeAllOldest()
            SortOrder.NAME -> recordingDao.observeAllByName()
            SortOrder.NAME_DESC -> recordingDao.observeAllByNameDesc()
            SortOrder.DURATION -> recordingDao.observeAllByDuration()
            SortOrder.SIZE -> recordingDao.observeAllBySize()
        }
    }

    override fun observeFavorites(): Flow<List<RecordingEntity>> {
        return recordingDao.observeFavorites()
    }

    override fun searchRecordings(query: String): Flow<List<RecordingEntity>> {
        return recordingDao.searchRecordings(query.trim())
    }

    override fun observeRecordingById(id: Long): Flow<RecordingEntity?> {
        return recordingDao.observeById(id)
    }

    override fun observeRecordingsByFolder(folderId: Long): Flow<List<RecordingEntity>> {
        return recordingDao.observeByFolder(folderId)
    }

    override fun observeRecordingCount(): Flow<Int> {
        return recordingDao.observeRecordingCount()
    }

    override suspend fun getRecordingById(id: Long): RecordingEntity? = withContext(ioDispatcher) {
        recordingDao.getById(id)
    }

    override suspend fun insertRecording(recording: RecordingEntity): Long = withContext(ioDispatcher) {
        recordingDao.insert(recording)
    }

    override suspend fun updateRecording(recording: RecordingEntity) = withContext(ioDispatcher) {
        recordingDao.update(recording)
    }

    override suspend fun deleteRecording(recording: RecordingEntity): StorageResult<Boolean> = withContext(ioDispatcher) {
        // Remove the physical file first. If it cannot be removed (IO/security failure), keep the
        // Room row so the Library never silently loses the file↔metadata relationship, and surface
        // the failure so the UI cannot report a false success.
        val fileResult: StorageResult<Boolean> = if (recording.filePath.isNotBlank()) {
            val result = storageManager.deleteFile(recording.filePath)
            // A file that is already absent is treated as successfully deleted
            if (result is StorageResult.Error && result.errorType == StorageErrorType.FILE_NOT_FOUND) {
                StorageResult.Success(true)
            } else {
                result
            }
        } else {
            StorageResult.Success(true)
        }

        if (fileResult is StorageResult.Error) {
            return@withContext fileResult
        }

        recordingDao.delete(recording)
        StorageResult.Success(true)
    }

    override suspend fun deleteRecordings(recordings: List<RecordingEntity>): List<StorageResult<Boolean>> = withContext(ioDispatcher) {
        recordings.map { deleteRecording(it) }
    }

    override suspend fun toggleFavorite(recordingId: Long, isFavorite: Boolean) = withContext(ioDispatcher) {
        recordingDao.setFavorite(recordingId, isFavorite)
    }

    override suspend fun setFavorites(recordingIds: List<Long>, isFavorite: Boolean) = withContext(ioDispatcher) {
        if (recordingIds.isNotEmpty()) {
            recordingDao.updateFavoriteStatus(recordingIds, isFavorite)
        }
    }

    override suspend fun renameRecording(recordingId: Long, newName: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Recording name cannot be blank"))
        }
        if (trimmed.length > 120) {
            return@withContext Result.failure(IllegalArgumentException("Recording name too long"))
        }
        recordingDao.renameRecording(recordingId, trimmed, System.currentTimeMillis())
        Result.success(Unit)
    }

    override suspend fun moveRecordingToFolder(recordingId: Long, folderId: Long?) = withContext(ioDispatcher) {
        recordingDao.setFolder(recordingId, folderId)
    }

    override suspend fun moveRecordingsToFolder(recordingIds: List<Long>, folderId: Long?) = withContext(ioDispatcher) {
        if (recordingIds.isNotEmpty()) {
            recordingDao.updateFolderForRecordings(recordingIds, folderId)
        }
    }

    override fun observeFolders(): Flow<List<FolderEntity>> {
        return folderDao.observeAllFolders()
    }

    override suspend fun getFolderById(id: Long): FolderEntity? = withContext(ioDispatcher) {
        folderDao.getById(id)
    }

    override suspend fun createFolder(name: String): Long = withContext(ioDispatcher) {
        folderDao.insert(
            FolderEntity(
                name = name.trim(),
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun createFolderSafely(name: String): Result<Long> = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be blank"))
        }
        if (trimmed.length > 50) {
            return@withContext Result.failure(IllegalArgumentException("Folder name must not exceed 50 characters"))
        }
        val existing = folderDao.getFolderByName(trimmed)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("A folder named \"$trimmed\" already exists"))
        }
        val id = folderDao.insert(
            FolderEntity(
                name = trimmed,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        )
        if (id > 0) Result.success(id) else Result.failure(IllegalStateException("Failed to create folder"))
    }

    override suspend fun updateFolder(folder: FolderEntity) = withContext(ioDispatcher) {
        folderDao.update(folder.copy(modifiedAt = System.currentTimeMillis()))
    }

    override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be blank"))
        }
        if (trimmed.length > 50) {
            return@withContext Result.failure(IllegalArgumentException("Folder name must not exceed 50 characters"))
        }
        val existing = folderDao.getFolderByName(trimmed)
        if (existing != null && existing.id != folderId) {
            return@withContext Result.failure(IllegalArgumentException("A folder named \"$trimmed\" already exists"))
        }
        folderDao.renameFolder(folderId, trimmed, System.currentTimeMillis())
        Result.success(Unit)
    }

    override suspend fun deleteFolder(folder: FolderEntity) = withContext(ioDispatcher) {
        // Safe folder deletion: clear folderId on all recordings in this folder first,
        // ensuring NO recordings or audio files are ever deleted
        recordingDao.clearFolderIdForRecordings(folder.id)
        folderDao.delete(folder)
    }

    override fun observeTags(): Flow<List<TagEntity>> {
        return tagDao.observeAllTags()
    }

    override fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>> {
        return tagDao.observeTagsForRecording(recordingId)
    }

    override fun observeAllCrossRefs(): Flow<List<RecordingTagCrossRef>> {
        return tagDao.observeAllCrossRefs()
    }

    override suspend fun createTag(name: String): Long = withContext(ioDispatcher) {
        tagDao.insert(
            TagEntity(
                name = name.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun createTagSafely(name: String): Result<Long> = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag name cannot be blank"))
        }
        if (trimmed.length > 30) {
            return@withContext Result.failure(IllegalArgumentException("Tag name must not exceed 30 characters"))
        }
        val existing = tagDao.getTagByName(trimmed)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("A tag named \"$trimmed\" already exists"))
        }
        val id = tagDao.insert(
            TagEntity(
                name = trimmed,
                createdAt = System.currentTimeMillis()
            )
        )
        if (id > 0) Result.success(id) else Result.failure(IllegalStateException("Failed to create tag"))
    }

    override suspend fun renameTag(tagId: Long, newName: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag name cannot be blank"))
        }
        if (trimmed.length > 30) {
            return@withContext Result.failure(IllegalArgumentException("Tag name must not exceed 30 characters"))
        }
        val existing = tagDao.getTagByName(trimmed)
        if (existing != null && existing.id != tagId) {
            return@withContext Result.failure(IllegalArgumentException("A tag named \"$trimmed\" already exists"))
        }
        tagDao.renameTag(tagId, trimmed)
        Result.success(Unit)
    }

    override suspend fun deleteTag(tag: TagEntity) = withContext(ioDispatcher) {
        // Safe tag deletion: remove relationships only, never delete recordings
        tagDao.deleteCrossRefsForTag(tag.id)
        tagDao.delete(tag)
    }

    override suspend fun addTagToRecording(recordingId: Long, tagId: Long) = withContext(ioDispatcher) {
        tagDao.insertCrossRef(RecordingTagCrossRef(recordingId = recordingId, tagId = tagId))
    }

    override suspend fun removeTagFromRecording(recordingId: Long, tagId: Long) = withContext(ioDispatcher) {
        tagDao.deleteCrossRef(RecordingTagCrossRef(recordingId = recordingId, tagId = tagId))
    }

    override suspend fun setTagsForRecording(recordingId: Long, tagIds: Set<Long>) = withContext(ioDispatcher) {
        tagDao.deleteCrossRefsForRecording(recordingId)
        if (tagIds.isNotEmpty()) {
            val refs = tagIds.map { RecordingTagCrossRef(recordingId = recordingId, tagId = it) }
            tagDao.insertCrossRefs(refs)
        }
    }

    override fun observeBookmarks(recordingId: Long): Flow<List<BookmarkEntity>> {
        return bookmarkDao.observeBookmarksForRecording(recordingId)
    }

    override suspend fun getBookmarks(recordingId: Long): List<BookmarkEntity> = withContext(ioDispatcher) {
        bookmarkDao.getBookmarksForRecording(recordingId)
    }

    override suspend fun addBookmark(recordingId: Long, positionMs: Long, label: String): Long = withContext(ioDispatcher) {
        bookmarkDao.insert(
            BookmarkEntity(
                recordingId = recordingId,
                positionMs = positionMs,
                label = label.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun updateBookmark(bookmark: BookmarkEntity) = withContext(ioDispatcher) {
        bookmarkDao.update(bookmark)
    }

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) = withContext(ioDispatcher) {
        bookmarkDao.delete(bookmark)
    }
}
