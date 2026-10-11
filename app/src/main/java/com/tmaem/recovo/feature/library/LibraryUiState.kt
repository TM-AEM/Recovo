package com.tmaem.recovo.feature.library

import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.domain.model.SortOrder

enum class LibraryTab {
    ALL,
    FAVORITES,
    FOLDERS,
    TAGS
}

data class RecordingUiModel(
    val entity: RecordingEntity,
    val isFileAvailable: Boolean,
    val formattedDate: String,
    val formattedDuration: String,
    val formattedSize: String,
    val folderName: String? = null,
    val tags: List<TagEntity> = emptyList(),
    val isFavorite: Boolean = entity.isFavorite,
    val fileErrorMessage: String? = null
)

sealed interface LibraryUiState {
    object Loading : LibraryUiState
    data class Success(
        val recordings: List<RecordingUiModel>,
        val allFolders: List<FolderEntity> = emptyList(),
        val allTags: List<TagEntity> = emptyList(),
        val folderRecordingCounts: Map<Long, Int> = emptyMap(),
        val tagRecordingCounts: Map<Long, Int> = emptyMap(),
        val selectedTab: LibraryTab = LibraryTab.ALL,
        val selectedFolder: FolderEntity? = null,
        val selectedTag: TagEntity? = null,
        val searchQuery: String = "",
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val selectedRecordingIds: Set<Long> = emptySet(),
        val isSelectionMode: Boolean = false,
        val totalRecordingsCount: Int = 0
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

/**
 * One-shot user-facing messages emitted by [LibraryViewModel]. The screen resolves them to a
 * localized string; the ViewModel carries only the parameters so it stays free of Android resources.
 */
sealed interface UiMessage {
    data class DeleteFailedSingle(val name: String) : UiMessage
    data class DeleteFailedBulk(val failed: Int, val total: Int) : UiMessage
}
