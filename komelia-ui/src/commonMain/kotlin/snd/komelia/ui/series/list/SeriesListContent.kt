package snd.komelia.ui.series.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.itemlist.SeriesLazyCardGrid
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.common.menus.bulk.BottomPopupBulkActionsPanel
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.common.menus.bulk.SeriesBulkActionsContent
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.EXPANDED
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.series.SeriesFilterState
import snd.komelia.ui.series.view.SeriesFilterContent
import snd.komga.client.series.KomgaSeries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListContent(
    series: List<KomgaSeries>,
    seriesTotalCount: Int,
    seriesActions: SeriesMenuActions,
    onSeriesClick: (KomgaSeries) -> Unit,

    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    selectedSeries: List<KomgaSeries>,
    onSeriesSelect: (KomgaSeries) -> Unit,

    filterState: SeriesFilterState?,

    totalPages: Int,
    currentPage: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,

    minSize: Dp,

    keepReadingBooks: List<KomeliaBook> = emptyList(),
    bookMenuActions: BookMenuActions? = null,
    onBookClick: (KomeliaBook) -> Unit = {},
    onBookReadClick: (KomeliaBook, Boolean) -> Unit = { _, _ -> },
    beforeContent: (@Composable () -> Unit)? = null,
) {
    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val strings = LocalStrings.current.seriesFilter
    var showFilters by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current
    val fabContainerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer
    val fabContentColor = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else contentColorFor(fabContainerColor)

    Box(Modifier.fillMaxSize()) {
        Column {
            if (editMode) {
                BulkActionsToolbar(
                    onCancel = { onEditModeChange(false) },
                    series = series,
                    selectedSeries = selectedSeries,
                    onSeriesSelect = onSeriesSelect
                )
            }

            SeriesLazyCardGrid(
                series = series,
                onSeriesClick = if (editMode) onSeriesSelect else onSeriesClick,
                seriesMenuActions = if (editMode) null else seriesActions,

                selectedSeries = selectedSeries,
                onSeriesSelect = onSeriesSelect,

                totalPages = totalPages,
                currentPage = currentPage,
                onPageChange = onPageChange,

                beforeContent = {
                    Column {
                        beforeContent?.invoke()
                        AnimatedVisibility(!editMode) {
                            Column {
                                if (useNewLibraryUI && keepReadingBooks.isNotEmpty() && bookMenuActions != null) {
                                    LibrarySectionHeader("Keep Reading")
                                    val gridPadding = 10.dp
                                    val density = LocalDensity.current
                                    LazyRow(
                                        modifier = Modifier.layout { measurable, constraints ->
                                            val insetPx = with(density) { gridPadding.roundToPx() }
                                            val placeable = measurable.measure(
                                                constraints.copy(maxWidth = constraints.maxWidth + insetPx * 2)
                                            )
                                            layout(constraints.maxWidth, placeable.height) {
                                                placeable.place(-insetPx, 0)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = gridPadding),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        items(keepReadingBooks) { book ->
                                            BookImageCard(
                                                book = book,
                                                onBookClick = { onBookClick(book) },
                                                onBookReadClick = { onBookReadClick(book, it) },
                                                bookMenuActions = bookMenuActions,
                                                showSeriesTitle = true,
                                                modifier = Modifier.width(minSize),
                                            )
                                        }
                                    }
                                }
                                if (useNewLibraryUI) LibrarySectionHeader("Browse")
                            }
                        }
                    }
                },
                minSize = minSize,
            )
            val width = LocalWindowWidth.current
            if ((width == COMPACT || width == MEDIUM) && selectedSeries.isNotEmpty()) {
                BottomPopupBulkActionsPanel {
                    SeriesBulkActionsContent(selectedSeries, true)
                }
            }
        }

        // Filter bottom sheet
        if (filterState != null && showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SeriesFilterContent(filterState = filterState)
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = filterState::reset,
                        containerColor = if (filterState.isChanged)
                            fabContainerColor
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (filterState.isChanged) fabContentColor else contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
                        icon = { Icon(Icons.Default.FilterListOff, null) },
                        text = { Text(strings.resetFilters) },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showFilters = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        icon = { Icon(Icons.Default.FilterList, null) },
                        text = { Text(strings.hideFilters) },
                    )
                }
            }
        }

        // Filter FAB
        if (filterState != null) {
            AnimatedVisibility(
                visible = !showFilters && !editMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp, end = 16.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showFilters = true },
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor,
                    icon = {
                        Icon(
                            Icons.Default.FilterList,
                            null,
                            tint = if (filterState.isChanged) Color(0xFFFFD600) else fabContentColor,
                        )
                    },
                    text = { Text("Filter") },
                )
            }
        }
    }
}

@Composable
private fun BulkActionsToolbar(
    onCancel: () -> Unit,
    series: List<KomgaSeries>,
    selectedSeries: List<KomgaSeries>,
    onSeriesSelect: (KomgaSeries) -> Unit,
) {
    BulkActionsContainer(
        onCancel = onCancel,
        selectedCount = selectedSeries.size,
        allSelected = series.size == selectedSeries.size,
        onSelectAll = {
            if (series.size == selectedSeries.size) series.forEach { onSeriesSelect(it) }
            else series.filter { it !in selectedSeries }.forEach { onSeriesSelect(it) }
        }
    ) {
        when (LocalWindowWidth.current) {
            FULL, EXPANDED -> {
                if (selectedSeries.isEmpty()) {
                    Text("Click on items to select or deselect them")
                } else {
                    Spacer(Modifier.weight(1f))
                    SeriesBulkActionsContent(selectedSeries, false)
                }
            }

            COMPACT, MEDIUM -> {}
        }
    }
}

@Composable
private fun LibrarySectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
