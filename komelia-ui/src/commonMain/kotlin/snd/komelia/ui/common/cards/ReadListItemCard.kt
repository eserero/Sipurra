package snd.komelia.ui.common.cards

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.common.images.ReadListThumbnail
import snd.komelia.ui.common.menus.ReadListActionsMenu
import snd.komga.client.readlist.KomgaReadList

@Composable
fun ReadListImageCard(
    readLists: KomgaReadList,
    onCollectionClick: () -> Unit,
    onCollectionDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    ItemCard(
        modifier = modifier,
        onClick = onCollectionClick,
        image = {
            ReadListCardHoverOverlay(readLists, onCollectionDelete) {
                ReadListImageOverlay(
                    readlist = readLists,
                    showTitle = !cardLayoutBelow,
                ) {
                    ReadListThumbnail(
                        readListId = readLists.id,
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
                    Text(
                        text = readLists.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (readLists.bookIds.size == 1) "1 book" else "${readLists.bookIds.size} books",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    )
}

@Composable
private fun ReadListCardHoverOverlay(
    readList: KomgaReadList,
    onReadListDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    val showOverlay = derivedStateOf { isHovered.value || isActionsMenuExpanded }

    val border = if (showOverlay.value) overlayBorderModifier() else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (showOverlay.value && isAdmin) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Spacer(Modifier.weight(1f))

                Box {
                    IconButton(
                        onClick = { isActionsMenuExpanded = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) { Icon(Icons.Default.MoreVert, contentDescription = null) }

                    ReadListActionsMenu(
                        readList = readList,
                        onReadListDelete = onReadListDelete,
                        expanded = isActionsMenuExpanded,
                        onDismissRequest = { isActionsMenuExpanded = false }

                    )
                }
            }
        }
    }
}

@Composable
private fun ReadListImageOverlay(
    readlist: KomgaReadList,
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
    val secondaryTextColor =
        if (overlayBackground) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        content()
        if (showTitle) {
            CardTopGradient()
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
                    Text(
                        text = readlist.name,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                        color = textColor,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (readlist.bookIds.size == 1) "1 book" else "${readlist.bookIds.size} books",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                        color = secondaryTextColor,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}