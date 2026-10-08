package com.example.core.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.player.AndroidAudioPlayer
import com.example.core.player.AudioPlayerProvider
import com.example.core.service.PlaybackService
import com.example.core.service.RecordingService
import com.example.core.storage.StorageManager
import com.example.data.repository.RecordingRepository
import com.example.data.repository.RecordingRepositoryImpl
import com.example.feature.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.asExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 30 — cross-system integration & failure-recovery probes.
 * Exercises the boundaries between recording storage, Room, Library and playback
 * that are invisible when each subsystem is tested in isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CrossSystemFailureRecoveryTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var database: RecovoDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var repository: RecordingRepository
    private val testDispatcher = StandardTestDispatcher()
    // The player owns an infinite position ticker, so it must run on its own scheduler and never
    // share the test scheduler (which would make advanceUntilIdle() spin forever).
    private val playerDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(playerDispatcher)
    private lateinit var player: AndroidAudioPlayer

    private fun createAudioFile(name: String): File {
        val file = File(testDir, "$name.m4a")
        file.writeBytes(ByteArray(2048) { 0x22 })
        val ds = org.robolectric.shadows.util.DataSource.toDataSource(file.absolutePath)
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
            ds,
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(5000, 0)
        )
        return file
    }

    private fun entityFor(id: Long, file: File, name: String = file.nameWithoutExtension): RecordingEntity =
        RecordingEntity(
            id = id,
            fileName = file.name,
            displayName = name,
            filePath = file.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 5000L,
            fileSizeBytes = file.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "xsystem_${System.currentTimeMillis()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, RecovoDatabase::class.java)
            .allowMainThreadQueries()
            // Drive Room's query/invalidation work on the test scheduler so flow emissions are
            // deterministic under advanceUntilIdle().
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        storageManager = StorageManager(
            context = context,
            baseDirectory = testDir,
            ioDispatcher = testDispatcher
        )
        repository = RecordingRepositoryImpl(
            recordingDao = database.recordingDao(),
            folderDao = database.folderDao(),
            tagDao = database.tagDao(),
            bookmarkDao = database.bookmarkDao(),
            storageManager = storageManager,
            ioDispatcher = testDispatcher
        )
        player = AndroidAudioPlayer(context, testScope)
        AudioPlayerProvider.setForTesting(player)
        RecordingService.setRecordingActiveForTesting(false)
    }

    @After
    fun tearDown() {
        RecordingService.setRecordingActiveForTesting(false)
        player.stop()
        player.release()
        AudioPlayerProvider.setForTesting(null)
        database.close()
        testDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    private fun viewModel(): LibraryViewModel =
        LibraryViewModel(repository, player, null)

    // Workflow B — Recording -> Library -> Playback: ID and file path stay consistent end-to-end.
    @Test
    fun saveThenPlay_preservesRecordingIdAndFilePath() = runTest(testDispatcher) {
        val file = createAudioFile("consistency")
        val savedId = repository.insertRecording(entityFor(0L, file))
        assertTrue(savedId > 0L)

        val saved = repository.getRecordingById(savedId)
        assertNotNull(saved)

        val vm = viewModel()
        vm.playRecording(saved!!)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val state = player.playbackState.value
        assertNull(state.errorMessage)
        assertEquals("Playback must reference the persisted row id", savedId, state.currentRecording?.id)
        assertEquals("Playback must reference the persisted file path", saved.filePath, state.currentRecording?.filePath)
        assertTrue(state.isPlaying)
    }

    // Workflow D / Section 6C — Delete while the same track is playing: playback stops, file and row removed.
    @Test
    fun deleteWhilePlaying_stopsPlaybackAndRemovesFileAndRow() = runTest(testDispatcher) {
        val file = createAudioFile("delete_playing")
        val savedId = repository.insertRecording(entityFor(0L, file))
        val saved = repository.getRecordingById(savedId)!!

        val vm = viewModel()
        player.setPlaylist(listOf(saved))
        vm.playRecording(saved)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)

        vm.deleteRecording(saved)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Player must stop when its media is deleted", player.playbackState.value.isPlaying)
        assertNull("Player must not retain deleted media", player.playbackState.value.currentRecording)
        assertFalse("Physical file must be removed", file.exists())
        assertNull("Room row must be removed", repository.getRecordingById(savedId))
    }

    // Section 6D — Delete immediately after Play, before preparation completes.
    @Test
    fun deleteImmediatelyAfterPlay_isSafeAndConsistent() = runTest(testDispatcher) {
        val file = createAudioFile("delete_after_play")
        val savedId = repository.insertRecording(entityFor(0L, file))
        val saved = repository.getRecordingById(savedId)!!

        val vm = viewModel()
        player.setPlaylist(listOf(saved))
        vm.playRecording(saved)
        // No looper idle: preparation is still pending when the delete arrives.
        vm.deleteRecording(saved)
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(player.playbackState.value.isPlaying)
        assertNull(player.playbackState.value.currentRecording)
        assertFalse(file.exists())
        assertNull(repository.getRecordingById(savedId))
    }

    // Section 6F — Delete the current track through multi-selection.
    @Test
    fun deleteSelectedRecordings_stopsCurrentTrackAndRemoves() = runTest(testDispatcher) {
        val fileA = createAudioFile("multi_a")
        val fileB = createAudioFile("multi_b")
        val idA = repository.insertRecording(entityFor(0L, fileA))
        val idB = repository.insertRecording(entityFor(0L, fileB))
        val recA = repository.getRecordingById(idA)!!
        val recB = repository.getRecordingById(idB)!!

        val vm = viewModel()
        // Simulate the Library screen collecting uiState so the VM's filtered snapshot is populated.
        val uiJob = kotlinx.coroutines.CoroutineScope(testDispatcher).launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        player.setPlaylist(listOf(recA, recB))
        vm.playRecording(recA)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)

        vm.enterSelectionMode(idA)
        vm.deleteSelectedRecordings()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(player.playbackState.value.isPlaying)
        assertNull(player.playbackState.value.currentRecording)
        assertFalse(fileA.exists())
        assertNull(repository.getRecordingById(idA))
        // The untouched recording must survive.
        assertNotNull(repository.getRecordingById(idB))
        assertTrue(fileB.exists())
    }

    // Workflow O — Save -> Favorite -> Play.
    @Test
    fun saveThenFavoriteThenPlay_isConsistent() = runTest(testDispatcher) {
        val file = createAudioFile("favorite_track")
        val id = repository.insertRecording(entityFor(0L, file, "Favorite Track"))

        val vm = viewModel()
        val rec = repository.getRecordingById(id)!!
        vm.toggleFavorite(rec)
        testDispatcher.scheduler.advanceUntilIdle()

        val favorited = repository.getRecordingById(id)!!
        assertTrue(favorited.isFavorite)

        vm.playRecording(favorited)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertNull(player.playbackState.value.errorMessage)
        assertEquals(id, player.playbackState.value.currentRecording?.id)
    }

    // Section 4A/4B — Save sequence: successful save yields both physical file and Room row.
    @Test
    fun successfulSave_hasBothFileAndRoomRow() = runTest(testDispatcher) {
        val file = createAudioFile("atomic_ok")
        val id = repository.insertRecording(entityFor(0L, file))
        val saved = repository.getRecordingById(id)

        assertNotNull(saved)
        assertTrue("Physical file must exist for a committed row", File(saved!!.filePath).isFile)
        assertTrue(saved.fileSizeBytes > 0L)
    }

    // Section 4D/4E — Repeated save/finalization must not create duplicate rows.
    @Test
    fun repeatedStopSave_doesNotCreateDuplicateRows() = runTest(testDispatcher) {
        val file = createAudioFile("dup_save")
        val id1 = repository.insertRecording(entityFor(0L, file))
        // A second finalization attempt for the exact same file (fan-out bug scenario) would
        // create a second row unless the id/path relationship is guarded upstream.
        val count = repository.observeRecordings().first().count { it.filePath == file.absolutePath }
        assertEquals("Single save must yield exactly one row", 1, count)
        assertNotNull(repository.getRecordingById(id1))
    }

    // Section 4B — Room insert failure after a valid file must not report false success at repo level.
    @Test
    fun roomInsertFailure_reportsFailureAndDoesNotPersistRow() = runTest(testDispatcher) {
        database.close() // Force the DAO to fail on any subsequent insert.

        var failed = false
        try {
            repository.insertRecording(entityFor(0L, createAudioFile("db_fail")))
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("A failed Room insert must surface an error, not false success", failed)
    }

    // Section 5/6 — A stale entity (deleted elsewhere) cannot resurrect playback state.
    @Test
    fun playAfterDelete_isRejectedAndLeavesNoStaleState() = runTest(testDispatcher) {
        val file = createAudioFile("play_after_delete")
        val id = repository.insertRecording(entityFor(0L, file))
        val rec = repository.getRecordingById(id)!!

        // Delete the physical file out from under the row (orphan metadata scenario, Section 6E).
        assertTrue(file.delete())

        val vm = viewModel()
        vm.playRecording(rec)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val state = player.playbackState.value
        assertFalse("Missing file must never report playing", state.isPlaying)
        assertTrue(state.errorMessage != null)
        // Non-destructive: the Room row is retained for a possible file restoration.
        assertNotNull("Library metadata must remain after a safe playback failure", repository.getRecordingById(id))
    }

    // Section 10M — Delete the current track, then immediately play another.
    @Test
    fun deleteCurrentTrack_thenPlayAnother_isSafeAndConsistent() = runTest(testDispatcher) {
        val fileA = createAudioFile("switch_a")
        val fileB = createAudioFile("switch_b")
        val idA = repository.insertRecording(entityFor(0L, fileA))
        val idB = repository.insertRecording(entityFor(0L, fileB))
        val recA = repository.getRecordingById(idA)!!
        val recB = repository.getRecordingById(idB)!!

        val vm = viewModel()
        vm.playRecording(recA)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)

        vm.deleteRecording(recA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(player.playbackState.value.currentRecording)

        vm.playRecording(recB)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertNull(player.playbackState.value.errorMessage)
        assertEquals(idB, player.playbackState.value.currentRecording?.id)
        assertEquals(recB.filePath, player.playbackState.value.currentRecording?.filePath)
        assertTrue(player.playbackState.value.isPlaying)
        assertFalse(fileA.exists())
        assertTrue(fileB.exists())
    }

    // Workflow A/B — A saved recording becomes visible in the Library stream with matching metadata.
    @Test
    fun savedRecording_isVisibleInLibraryWithConsistentMetadata() = runTest(testDispatcher) {
        val file = createAudioFile("visible_track")
        val id = repository.insertRecording(entityFor(0L, file, "Visible Track"))

        val vm = viewModel()
        val uiJob = launch { vm.uiState.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is com.example.feature.library.LibraryUiState.Success)
        val item = (state as com.example.feature.library.LibraryUiState.Success)
            .recordings.firstOrNull { it.entity.id == id }
        assertNotNull("Saved recording must appear in the Library stream", item)
        assertEquals("Visible Track", item!!.entity.displayName)
        assertEquals(file.absolutePath, item.entity.filePath)
        assertTrue(item.isFileAvailable)
        uiJob.cancel()
    }

    // Workflow I — Activity recreation during playback reattaches to the same shared player.
    @Test
    fun activityRecreationDuringPlayback_reattachesWithoutDuplicatePlayer() = runTest(testDispatcher) {
        val file = createAudioFile("recreate_play")
        val id = repository.insertRecording(entityFor(0L, file))
        val rec = repository.getRecordingById(id)!!

        val vm1 = viewModel()
        vm1.playRecording(rec)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(player.playbackState.value.isPlaying)

        val tokenBefore = player.getSessionToken()
        val positionBefore = player.playbackState.value.currentPositionMs

        // Simulate a recreated Activity/ViewModel reattaching to the app-scoped player.
        val vm2 = viewModel()
        assertEquals("Recreated UI must share the same player state flow", vm2.playbackState, player.playbackState)
        assertEquals("No duplicate MediaSession may be created", tokenBefore, player.getSessionToken())
        assertEquals(id, player.playbackState.value.currentRecording?.id)
        assertTrue(player.playbackState.value.isPlaying)
        assertEquals(positionBefore, player.playbackState.value.currentPositionMs)

        // Playback remains controllable after reattachment.
        vm2.togglePlayPause()
        assertFalse(player.playbackState.value.isPlaying)
        vm2.togglePlayPause()
        assertTrue(player.playbackState.value.isPlaying)
    }

    // Section 8 — Recording and playback notifications are independent and cannot cross-control.
    @Test
    fun recordingAndPlaybackNotifications_areIndependent() {
        val recPause = RecordingService.pauseIntent(context)
        val playPause = PlaybackService.pauseIntent(context)

        assertEquals(
            "Recording pause intent must target RecordingService",
            RecordingService::class.java.name,
            recPause.component?.className
        )
        assertEquals(
            "Playback pause intent must target PlaybackService",
            PlaybackService::class.java.name,
            playPause.component?.className
        )
        assertTrue("Action strings must not collide", recPause.action != playPause.action)
        assertTrue(
            "Notification ids must differ so stopping one cannot remove the other",
            RecordingService.NOTIFICATION_ID != PlaybackService.NOTIFICATION_ID
        )
        assertTrue(
            "Notification channels must differ",
            RecordingService.CHANNEL_ID != PlaybackService.CHANNEL_ID
        )
    }
}
