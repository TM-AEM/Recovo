package com.example.core.lifecycle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.database.RecovoDatabase
import com.example.core.engine.RecordingQuality
import com.example.core.engine.RecordingState
import com.example.core.player.AudioPlayerProvider
import com.example.core.service.PlaybackService
import com.example.core.service.RecordingController
import com.example.core.service.RecordingService
import com.example.core.storage.StorageManager
import com.example.data.repository.RecordingRepositoryImpl
import com.example.domain.model.SortOrder
import com.example.feature.library.LibraryTab
import com.example.feature.library.LibraryViewModel
import com.example.feature.record.RecordViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

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
}
