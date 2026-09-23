package com.example.data.repository

import com.example.core.database.dao.BookmarkDao
import com.example.core.database.dao.FolderDao
import com.example.core.database.dao.RecordingDao
import com.example.core.database.dao.TagDao
import com.example.core.database.model.BookmarkEntity
import com.example.core.database.model.FolderEntity
import com.example.core.database.model.RecordingEntity
import com.example.core.database.model.RecordingTagCrossRef
import com.example.core.database.model.TagEntity
import com.example.core.storage.StorageManager
import com.example.core.storage.StorageResult
import com.example.domain.model.SortOrder
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
            SortOrder.DURATION -> recordingDao.observeAllByDuration()
            SortOrder.SIZE -> recordingDao.observeAllBySize()
        }
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
        // Delete database row first
        recordingDao.delete(recording)

        // Then clean up file from storage if present
        if (recording.filePath.isNotBlank()) {
            storageManager.deleteFile(recording.filePath)
        } else {
            StorageResult.Success(true)
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

    override suspend fun updateFolder(folder: FolderEntity) = withContext(ioDispatcher) {
        folderDao.update(folder.copy(modifiedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteFolder(folder: FolderEntity) = withContext(ioDispatcher) {
        folderDao.delete(folder)
    }

    override fun observeTags(): Flow<List<TagEntity>> {
        return tagDao.observeAllTags()
    }

    override fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>> {
        return tagDao.observeTagsForRecording(recordingId)
    }

    override suspend fun createTag(name: String): Long = withContext(ioDispatcher) {
        tagDao.insert(
            TagEntity(
                name = name.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteTag(tag: TagEntity) = withContext(ioDispatcher) {
        tagDao.delete(tag)
    }

    override suspend fun addTagToRecording(recordingId: Long, tagId: Long) = withContext(ioDispatcher) {
        tagDao.insertCrossRef(RecordingTagCrossRef(recordingId = recordingId, tagId = tagId))
    }

    override suspend fun removeTagFromRecording(recordingId: Long, tagId: Long) = withContext(ioDispatcher) {
        tagDao.deleteCrossRef(RecordingTagCrossRef(recordingId = recordingId, tagId = tagId))
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
