package snd.komelia.ui.oneshot.immersive

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import snd.komelia.DefaultDateTimeFormats.localDateTimeFormat
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalKomgaEvents
import snd.komelia.ui.LocalSharedTransitionScope
import snd.komelia.ui.collection.SeriesCollectionsContent
import snd.komelia.ui.common.components.AppFilterChipDefaults
import coil3.compose.rememberAsyncImagePainter
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.common.immersive.rememberPublisherLogo
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.OneshotActionsMenu
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.readlist.BookReadListsContent
import snd.komelia.ui.book.BookInfoColumn
import snd.komelia.ui.series.view.SeriesDescriptionRow
import snd.komelia.ui.series.view.SeriesSummary
import snd.komelia.utils.removeParentheses
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.series.KomgaSeries
import snd.komga.client.sse.KomgaEvent.ThumbnailBookEvent
import snd.komga.client.sse.KomgaEvent.ThumbnailSeriesEvent
import kotlin.math.roundToInt

private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

private enum class OneshotImmersiveTab { TAGS, COLLECTIONS, READ_LISTS }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImmersiveOneshotContent(
    series: KomgaSeries,
    book: KomeliaBook?,
    library: KomgaLibrary?,
    accentColor: Color?,
    onLibraryClick: (KomgaLibrary) -> Unit,
    onBookReadClick: (markReadProgress: Boolean) -> Unit,
    oneshotMenuActions: BookMenuActions,
    collections: Map<KomgaCollection, List<KomgaSeries>>,
    onCollectionClick: (KomgaCollection) -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadlistBookClick: (KomeliaBook, KomgaReadList) -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    onBookDownload: () -> Unit,
    cardWidth: Dp,
    onBackClick: () -> Unit,
    initiallyExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
) {
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = remember(book, hideParentheses) {
        if (hideParentheses) book?.metadata?.title?.removeParentheses() ?: ""
        else book?.metadata?.title ?: ""
    }

    var showDownloadConfirmationDialog by remember { mutableStateOf(false) }
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

    val publisherLogo = rememberPublisherLogo(series.metadata.publisher)

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val fabOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                    .animateEnterExit(
                        enter = fadeIn(tween(300, delayMillis = 50)),
                        exit = slideOutVertically(tween(200, easing = emphasizedAccelerateEasing)) { it / 2 }
                               + fadeOut(tween(150))
                    )
            }
        }
    } else Modifier

    val uiOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.75f)
                    .animateEnterExit(
                        enter = fadeIn(tween(durationMillis = 500)),
                        exit = fadeOut(tween(durationMillis = 100))
                    )
            }
        }
    } else Modifier

    var currentTab by remember { mutableStateOf(OneshotImmersiveTab.TAGS) }

    Box(modifier = Modifier.fillMaxSize()) {

        ImmersiveDetailScaffold(
            coverData = coverData,
            coverKey = series.id.value,
            cardColor = dominantColor.value,
            immersive = true,
            initiallyExpanded = initiallyExpanded,
            onExpandChange = onExpandChange,
            publisherLogo = publisherLogo,
            topBarContent = {},   // Fixed overlay handles this
            fabContent = {},      // Fixed overlay handles this
            cardContent = { expandFraction ->
                if (book == null || library == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    OneshotCardContent(
                        title = title,
                        series = series,
                        book = book,
                        library = library,
                        coverData = coverData,
                        publisherLogo = publisherLogo,
                        expandFraction = expandFraction,
                        onLibraryClick = onLibraryClick,
                        onFilterClick = onFilterClick,
                        readLists = readLists,
                        onReadListClick = onReadListClick,
                        onReadlistBookClick = onReadlistBookClick,
                        collections = collections,
                        onCollectionClick = onCollectionClick,
                        onSeriesClick = onSeriesClick,
                        cardWidth = cardWidth,
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        accentColor = accentColor,
                    )
                }
            }
        )

        // Fixed overlay: back button + 3-dot menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(uiOverlayModifier)
                .statusBarsPadding()
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }

            if (book != null) {
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
                    OneshotActionsMenu(
                        series = series,
                        book = book,
                        actions = oneshotMenuActions,
                        expanded = expandActions,
                        onDismissRequest = { expandActions = false },
                    )
                }
            }
        }

        // Fixed overlay: FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(fabOverlayModifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            ImmersiveDetailFab(
                onReadClick = { if (book != null) onBookReadClick(true) },
                onReadIncognitoClick = { if (book != null) onBookReadClick(false) },
                onDownloadClick = { if (book != null) showDownloadConfirmationDialog = true },
                accentColor = accentColor,
                showReadActions = book != null,
            )
        }
    }

    if (showDownloadConfirmationDialog && book != null) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }
        if (permissionRequested) {
            ConfirmationDialog(
                body = "Download \"$title\"?",
                onDialogConfirm = {
                    onBookDownload()
                    showDownloadConfirmationDialog = false
                },
                onDialogDismiss = { showDownloadConfirmationDialog = false },
            )
        }
    }
}

@Composable
private fun OneshotCardContent(
    title: String,
    series: KomgaSeries,
    book: KomeliaBook,
    library: KomgaLibrary,
    coverData: Any,
    publisherLogo: androidx.compose.ui.graphics.ImageBitmap?,
    expandFraction: Float,
    onLibraryClick: (KomgaLibrary) -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadlistBookClick: (KomeliaBook, KomgaReadList) -> Unit,
    collections: Map<KomgaCollection, List<KomgaSeries>>,
    onCollectionClick: (KomgaCollection) -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
    cardWidth: Dp,
    currentTab: OneshotImmersiveTab,
    onTabChange: (OneshotImmersiveTab) -> Unit,
    accentColor: Color?,
) {
    val thumbnailOffset = (126.dp * expandFraction).coerceAtLeast(0.dp)
    val thumbnailTopGap = 20.dp
    val thumbnailHeight = 110.dp / 0.703f // ≈ 156.5 dp

    val navBarBottom = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = navBarBottom + 80.dp),
    ) {
        // Collapsed stats line (fades out as card expands)
        item(span = { GridItemSpan(maxLineSpan) }) {
            val alpha = (1f - expandFraction * 2f).coerceIn(0f, 1f)
            if (alpha > 0.01f)
                BookStatsLine(book, Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                    .graphicsLayer { this.alpha = alpha })
        }

        // Header: book title + writers (year)
        item {
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
                    // Book title (headlineSmall, bold)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    // Writers (year) — labelSmall
                    val writers = remember(book.metadata.authors) {
                        book.metadata.authors
                            .filter { it.role.lowercase() == "writer" }
                            .joinToString(", ") { it.name }
                    }
                    val year = book.metadata.releaseDate?.year
                    val writersYearText = buildString {
                        if (writers.isNotEmpty()) append(writers)
                        if (year != null) {
                            if (writers.isNotEmpty()) append(" ")
                            append("($year)")
                        }
                    }
                    if (writersYearText.isNotEmpty()) {
                        Text(
                            text = writersYearText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                if (publisherLogo != null && expandFraction > 0.01f) {
                    val logoHeight = thumbnailHeight * 0.25f
                    Image(
                        bitmap = publisherLogo,
                        contentDescription = null,
                        modifier = Modifier
                            .height(logoHeight)
                            .widthIn(max = 100.dp)
                            .align(Alignment.BottomEnd)
                            .graphicsLayer { alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f) },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Expanded stats line (fades in as card expands)
        item(span = { GridItemSpan(maxLineSpan) }) {
            val alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f)
            if (alpha > 0.01f)
                BookStatsLine(book, Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .graphicsLayer { this.alpha = alpha })
        }

        // SeriesDescriptionRow (library, status, age rating, etc.)
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesDescriptionRow(
                library = library,
                onLibraryClick = onLibraryClick,
                releaseDate = null,
                status = null,
                ageRating = series.metadata.ageRating,
                language = series.metadata.language,
                readingDirection = series.metadata.readingDirection,
                deleted = series.deleted || library.unavailable,
                alternateTitles = series.metadata.alternateTitles,
                onFilterClick = onFilterClick,
                showReleaseYear = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Summary
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SeriesSummary(
                    seriesSummary = series.metadata.summary,
                    bookSummary = book.metadata.summary,
                    bookSummaryNumber = book.metadata.number.toString(),
                )
            }
        }

        // Tab row
        item {
            OneshotImmersiveTabRow(
                currentTab = currentTab,
                onTabChange = onTabChange,
                showCollectionsTab = collections.isNotEmpty(),
                showReadListsTab = readLists.isNotEmpty(),
                accentColor = accentColor,
            )
        }

        when (currentTab) {
            OneshotImmersiveTab.TAGS -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        BookInfoColumn(
                            publisher = series.metadata.publisher,
                            genres = series.metadata.genres,
                            authors = book.metadata.authors,
                            tags = book.metadata.tags,
                            links = book.metadata.links,
                            sizeInMiB = book.size,
                            mediaType = book.media.mediaType,
                            isbn = book.metadata.isbn,
                            fileUrl = book.url,
                            onFilterClick = onFilterClick,
                        )
                    }
                }
            }

            OneshotImmersiveTab.COLLECTIONS -> {
                // Collections
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesCollectionsContent(
                        collections = collections,
                        onCollectionClick = onCollectionClick,
                        onSeriesClick = onSeriesClick,
                        cardWidth = cardWidth,
                    )
                }
            }

            OneshotImmersiveTab.READ_LISTS -> {
                // Reading lists
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BookReadListsContent(
                        readLists = readLists,
                        onReadListClick = onReadListClick,
                        onBookClick = onReadlistBookClick,
                        cardWidth = cardWidth,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneshotImmersiveTabRow(
    currentTab: OneshotImmersiveTab,
    onTabChange: (OneshotImmersiveTab) -> Unit,
    showCollectionsTab: Boolean,
    showReadListsTab: Boolean,
    accentColor: Color?,
) {
    val selectedTabIndex = when (currentTab) {
        OneshotImmersiveTab.TAGS -> 0
        OneshotImmersiveTab.COLLECTIONS -> 1
        OneshotImmersiveTab.READ_LISTS -> if (showCollectionsTab) 2 else 1
    }
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                width = 48.dp,
                color = accentColor ?: MaterialTheme.colorScheme.primary
            )
        },
        divider = {}
    ) {
        Tab(
            selected = currentTab == OneshotImmersiveTab.TAGS,
            onClick = { onTabChange(OneshotImmersiveTab.TAGS) },
            text = { Text("Tags") },
        )
        if (showCollectionsTab) {
            Tab(
                selected = currentTab == OneshotImmersiveTab.COLLECTIONS,
                onClick = { onTabChange(OneshotImmersiveTab.COLLECTIONS) },
                text = { Text("Collections") },
            )
        }
        if (showReadListsTab) {
            Tab(
                selected = currentTab == OneshotImmersiveTab.READ_LISTS,
                onClick = { onTabChange(OneshotImmersiveTab.READ_LISTS) },
                text = { Text("Read Lists") },
            )
        }
    }
}

@Composable
private fun BookStatsLine(book: KomeliaBook, modifier: Modifier = Modifier) {
    val pagesCount = book.media.pagesCount
    val segments = remember(book) {
        buildList {
            add("$pagesCount page${if (pagesCount == 1) "" else "s"}")
            book.metadata.releaseDate?.let { add(it.toString()) }
            book.readProgress?.let { progress ->
                if (!progress.completed) {
                    val pagesLeft = pagesCount - progress.page
                    val pct = (progress.page.toFloat() / pagesCount * 100).roundToInt()
                    add("$pct%, $pagesLeft page${if (pagesLeft == 1) "" else "s"} left")
                }
                add(progress.readDate
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(localDateTimeFormat))
            }
        }
    }
    if (segments.isEmpty()) return
    Text(
        text = segments.joinToString(" | "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
