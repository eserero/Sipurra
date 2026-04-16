package snd.komelia.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalNavBarColor
import snd.komelia.ui.LocalTheme

@Composable
fun FloatingFABContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hazeState = LocalHazeState.current
    val theme = LocalTheme.current
    val containerColor = if (theme.transparentBars)
        theme.navBarContainerColor
    else
        LocalNavBarColor.current ?: MaterialTheme.colorScheme.surfaceVariant
    val useHaze = hazeState != null && theme.transparentBars
    val hazeStyle = if (useHaze) HazeMaterials.regular(containerColor) else null

    Surface(
        color = if (useHaze) Color.Transparent else containerColor,
        shape = CircleShape,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = modifier
            .wrapContentSize()
            .clip(CircleShape)
            .then(
                if (useHaze && hazeStyle != null)
                    Modifier.hazeEffect(hazeState!!) { style = hazeStyle }
                else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun FloatingFAB(
    icon: ImageVector,
    onClick: () -> Unit,
    accentColor: Color?,
    iconTint: Color? = null,
    modifier: Modifier = Modifier
) {
    FloatingFABContainer(modifier = modifier.height(56.dp)) {
        val tint = iconTint ?: accentColor ?: MaterialTheme.colorScheme.primary
        IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Icon(icon, null, tint = tint)
        }
    }
}

@Composable
fun FloatingSplitFAB(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    primaryActionIcon: ImageVector,
    onPrimaryActionClick: () -> Unit,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    menuItems: @Composable ColumnScope.() -> Unit,
) {
    val tint = accentColor ?: MaterialTheme.colorScheme.primary
    Box(modifier = modifier, contentAlignment = Alignment.BottomStart) {
        if (expanded) {
            FloatingFABContainer(
                modifier = Modifier
                    .padding(bottom = 64.dp)
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 200.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    menuItems()
                }
            }
        }

        FloatingFABContainer(modifier = Modifier.height(56.dp).animateContentSize()) {
            if (expanded) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable { onExpandedChange(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close menu",
                        tint = tint
                    )
                }
            } else {
                Row(
                    modifier = Modifier.height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side for primary action
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                            .clickable { onPrimaryActionClick() }
                            .padding(start = 16.dp, end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(primaryActionIcon, contentDescription = null, tint = tint)
                    }

                    // Right side for toggle
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                            .clickable { onExpandedChange(true) }
                            .padding(start = 8.dp, end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Open menu",
                            tint = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingIslandMenuItem(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    containerColor: Color = Color.Unspecified, // Not used, kept for compatibility
    contentColor: Color = Color.Unspecified,
) {
    val color = if (contentColor != Color.Unspecified) contentColor else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides color) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            text()
        }
    }
}

