package snd.komelia.ui.common.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalHideParenthesesInNames
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.common.components.NoPaddingChip
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.utils.removeParentheses
import snd.komga.client.series.KomgaSeries

@Composable
fun SeriesImageCard(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onSeriesSelect: (() -> Unit)? = null,
    seriesMenuActions: SeriesMenuActions? = null,
    modifier: Modifier = Modifier,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == series.libraryId }?.unavailable ?: false
    }
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    ItemCard(
        modifier = modifier,
        onClick = onSeriesClick,
        onLongClick = onSeriesSelect,
        image = {
            SeriesCardHoverOverlay(
                series = series,
                onSeriesSelect = onSeriesSelect,
                isSelected = isSelected,
                seriesActions = seriesMenuActions,
            ) {
                SeriesImageOverlay(
                    title = title,
                    series = series,
                    libraryIsDeleted = libraryIsDeleted,
                    showTitle = !cardLayoutBelow
                ) {
                    SeriesThumbnail(
                        series.id,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        content = {
            if (cardLayoutBelow) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    val isUnavailable = series.deleted || libraryIsDeleted
                    if (isUnavailable) {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Unavailable",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = title,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SeriesSimpleImageCard(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    ItemCard(
        modifier = modifier,
        onClick = onSeriesClick,
        image = {
            SeriesImageOverlay(
                title = title,
                series = series,
                libraryIsDeleted = false,
                showTitle = !cardLayoutBelow,
            ) {
                SeriesThumbnail(
                    series.id,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        },
        content = {
            if (cardLayoutBelow) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        text = title,
                        maxLines = 2,
                        minLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    )
}

@Composable
private fun SeriesCardHoverOverlay(
    series: KomgaSeries,
    isSelected: Boolean,
    onSeriesSelect: (() -> Unit)?,
    seriesActions: SeriesMenuActions?,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    val showOverlay = derivedStateOf { isHovered.value || isActionsMenuExpanded || isSelected }
    val border = if (showOverlay.value) overlayBorderModifier() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (showOverlay.value) {
            val backgroundModifier =
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .5f))
                else Modifier
            Column(backgroundModifier.fillMaxSize()) {
                if (onSeriesSelect != null) {
                    SelectionRadioButton(isSelected, onSeriesSelect)
                    Spacer(Modifier.weight(1f))
                }

                if (seriesActions != null) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Spacer(Modifier.weight(1f))

                        Box {
                            IconButton(
                                onClick = { isActionsMenuExpanded = true },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = null)
                            }

                            SeriesActionsMenu(
                                series = series,
                                actions = seriesActions,
                                expanded = isActionsMenuExpanded,
                                showEditOption = true,
                                showDownloadOption = true,
                                onDismissRequest = { isActionsMenuExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesImageOverlay(
    title: String,
    series: KomgaSeries,
    libraryIsDeleted: Boolean,
    showTitle: Boolean = true,
    content: @Composable () -> Unit
) {
    val overlayBackground = LocalCardLayoutOverlayBackground.current
    val shadow = if (overlayBackground) null else Shadow(
        color = Color.Black,
        offset = Offset(1f, 1f),
        blurRadius = 4f
    )
    val textColor = if (overlayBackground) MaterialTheme.colorScheme.onSurface else Color.White

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
        if (showTitle) {
            CardTopGradient()
        }

        if (series.booksUnreadCount > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${series.booksUnreadCount}",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (showTitle) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                CardTextBackground()
                Column(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    val isBothPresent = series.deleted || libraryIsDeleted
                    if (series.deleted || libraryIsDeleted) {
                        Text(
                            text = "Unavailable",
                            maxLines = 1,
                            style = if (overlayBackground) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value - 1).sp,
                                    fontWeight = FontWeight.Normal
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
                            },
                            color = if (overlayBackground) MaterialTheme.colorScheme.error else Color.White,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        maxLines = if (isBothPresent) 1 else 2,
                        style = if (overlayBackground) {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value - 1).sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
                        },
                        color = textColor,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailedListCard(
    series: KomgaSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hideParentheses = LocalHideParenthesesInNames.current
    val title = if (hideParentheses) series.metadata.title.removeParentheses() else series.metadata.title

    Card(
        modifier
            .cursorForHand()
            .clickable { onClick() }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .padding(10.dp)
        ) {
            SeriesSimpleImageCard(series, onClick)
            SeriesDetails(title, series)
        }
    }
}

@Composable
private fun SeriesDetails(title: String, series: KomgaSeries) {
    Column(Modifier.padding(start = 10.dp)) {
        Row {
            Text(title, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(series.metadata.genres) {
                NoPaddingChip(
                    borderColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

            }
        }
        Text(series.metadata.summary, maxLines = 4, style = MaterialTheme.typography.bodyMedium)

    }
}