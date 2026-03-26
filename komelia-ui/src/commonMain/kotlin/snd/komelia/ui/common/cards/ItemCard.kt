package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.cursorForHand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

const val defaultCardWidth = 240
const val DEFAULT_CARD_MAX_LINES = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    modifier: Modifier = Modifier,
    title: String,
    secondaryText: String? = null,
    secondaryTextTop: Boolean = false,
    isUnavailable: Boolean = false,
    titleBold: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    badges: @Composable BoxScope.() -> Unit = {},
    progress: @Composable BoxScope.() -> Unit = {},
    image: @Composable () -> Unit,
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val overlayBackground = LocalCardLayoutOverlayBackground.current

    val shape = if (cardLayoutBelow) RoundedCornerShape(12.dp) else RoundedCornerShape(8.dp)
    val color = if (cardLayoutBelow) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    val elevation = CardDefaults.cardElevation(defaultElevation = if (cardLayoutBelow) 0.dp else 2.dp)

    Card(
        shape = shape,
        modifier = modifier
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .then(if (onClick != null || onLongClick != null) Modifier.cursorForHand() else Modifier),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = elevation
    ) {
        // Thumbnail Logic
        val imageShape = if (cardLayoutBelow) RoundedCornerShape(12.dp) else RoundedCornerShape(8.dp)

        Box(
            modifier = Modifier
                .aspectRatio(0.703f)
                .clip(imageShape)
        ) {
            image()
            badges()

            // Gradients and overlay text — centralized here
            if (!cardLayoutBelow && !overlayBackground) CardTopGradient()
            if (!cardLayoutBelow) {
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                ) {
                    CardTextBackground()
                    Column(
                        modifier = Modifier
                            .height(if (overlayBackground) 38.dp else 48.dp)
                            .padding(
                                start = 8.dp,
                                end = 8.dp,
                                top = if (overlayBackground) 2.dp else 4.dp,
                                bottom = 4.dp
                            ),
                        verticalArrangement = if (overlayBackground) Arrangement.Top else Arrangement.Bottom
                    ) {
                        val textColor = if (overlayBackground) MaterialTheme.colorScheme.onSurface else Color.White
                        val secondaryTextColor = if (overlayBackground) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f)
                        val shadow = if (overlayBackground) null else Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 4f)

                        val primaryStyle = MaterialTheme.typography.bodySmall.copy(
                            shadow = shadow,
                            fontWeight = FontWeight.Bold,
                        )
                        val secondaryStyle = MaterialTheme.typography.labelSmall.copy(
                            shadow = shadow,
                            fontWeight = FontWeight.Normal,
                        )

                        if (isUnavailable) {
                            Text("Unavailable", style = primaryStyle, color = MaterialTheme.colorScheme.error, maxLines = 1)
                            Text(title, style = primaryStyle, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            val secondary = @Composable {
                                if (secondaryText != null) {
                                    Text(secondaryText, style = secondaryStyle, color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            val primary = @Composable {
                                Text(title, style = primaryStyle, color = textColor, maxLines = if (secondaryText == null) 2 else 1, overflow = TextOverflow.Ellipsis)
                            }

                            if (secondaryTextTop) { secondary(); primary() } else { primary(); secondary() }
                        }
                    }
                }
            }
            progress() // Always rendered on the image
        }

        // Below Card Text Logic
        if (cardLayoutBelow) {
            Column(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp), // NO horizontal padding
                verticalArrangement = Arrangement.Center
            ) {
                val primaryStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                val secondaryStyle = MaterialTheme.typography.labelSmall

                if (isUnavailable) {
                    Text("Unavailable", style = primaryStyle, color = MaterialTheme.colorScheme.error, maxLines = 1)
                    Text(title, style = primaryStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    val secondary = @Composable {
                        if (secondaryText != null) {
                            Text(secondaryText, style = secondaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val primary = @Composable {
                        Text(
                            text = title,
                            style = primaryStyle,
                            maxLines = if (secondaryText == null) 2 else 1,
                            minLines = if (secondaryText == null) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (secondaryTextTop) { secondary(); primary() } else { primary(); secondary() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val color = containerColor ?: if (cardLayoutBelow) Color.Transparent
    else MaterialTheme.colorScheme.surfaceVariant

    val shape = if (cardLayoutBelow) RoundedCornerShape(12.dp)
    else RoundedCornerShape(8.dp)

    Card(
        shape = shape,
        modifier = modifier
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .then(if (onClick != null || onLongClick != null) Modifier.cursorForHand() else Modifier),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = if (cardLayoutBelow) 0.dp else 2.dp)
    ) {
        val imageShape = if (cardLayoutBelow) RoundedCornerShape(12.dp)
        else RoundedCornerShape(8.dp)

        Box(
            modifier = Modifier
                .aspectRatio(0.703f)
                .clip(imageShape)
        ) { image() }
        content()
    }
}

@Composable
fun ItemCardWithContent(
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.aspectRatio(0.703f)) { image() }
        content()
    }
}

@Composable
fun CardTextBackground(modifier: Modifier = Modifier) {
    val overlayBackground = LocalCardLayoutOverlayBackground.current
    if (overlayBackground) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(38.dp) // Reduced by 20%
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
        )
    } else {
        CardBottomGradient(modifier)
    }
}

@Composable
fun CardTopGradient() {
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
    )
}

@Composable
fun CardBottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(80.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
    )
}

@Composable
fun overlayBorderModifier() =
    Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary), RoundedCornerShape(5.dp))


@Composable
fun SelectionRadioButton(
    isSelected: Boolean,
    onSelect: () -> Unit,
) {

    RadioButton(
        selected = isSelected,
        onClick = onSelect,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(topEnd = 17.dp, bottomEnd = 17.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .4f))
            .selectable(selected = isSelected, onClick = onSelect)
    )
}

@Composable
fun LazyGridItemScope.DraggableImageCard(
    key: String,
    dragEnabled: Boolean,
    reorderableState: ReorderableLazyGridState,
    content: @Composable () -> Unit
) {
    val platform = LocalPlatform.current
    if (dragEnabled) {
        ReorderableItem(reorderableState, key = key) {
            if (platform == PlatformType.MOBILE) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    content()
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .fillMaxWidth()
                            .draggableHandle()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.DragHandle, null) }
                }

            } else {
                Box(Modifier.draggableHandle()) { content() }
            }

        }
    } else content()
}