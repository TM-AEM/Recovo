package com.example.core.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.database.model.RecordingEntity
import com.example.core.engine.RecordingQuality
import com.example.core.engine.RecordingState
import com.example.core.player.AndroidAudioPlayer
import com.example.core.player.AudioPlayerProvider
import com.example.core.service.RecordingController
import com.example.core.service.RecordingService
import com.example.core.storage.StorageManager
import com.example.data.repository.RecordingRepositoryImpl
import com.example.feature.record.RecordViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CrossSystemArchitectureTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var player: AndroidAudioPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    }

    @Test
    fun recordViewModel_initialValue_reflectsActiveControllerState() {
        val controller = RecordingController(context)
        // Controller initial state is Idle
        val initialVm = RecordViewModel(controller)
        assertTrue(initialVm.recordingState.value is RecordingState.Idle)

        // When a service instance is active with a non-idle state
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        val activeFile = File(context.cacheDir, "active_recreation.m4a")
        activeFile.writeBytes(ByteArray(256))

        // Reflect an active recording in service
        val dummyRecordingState = RecordingState.Recording(activeFile, 12000L, 500)
        val stateField = RecordingService::class.java.getDeclaredField("_recordingState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(service) as kotlinx.coroutines.flow.MutableStateFlow<RecordingState>
        stateFlow.value = dummyRecordingState

        // Now creating a new RecordViewModel (such as after Activity recreation or process rotation)
        val controller2 = RecordingController(context)
        val vmAfterRecreation = RecordViewModel(controller2)

        // The ViewModel MUST immediately have the active recording state without a momentary Idle glitch
        assertEquals(dummyRecordingState, vmAfterRecreation.recordingState.value)

        serviceController.destroy()
        activeFile.delete()
    }

    @Test
    fun database_allEntitiesAndDaos_accessibleThroughDatabase() {
        val db = RecovoDatabase.getInstance(context)
        assertNotNull(db.recordingDao())
        assertNotNull(db.folderDao())
        assertNotNull(db.tagDao())
        assertNotNull(db.bookmarkDao())
    }

    @Test
    fun architecture_cancelRecording_clearsActiveStateAndAllowsPlayback() = runTest {
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        val activeFile = File(context.cacheDir, "cancel_test.m4a").apply { writeBytes(ByteArray(128)) }
        val stateField = RecordingService::class.java.getDeclaredField("_recordingState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(service) as kotlinx.coroutines.flow.MutableStateFlow<RecordingState>
        stateFlow.value = RecordingState.Recording(activeFile, 5000L, 100)
        RecordingService.setRecordingActiveForTesting(true)
        assertTrue(RecordingService.isRecordingActive())

        // User cancels
        service.cancelRecording()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // State must transition to Idle and active flag must be cleared
        assertEquals(RecordingState.Idle, service.recordingState.value)
        assertFalse(RecordingService.isRecordingActive())

        serviceController.destroy()
        activeFile.delete()
    }

    @Test
    fun architecture_persistedRecording_canBeImmediatelyPlayedAndManaged() = runTest {
        val db = RecovoDatabase.getInstance(context)
        val storageManager = StorageManager(context)
        val repository = RecordingRepositoryImpl(
            recordingDao = db.recordingDao(),
            folderDao = db.folderDao(),
            tagDao = db.tagDao(),
            bookmarkDao = db.bookmarkDao(),
            storageManager = storageManager
        )

        val testFile = File(context.cacheDir, "test_interop.m4a").apply {
            writeBytes(ByteArray(1024))
            val ds = org.robolectric.shadows.util.DataSource.toDataSource(absolutePath)
            org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(ds, org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(6000, 0))
        }

        val testEntity = RecordingEntity(
            fileName = testFile.name,
            displayName = "Interop Test Track",
            filePath = testFile.absolutePath,
            mimeType = "audio/mp4",
            format = "M4A",
            durationMs = 6000L,
            fileSizeBytes = testFile.length(),
            sampleRate = 44100,
            bitRate = 128000,
            channelCount = 1
        )
        val savedId = repository.insertRecording(testEntity)
        assertTrue(savedId > 0L)

        // Verify retrieval and immediate playback
        val saved = repository.getRecordingById(savedId)
        assertNotNull(saved)

        player.play(saved!!)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertNull(player.playbackState.value.errorMessage)
        assertEquals("Interop Test Track", player.playbackState.value.currentRecording?.displayName)

        // Rename while active updates player metadata seamlessly
        repository.renameRecording(savedId, "Renamed Interop Track")
        val updated = repository.getRecordingById(savedId)
        assertNotNull(updated)
        player.updateCurrentRecordingMetadata(updated!!)

        assertEquals("Renamed Interop Track", player.playbackState.value.currentRecording?.displayName)

        player.stop()
        testFile.delete()
    }
}
