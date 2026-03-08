package snd.komelia.ui.series.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalKomgaEvents
import snd.komga.client.sse.KomgaEvent.ThumbnailBookEvent
import snd.komga.client.sse.KomgaEvent.ThumbnailSeriesEvent
import snd.komelia.ui.collection.SeriesCollectionsContent
import snd.komelia.ui.collection.SeriesCollectionsState
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.images.ThumbnailImage
import coil3.compose.rememberAsyncImagePainter
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.common.menus.SeriesActionsMenu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.common.menus.bulk.BooksBulkActionsContent
import snd.komelia.ui.common.menus.bulk.BottomPopupBulkActionsPanel
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.series.SeriesBooksState
import snd.komelia.ui.series.SeriesBooksState.BooksData
import snd.komelia.ui.series.SeriesViewModel.SeriesTab
import snd.komelia.ui.series.view.SeriesBooksContent
import snd.komelia.ui.series.view.SeriesChipTags
import snd.komelia.ui.series.view.SeriesDescriptionRow
import snd.komelia.ui.series.view.SeriesSummary
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeries

private enum class ImmersiveTab { BOOKS, COLLECTIONS, TAGS }

@Composable
fun ImmersiveSeriesContent(
    series: KomgaSeries,
    library: KomgaLibrary?,
    accentColor: Color?,
    onLibraryClick: (KomgaLibrary) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    currentTab: SeriesTab,
    onTabChange: (SeriesTab) -> Unit,
    booksState: SeriesBooksState,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    collectionsState: SeriesCollectionsState,
    onCollectionClick: (KomgaCollection) -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
    onBackClick: () -> Unit,
    onDownload: () -> Unit,
    initiallyExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
) {
    val booksLoadState = booksState.state.collectAsState().value
    val booksData = remember(booksLoadState) {
        if (booksLoadState is LoadState.Success<BooksData>) booksLoadState.value else BooksData()
    }
    val bookMenuActions = remember { booksState.bookMenuActions() }
    val bookBulkActions = remember { booksState.bookBulkMenuActions() }
    val gridMinWidth = booksState.cardWidth.collectAsState().value
    val scrollState = rememberLazyGridState()

    val selectionMode = booksData.selectionMode
    val selectedBooks = booksData.selectedBooks

    // First unread book — used for Read Now action
    val firstUnreadBook = remember(booksData.books) {
        booksData.books.firstOrNull { it.readProgress == null || it.readProgress?.completed == false }
            ?: booksData.books.firstOrNull()
    }

    var showDownloadConfirmationDialog by remember { mutableStateOf(false) }

    // Local tab state — includes TAGS which has no VM counterpart
    var immersiveTab by remember {
        mutableStateOf(
            when (currentTab) {
                SeriesTab.BOOKS -> ImmersiveTab.BOOKS
                SeriesTab.COLLECTIONS -> ImmersiveTab.COLLECTIONS
            }
        )
    }

    val onImmersiveTabChange: (ImmersiveTab) -> Unit = { tab ->
        immersiveTab = tab
        when (tab) {
            ImmersiveTab.BOOKS -> onTabChange(SeriesTab.BOOKS)
            ImmersiveTab.COLLECTIONS -> onTabChange(SeriesTab.COLLECTIONS)
            ImmersiveTab.TAGS -> Unit
        }
    }

    // Keep in sync if something external changes the VM tab
    LaunchedEffect(currentTab) {
        if (immersiveTab != ImmersiveTab.TAGS) {
            immersiveTab = when (currentTab) {
                SeriesTab.BOOKS -> ImmersiveTab.BOOKS
                SeriesTab.COLLECTIONS -> ImmersiveTab.COLLECTIONS
            }
        }
    }

    val komgaEvents = LocalKomgaEvents.current
    var coverData by remember(series.id) { mutableStateOf(SeriesDefaultThumbnailRequest(series.id)) }
    LaunchedEffect(series.id) {
        komgaEvents.collect { event ->
            val eventSeriesId = when (event) {
                is ThumbnailSeriesEvent -> event.seriesId
                is ThumbnailBookEvent -> event.seriesId
                else -> null
            }
            if (eventSeriesId == series.id) coverData = SeriesDefaultThumbnailRequest(series.id)
        }
    }

    val coverPainter = rememberAsyncImagePainter(model = coverData)
    val dominantColor = remember(series.id.value) { mutableStateOf<Color?>(null) }
    LaunchedEffect(series.id.value, coverData) {
        dominantColor.value = extractDominantColor(coverPainter)
    }

    ImmersiveDetailScaffold(
        coverData = coverData,
        coverKey = series.id.value,
        cardColor = dominantColor.value,
        immersive = true,
        initiallyExpanded = initiallyExpanded,
        onExpandChange = onExpandChange,
        topBarContent = {
            if (selectionMode) {
                BulkActionsContainer(
                    onCancel = { booksState.setSelectionMode(false) },
                    selectedCount = selectedBooks.size,
                    allSelected = booksData.books.size == selectedBooks.size,
                    onSelectAll = {
                        val allSelected = booksData.books.size == selectedBooks.size
                        if (allSelected) booksData.books.forEach { booksState.onBookSelect(it) }
                        else booksData.books
                            .filter { book -> selectedBooks.none { it.id == book.id } }
                            .forEach { booksState.onBookSelect(it) }
                    }
                ) {}
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .clickable(onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    var expandActions by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .clickable { expandActions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                        }
                        SeriesActionsMenu(
                            series = series,
                            actions = seriesMenuActions,
                            expanded = expandActions,
                            showEditOption = true,
                            showDownloadOption = false,
                            onDismissRequest = { expandActions = false },
                        )
                    }
                }
            }
        },
        fabContent = {
            ImmersiveDetailFab(
                onReadClick = { firstUnreadBook?.let { onBookReadClick(it, true) } },
                onReadIncognitoClick = { firstUnreadBook?.let { onBookReadClick(it, false) } },
                onDownloadClick = { showDownloadConfirmationDialog = true },
                accentColor = accentColor,
                showReadActions = false,
            )
        },
        cardContent = { expandFraction ->
            val thumbnailOffset = (126.dp * expandFraction).coerceAtLeast(0.dp)

            // Thumbnail metrics — must match ImmersiveDetailScaffold Layer 3
            val thumbnailTopGap = 20.dp
            val thumbnailHeight = 110.dp / 0.703f // ≈ 156.5 dp

            val navBarBottom = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            LazyVerticalGrid(
                state = scrollState,
                columns = GridCells.Adaptive(gridMinWidth),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = navBarBottom + 80.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Title + writers in a single item whose minimum height equals the thumbnail
                // bottom when expanded — this pushes the description row below the thumbnail,
                // avoiding Z-order overlap, while still scrolling with the rest of the content.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (thumbnailTopGap + thumbnailHeight) * expandFraction)
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = lerp(8f, thumbnailTopGap.value, expandFraction).dp,
                            )
                    ) {
                        if (expandFraction > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .padding(top = (thumbnailTopGap - 8.dp) * expandFraction)
                                    .graphicsLayer { alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f) }
                            ) {
                                ThumbnailImage(
                                    data = coverData,
                                    cacheKey = series.id.value,
                                    crossfade = false,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 110.dp, height = thumbnailHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(start = thumbnailOffset)) {
                            Text(
                                text = series.metadata.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            val writers = remember(series.booksMetadata.authors) {
                                series.booksMetadata.authors
                                    .filter { it.role.lowercase() == "writer" }
                                    .joinToString(", ") { it.name }
                            }
                            val year = series.booksMetadata.releaseDate?.year
                            val writersYearText = buildString {
                                if (writers.isNotEmpty()) append(writers)
                                if (year != null) { if (writers.isNotEmpty()) append(" "); append("($year)") }
                            }
                            if (writersYearText.isNotEmpty()) {
                                Text(
                                    text = writersYearText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }

                // Description row (library, status, age rating, etc.) — full width
                if (library != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SeriesDescriptionRow(
                            library = library,
                            onLibraryClick = onLibraryClick,
                            releaseDate = series.booksMetadata.releaseDate,
                            status = series.metadata.status,
                            ageRating = series.metadata.ageRating,
                            language = series.metadata.language,
                            readingDirection = series.metadata.readingDirection,
                            deleted = series.deleted || library.unavailable,
                            alternateTitles = series.metadata.alternateTitles,
                            onFilterClick = onFilterClick,
                            showReleaseYear = false,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // Summary — full width
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SeriesSummary(
                            seriesSummary = series.metadata.summary,
                            bookSummary = series.booksMetadata.summary,
                            bookSummaryNumber = series.booksMetadata.summaryNumber,
                        )
                    }
                }

                // Divider
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Tab row
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesImmersiveTabRow(
                        currentTab = immersiveTab,
                        onTabChange = onImmersiveTabChange,
                        showCollectionsTab = collectionsState.collections.isNotEmpty(),
                    )
                }

                // Tab content
                when (immersiveTab) {
                    ImmersiveTab.BOOKS -> SeriesBooksContent(
                        series = series,
                        onBookClick = onBookClick,
                        onBookReadClick = onBookReadClick,
                        scrollState = scrollState,
                        booksLoadState = booksLoadState,
                        onBooksLayoutChange = booksState::onBookLayoutChange,
                        onBooksPageSizeChange = booksState::onBookPageSizeChange,
                        onPageChange = booksState::onPageChange,
                        onBookSelect = booksState::onBookSelect,
                        booksFilterState = booksState.filterState,
                        bookContextMenuActions = bookMenuActions,
                    )

                    ImmersiveTab.COLLECTIONS -> item(span = { GridItemSpan(maxLineSpan) }) {
                        SeriesCollectionsContent(
                            collections = collectionsState.collections,
                            onCollectionClick = onCollectionClick,
                            onSeriesClick = onSeriesClick,
                            cardWidth = collectionsState.cardWidth.collectAsState().value,
                        )
                    }

                    ImmersiveTab.TAGS -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SeriesChipTags(series = series, onFilterClick = onFilterClick)
                        }
                    }
                }
            }
        }
    )

    if (showDownloadConfirmationDialog) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }
        if (permissionRequested) {
            ConfirmationDialog(
                body = "Download series \"${series.metadata.title}\"?",
                onDialogConfirm = {
                    onDownload()
                    showDownloadConfirmationDialog = false
                },
                onDialogDismiss = { showDownloadConfirmationDialog = false }
            )
        }
    }

    if (selectionMode && selectedBooks.isNotEmpty()) {
        BottomPopupBulkActionsPanel {
            BooksBulkActionsContent(
                books = selectedBooks,
                actions = bookBulkActions,
                compact = true
            )
        }
    }
}

@Composable
private fun SeriesImmersiveTabRow(
    currentTab: ImmersiveTab,
    onTabChange: (ImmersiveTab) -> Unit,
    showCollectionsTab: Boolean,
) {
    val chipColors = AppFilterChipDefaults.filterChipColors()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(
                onClick = { onTabChange(ImmersiveTab.BOOKS) },
                selected = currentTab == ImmersiveTab.BOOKS,
                label = { Text("Books") },
                colors = chipColors,
                border = null,
            )
            if (showCollectionsTab) {
                FilterChip(
                    onClick = { onTabChange(ImmersiveTab.COLLECTIONS) },
                    selected = currentTab == ImmersiveTab.COLLECTIONS,
                    label = { Text("Collections") },
                    colors = chipColors,
                    border = null,
                )
            }
            FilterChip(
                onClick = { onTabChange(ImmersiveTab.TAGS) },
                selected = currentTab == ImmersiveTab.TAGS,
                label = { Text("Tags") },
                colors = chipColors,
                border = null,
            )
        }
        HorizontalDivider()
    }
}
