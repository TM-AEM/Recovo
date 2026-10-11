package com.tmaem.recovo.feature.library

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tmaem.recovo.core.database.RecovoDatabase
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.RecordingTagCrossRef
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.core.player.AudioPlayer
import com.tmaem.recovo.core.player.AudioPlayerProvider
import com.tmaem.recovo.core.player.PlaybackState
import com.tmaem.recovo.core.player.RepeatMode
import com.tmaem.recovo.core.storage.StorageManager
import com.tmaem.recovo.core.storage.StorageResult
import com.tmaem.recovo.data.repository.RecordingRepository
import com.tmaem.recovo.data.repository.RecordingRepositoryImpl
import com.tmaem.recovo.domain.model.SortOrder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val recordingRepository: RecordingRepository,
    private val audioPlayer: AudioPlayer,
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = audioPlayer.playbackState

    private val _searchQuery = MutableStateFlow(savedStateHandle?.get<String>(KEY_SEARCH_QUERY) ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _debouncedSearchQuery = MutableStateFlow(savedStateHandle?.get<String>(KEY_SEARCH_QUERY) ?: "")

    private val _selectedTab = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_SELECTED_TAB)?.let {
            runCatching { LibraryTab.valueOf(it) }.getOrNull()
        } ?: LibraryTab.ALL
    )
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FolderEntity?>(null)
    val selectedFolder: StateFlow<FolderEntity?> = _selectedFolder.asStateFlow()

    private val _selectedTag = MutableStateFlow<TagEntity?>(null)
    val selectedTag: StateFlow<TagEntity?> = _selectedTag.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_SORT_ORDER)?.let {
            runCatching { SortOrder.valueOf(it) }.getOrNull()
        } ?: SortOrder.NEWEST
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedRecordingIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecordingIds: StateFlow<Set<Long>> = _selectedRecordingIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private var currentFilteredRecordings: List<RecordingEntity> = emptyList()

    // One-shot UI messages (e.g. physical file-deletion failures). Buffered so an event emitted
    // while the screen is not collecting is still delivered once it (re)subscribes. Exactly one
    // consumer (the LibraryScreen LaunchedEffect); ViewModel-scoped so no extra host is introduced.
    private val _userMessages = Channel<UiMessage>(Channel.BUFFERED)
    val userMessages: Flow<UiMessage> = _userMessages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(250L)
                .collect { debounced ->
                    _debouncedSearchQuery.value = debounced
                }
        }
        // Safely synchronize persisted duration if actual playback duration differs
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                val rec = state.currentRecording
                if (state.isPrepared && rec != null && state.durationMs > 0L) {
                    if (kotlin.math.abs(state.durationMs - rec.durationMs) >= 500L) {
                        try {
                            val updated = rec.copy(durationMs = state.durationMs, modifiedAt = System.currentTimeMillis())
                            recordingRepository.updateRecording(updated)
                            audioPlayer.updateCurrentRecordingMetadata(updated)
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
    }

    // Reactive data streams from Repository
    private val allRecordingsFlow = recordingRepository.observeRecordings(SortOrder.NEWEST)
    private val foldersFlow = recordingRepository.observeFolders()
    private val tagsFlow = recordingRepository.observeTags()
    private val crossRefsFlow = recordingRepository.observeAllCrossRefs()

    val uiState: StateFlow<LibraryUiState> = combine(
        allRecordingsFlow,
        foldersFlow,
        tagsFlow,
        crossRefsFlow,
        _debouncedSearchQuery,
        _selectedTab,
        _selectedFolder,
        _selectedTag,
        _sortOrder,
        _selectedRecordingIds,
        _isSelectionMode
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val recordings = args[0] as List<RecordingEntity>
        @Suppress("UNCHECKED_CAST")
        val folders = args[1] as List<FolderEntity>
        @Suppress("UNCHECKED_CAST")
        val tags = args[2] as List<TagEntity>
        @Suppress("UNCHECKED_CAST")
        val crossRefs = args[3] as List<RecordingTagCrossRef>
        val query = args[4] as String
        val tab = args[5] as LibraryTab
        val currentFolder = args[6] as FolderEntity?
        val currentTag = args[7] as TagEntity?
        val sort = args[8] as SortOrder
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[9] as Set<Long>
        val selectionMode = args[10] as Boolean

        // Quick lookup maps to avoid N+1 queries
        val folderMap = folders.associateBy { it.id }
        val tagsMap = tags.associateBy { it.id }

        // RecordingId -> List<TagEntity>
        val recordingTagsMap = mutableMapOf<Long, MutableList<TagEntity>>()
        for (ref in crossRefs) {
            tagsMap[ref.tagId]?.let { tag ->
                recordingTagsMap.getOrPut(ref.recordingId) { mutableListOf() }.add(tag)
            }
        }

        // Folder & Tag recording counts
        val folderCounts = mutableMapOf<Long, Int>()
        for (rec in recordings) {
            rec.folderId?.let { fId ->
                folderCounts[fId] = (folderCounts[fId] ?: 0) + 1
            }
        }

        val tagCounts = mutableMapOf<Long, Int>()
        for (ref in crossRefs) {
            tagCounts[ref.tagId] = (tagCounts[ref.tagId] ?: 0) + 1
        }

        // Build UI models
        val allUiModels = recordings.map { entity ->
            val file = File(entity.filePath)
            val fileExists = file.exists()
            val isZeroByte = fileExists && file.length() == 0L
            val isAvailable = fileExists && file.isFile && !isZeroByte
            val fileErrorMessage = when {
                !fileExists -> "Audio file missing from storage"
                isZeroByte -> "Audio file is corrupted or empty (0 bytes)"
                else -> null
            }
            val actualSizeBytes = if (entity.fileSizeBytes > 0L) entity.fileSizeBytes else if (isAvailable) file.length() else 0L
            val assignedTags = recordingTagsMap[entity.id] ?: emptyList()
            val folderName = entity.folderId?.let { folderMap[it]?.name }

            RecordingUiModel(
                entity = entity,
                isFileAvailable = isAvailable,
                formattedDate = formatDate(entity.createdAt),
                formattedDuration = formatDuration(entity.durationMs),
                formattedSize = formatFileSize(actualSizeBytes),
                folderName = folderName,
                tags = assignedTags,
                isFavorite = entity.isFavorite,
                fileErrorMessage = fileErrorMessage
            )
        }

        // 1. Filter by Tab
        val tabFiltered = when (tab) {
            LibraryTab.ALL -> allUiModels
            LibraryTab.FAVORITES -> allUiModels.filter { it.isFavorite }
            LibraryTab.FOLDERS -> {
                if (currentFolder != null) {
                    allUiModels.filter { it.entity.folderId == currentFolder.id }
                } else {
                    allUiModels
                }
            }
            LibraryTab.TAGS -> {
                if (currentTag != null) {
                    allUiModels.filter { item -> item.tags.any { it.id == currentTag.id } }
                } else {
                    allUiModels
                }
            }
        }

        // 2. Filter by Search Query (Case-insensitive, Arabic & English, displayName, folder, tags)
        val searchFiltered = if (query.isBlank()) {
            tabFiltered
        } else {
            val q = query.trim()
            tabFiltered.filter { item ->
                item.entity.displayName.contains(q, ignoreCase = true) ||
                    (item.folderName?.contains(q, ignoreCase = true) == true) ||
                    item.tags.any { it.name.contains(q, ignoreCase = true) }
            }
        }

        // 3. Sort order
        val sortedList = when (sort) {
            SortOrder.NEWEST -> searchFiltered.sortedByDescending { it.entity.createdAt }
            SortOrder.OLDEST -> searchFiltered.sortedBy { it.entity.createdAt }
            SortOrder.NAME -> searchFiltered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entity.displayName })
            SortOrder.NAME_DESC -> searchFiltered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.entity.displayName })
            SortOrder.DURATION -> searchFiltered.sortedByDescending { it.entity.durationMs }
            SortOrder.SIZE -> searchFiltered.sortedByDescending { it.entity.fileSizeBytes }
        }

        currentFilteredRecordings = sortedList.map { it.entity }

        LibraryUiState.Success(
            recordings = sortedList,
            allFolders = folders,
            allTags = tags,
            folderRecordingCounts = folderCounts,
            tagRecordingCounts = tagCounts,
            selectedTab = tab,
            selectedFolder = currentFolder,
            selectedTag = currentTag,
            searchQuery = _searchQuery.value,
            sortOrder = sort,
            selectedRecordingIds = selectedIds,
            isSelectionMode = selectionMode,
            totalRecordingsCount = recordings.size
        ) as LibraryUiState
    }
        .catch { e ->
            emit(LibraryUiState.Error(e.message ?: "Failed to load audio library"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState.Loading
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        savedStateHandle?.set(KEY_SEARCH_QUERY, query)
        if (query.isBlank()) {
            _debouncedSearchQuery.value = ""
        }
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
        savedStateHandle?.set(KEY_SEARCH_QUERY, "")
        _debouncedSearchQuery.value = ""
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
        savedStateHandle?.set(KEY_SELECTED_TAB, tab.name)
        if (tab != LibraryTab.FOLDERS) {
            _selectedFolder.value = null
        }
        if (tab != LibraryTab.TAGS) {
            _selectedTag.value = null
        }
    }

    fun selectFolder(folder: FolderEntity?) {
        _selectedFolder.value = folder
    }

    fun selectTag(tag: TagEntity?) {
        _selectedTag.value = tag
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        savedStateHandle?.set(KEY_SORT_ORDER, order.name)
    }

    // Favorites
    fun toggleFavorite(recording: RecordingEntity) {
        viewModelScope.launch {
            recordingRepository.toggleFavorite(recording.id, !recording.isFavorite)
        }
    }

    // Rename
    fun renameRecording(recording: RecordingEntity, newName: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            val result = recordingRepository.renameRecording(recording.id, trimmed)
            if (result.isSuccess) {
                // If the renamed track is currently loaded/playing, update player & MediaSession metadata immediately
                if (playbackState.value.currentRecording?.id == recording.id) {
                    audioPlayer.updateCurrentRecordingMetadata(
                        recording.copy(displayName = trimmed, modifiedAt = System.currentTimeMillis())
                    )
                }
                onResult?.invoke(true, null)
            } else {
                onResult?.invoke(false, result.exceptionOrNull()?.message ?: "Rename failed")
            }
        }
    }

    // Move to folder
    fun moveRecordingToFolder(recording: RecordingEntity, folderId: Long?) {
        viewModelScope.launch {
            recordingRepository.moveRecordingToFolder(recording.id, folderId)
        }
    }

    // Tags
    fun setTagsForRecording(recording: RecordingEntity, tagIds: Set<Long>) {
        viewModelScope.launch {
            recordingRepository.setTagsForRecording(recording.id, tagIds)
        }
    }

    // Folders operations
    fun createFolder(name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.createFolderSafely(name)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to create folder")
            }
        }
    }

    fun renameFolder(folder: FolderEntity, newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.renameFolder(folder.id, newName)
            if (result.isSuccess) {
                if (_selectedFolder.value?.id == folder.id) {
                    _selectedFolder.value = folder.copy(name = newName.trim())
                }
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to rename folder")
            }
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            if (_selectedFolder.value?.id == folder.id) {
                _selectedFolder.value = null
            }
            recordingRepository.deleteFolder(folder)
        }
    }

    // Tag operations
    fun createTag(name: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.createTagSafely(name)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to create tag")
            }
        }
    }

    fun renameTag(tag: TagEntity, newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.renameTag(tag.id, newName)
            if (result.isSuccess) {
                if (_selectedTag.value?.id == tag.id) {
                    _selectedTag.value = tag.copy(name = newName.trim())
                }
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to rename tag")
            }
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            if (_selectedTag.value?.id == tag.id) {
                _selectedTag.value = null
            }
            recordingRepository.deleteTag(tag)
        }
    }

    // Multi-Selection
    fun enterSelectionMode(initialSelectedId: Long? = null) {
        _isSelectionMode.value = true
        if (initialSelectedId != null) {
            _selectedRecordingIds.value = setOf(initialSelectedId)
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecordingIds.value = emptySet()
    }

    fun toggleSelection(recordingId: Long) {
        val current = _selectedRecordingIds.value
        val updated = if (current.contains(recordingId)) {
            current - recordingId
        } else {
            current + recordingId
        }
        _selectedRecordingIds.value = updated
        if (updated.isEmpty() && _isSelectionMode.value) {
            _isSelectionMode.value = false
        }
    }

    fun selectAll() {
        val allIds = currentFilteredRecordings.map { it.id }.toSet()
        _selectedRecordingIds.value = allIds
    }

    fun clearSelection() {
        _selectedRecordingIds.value = emptySet()
    }

    fun setFavoritesForSelected(isFavorite: Boolean) {
        viewModelScope.launch {
            val ids = _selectedRecordingIds.value.toList()
            recordingRepository.setFavorites(ids, isFavorite)
            exitSelectionMode()
        }
    }

    fun moveSelectedToFolder(folderId: Long?) {
        viewModelScope.launch {
            val ids = _selectedRecordingIds.value.toList()
            recordingRepository.moveRecordingsToFolder(ids, folderId)
            exitSelectionMode()
        }
    }

    fun deleteSelectedRecordings() {
        viewModelScope.launch {
            val selectedIds = _selectedRecordingIds.value
            val toDelete = currentFilteredRecordings.filter { selectedIds.contains(it.id) }
            val currentPlayingId = playbackState.value.currentRecording?.id
            if (currentPlayingId != null && selectedIds.contains(currentPlayingId)) {
                audioPlayer.stop()
            }
            val results = recordingRepository.deleteRecordings(toDelete)
            val failed = results.count { it is StorageResult.Error }
            if (failed > 0) {
                // The repository keeps the Room row for each item whose physical file could not be
                // removed, so those recordings remain. Report the real failure count exactly once
                // rather than multiple overlapping messages.
                _userMessages.trySend(UiMessage.DeleteFailedBulk(failed, toDelete.size))
            }
            exitSelectionMode()
        }
    }

    // Playback
    fun playRecording(recording: RecordingEntity) {
        val current = playbackState.value
        if (current.currentRecording?.id == recording.id) {
            if (current.isPlaying) {
                audioPlayer.pause()
            } else {
                audioPlayer.resume()
            }
        } else {
            // Update playlist to the current filtered list before playing
            audioPlayer.setPlaylist(currentFilteredRecordings)
            audioPlayer.play(recording)
        }
    }

    fun togglePlayPause() {
        val current = playbackState.value
        if (current.isPlaying) {
            audioPlayer.pause()
        } else {
            audioPlayer.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        audioPlayer.seekRelative(offsetMs)
    }

    fun playNext() {
        audioPlayer.playNext()
    }

    fun playPrevious() {
        audioPlayer.playPrevious()
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (playbackState.value.currentRecording?.id == recording.id) {
                audioPlayer.stop()
            }
            val result = recordingRepository.deleteRecording(recording)
            if (result is StorageResult.Error) {
                // The repository keeps the Room row when the physical file cannot be removed, so the
                // recording stays visible. Surface the failure instead of silently reporting success.
                _userMessages.trySend(UiMessage.DeleteFailedSingle(recording.displayName))
            }
        }
    }

    fun clearPlaybackError() {
        audioPlayer.clearError()
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
    }

    fun startTemporaryFastForward() {
        audioPlayer.startTemporaryFastForward()
    }

    fun stopTemporaryFastForward() {
        audioPlayer.stopTemporaryFastForward()
    }

    fun setRepeatMode(mode: RepeatMode) {
        audioPlayer.setRepeatMode(mode)
    }

    fun toggleRepeatMode() {
        audioPlayer.toggleRepeatMode()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        audioPlayer.setShuffleEnabled(enabled)
    }

    fun toggleShuffle() {
        audioPlayer.toggleShuffle()
    }

    fun setSleepTimer(durationMinutes: Int?) {
        audioPlayer.setSleepTimer(durationMinutes)
    }

    fun cancelSleepTimer() {
        audioPlayer.cancelSleepTimer()
    }

    override fun onCleared() {
        // AudioPlayer is an application-scoped singleton shared with PlaybackService.
        // We intentionally do NOT call audioPlayer.release() here so background playback survives navigation.
        super.onCleared()
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "key_library_search_query"
        private const val KEY_SELECTED_TAB = "key_library_selected_tab"
        private const val KEY_SORT_ORDER = "key_library_sort_order"

        // ICU skeleton for date + time. The framework expands it into the
        // locale's own ordering, separators and month names (e.g. English vs
        // Arabic) instead of a hard-coded pattern.
        private const val DATE_TIME_SKELETON = "yMMMdhmma"

        fun formatDuration(durationMs: Long): String {
            val totalSecs = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSecs / 60
            val seconds = totalSecs % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }

        fun formatDate(timestampMs: Long): String {
            return try {
                val locale = Locale.getDefault()
                val pattern = DateFormat.getBestDateTimePattern(locale, DATE_TIME_SKELETON)
                SimpleDateFormat(pattern, locale).format(Date(timestampMs))
            } catch (e: Exception) {
                "Recent"
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = RecovoDatabase.getInstance(appContext)
            val storageManager = StorageManager(appContext)
            val repository = RecordingRepositoryImpl(
                recordingDao = db.recordingDao(),
                folderDao = db.folderDao(),
                tagDao = db.tagDao(),
                bookmarkDao = db.bookmarkDao(),
                storageManager = storageManager
            )
            val audioPlayer = AudioPlayerProvider.get(appContext)
            return LibraryViewModel(repository, audioPlayer) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val appContext = context.applicationContext
            val db = RecovoDatabase.getInstance(appContext)
            val storageManager = StorageManager(appContext)
            val repository = RecordingRepositoryImpl(
                recordingDao = db.recordingDao(),
                folderDao = db.folderDao(),
                tagDao = db.tagDao(),
                bookmarkDao = db.bookmarkDao(),
                storageManager = storageManager
            )
            val audioPlayer = AudioPlayerProvider.get(appContext)
            val savedStateHandle = extras.createSavedStateHandle()
            return LibraryViewModel(repository, audioPlayer, savedStateHandle) as T
        }
    }
}
