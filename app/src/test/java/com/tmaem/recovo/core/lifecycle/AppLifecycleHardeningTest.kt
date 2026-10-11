package com.tmaem.recovo.core.lifecycle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tmaem.recovo.core.database.RecovoDatabase
import com.tmaem.recovo.core.engine.RecordingQuality
import com.tmaem.recovo.core.engine.RecordingState
import com.tmaem.recovo.core.player.AudioPlayerProvider
import com.tmaem.recovo.core.service.PlaybackService
import com.tmaem.recovo.core.service.RecordingController
import com.tmaem.recovo.core.service.RecordingService
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.data.repository.RecordingRepositoryImpl
import com.tmaem.recovo.domain.model.SortOrder
import com.tmaem.recovo.feature.library.LibraryTab
import com.tmaem.recovo.feature.library.LibraryViewModel
import com.tmaem.recovo.feature.record.RecordViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AppLifecycleHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun database_singletonPreservedAcrossCalls() {
        val db1 = RecovoDatabase.getInstance(context)
        val db2 = RecovoDatabase.getInstance(context)
        assertSame("Database instance must be a singleton across the application lifecycle", db1, db2)
    }

    @Test
    fun audioPlayerProvider_singletonPreservedAcrossCalls() {
        val player1 = AudioPlayerProvider.get(context)
        val player2 = AudioPlayerProvider.get(context)
        assertSame("AudioPlayer instance must be an application singleton to preserve background playback", player1, player2)
    }

    @Test
    fun recordingService_onStartCommand_returnsStartNotSticky() {
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        val result = service.onStartCommand(null, 0, 1)
        assertEquals(
            "RecordingService must be START_NOT_STICKY to prevent unwanted recording resurrecting on process recreation",
            android.app.Service.START_NOT_STICKY,
            result
        )

        serviceController.destroy()
    }

    @Test
    fun playbackService_onStartCommand_returnsStartNotSticky() {
        val serviceController = Robolectric.buildService(PlaybackService::class.java)
        val service = serviceController.create().get()

        val result = service.onStartCommand(null, 0, 1)
        assertEquals(
            "PlaybackService must be START_NOT_STICKY to prevent orphan playback service restarting on process recreation",
            android.app.Service.START_NOT_STICKY,
            result
        )

        serviceController.destroy()
    }

    @Test
    fun libraryViewModel_savedStateHandle_restoresAndPersistsState() {
        val initialHandle = SavedStateHandle(
            mapOf(
                "key_library_search_query" to "Meeting Notes",
                "key_library_selected_tab" to LibraryTab.FAVORITES.name,
                "key_library_sort_order" to SortOrder.NAME.name
            )
        )

        val db = RecovoDatabase.getInstance(context)
        val storageManager = StorageManager(context)
        val repo = RecordingRepositoryImpl(
            recordingDao = db.recordingDao(),
            folderDao = db.folderDao(),
            tagDao = db.tagDao(),
            bookmarkDao = db.bookmarkDao(),
            storageManager = storageManager
        )
        val player = AudioPlayerProvider.get(context)

        val viewModel = LibraryViewModel(
            recordingRepository = repo,
            audioPlayer = player,
            savedStateHandle = initialHandle
        )

        // Verify state is restored from SavedStateHandle on process recreation
        assertEquals("Meeting Notes", viewModel.searchQuery.value)
        assertEquals(LibraryTab.FAVORITES, viewModel.selectedTab.value)
        assertEquals(SortOrder.NAME, viewModel.sortOrder.value)

        // Update state and verify it updates the SavedStateHandle
        viewModel.setSearchQuery("Design Review")
        viewModel.selectTab(LibraryTab.FOLDERS)
        viewModel.setSortOrder(SortOrder.DURATION)

        assertEquals("Design Review", initialHandle.get<String>("key_library_search_query"))
        assertEquals(LibraryTab.FOLDERS.name, initialHandle.get<String>("key_library_selected_tab"))
        assertEquals(SortOrder.DURATION.name, initialHandle.get<String>("key_library_sort_order"))
    }

    @Test
    fun recordViewModel_lifecycleCleanup_unbindsController() {
        val controller = RecordingController(context)
        val viewModel = RecordViewModel(controller)

        // Verify initial state is Idle
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)

        // Verify onCleared safely unbinds without throwing
        val onClearedMethod = RecordViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)
    }

    @Test
    fun recordViewModel_factory_createsValidInstance() {
        val factory = RecordViewModel.Factory(context)
        val viewModel = factory.create(RecordViewModel::class.java)
        assertNotNull(viewModel)
        assertEquals(RecordingQuality.HIGH, viewModel.selectedQuality.value)
    }

    @Test
    fun recordingController_restoresStateFromActiveRecordingService() {
        // Build and start RecordingService
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        // Verify active service instance registration
        assertSame(service, RecordingService.getActiveService())

        // Creating a new RecordingController while service is running initializes state seamlessly
        val controller = RecordingController(context)
        assertNotNull(controller.recordingState.value)

        // Cleanup
        controller.unbind()
        serviceController.destroy()
    }

    @Test
    fun recordingController_onServiceDisconnected_transitionsActiveRecordingToError() {
        val controller = RecordingController(context)

        // Simulate active recording state
        val dummyFile = java.io.File(context.cacheDir, "disconnect_test.m4a")
        val stateField = RecordingController::class.java.getDeclaredField("_recordingState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = stateField.get(controller) as kotlinx.coroutines.flow.MutableStateFlow<RecordingState>
        flow.value = RecordingState.Recording(dummyFile, 5000L, 200)

        // Retrieve the internal connection
        val connField = RecordingController::class.java.getDeclaredField("connection")
        connField.isAccessible = true
        val connection = connField.get(controller) as android.content.ServiceConnection

        // Simulate unexpected service disconnection (process crash / kill)
        connection.onServiceDisconnected(android.content.ComponentName(context, RecordingService::class.java))

        assertTrue(
            "Unexpected disconnection during active recording must transition controller to Error state",
            controller.recordingState.value is RecordingState.Error
        )

        controller.release()
    }

    @Test
    fun recordingService_onDestroy_whileRecording_cancelsAndCleansUpFile() {
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        val activeFile = java.io.File(context.cacheDir, "service_destroy_test.m4a").apply {
            writeBytes(ByteArray(1024))
        }
        assertTrue(activeFile.exists())

        // Set service to active recording with currentFile
        val stateField = RecordingService::class.java.getDeclaredField("_recordingState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(service) as kotlinx.coroutines.flow.MutableStateFlow<RecordingState>
        stateFlow.value = RecordingState.Recording(activeFile, 15000L, 300)

        val engineField = RecordingService::class.java.getDeclaredField("recordingEngine")
        engineField.isAccessible = true
        val engine = engineField.get(service) as com.tmaem.recovo.core.engine.MediaRecorderEngine
        val fileField = com.tmaem.recovo.core.engine.MediaRecorderEngine::class.java.getDeclaredField("currentFile")
        fileField.isAccessible = true
        fileField.set(engine, activeFile)

        // Destroy service while recording
        serviceController.destroy()

        // Verify partial file was cleaned up and active instance was nulled
        org.junit.Assert.assertNull(RecordingService.getActiveService())
        assertFalse(
            "Incomplete partial file must be deleted upon unexpected service destruction",
            activeFile.exists()
        )
    }

    @Test
    fun recordingService_notificationActions_areIdempotentAndSafe() {
        val serviceController = Robolectric.buildService(RecordingService::class.java)
        val service = serviceController.create().get()

        // Calling pause/resume/stop intents while Idle must be completely safe and no-op
        service.onStartCommand(RecordingService.pauseIntent(context), 0, 1)
        assertTrue(service.recordingState.value is RecordingState.Idle)

        service.onStartCommand(RecordingService.resumeIntent(context), 0, 2)
        assertTrue(service.recordingState.value is RecordingState.Idle)

        service.onStartCommand(RecordingService.stopIntent(context), 0, 3)
        assertTrue(service.recordingState.value is RecordingState.Idle)

        service.onStartCommand(RecordingService.cancelIntent(context), 0, 4)
        assertTrue(service.recordingState.value is RecordingState.Idle)

        serviceController.destroy()
    }

    @Test
    fun recordingController_repeatedCommands_doNotCreateDuplicateServiceBindings() {
        val app = context as android.app.Application
        fun boundConnectionCount(): Int = shadowOf(app).boundServiceConnections.size

        val controller = RecordingController(context)
        assertEquals("Constructor must establish exactly one service binding", 1, boundConnectionCount())

        // Rapid duplicate commands must never issue an additional bindService() request
        controller.startRecording("Binding Regression")
        assertEquals("startRecording must not create a duplicate binding", 1, boundConnectionCount())

        controller.startRecording("Binding Regression Duplicate")
        assertEquals("Repeated startRecording must not create a duplicate binding", 1, boundConnectionCount())

        controller.pauseRecording()
        controller.resumeRecording()
        controller.stopRecording()
        assertEquals("Lifecycle commands must not create duplicate bindings", 1, boundConnectionCount())

        controller.release()
        assertEquals("release() must fully unbind the single established binding", 0, boundConnectionCount())
    }

    @Test
    fun recordingController_unbind_isIdempotent() {
        val app = context as android.app.Application
        fun boundConnectionCount(): Int = shadowOf(app).boundServiceConnections.size

        val controller = RecordingController(context)
        assertEquals(1, boundConnectionCount())

        controller.unbind()
        controller.unbind()
        controller.release()

        assertEquals("Repeated unbind/release must not throw and must fully clear the binding", 0, boundConnectionCount())
    }
}
