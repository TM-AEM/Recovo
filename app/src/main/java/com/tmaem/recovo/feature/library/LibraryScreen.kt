package com.tmaem.recovo.feature.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmaem.recovo.R
import com.tmaem.recovo.core.database.model.FolderEntity
import com.tmaem.recovo.core.database.model.RecordingEntity
import com.tmaem.recovo.core.database.model.TagEntity
import com.tmaem.recovo.core.designsystem.theme.RecovoDimensions
import com.tmaem.recovo.core.designsystem.theme.RecovoSpacing
import com.tmaem.recovo.domain.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onNavigateToRecord: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // Single collector for one-shot deletion-failure messages (no Snackbar host exists in this
    // screen; the project reports transient errors via Toast, which we follow here).
    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { message ->
            val text = when (message) {
                is UiMessage.DeleteFailedSingle ->
                    context.getString(R.string.library_delete_failed_single_message, message.name)
                is UiMessage.DeleteFailedBulk ->
                    context.resources.getQuantityString(
                        R.plurals.library_bulk_delete_failed_message,
                        message.failed,
                        message.failed,
                        message.total
                    )
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    // Dialog and Menu states
    var recordingToDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    var recordingToRename by remember { mutableStateOf<RecordingEntity?>(null) }
    var recordingToMove by remember { mutableStateOf<RecordingEntity?>(null) }
    var recordingToManageTags by remember { mutableStateOf<RecordingUiModel?>(null) }

    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

    var showCreateTagDialog by rememberSaveable { mutableStateOf(false) }
    var tagToRename by remember { mutableStateOf<TagEntity?>(null) }
    var tagToDelete by remember { mutableStateOf<TagEntity?>(null) }

    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkMoveFolderDialog by rememberSaveable { mutableStateOf(false) }

    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showTimerDialog by rememberSaveable { mutableStateOf(false) }

    val state = uiState
    val isSelectionMode = (state as? LibraryUiState.Success)?.isSelectionMode == true
    val selectedIds = (state as? LibraryUiState.Success)?.selectedRecordingIds ?: emptySet()
    val openFolder = (state as? LibraryUiState.Success)?.selectedFolder

    // Hardware/Gesture back handler:
    // If in selection mode -> exit selection
    // If inside a folder -> return to folders list
    // Otherwise -> navigate back
    BackHandler {
        if (isSelectionMode) {
            viewModel.exitSelectionMode()
        } else if (openFolder != null) {
            viewModel.selectFolder(null)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Multi-Selection TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = pluralStringResource(R.plurals.library_selected_count, selectedIds.size, selectedIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.exitSelectionMode() },
                            modifier = Modifier.testTag("selection_exit_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_cd_exit_selection))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier.testTag("selection_select_all_button")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.library_cd_select_all))
                        }
                        IconButton(
                            onClick = { viewModel.setFavoritesForSelected(true) },
                            modifier = Modifier.testTag("selection_favorite_button")
                        ) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.library_cd_favorite_selected))
                        }
                        IconButton(
                            onClick = { showBulkMoveFolderDialog = true },
                            modifier = Modifier.testTag("selection_move_button")
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = stringResource(R.string.library_cd_move_to_folder))
                        }
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.testTag("selection_delete_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.library_cd_delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.library_title))
                            if (state is LibraryUiState.Success) {
                                val count = state.recordings.size
                                Text(
                                    text = pluralStringResource(R.plurals.library_recording_count, count, count),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (openFolder != null) {
                                    viewModel.selectFolder(null)
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.testTag("library_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.library_cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSortDialog = true },
                            modifier = Modifier.testTag("library_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = stringResource(R.string.sort_by)
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = playbackState.currentRecording != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PlaybackBar(
                    playbackState = playbackState,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onRewind = { viewModel.seekRelative(-10000L) },
                    onForward = { viewModel.seekRelative(10000L) },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onPlayNext = { viewModel.playNext() },
                    onToggleRepeat = { viewModel.toggleRepeatMode() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onOpenSpeedDialog = { showSpeedDialog = true },
                    onOpenTimerDialog = { showTimerDialog = true },
                    onStartFastForward = { viewModel.startTemporaryFastForward() },
                    onStopFastForward = { viewModel.stopTemporaryFastForward() },
                    onClose = { viewModel.stopPlayback() },
                    onDismissError = { viewModel.clearPlaybackError() }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val successState = uiState) {
                is LibraryUiState.Loading -> {
                    val loadingCd = stringResource(R.string.a11y_loading_library)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .testTag("library_loading_indicator")
                                .semantics { contentDescription = loadingCd }
                        )
                    }
                }

                is LibraryUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(RecovoSpacing.large),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(RecovoSpacing.medium))
                        Text(
                            text = stringResource(R.string.library_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(RecovoSpacing.small))
                        Text(
                            text = successState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is LibraryUiState.Success -> {
                    // Search Bar
                    SearchHeader(
                        query = successState.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onClear = { viewModel.clearSearchQuery() }
                    )

                    // Navigation Tabs: All, Favorites, Folders, Tags
                    LibraryTabsHeader(
                        selectedTab = successState.selectedTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )

                    // Tab specific sub-headers (Folders navigation or Tags filter chips)
                    if (successState.selectedTab == LibraryTab.FOLDERS) {
                        FoldersSubHeader(
                            openFolder = successState.selectedFolder,
                            folders = successState.allFolders,
                            recordingCounts = successState.folderRecordingCounts,
                            onFolderClick = { viewModel.selectFolder(it) },
                            onBackToAllFolders = { viewModel.selectFolder(null) },
                            onCreateFolder = { showCreateFolderDialog = true },
                            onRenameFolder = { folderToRename = it },
                            onDeleteFolder = { folderToDelete = it }
                        )
                    } else if (successState.selectedTab == LibraryTab.TAGS) {
                        TagsSubHeader(
                            tags = successState.allTags,
                            selectedTag = successState.selectedTag,
                            recordingCounts = successState.tagRecordingCounts,
                            onTagSelected = { viewModel.selectTag(if (successState.selectedTag?.id == it.id) null else it) },
                            onCreateTag = { showCreateTagDialog = true },
                            onRenameTag = { tagToRename = it },
                            onDeleteTag = { tagToDelete = it }
                        )
                    }

                    // Content View: Recordings list or empty state
                    if (successState.recordings.isEmpty()) {
                        EmptyStateView(
                            tab = successState.selectedTab,
                            searchQuery = successState.searchQuery,
                            hasOpenFolder = successState.selectedFolder != null,
                            hasSelectedTag = successState.selectedTag != null,
                            onClearSearch = { viewModel.clearSearchQuery() },
                            onNavigateToRecord = onNavigateToRecord,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = RecovoSpacing.medium,
                                end = RecovoSpacing.medium,
                                top = RecovoSpacing.small,
                                bottom = RecovoSpacing.large
                            ),
                            verticalArrangement = Arrangement.spacedBy(RecovoSpacing.small),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_recordings_list")
                        ) {
                            items(
                                items = successState.recordings,
                                key = { it.entity.id }
                            ) { item ->
                                val isCurrentTrack = playbackState.currentRecording?.id == item.entity.id
                                val isTrackPlaying = isCurrentTrack && playbackState.isPlaying
                                val isSelected = successState.selectedRecordingIds.contains(item.entity.id)

                                RecordingCard(
                                    item = item,
                                    isCurrentTrack = isCurrentTrack,
                                    isPlaying = isTrackPlaying,
                                    isSelected = isSelected,
                                    isSelectionMode = successState.isSelectionMode,
                                    onPlayClick = { viewModel.playRecording(item.entity) },
                                    onItemClick = {
                                        if (successState.isSelectionMode) {
                                            viewModel.toggleSelection(item.entity.id)
                                        } else {
                                            viewModel.playRecording(item.entity)
                                        }
                                    },
                                    onLongClick = {
                                        if (!successState.isSelectionMode) {
                                            viewModel.enterSelectionMode(item.entity.id)
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.entity) },
                                    onRenameClick = { recordingToRename = item.entity },
                                    onMoveToFolderClick = { recordingToMove = item.entity },
                                    onManageTagsClick = { recordingToManageTags = item },
                                    onDeleteClick = { recordingToDelete = item.entity },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Rename recording dialog
    recordingToRename?.let { rec ->
        RenameRecordingDialog(
            recording = rec,
            onDismiss = { recordingToRename = null },
            onConfirm = { newName ->
                viewModel.renameRecording(rec, newName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
                recordingToRename = null
            }
        )
    }

    // Move single recording to folder dialog
    recordingToMove?.let { rec ->
        val folders = (uiState as? LibraryUiState.Success)?.allFolders ?: emptyList()
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = rec.folderId,
            onDismiss = { recordingToMove = null },
            onFolderSelected = { targetFolderId ->
                viewModel.moveRecordingToFolder(rec, targetFolderId)
                recordingToMove = null
            }
        )
    }

    // Manage tags for recording dialog
    recordingToManageTags?.let { item ->
        val allTags = (uiState as? LibraryUiState.Success)?.allTags ?: emptyList()
        ManageTagsDialog(
            allTags = allTags,
            assignedTagIds = item.tags.map { it.id }.toSet(),
            onDismiss = { recordingToManageTags = null },
            onConfirm = { selectedTagIds ->
                viewModel.setTagsForRecording(item.entity, selectedTagIds)
                recordingToManageTags = null
            },
            onCreateNewTag = { newTagName ->
                viewModel.createTag(newTagName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Single Recording Delete confirmation dialog
    recordingToDelete?.let { recording ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_recording_title),
            message = stringResource(R.string.library_delete_recording_message, recording.displayName),
            onDismiss = { recordingToDelete = null },
            onConfirm = {
                viewModel.deleteRecording(recording)
                recordingToDelete = null
            }
        )
    }

    // Bulk Delete confirmation dialog
    if (showBulkDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_selected_title),
            message = pluralStringResource(R.plurals.library_bulk_delete_message, selectedIds.size, selectedIds.size),
            onDismiss = { showBulkDeleteDialog = false },
            onConfirm = {
                viewModel.deleteSelectedRecordings()
                showBulkDeleteDialog = false
            }
        )
    }

    // Bulk Move to folder dialog
    if (showBulkMoveFolderDialog) {
        val folders = (uiState as? LibraryUiState.Success)?.allFolders ?: emptyList()
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = null,
            onDismiss = { showBulkMoveFolderDialog = false },
            onFolderSelected = { targetFolderId ->
                viewModel.moveSelectedToFolder(targetFolderId)
                showBulkMoveFolderDialog = false
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        FolderInputDialog(
            title = stringResource(R.string.new_folder),
            confirmButtonText = stringResource(R.string.create_folder),
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createFolder(folderName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
                showCreateFolderDialog = false
            }
        )
    }

    // Rename Folder Dialog
    folderToRename?.let { folder ->
        FolderInputDialog(
            title = stringResource(R.string.rename_folder),
            initialName = folder.name,
            confirmButtonText = stringResource(R.string.save),
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                viewModel.renameFolder(folder, newName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
                folderToRename = null
            }
        )
    }

    // Delete Folder Dialog
    folderToDelete?.let { folder ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_folder),
            message = stringResource(R.string.delete_folder_message),
            onDismiss = { folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolder(folder)
                folderToDelete = null
            }
        )
    }

    // Create Tag Dialog
    if (showCreateTagDialog) {
        TagInputDialog(
            title = stringResource(R.string.new_tag),
            confirmButtonText = stringResource(R.string.create_tag),
            onDismiss = { showCreateTagDialog = false },
            onConfirm = { tagName ->
                viewModel.createTag(tagName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
                showCreateTagDialog = false
            }
        )
    }

    // Rename Tag Dialog
    tagToRename?.let { tag ->
        TagInputDialog(
            title = stringResource(R.string.rename_tag),
            initialName = tag.name,
            confirmButtonText = stringResource(R.string.save),
            onDismiss = { tagToRename = null },
            onConfirm = { newName ->
                viewModel.renameTag(tag, newName) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
                tagToRename = null
            }
        )
    }

    // Delete Tag Dialog
    tagToDelete?.let { tag ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_tag),
            message = stringResource(R.string.delete_tag_message),
            onDismiss = { tagToDelete = null },
            onConfirm = {
                viewModel.deleteTag(tag)
                tagToDelete = null
            }
        )
    }

    // Sort order selection dialog
    if (showSortDialog) {
        val currentSort = (uiState as? LibraryUiState.Success)?.sortOrder ?: SortOrder.NEWEST
        SortOrderDialog(
            currentSortOrder = currentSort,
            onDismiss = { showSortDialog = false },
            onSelectSortOrder = { viewModel.setSortOrder(it) }
        )
    }
}

@Composable
fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_recordings_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("library_search_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.extraSmall)
            .testTag("library_search_text_field")
    )
}

@Composable
fun LibraryTabsHeader(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RecovoSpacing.small)
    ) {
        Tab(
            selected = selectedTab == LibraryTab.ALL,
            onClick = { onTabSelected(LibraryTab.ALL) },
            text = { Text(stringResource(R.string.tab_all)) },
            modifier = Modifier.testTag("tab_all")
        )
        Tab(
            selected = selectedTab == LibraryTab.FAVORITES,
            onClick = { onTabSelected(LibraryTab.FAVORITES) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selectedTab == LibraryTab.FAVORITES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tab_favorites))
                }
            },
            modifier = Modifier.testTag("tab_favorites")
        )
        Tab(
            selected = selectedTab == LibraryTab.FOLDERS,
            onClick = { onTabSelected(LibraryTab.FOLDERS) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tab_folders))
                }
            },
            modifier = Modifier.testTag("tab_folders")
        )
        Tab(
            selected = selectedTab == LibraryTab.TAGS,
            onClick = { onTabSelected(LibraryTab.TAGS) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tab_tags))
                }
            },
            modifier = Modifier.testTag("tab_tags")
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersSubHeader(
    openFolder: FolderEntity?,
    folders: List<FolderEntity>,
    recordingCounts: Map<Long, Int>,
    onFolderClick: (FolderEntity) -> Unit,
    onBackToAllFolders: () -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (openFolder != null) {
        // Folder Detail Breadcrumb
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.extraSmall)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToAllFolders,
                        modifier = Modifier
                            .size(RecovoDimensions.minTouchTarget)
                            .wrapContentSize(Alignment.Center)
                            .testTag("folder_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.library_cd_back_to_folders),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = openFolder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    IconButton(
                        onClick = { onRenameFolder(openFolder) },
                        modifier = Modifier
                            .size(RecovoDimensions.minTouchTarget)
                            .wrapContentSize(Alignment.Center)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.library_cd_rename_folder), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onDeleteFolder(openFolder) },
                        modifier = Modifier
                            .size(RecovoDimensions.minTouchTarget)
                            .wrapContentSize(Alignment.Center)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.library_cd_delete_folder),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Folders list row with "New Folder" action
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.extraSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_folders_header, folders.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onCreateFolder,
                    modifier = Modifier.testTag("create_folder_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.new_folder))
                }
            }

            if (folders.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        val count = recordingCounts[folder.id] ?: 0

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onFolderClick(folder) },
                                    onLongClick = { menuExpanded = true }
                                )
                                .testTag("folder_item_${folder.id}"),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier
                                            .size(RecovoDimensions.minTouchTarget)
                                            .wrapContentSize(Alignment.Center)
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_folder_options), modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.library_folder_open)) },
                                            onClick = {
                                                menuExpanded = false
                                                onFolderClick(folder)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rename_folder)) },
                                            onClick = {
                                                menuExpanded = false
                                                onRenameFolder(folder)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete_folder), color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                menuExpanded = false
                                                onDeleteFolder(folder)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSubHeader(
    tags: List<TagEntity>,
    selectedTag: TagEntity?,
    recordingCounts: Map<Long, Int>,
    onTagSelected: (TagEntity) -> Unit,
    onCreateTag: () -> Unit,
    onRenameTag: (TagEntity) -> Unit,
    onDeleteTag: (TagEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.extraSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_tags_header, tags.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onCreateTag,
                modifier = Modifier.testTag("create_tag_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.new_tag))
            }
        }

        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.forEach { tag ->
                    val isSelected = selectedTag?.id == tag.id
                    val count = recordingCounts[tag.id] ?: 0
                    var tagMenuExpanded by remember { mutableStateOf(false) }

                    Box {
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTagSelected(tag) },
                            label = { Text(stringResource(R.string.library_tag_chip, tag.name, count)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("tag_chip_${tag.id}")
                        )

                        DropdownMenu(
                            expanded = tagMenuExpanded,
                            onDismissRequest = { tagMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_tag)) },
                                onClick = {
                                    tagMenuExpanded = false
                                    onRenameTag(tag)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_tag), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    tagMenuExpanded = false
                                    onDeleteTag(tag)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingCard(
    item: RecordingUiModel,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlayClick: () -> Unit,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRenameClick: () -> Unit,
    onMoveToFolderClick: () -> Unit,
    onManageTagsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val cardColors = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
    } else if (isCurrentTrack) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    }

    Card(
        modifier = modifier
            .testTag("recording_item_${item.entity.id}")
            .clip(RoundedCornerShape(RecovoDimensions.cardCornerRadius))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onLongClick
            ),
        colors = cardColors,
        shape = RoundedCornerShape(RecovoDimensions.cardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RecovoSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If in selection mode, show checkbox; else show play button
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick() },
                        modifier = Modifier.testTag("recording_checkbox_${item.entity.id}")
                    )
                } else {
                    FilledIconButton(
                        onClick = onPlayClick,
                        enabled = item.isFileAvailable,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("recording_play_pause_button_${item.entity.id}"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.playback_cd_pause) else stringResource(R.string.playback_cd_play)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(RecovoSpacing.medium))

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.entity.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.formattedDuration,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.library_meta_separator),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.library_meta_separator),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!item.isFileAvailable) {
                        Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.fileErrorMessage ?: stringResource(R.string.library_file_unavailable),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Favorite Icon Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("recording_favorite_button_${item.entity.id}")
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (item.isFavorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3-dots Context Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("recording_more_button_${item.entity.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.library_cd_recording_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (item.isFileAvailable) {
                            DropdownMenuItem(
                                text = { Text(if (isPlaying) stringResource(R.string.playback_cd_pause) else stringResource(R.string.playback_cd_play)) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_recording)) },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_folder)) },
                            onClick = {
                                menuExpanded = false
                                onMoveToFolderClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_tags)) },
                            onClick = {
                                menuExpanded = false
                                onManageTagsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.isFavorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite)) },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (!item.isFileAvailable) stringResource(R.string.library_cd_remove_missing) else stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            // Badges row: Folder chip & Tag chips
            if (item.folderName != null || item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(RecovoSpacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.folderName?.let { fName ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = fName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    item.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (item.tags.size > 3) {
                        Text(
                            text = stringResource(R.string.library_tag_overflow, item.tags.size - 3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    tab: LibraryTab,
    searchQuery: String,
    hasOpenFolder: Boolean,
    hasSelectedTag: Boolean,
    onClearSearch: () -> Unit,
    onNavigateToRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(RecovoSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(RecovoSpacing.medium))
            Text(
                text = stringResource(R.string.no_recordings_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
            Text(
                text = stringResource(R.string.no_recordings_found_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(RecovoSpacing.medium))
            OutlinedButton(
                onClick = onClearSearch,
                modifier = Modifier.testTag("empty_search_clear_button")
            ) {
                Text(stringResource(R.string.clear_filter))
            }
        } else if (tab == LibraryTab.FAVORITES) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(RecovoSpacing.medium))
            Text(
                text = stringResource(R.string.library_empty_favorites_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
            Text(
                text = stringResource(R.string.library_empty_favorites_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (tab == LibraryTab.FOLDERS && hasOpenFolder) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(RecovoSpacing.medium))
            Text(
                text = stringResource(R.string.library_empty_folder_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(RecovoSpacing.extraSmall))
            Text(
                text = stringResource(R.string.library_empty_folder_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // General empty library
            EmptyLibraryState(
                onNavigateToRecord = onNavigateToRecord,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun EmptyLibraryState(
    onNavigateToRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(RecovoSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(RecovoSpacing.large))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(RecovoSpacing.small))
        Text(
            text = stringResource(R.string.library_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = RecovoSpacing.medium)
        )
        Spacer(modifier = Modifier.height(RecovoSpacing.extraLarge))
        Button(
            onClick = onNavigateToRecord,
            modifier = Modifier
                .height(RecovoDimensions.minTouchTarget)
                .testTag("empty_state_record_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.padding(end = RecovoSpacing.small)
            )
            Text(stringResource(R.string.start_recording))
        }
    }
}
