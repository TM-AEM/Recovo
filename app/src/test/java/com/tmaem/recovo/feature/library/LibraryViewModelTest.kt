package com.tmaem.recovo.feature.library

import com.tmaem.recovo.core.database.model.BookmarkEntity
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.core.player.AudioPlayer
import com.tmaem.recovo.core.player.PlaybackState
import com.tmaem.recovo.core.player.RepeatMode
import com.tmaem.recovo.core.storage.StorageErrorType
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.data.repository.RecordingRepository
import com.tmaem.recovo.domain.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeRecordingRepository
    private lateinit var fakePlayer: FakeAudioPlayer
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeRecordingRepository()
        fakePlayer = FakeAudioPlayer()
        viewModel = LibraryViewModel(fakeRepository, fakePlayer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoadingAndEmptyState() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { it is LibraryUiState.Success }
        assertTrue(state is LibraryUiState.Success)
        val success = state as LibraryUiState.Success
        assertTrue(success.recordings.isEmpty())
    }

    @Test
    fun populatedRecordings_formatsAndSortsProperly() = runTest(testDispatcher) {
        val tempFile = File.createTempFile("test_rec", ".m4a")
        tempFile.writeText("audio data")

        val rec1 = RecordingEntity(
            id = 1,
            fileName = tempFile.name,
            displayName = "Meeting Notes",
            filePath = tempFile.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 65000L, // 01:05
            fileSizeBytes = 1024L * 500L, // ~500 KB
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 1000L
        )

        val rec2 = RecordingEntity(
            id = 2,
            fileName = "missing.m4a",
            displayName = "Quick Memo",
            filePath = "/non/existent/path/missing.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 12000L, // 00:12
            fileSizeBytes = 1024L * 1024L * 2L, // 2.0 MB
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 2000L
        )

        fakeRepository.setRecordings(listOf(rec2, rec1)) // Newest first

        val state = viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 }
        val success = state as LibraryUiState.Success
        assertEquals(2, success.recordings.size)

        // First item (rec2)
        val item1 = success.recordings[0]
        assertEquals("Quick Memo", item1.entity.displayName)
        assertFalse(item1.isFileAvailable)
        assertEquals("00:12", item1.formattedDuration)
        assertEquals("2.0 MB", item1.formattedSize)

        // Second item (rec1)
        val item2 = success.recordings[1]
        assertEquals("Meeting Notes", item2.entity.displayName)
        assertTrue(item2.isFileAvailable)
        assertEquals("01:05", item2.formattedDuration)
        assertEquals("500.0 KB", item2.formattedSize)

        tempFile.delete()
    }

    @Test
    fun playRecording_invokesPlayer() = runTest(testDispatcher) {
        val rec = RecordingEntity(
            id = 10,
            fileName = "rec.m4a",
            displayName = "Voice memo",
            filePath = "/path/rec.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        viewModel.playRecording(rec)
        assertEquals(rec, fakePlayer.lastPlayedRecording)
        assertTrue(fakePlayer.playbackState.value.isPlaying)
    }

    @Test
    fun togglePlayPause_whenPlaying_pausesPlayer() = runTest(testDispatcher) {
        val rec = RecordingEntity(
            id = 10,
            fileName = "rec.m4a",
            displayName = "Voice memo",
            filePath = "/path/rec.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        viewModel.playRecording(rec)
        assertTrue(viewModel.playbackState.value.isPlaying)

        viewModel.togglePlayPause()
        assertFalse(viewModel.playbackState.value.isPlaying)

        viewModel.togglePlayPause()
        assertTrue(viewModel.playbackState.value.isPlaying)
    }

    @Test
    fun playNextAndPrevious_navigatesList() = runTest(testDispatcher) {
        val rec1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "Memo 1",
            filePath = "/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 10000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 2000L
        )
        val rec2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "Memo 2",
            filePath = "/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 20000L,
            fileSizeBytes = 2000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 1000L
        )

        fakeRepository.setRecordings(listOf(rec1, rec2))
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 }

        // Start with rec1
        viewModel.playRecording(rec1)
        assertEquals(rec1, fakePlayer.lastPlayedRecording)

        // Advance to next
        viewModel.playNext()
        assertEquals(rec2, fakePlayer.lastPlayedRecording)

        // Advance to previous (when near start < 3000ms)
        fakePlayer.seekTo(1000L)
        viewModel.playPrevious()
        assertEquals(rec1, fakePlayer.lastPlayedRecording)
    }

    @Test
    fun deleteRecording_stopsPlaybackIfActiveAndDeletesFromRepo() = runTest(testDispatcher) {
        val rec = RecordingEntity(
            id = 99,
            fileName = "to_delete.m4a",
            displayName = "Delete Me",
            filePath = "/del.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        fakeRepository.setRecordings(listOf(rec))
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 1 }

        viewModel.playRecording(rec)
        assertTrue(fakePlayer.playbackState.value.isPlaying)

        viewModel.deleteRecording(rec)
        advanceUntilIdle()

        assertFalse(fakePlayer.playbackState.value.isPlaying)
        assertEquals(0, fakeRepository.recordingsFlow.value.size)
    }

    // --- PHASE 09 TESTS ---

    @Test
    fun search_caseInsensitiveAndNoResultsAndArabic() = runTest(testDispatcher) {
        val rec1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "Project Brainstorming",
            filePath = "/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val rec2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "تسجيل صوتي مهم",
            filePath = "/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val rec3 = RecordingEntity(
            id = 3,
            fileName = "3.m4a",
            displayName = "Grocery List",
            filePath = "/3.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        fakeRepository.setRecordings(listOf(rec1, rec2, rec3))

        // Initial state has 3 recordings
        val state1 = viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 3 } as LibraryUiState.Success
        assertEquals(3, state1.recordings.size)

        // Case-insensitive English search: "brain"
        viewModel.setSearchQuery("BRAIN")
        advanceTimeBy(300L)
        advanceUntilIdle()

        val searchResult1 = viewModel.uiState.first { it is LibraryUiState.Success && it.searchQuery == "BRAIN" } as LibraryUiState.Success
        assertEquals(1, searchResult1.recordings.size)
        assertEquals("Project Brainstorming", searchResult1.recordings[0].entity.displayName)

        // Arabic text search: "صوتي"
        viewModel.setSearchQuery("صوتي")
        advanceTimeBy(300L)
        advanceUntilIdle()

        val searchResult2 = viewModel.uiState.first { it is LibraryUiState.Success && it.searchQuery == "صوتي" } as LibraryUiState.Success
        assertEquals(1, searchResult2.recordings.size)
        assertEquals("تسجيل صوتي مهم", searchResult2.recordings[0].entity.displayName)

        // Search with no results
        viewModel.setSearchQuery("NonExistentTerm123")
        advanceTimeBy(300L)
        advanceUntilIdle()

        val noResults = viewModel.uiState.first { it is LibraryUiState.Success && it.searchQuery == "NonExistentTerm123" } as LibraryUiState.Success
        assertTrue(noResults.recordings.isEmpty())

        // Clear search
        viewModel.clearSearchQuery()
        advanceUntilIdle()
        val cleared = viewModel.uiState.first { it is LibraryUiState.Success && it.searchQuery.isEmpty() } as LibraryUiState.Success
        assertEquals(3, cleared.recordings.size)
    }

    @Test
    fun favorites_toggleAndFilterFavoritesTab() = runTest(testDispatcher) {
        val rec1 = RecordingEntity(
            id = 101,
            fileName = "101.m4a",
            displayName = "Voice memo 101",
            filePath = "/101.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            isFavorite = false
        )
        val rec2 = RecordingEntity(
            id = 102,
            fileName = "102.m4a",
            displayName = "Voice memo 102",
            filePath = "/102.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            isFavorite = true
        )

        fakeRepository.setRecordings(listOf(rec1, rec2))

        // Switch to Favorites tab
        viewModel.selectTab(LibraryTab.FAVORITES)
        advanceUntilIdle()

        val favState1 = viewModel.uiState.first { it is LibraryUiState.Success && it.selectedTab == LibraryTab.FAVORITES } as LibraryUiState.Success
        assertEquals(1, favState1.recordings.size)
        assertEquals("Voice memo 102", favState1.recordings[0].entity.displayName)

        // Toggle favorite on rec1 -> now both are favorites
        viewModel.toggleFavorite(rec1)
        advanceUntilIdle()

        val favState2 = viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 } as LibraryUiState.Success
        assertEquals(2, favState2.recordings.size)

        // Toggle favorite off on rec2
        val updatedRec2 = fakeRepository.recordingsFlow.value.first { it.id == 102L }
        viewModel.toggleFavorite(updatedRec2)
        advanceUntilIdle()

        val favState3 = viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 1 } as LibraryUiState.Success
        assertEquals(1, favState3.recordings.size)
        assertEquals("Voice memo 101", favState3.recordings[0].entity.displayName)
    }

    @Test
    fun folders_createRenameDeleteAndMoveRecordings() = runTest(testDispatcher) {
        var created = false
        viewModel.createFolder("Lectures") { success, _ -> created = success }
        advanceUntilIdle()
        assertTrue(created)
        assertEquals(1, fakeRepository.foldersFlow.value.size)
        assertEquals("Lectures", fakeRepository.foldersFlow.value[0].name)

        val folder = fakeRepository.foldersFlow.value[0]

        // Add recording and move to folder
        val rec = RecordingEntity(
            id = 200,
            fileName = "lec.m4a",
            displayName = "History Lecture",
            filePath = "/lec.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 2000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            folderId = null
        )
        fakeRepository.setRecordings(listOf(rec))

        viewModel.moveRecordingToFolder(rec, folder.id)
        advanceUntilIdle()
        assertEquals(folder.id, fakeRepository.recordingsFlow.value[0].folderId)

        // View folder recordings in Folders tab
        viewModel.selectTab(LibraryTab.FOLDERS)
        viewModel.selectFolder(folder)
        advanceUntilIdle()

        val folderState = viewModel.uiState.first { it is LibraryUiState.Success && it.selectedFolder?.id == folder.id } as LibraryUiState.Success
        assertEquals(1, folderState.recordings.size)
        assertEquals("History Lecture", folderState.recordings[0].entity.displayName)

        // Rename folder
        var renamed = false
        viewModel.renameFolder(folder, "University Lectures") { success, _ -> renamed = success }
        advanceUntilIdle()
        assertTrue(renamed)
        assertEquals("University Lectures", fakeRepository.foldersFlow.value[0].name)

        // Delete folder: must NOT delete recording, must clear folderId
        viewModel.deleteFolder(fakeRepository.foldersFlow.value[0])
        advanceUntilIdle()
        assertTrue(fakeRepository.foldersFlow.value.isEmpty())
        assertEquals(1, fakeRepository.recordingsFlow.value.size)
        assertNull(fakeRepository.recordingsFlow.value[0].folderId)
    }

    @Test
    fun tags_createRenameDeleteAttachDetachAndFilter() = runTest(testDispatcher) {
        var tagCreated = false
        viewModel.createTag("Work") { success, _ -> tagCreated = success }
        advanceUntilIdle()
        assertTrue(tagCreated)
        assertEquals(1, fakeRepository.tagsFlow.value.size)
        assertEquals("Work", fakeRepository.tagsFlow.value[0].name)

        val tag = fakeRepository.tagsFlow.value[0]

        val rec = RecordingEntity(
            id = 300,
            fileName = "work.m4a",
            displayName = "Daily Standup",
            filePath = "/work.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 3000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        fakeRepository.setRecordings(listOf(rec))

        // Attach tag to recording
        viewModel.setTagsForRecording(rec, setOf(tag.id))
        advanceUntilIdle()
        assertEquals(1, fakeRepository.crossRefsFlow.value.size)
        assertEquals(300L, fakeRepository.crossRefsFlow.value[0].recordingId)
        assertEquals(tag.id, fakeRepository.crossRefsFlow.value[0].tagId)

        // Filter by Tag
        viewModel.selectTab(LibraryTab.TAGS)
        viewModel.selectTag(tag)
        advanceUntilIdle()

        val tagState = viewModel.uiState.first { it is LibraryUiState.Success && it.selectedTag?.id == tag.id } as LibraryUiState.Success
        assertEquals(1, tagState.recordings.size)
        assertEquals("Daily Standup", tagState.recordings[0].entity.displayName)

        // Delete tag: removes tag and relationships, preserves recording
        viewModel.deleteTag(tag)
        advanceUntilIdle()
        assertTrue(fakeRepository.tagsFlow.value.isEmpty())
        assertTrue(fakeRepository.crossRefsFlow.value.isEmpty())
        assertEquals(1, fakeRepository.recordingsFlow.value.size)
    }

    @Test
    fun sorting_newestOldestNameAZNameZADurationSize() = runTest(testDispatcher) {
        val recA = RecordingEntity(
            id = 1,
            fileName = "a.m4a",
            displayName = "Alpha",
            filePath = "/a.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 10000L,
            fileSizeBytes = 500L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 1000L
        )
        val recZ = RecordingEntity(
            id = 2,
            fileName = "z.m4a",
            displayName = "Zulu",
            filePath = "/z.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 50000L,
            fileSizeBytes = 5000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 2000L
        )

        fakeRepository.setRecordings(listOf(recA, recZ))

        // NEWEST: Zulu, Alpha
        viewModel.setSortOrder(SortOrder.NEWEST)
        advanceUntilIdle()
        val newest = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.NEWEST } as LibraryUiState.Success
        assertEquals("Zulu", newest.recordings[0].entity.displayName)
        assertEquals("Alpha", newest.recordings[1].entity.displayName)

        // OLDEST: Alpha, Zulu
        viewModel.setSortOrder(SortOrder.OLDEST)
        advanceUntilIdle()
        val oldest = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.OLDEST } as LibraryUiState.Success
        assertEquals("Alpha", oldest.recordings[0].entity.displayName)
        assertEquals("Zulu", oldest.recordings[1].entity.displayName)

        // NAME (A-Z): Alpha, Zulu
        viewModel.setSortOrder(SortOrder.NAME)
        advanceUntilIdle()
        val nameAZ = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.NAME } as LibraryUiState.Success
        assertEquals("Alpha", nameAZ.recordings[0].entity.displayName)

        // NAME_DESC (Z-A): Zulu, Alpha
        viewModel.setSortOrder(SortOrder.NAME_DESC)
        advanceUntilIdle()
        val nameZA = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.NAME_DESC } as LibraryUiState.Success
        assertEquals("Zulu", nameZA.recordings[0].entity.displayName)

        // DURATION: Zulu (50s), Alpha (10s)
        viewModel.setSortOrder(SortOrder.DURATION)
        advanceUntilIdle()
        val durationSort = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.DURATION } as LibraryUiState.Success
        assertEquals("Zulu", durationSort.recordings[0].entity.displayName)

        // SIZE: Zulu (5000B), Alpha (500B)
        viewModel.setSortOrder(SortOrder.SIZE)
        advanceUntilIdle()
        val sizeSort = viewModel.uiState.first { it is LibraryUiState.Success && it.sortOrder == SortOrder.SIZE } as LibraryUiState.Success
        assertEquals("Zulu", sizeSort.recordings[0].entity.displayName)
    }

    @Test
    fun multiSelection_batchFavoriteMoveDelete() = runTest(testDispatcher) {
        val r1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "R1",
            filePath = "/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            isFavorite = false
        )
        val r2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "R2",
            filePath = "/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            isFavorite = false
        )
        fakeRepository.setRecordings(listOf(r1, r2))
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 }

        viewModel.enterSelectionMode(1L)
        viewModel.toggleSelection(2L)
        advanceUntilIdle()

        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(setOf(1L, 2L), viewModel.selectedRecordingIds.value)

        // Batch Favorite
        viewModel.setFavoritesForSelected(true)
        advanceUntilIdle()
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.all { r -> r.isFavorite } }

        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(fakeRepository.recordingsFlow.value.all { it.isFavorite })

        // Re-enter selection mode and Select All
        viewModel.enterSelectionMode()
        viewModel.selectAll()
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L), viewModel.selectedRecordingIds.value)

        // Batch Delete
        viewModel.deleteSelectedRecordings()
        advanceUntilIdle()
        assertTrue(fakeRepository.recordingsFlow.value.isEmpty())
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun renameRecording_onlyChangesDisplayName() = runTest(testDispatcher) {
        val original = RecordingEntity(
            id = 42,
            fileName = "file_42.m4a",
            displayName = "Old Title",
            filePath = "/immutable/path/file_42.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 15000L,
            fileSizeBytes = 50000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = 10000L
        )
        fakeRepository.setRecordings(listOf(original))

        var renameResult = false
        viewModel.renameRecording(original, "New Beautiful Title") { success, _ -> renameResult = success }
        advanceUntilIdle()

        assertTrue(renameResult)
        val updated = fakeRepository.recordingsFlow.value[0]
        assertEquals("New Beautiful Title", updated.displayName)
        assertEquals("/immutable/path/file_42.m4a", updated.filePath)
        assertEquals(42L, updated.id)
        assertEquals(10000L, updated.createdAt)
    }

    @Test
    fun searchDoesNotInterruptPlayback() = runTest(testDispatcher) {
        val r1 = RecordingEntity(
            id = 1,
            fileName = "1.m4a",
            displayName = "Current Song",
            filePath = "/1.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val r2 = RecordingEntity(
            id = 2,
            fileName = "2.m4a",
            displayName = "Other Song",
            filePath = "/2.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 1000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        fakeRepository.setRecordings(listOf(r1, r2))

        viewModel.playRecording(r1)
        assertTrue(viewModel.playbackState.value.isPlaying)
        assertEquals(1L, viewModel.playbackState.value.currentRecording?.id)

        // Now search for "Other"
        viewModel.setSearchQuery("Other")
        advanceTimeBy(300L)
        advanceUntilIdle()

        // Current track must still be playing uninterrupted!
        assertTrue(viewModel.playbackState.value.isPlaying)
        assertEquals(1L, viewModel.playbackState.value.currentRecording?.id)
    }

    @Test
    fun missingAndCorruptedFiles_distinguishStatusAndErrorMessages() = runTest(testDispatcher) {
        val zeroByteFile = File.createTempFile("zero_byte", ".m4a")
        zeroByteFile.deleteOnExit()

        val missingRec = RecordingEntity(
            id = 501,
            fileName = "missing.m4a",
            displayName = "Missing Tape",
            filePath = "/non/existent/missing.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val zeroByteRec = RecordingEntity(
            id = 502,
            fileName = zeroByteFile.name,
            displayName = "Corrupted Tape",
            filePath = zeroByteFile.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 0L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )

        fakeRepository.setRecordings(listOf(missingRec, zeroByteRec))

        val state = viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 } as LibraryUiState.Success
        val missingUi = state.recordings.first { it.entity.id == 501L }
        assertFalse(missingUi.isFileAvailable)
        assertEquals("Audio file missing from storage", missingUi.fileErrorMessage)

        val zeroByteUi = state.recordings.first { it.entity.id == 502L }
        assertFalse(zeroByteUi.isFileAvailable)
        assertEquals("Audio file is corrupted or empty (0 bytes)", zeroByteUi.fileErrorMessage)

        zeroByteFile.delete()
    }

    @Test
    fun renameRecording_whenActiveTrack_updatesPlayerMetadataImmediately() = runTest(testDispatcher) {
        val rec = RecordingEntity(
            id = 601,
            fileName = "track.m4a",
            displayName = "Old Name",
            filePath = "/fake/track.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 15000L,
            fileSizeBytes = 5000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        fakeRepository.setRecordings(listOf(rec))

        viewModel.playRecording(rec)
        assertEquals("Old Name", viewModel.playbackState.value.currentRecording?.displayName)

        var renameSuccess = false
        viewModel.renameRecording(rec, "Brand New Name") { success, _ -> renameSuccess = success }
        advanceUntilIdle()

        assertTrue(renameSuccess)
        assertEquals("Brand New Name", viewModel.playbackState.value.currentRecording?.displayName)
    }

    @Test
    fun deleteOrphanRecording_cleansUpDatabaseSuccessfully() = runTest(testDispatcher) {
        val orphan = RecordingEntity(
            id = 701,
            fileName = "lost.m4a",
            displayName = "Lost Track",
            filePath = "/deleted/on/disk/lost.m4a",
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = 1000L,
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        fakeRepository.setRecordings(listOf(orphan))

        viewModel.deleteRecording(orphan)
        advanceUntilIdle()

        assertTrue(fakeRepository.recordingsFlow.value.isEmpty())
    }

    // --- PHASE 34-1: deletion-result feedback ---

    /**
     * Reads the next one-shot message, or null if none arrives. Uses virtual time (runTest) so it
     * returns promptly instead of blocking on the open channel.
     */
    private suspend fun awaitUserMessage(): UiMessage? =
        withTimeoutOrNull(1_000L) { viewModel.userMessages.first() }

    private fun recForDelete(id: Long, name: String) = RecordingEntity(
        id = id,
        fileName = "$id.m4a",
        displayName = name,
        filePath = "/$id.m4a",
        mimeType = "audio/mp4",
        format = "M4A",
        durationMs = 5000L,
        fileSizeBytes = 1000L,
        sampleRate = 44100,
        bitRate = 128000,
        channelCount = 1
    )

    @Test
    fun deleteRecording_success_emitsNoUserMessage() = runTest(testDispatcher) {
        val rec = recForDelete(801L, "Good Delete")
        fakeRepository.setRecordings(listOf(rec))
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 1 }

        assertNull(awaitUserMessage())

        viewModel.deleteRecording(rec)
        advanceUntilIdle()

        assertTrue(fakeRepository.recordingsFlow.value.isEmpty())
        assertNull("A successful delete must not surface an error", awaitUserMessage())
    }

    @Test
    fun deleteRecording_failure_emitsDeleteFailedSingleWithName() = runTest(testDispatcher) {
        val rec = recForDelete(802L, "Stuck File")
        fakeRepository.setRecordings(listOf(rec))
        fakeRepository.failDeleteIds = setOf(802L)
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 1 }

        viewModel.deleteRecording(rec)
        advanceUntilIdle()

        // Not falsely reported as success: the row is retained and an error event is emitted.
        assertEquals(1, fakeRepository.recordingsFlow.value.size)
        val message = awaitUserMessage()
        assertTrue(message is UiMessage.DeleteFailedSingle)
        assertEquals("Stuck File", (message as UiMessage.DeleteFailedSingle).name)
    }

    @Test
    fun deleteSelectedRecordings_allSucceed_emitsNoUserMessage() = runTest(testDispatcher) {
        val r1 = recForDelete(811L, "A")
        val r2 = recForDelete(812L, "B")
        fakeRepository.setRecordings(listOf(r1, r2))
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 }

        viewModel.enterSelectionMode(811L)
        viewModel.toggleSelection(812L)
        viewModel.deleteSelectedRecordings()
        advanceUntilIdle()

        assertTrue(fakeRepository.recordingsFlow.value.isEmpty())
        assertFalse(viewModel.isSelectionMode.value)
        assertNull(awaitUserMessage())
    }

    @Test
    fun deleteSelectedRecordings_partialFailure_reportsExactFailureCount() = runTest(testDispatcher) {
        val r1 = recForDelete(821L, "A")
        val r2 = recForDelete(822L, "B")
        val r3 = recForDelete(823L, "C")
        fakeRepository.setRecordings(listOf(r1, r2, r3))
        fakeRepository.failDeleteIds = setOf(822L)
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 3 }

        viewModel.enterSelectionMode(821L)
        viewModel.toggleSelection(822L)
        viewModel.toggleSelection(823L)
        viewModel.deleteSelectedRecordings()
        advanceUntilIdle()

        // Two succeeded, one retained (row kept on failure).
        assertEquals(listOf(822L), fakeRepository.recordingsFlow.value.map { it.id })
        assertFalse(viewModel.isSelectionMode.value)
        val message = viewModel.userMessages.first()
        assertTrue(message is UiMessage.DeleteFailedBulk)
        message as UiMessage.DeleteFailedBulk
        assertEquals(1, message.failed)
        assertEquals(3, message.total)
    }

    @Test
    fun deleteSelectedRecordings_totalFailure_reportsAllFailed() = runTest(testDispatcher) {
        val r1 = recForDelete(831L, "A")
        val r2 = recForDelete(832L, "B")
        fakeRepository.setRecordings(listOf(r1, r2))
        fakeRepository.failDeleteIds = setOf(831L, 832L)
        viewModel.uiState.first { it is LibraryUiState.Success && it.recordings.size == 2 }

        viewModel.enterSelectionMode()
        viewModel.selectAll()
        viewModel.deleteSelectedRecordings()
        advanceUntilIdle()

        assertEquals(setOf(831L, 832L), fakeRepository.recordingsFlow.value.map { it.id }.toSet())
        assertFalse(viewModel.isSelectionMode.value)
        val message = viewModel.userMessages.first()
        assertTrue(message is UiMessage.DeleteFailedBulk)
        message as UiMessage.DeleteFailedBulk
        assertEquals(2, message.failed)
        assertEquals(2, message.total)
    }

    @Test
    fun searchWithinFolder_returnsOnlyIntersection() = runTest(testDispatcher) {
        val folder = FolderEntity(id = 1L, name = "Work")
        fakeRepository.foldersFlow.value = listOf(folder)

        fun rec(id: Long, name: String, folderId: Long?) = RecordingEntity(
            id = id, fileName = "$id.m4a", displayName = name, filePath = "/$id.m4a",
            mimeType = "audio/mp4", format = "M4A", durationMs = 1000L, fileSizeBytes = 1000L,
            sampleRate = 44100, bitRate = 128000, channelCount = 1, folderId = folderId
        )
        // Two recordings inside the folder, one matching the query outside the folder.
        fakeRepository.setRecordings(
            listOf(
                rec(1L, "Meeting Alpha", 1L),
                rec(2L, "Beta", 1L),
                rec(3L, "Meeting Gamma", null)
            )
        )

        viewModel.selectTab(LibraryTab.FOLDERS)
        viewModel.selectFolder(folder)
        viewModel.setSearchQuery("Meeting")
        advanceTimeBy(300L)

        val state = viewModel.uiState.first {
            it is LibraryUiState.Success && it.selectedFolder?.id == 1L && it.searchQuery == "Meeting"
        } as LibraryUiState.Success

        // Only the folder member whose name matches; the non-folder match must not leak in.
        assertEquals(1, state.recordings.size)
        assertEquals(1L, state.recordings[0].entity.id)
    }

    @Test
    fun searchWithinFavorites_returnsOnlyIntersection() = runTest(testDispatcher) {
        fun rec(id: Long, name: String, favorite: Boolean) = RecordingEntity(
            id = id, fileName = "$id.m4a", displayName = name, filePath = "/$id.m4a",
            mimeType = "audio/mp4", format = "M4A", durationMs = 1000L, fileSizeBytes = 1000L,
            sampleRate = 44100, bitRate = 128000, channelCount = 1, isFavorite = favorite
        )
        fakeRepository.setRecordings(
            listOf(
                rec(1L, "Alpha One", true),
                rec(2L, "Alpha Two", false),
                rec(3L, "Gamma", true)
            )
        )

        viewModel.selectTab(LibraryTab.FAVORITES)
        viewModel.setSearchQuery("Alpha")
        advanceTimeBy(300L)

        val state = viewModel.uiState.first {
            it is LibraryUiState.Success && it.selectedTab == LibraryTab.FAVORITES && it.searchQuery == "Alpha"
        } as LibraryUiState.Success

        // Intersection of favorite AND name match; the non-favorite match must not appear.
        assertEquals(1, state.recordings.size)
        assertEquals(1L, state.recordings[0].entity.id)
    }

    // --- Fake Test Doubles ---

    private class FakeAudioPlayer : AudioPlayer {
        private val _state = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _state.asStateFlow()

        var lastPlayedRecording: RecordingEntity? = null

        override fun play(recording: RecordingEntity) {
            lastPlayedRecording = recording
            _state.value = PlaybackState(
                currentRecording = recording,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = recording.durationMs,
                isPrepared = true
            )
        }

        override fun pause() {
            _state.value = _state.value.copy(isPlaying = false)
        }

        override fun resume() {
            _state.value = _state.value.copy(isPlaying = true)
        }

        override fun seekTo(positionMs: Long) {
            _state.value = _state.value.copy(currentPositionMs = positionMs)
        }

        override fun seekRelative(offsetMs: Long) {
            val target = (_state.value.currentPositionMs + offsetMs).coerceIn(0L, _state.value.durationMs)
            seekTo(target)
        }

        private var playlist: List<RecordingEntity> = emptyList()

        override fun setPlaylist(recordings: List<RecordingEntity>) {
            playlist = recordings
        }

        override fun playNext() {
            val current = _state.value.currentRecording ?: return
            val idx = playlist.indexOfFirst { it.id == current.id }
            if (idx != -1 && idx + 1 < playlist.size) {
                play(playlist[idx + 1])
            }
        }

        override fun playPrevious() {
            if (_state.value.currentPositionMs > 3000L) {
                seekTo(0)
                return
            }
            val current = _state.value.currentRecording ?: return
            val idx = playlist.indexOfFirst { it.id == current.id }
            if (idx > 0) {
                play(playlist[idx - 1])
            } else {
                seekTo(0)
            }
        }

        override fun stop() {
            _state.value = PlaybackState()
        }

        override fun release() {
            _state.value = PlaybackState()
        }

        override fun clearError() {
            _state.value = _state.value.copy(errorMessage = null)
        }

        private var savedSpeed: Float? = null

        override fun setSpeed(speed: Float) {
            val clamped = speed.coerceIn(0.25f, 2.0f)
            _state.value = _state.value.copy(playbackSpeed = clamped)
        }

        override fun startTemporaryFastForward() {
            if (savedSpeed == null) {
                savedSpeed = _state.value.playbackSpeed
                setSpeed(2.0f)
            }
        }

        override fun stopTemporaryFastForward() {
            val target = savedSpeed ?: 1.0f
            savedSpeed = null
            setSpeed(target)
        }

        override fun setRepeatMode(mode: RepeatMode) {
            _state.value = _state.value.copy(repeatMode = mode)
        }

        override fun toggleRepeatMode() {
            val next = when (_state.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            setRepeatMode(next)
        }

        override fun setShuffleEnabled(enabled: Boolean) {
            _state.value = _state.value.copy(isShuffleEnabled = enabled)
        }

        override fun toggleShuffle() {
            setShuffleEnabled(!_state.value.isShuffleEnabled)
        }

        override fun setSleepTimer(durationMinutes: Int?) {
            _state.value = _state.value.copy(
                sleepTimerRemainingMs = durationMinutes?.let { it * 60 * 1000L }
            )
        }

        override fun cancelSleepTimer() {
            setSleepTimer(null)
        }

        override fun updateCurrentRecordingMetadata(recording: RecordingEntity) {
            if (_state.value.currentRecording?.id == recording.id) {
                _state.value = _state.value.copy(currentRecording = recording)
            }
        }
    }

    private class FakeRecordingRepository : RecordingRepository {
        val recordingsFlow = MutableStateFlow<List<RecordingEntity>>(emptyList())
        val foldersFlow = MutableStateFlow<List<FolderEntity>>(emptyList())
        val tagsFlow = MutableStateFlow<List<TagEntity>>(emptyList())
        val crossRefsFlow = MutableStateFlow<List<RecordingTagCrossRef>>(emptyList())

        // When set, recordings whose id is in this set fail to delete: the physical file is "stuck",
        // so the repository keeps the Room row and returns StorageResult.Error — mirroring the real
        // RecordingRepositoryImpl failure semantics.
        var failDeleteIds: Set<Long> = emptySet()

        fun setRecordings(list: List<RecordingEntity>) {
            recordingsFlow.value = list
        }

        override fun observeRecordings(sortOrder: SortOrder): Flow<List<RecordingEntity>> = recordingsFlow
        override fun observeFavorites(): Flow<List<RecordingEntity>> = flowOf(recordingsFlow.value.filter { it.isFavorite })
        override fun searchRecordings(query: String): Flow<List<RecordingEntity>> = recordingsFlow
        override fun observeRecordingById(id: Long): Flow<RecordingEntity?> = flowOf(recordingsFlow.value.firstOrNull { it.id == id })
        override fun observeRecordingsByFolder(folderId: Long): Flow<List<RecordingEntity>> = flowOf(recordingsFlow.value.filter { it.folderId == folderId })
        override fun observeRecordingCount(): Flow<Int> = flowOf(recordingsFlow.value.size)
        override suspend fun getRecordingById(id: Long): RecordingEntity? = recordingsFlow.value.firstOrNull { it.id == id }
        override suspend fun insertRecording(recording: RecordingEntity): Long {
            recordingsFlow.value = recordingsFlow.value + recording
            return recording.id
        }
        override suspend fun updateRecording(recording: RecordingEntity) {
            recordingsFlow.value = recordingsFlow.value.map { if (it.id == recording.id) recording else it }
        }
        override suspend fun deleteRecording(recording: RecordingEntity): StorageResult<Boolean> {
            if (failDeleteIds.contains(recording.id)) {
                // Row deliberately retained to mirror the repository's failure semantics.
                return StorageResult.Error(StorageErrorType.IO_ERROR, "Simulated physical delete failure")
            }
            recordingsFlow.value = recordingsFlow.value.filter { it.id != recording.id }
            return StorageResult.Success(true)
        }
        override suspend fun deleteRecordings(recordings: List<RecordingEntity>): List<StorageResult<Boolean>> {
            return recordings.map { rec ->
                if (failDeleteIds.contains(rec.id)) {
                    StorageResult.Error(StorageErrorType.IO_ERROR, "Simulated physical delete failure")
                } else {
                    recordingsFlow.value = recordingsFlow.value.filter { it.id != rec.id }
                    StorageResult.Success(true)
                }
            }
        }
        override suspend fun toggleFavorite(recordingId: Long, isFavorite: Boolean) {
            recordingsFlow.value = recordingsFlow.value.map {
                if (it.id == recordingId) it.copy(isFavorite = isFavorite) else it
            }
        }
        override suspend fun setFavorites(recordingIds: List<Long>, isFavorite: Boolean) {
            val set = recordingIds.toSet()
            recordingsFlow.value = recordingsFlow.value.map {
                if (set.contains(it.id)) it.copy(isFavorite = isFavorite) else it
            }
        }
        override suspend fun renameRecording(recordingId: Long, newName: String): Result<Unit> {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be blank"))
            recordingsFlow.value = recordingsFlow.value.map {
                if (it.id == recordingId) it.copy(displayName = trimmed, modifiedAt = System.currentTimeMillis()) else it
            }
            return Result.success(Unit)
        }
        override suspend fun moveRecordingToFolder(recordingId: Long, folderId: Long?) {
            recordingsFlow.value = recordingsFlow.value.map {
                if (it.id == recordingId) it.copy(folderId = folderId) else it
            }
        }
        override suspend fun moveRecordingsToFolder(recordingIds: List<Long>, folderId: Long?) {
            val set = recordingIds.toSet()
            recordingsFlow.value = recordingsFlow.value.map {
                if (set.contains(it.id)) it.copy(folderId = folderId) else it
            }
        }
        override fun observeFolders(): Flow<List<FolderEntity>> = foldersFlow
        override suspend fun getFolderById(id: Long): FolderEntity? = foldersFlow.value.firstOrNull { it.id == id }
        override suspend fun createFolder(name: String): Long {
            val newFolder = FolderEntity(id = (foldersFlow.value.size + 1).toLong(), name = name.trim())
            foldersFlow.value = foldersFlow.value + newFolder
            return newFolder.id
        }
        override suspend fun createFolderSafely(name: String): Result<Long> {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Blank name"))
            if (foldersFlow.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("Folder already exists"))
            }
            val id = createFolder(trimmed)
            return Result.success(id)
        }
        override suspend fun updateFolder(folder: FolderEntity) {
            foldersFlow.value = foldersFlow.value.map { if (it.id == folder.id) folder else it }
        }
        override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Blank name"))
            foldersFlow.value = foldersFlow.value.map {
                if (it.id == folderId) it.copy(name = trimmed) else it
            }
            return Result.success(Unit)
        }
        override suspend fun deleteFolder(folder: FolderEntity) {
            // Clear folderId from recordings
            recordingsFlow.value = recordingsFlow.value.map {
                if (it.folderId == folder.id) it.copy(folderId = null) else it
            }
            foldersFlow.value = foldersFlow.value.filter { it.id != folder.id }
        }
        override fun observeTags(): Flow<List<TagEntity>> = tagsFlow
        override fun observeTagsForRecording(recordingId: Long): Flow<List<TagEntity>> {
            val tagIds = crossRefsFlow.value.filter { it.recordingId == recordingId }.map { it.tagId }.toSet()
            return flowOf(tagsFlow.value.filter { tagIds.contains(it.id) })
        }
        override fun observeAllCrossRefs(): Flow<List<RecordingTagCrossRef>> = crossRefsFlow
        override suspend fun createTag(name: String): Long {
            val newTag = TagEntity(id = (tagsFlow.value.size + 1).toLong(), name = name.trim())
            tagsFlow.value = tagsFlow.value + newTag
            return newTag.id
        }
        override suspend fun createTagSafely(name: String): Result<Long> {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Blank name"))
            if (tagsFlow.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("Tag already exists"))
            }
            val id = createTag(trimmed)
            return Result.success(id)
        }
        override suspend fun renameTag(tagId: Long, newName: String): Result<Unit> {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Blank name"))
            tagsFlow.value = tagsFlow.value.map {
                if (it.id == tagId) it.copy(name = trimmed) else it
            }
            return Result.success(Unit)
        }
        override suspend fun deleteTag(tag: TagEntity) {
            crossRefsFlow.value = crossRefsFlow.value.filter { it.tagId != tag.id }
            tagsFlow.value = tagsFlow.value.filter { it.id != tag.id }
        }
        override suspend fun addTagToRecording(recordingId: Long, tagId: Long) {
            crossRefsFlow.value = crossRefsFlow.value + RecordingTagCrossRef(recordingId, tagId)
        }
        override suspend fun removeTagFromRecording(recordingId: Long, tagId: Long) {
            crossRefsFlow.value = crossRefsFlow.value.filter { !(it.recordingId == recordingId && it.tagId == tagId) }
        }
        override suspend fun setTagsForRecording(recordingId: Long, tagIds: Set<Long>) {
            val remaining = crossRefsFlow.value.filter { it.recordingId != recordingId }
            val newRefs = tagIds.map { RecordingTagCrossRef(recordingId, it) }
            crossRefsFlow.value = remaining + newRefs
        }
        override fun observeBookmarks(recordingId: Long): Flow<List<BookmarkEntity>> = flowOf(emptyList())
        override suspend fun getBookmarks(recordingId: Long): List<BookmarkEntity> = emptyList()
        override suspend fun addBookmark(recordingId: Long, positionMs: Long, label: String): Long = 1
        override suspend fun updateBookmark(bookmark: BookmarkEntity) {}
        override suspend fun deleteBookmark(bookmark: BookmarkEntity) {}
    }
}
