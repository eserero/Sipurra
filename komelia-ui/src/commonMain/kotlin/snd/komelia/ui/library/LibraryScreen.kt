package snd.komelia.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalMainScreenViewModel
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.AppSuggestionChipDefaults
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.menus.LibraryActionsMenu
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komelia.ui.library.view.LibraryCollectionsContent
import snd.komelia.ui.library.view.LibraryReadListsContent
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.series.list.SeriesListContent
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesStatus
import kotlin.jvm.Transient

class LibraryScreen(
    val libraryId: KomgaLibraryId? = null,
    @Transient
    private val seriesFilter: SeriesScreenFilter? = null
) : ReloadableScreen {

    override val key: ScreenKey = "${libraryId}_${seriesFilter.hashCode()}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(libraryId?.value) { viewModelFactory.getLibraryViewModel(libraryId) }
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(libraryId) {
            vm.initialize(seriesFilter)
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(message = state.exception.message ?: "Unknown Error", onReload = vm::reload)
                Uninitialized, Loading, is Success -> {
                    Column {
                        val (totalCountInfo, onPageSizeChange) = when (vm.currentTab) {
                            SERIES -> {
                                val state = vm.seriesTabState
                                Triple(
                                    state.totalSeriesCount,
                                    "series",
                                    state.pageLoadSize.collectAsState().value
                                ) to state::onPageSizeChange
                            }

                            COLLECTIONS -> {
                                val state = vm.collectionsTabState
                                Triple(
                                    state.totalCollections,
                                    if (state.totalCollections > 1) "collections" else "collection",
                                    state.pageSize
                                ) to state::onPageSizeChange
                            }

                            READ_LISTS -> {
                                val state = vm.readListsTabState
                                Triple(
                                    state.totalReadLists,
                                    if (state.totalReadLists > 1) "read lists" else "read list",
                                    state.pageSize
                                ) to state::onPageSizeChange
                            }
                        }
                        val (totalCount, countLabel, pageSize) = totalCountInfo

                        if (vm.showToolbar.collectAsState().value) {
                            LibraryToolBar(
                                library = vm.library.collectAsState().value,
                                libraryActions = vm.libraryActions(),
                                totalCount = totalCount,
                                countLabel = countLabel,
                                pageSize = pageSize,
                                onPageSizeChange = onPageSizeChange
                            )
                        }

                        val segmentedButtons = @Composable {
                            LibrarySegmentedButtons(
                                currentTab = vm.currentTab,
                                collectionsCount = vm.collectionsCount,
                                readListsCount = vm.readListsCount,
                                onBrowseClick = vm::toBrowseTab,
                                onCollectionsClick = vm::toCollectionsTab,
                                onReadListsClick = vm::toReadListsTab
                            )
                        }

                        when (vm.currentTab) {
                            SERIES -> BrowseTab(vm.seriesTabState, segmentedButtons)
                            COLLECTIONS -> CollectionsTab(vm.collectionsTabState, segmentedButtons)
                            READ_LISTS -> ReadListsTab(vm.readListsTabState, segmentedButtons)
                        }
                    }
                }
            }
            BackPressHandler { navigator.pop() }
        }
    }

    @Composable
    private fun BrowseTab(seriesTabState: LibrarySeriesTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { seriesTabState.initialize(seriesFilter) }
        DisposableEffect(Unit) {
            seriesTabState.startKomgaEventHandler()
            onDispose { seriesTabState.stopKomgaEventHandler() }
        }

        when (val state = seriesTabState.state.collectAsState().value) {
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = seriesTabState::reload
            )

            else -> {
                SeriesListContent(
                    series = seriesTabState.series,
                    seriesActions = seriesTabState.seriesMenuActions(),
                    seriesTotalCount = seriesTabState.totalSeriesCount,
                    onSeriesClick = { navigator.push(seriesScreen(it)) },

                    editMode = seriesTabState.isInEditMode.collectAsState().value,
                    onEditModeChange = seriesTabState::onEditModeChange,
                    selectedSeries = seriesTabState.selectedSeries,
                    onSeriesSelect = seriesTabState::onSeriesSelect,

                    filterState = seriesTabState.filterState,

                    currentPage = seriesTabState.currentSeriesPage,
                    totalPages = seriesTabState.totalSeriesPages,
                    pageSize = seriesTabState.pageLoadSize.collectAsState().value,
                    onPageSizeChange = seriesTabState::onPageSizeChange,
                    onPageChange = seriesTabState::onPageChange,

                    minSize = seriesTabState.cardWidth.collectAsState().value,

                    keepReadingBooks = seriesTabState.keepReadingBooks,
                    bookMenuActions = seriesTabState.bookMenuActions(),
                    onBookClick = { navigator.push(bookScreen(it)) },
                    onBookReadClick = { book, mark -> navigator.push(readerScreen(book, mark)) },
                    beforeContent = beforeContent
                )
            }
        }
    }

    @Composable
    private fun CollectionsTab(collectionsTabState: LibraryCollectionsTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { collectionsTabState.initialize() }
        DisposableEffect(Unit) {
            collectionsTabState.startKomgaEventHandler()
            onDispose { collectionsTabState.stopKomgaEventHandler() }
        }

        when (val state = collectionsTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = collectionsTabState::reload
            )

            else -> {
                val loading = state is Loading
                LibraryCollectionsContent(
                    collections = collectionsTabState.collections,
                    collectionsTotalCount = collectionsTabState.totalCollections,
                    onCollectionClick = { navigator push CollectionScreen(it) },
                    onCollectionDelete = collectionsTabState::onCollectionDelete,
                    isLoading = loading,

                    totalPages = collectionsTabState.totalPages,
                    currentPage = collectionsTabState.currentPage,
                    pageSize = collectionsTabState.pageSize,
                    onPageChange = collectionsTabState::onPageChange,
                    onPageSizeChange = collectionsTabState::onPageSizeChange,

                    minSize = collectionsTabState.cardWidth.collectAsState().value,
                    beforeContent = beforeContent
                )

            }
        }

    }

    @Composable
    private fun ReadListsTab(readListTabState: LibraryReadListsTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { readListTabState.initialize() }
        DisposableEffect(Unit) {
            readListTabState.startKomgaEventHandler()
            onDispose { readListTabState.stopKomgaEventHandler() }
        }

        when (val state = readListTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> Text("Error")
            else -> {
                val loading = state is Loading
                LibraryReadListsContent(
                    readLists = readListTabState.readLists,
                    readListsTotalCount = readListTabState.totalReadLists,
                    onReadListClick = { navigator push ReadListScreen(it) },
                    onReadListDelete = readListTabState::onReadListDelete,
                    isLoading = loading,

                    totalPages = readListTabState.totalPages,
                    currentPage = readListTabState.currentPage,
                    pageSize = readListTabState.pageSize,
                    onPageChange = readListTabState::onPageChange,
                    onPageSizeChange = readListTabState::onPageSizeChange,

                    minSize = readListTabState.cardWidth.collectAsState().value,
                    beforeContent = beforeContent
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolBar(
    library: KomgaLibrary?,
    libraryActions: LibraryMenuActions,
    totalCount: Int,
    countLabel: String,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    val mainScreenVm = LocalMainScreenViewModel.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Text(
                    library?.let { library.name } ?: "All Libraries",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = { coroutineScope.launch { mainScreenVm.toggleNavBar() } }) {
                    Icon(Icons.Rounded.Menu, contentDescription = null)
                }
            },
            actions = {
                if (totalCount != 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$totalCount $countLabel") },
                        shape = AppSuggestionChipDefaults.shape(),
                        modifier = Modifier.padding(end = 5.dp)
                    )

                    PageSizeSelectionDropdown(pageSize, onPageSizeChange)
                }

                if (library != null && (isAdmin || isOffline)) {
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null,
                            )
                        }

                        LibraryActionsMenu(
                            library = library,
                            actions = libraryActions,
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySegmentedButtons(
    currentTab: LibraryTab,
    collectionsCount: Int,
    readListsCount: Int,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
) {
    if (collectionsCount > 0 || readListsCount > 0) {
        val tabCount = getTabCount(collectionsCount, readListsCount)
        val accentColor = LocalAccentColor.current
        val colors = if (accentColor != null) {
            SegmentedButtonDefaults.colors(
                activeContainerColor = accentColor,
                activeContentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
            )
        } else {
            SegmentedButtonDefaults.colors()
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = currentTab == SERIES,
                onClick = onBrowseClick,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = tabCount),
                label = { Text("Series") },
                colors = colors
            )
            var index = 1
            if (collectionsCount > 0) {
                SegmentedButton(
                    selected = currentTab == COLLECTIONS,
                    onClick = onCollectionsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index++, count = tabCount),
                    label = { Text("Collections") },
                    colors = colors
                )
            }
            if (readListsCount > 0) {
                SegmentedButton(
                    selected = currentTab == READ_LISTS,
                    onClick = onReadListsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabCount),
                    label = { Text("Read Lists") },
                    colors = colors
                )
            }
        }
    }
}

private fun getTabCount(collectionsCount: Int, readListsCount: Int): Int {
    var count = 1
    if (collectionsCount > 0) count++
    if (readListsCount > 0) count++
    return count
}

data class SeriesScreenFilter(
    val publicationStatus: List<KomgaSeriesStatus>? = null,
    val ageRating: List<Int>? = null,
    val language: List<String>? = null,
    val publisher: List<String>? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val authors: List<KomgaAuthor>? = null,
)
