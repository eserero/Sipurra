package snd.komelia.ui.book.immersive

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalSharedTransitionScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import snd.komelia.DefaultDateTimeFormats.localDateTimeFormat
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.book.BookInfoColumn
import coil3.compose.rememberAsyncImagePainter
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.readlist.BookReadListsContent
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.series.KomgaSeriesId
import kotlin.math.roundToInt

private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImmersiveBookContent(
    book: KomeliaBook,
    siblingBooks: List<KomeliaBook>,
    accentColor: Color?,
    bookMenuActions: BookMenuActions,
    onBackClick: () -> Unit,
    onReadBook: (KomeliaBook, Boolean) -> Unit,
    onDownload: () -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadListBookPress: (KomeliaBook, KomgaReadList) -> Unit,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeriesId) -> Unit,
    onBookChange: (KomeliaBook) -> Unit = {},
    initiallyExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
) {
    // Detect when the shared transition is no longer "entering".
    // animatedVisibilityScope is null when there is no shared transition (fallback: treat as settled).
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val transitionIsSettled = remember(animatedVisibilityScope) {
        derivedStateOf {
            animatedVisibilityScope == null ||
                animatedVisibilityScope.transition.currentState == EnterExitState.Visible
        }
    }

    // pagerExpanded: controls page count (1 → N). Flipped first so the pager can be scrolled.
    // initialScrollDone: controls the pageBook guard. Flipped AFTER scrollToPage so that no
    // page shows the wrong cover during the brief window between expansion and scroll.
    var pagerExpanded by remember { mutableStateOf(false) }
    var initialScrollDone by remember { mutableStateOf(false) }
    val pagerPageCount = if (pagerExpanded) maxOf(1, siblingBooks.size) else 1
    val pagerState = rememberPagerState(pageCount = { pagerPageCount })

    LaunchedEffect(siblingBooks) {
        if (!initialScrollDone && siblingBooks.isNotEmpty()) {
            // Wait for the transition to settle so new pages don't fire animateEnterExit.
            snapshotFlow { transitionIsSettled.value }.first { it }
            val idx = siblingBooks.indexOfFirst { it.id == book.id }.coerceAtLeast(0)
            // Expand pager (pageBook guard still holds — all pages show book's cover).
            pagerExpanded = true
            // Wait for the pager to recognise the expanded page count, then snap to correct page.
            snapshotFlow { pagerState.pageCount }.first { it > idx }
            pagerState.scrollToPage(idx)
            // Only now unlock pageBook — pager is already on the right page, so siblingBooks[idx]
            // is the same book and coverData key is unchanged → no flash.
            initialScrollDone = true
        }
    }

    // selectedBook drives the FAB and 3-dot menu after each swipe settles.
    // Guard by initialScrollDone: before the pager has landed on the right page, always return
    // the originally-tapped book. Without this guard, settledPage=0 when siblings first load
    // would produce selectedBook=siblingBooks[0], triggering onBookChange with the wrong book —
    // which propagates back as the `book` prop and corrupts the pageBook guard above.
    val selectedBook = remember(pagerState.settledPage, siblingBooks, initialScrollDone) {
        if (initialScrollDone) siblingBooks.getOrNull(pagerState.settledPage) ?: book
        else book
    }

    LaunchedEffect(selectedBook) {
        onBookChange(selectedBook)
    }

    var showDownloadConfirmationDialog by remember { mutableStateOf(false) }

    val sharedTransitionScope = LocalSharedTransitionScope.current

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

    Box(modifier = Modifier.fillMaxSize()) {

        // Outer HorizontalPager — slides the entire scaffold (cover + card) laterally
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 8.dp,
        ) { pageIndex ->
            // During the transition (initialScrollDone = false, pager has 1 page) always show the
            // tapped book so the shared-element cover key stays stable throughout the animation.
            val pageBook = if (!initialScrollDone) book else (siblingBooks.getOrNull(pageIndex) ?: book)
            // Memoize to avoid a new Random requestCache on every recomposition, which would
            // cause ThumbnailImage's remember(data,cacheKey) to rebuild the ImageRequest and flash.
            val coverData = remember(pageBook.id) { BookDefaultThumbnailRequest(pageBook.id) }
            val coverPainter = rememberAsyncImagePainter(model = coverData)
            val dominantColor = remember(pageBook.id) { mutableStateOf<Color?>(null) }
            LaunchedEffect(pageBook.id) {
                dominantColor.value = extractDominantColor(coverPainter)
            }

            ImmersiveDetailScaffold(
                coverData = coverData,
                coverKey = pageBook.id.value,
                cardColor = dominantColor.value,
                immersive = true,
                initiallyExpanded = initiallyExpanded,
                onExpandChange = onExpandChange,
                topBarContent = {},  // Fixed overlay handles this
                fabContent = {},     // Fixed overlay handles this
                cardContent = { expandFraction ->
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
                                BookStatsLine(pageBook, Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                                    .graphicsLayer { this.alpha = alpha })
                        }

                        // Header: thumbnail offset + series title · #N, book title, writers (year)
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
                                            .padding(top = (thumbnailTopGap - 8.dp) * expandFraction)
                                            .graphicsLayer { alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f) }
                                    ) {
                                        ThumbnailImage(
                                            data = coverData,
                                            cacheKey = pageBook.id.value,
                                            crossfade = false,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 110.dp, height = thumbnailHeight)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(start = thumbnailOffset)
                                ) {
                                    // Line 1: Series · #N (headlineSmall, bold) — tappable link
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onSeriesClick(pageBook.seriesId) }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = "${pageBook.seriesTitle} · #${pageBook.metadata.number}",
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        )
                                    }
                                    // Line 2: Book title (titleMedium) — only if different from series title
                                    if (pageBook.metadata.title != pageBook.seriesTitle) {
                                        Text(
                                            text = pageBook.metadata.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    // Line 3: Writers (year) — labelSmall
                                    val writers = remember(pageBook.metadata.authors) {
                                        pageBook.metadata.authors
                                            .filter { it.role.lowercase() == "writer" }
                                            .joinToString(", ") { it.name }
                                    }
                                    val year = pageBook.metadata.releaseDate?.year
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
                            }
                        }

                        // Expanded stats line (fades in as card expands)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val alpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f)
                            if (alpha > 0.01f)
                                BookStatsLine(pageBook, Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .graphicsLayer { this.alpha = alpha })
                        }

                        // Summary
                        if (pageBook.metadata.summary.isNotBlank()) {
                            item {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = pageBook.metadata.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        // Divider
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                        // Book metadata (authors, tags, links, file info, ISBN)
                        item {
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                BookInfoColumn(
                                    publisher = null,
                                    genres = null,
                                    authors = pageBook.metadata.authors,
                                    tags = pageBook.metadata.tags,
                                    links = pageBook.metadata.links,
                                    sizeInMiB = pageBook.size,
                                    mediaType = pageBook.media.mediaType,
                                    isbn = pageBook.metadata.isbn,
                                    fileUrl = pageBook.url,
                                    onFilterClick = onFilterClick,
                                )
                            }
                        }

                        // Reading lists
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BookReadListsContent(
                                readLists = readLists,
                                onReadListClick = onReadListClick,
                                onBookClick = onReadListBookPress,
                                cardWidth = cardWidth,
                            )
                        }
                    }
                }
            )
        }

        // Fixed overlay: back button + 3-dot menu (stays still while pager slides)
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
                BookActionsMenu(
                    book = selectedBook,
                    actions = bookMenuActions,
                    expanded = expandActions,
                    showEditOption = true,
                    showDownloadOption = false,  // download is in FAB
                    onDismissRequest = { expandActions = false },
                )
            }
        }

        // Fixed overlay: FAB (stays still while pager slides)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(fabOverlayModifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            ImmersiveDetailFab(
                onReadClick = { onReadBook(selectedBook, true) },
                onReadIncognitoClick = { onReadBook(selectedBook, false) },
                onDownloadClick = { showDownloadConfirmationDialog = true },
                accentColor = accentColor,
                showReadActions = true,
            )
        }
    }

    // Two-step download confirmation dialog
    if (showDownloadConfirmationDialog) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }
        if (permissionRequested) {
            ConfirmationDialog(
                body = "Download \"${selectedBook.metadata.title}\"?",
                onDialogConfirm = {
                    onDownload()
                    showDownloadConfirmationDialog = false
                },
                onDialogDismiss = { showDownloadConfirmationDialog = false },
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
