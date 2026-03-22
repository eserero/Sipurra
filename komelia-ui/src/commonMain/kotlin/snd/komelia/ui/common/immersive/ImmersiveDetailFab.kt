package snd.komelia.ui.common.immersive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalNavBarColor
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.common.SplitFabMenu

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImmersiveDetailFab(
    onReadClick: () -> Unit,
    onReadIncognitoClick: () -> Unit,
    onDownloadClick: () -> Unit,
    accentColor: Color? = null,
    showReadActions: Boolean = true,
) {
    val theme = LocalTheme.current
    val navBarColor = LocalNavBarColor.current

    val (fabContainerColor, fabContentColor) = if (theme.type == Theme.ThemeType.LIGHT) {
        Color(red = 43, green = 43, blue = 43) to Color.White
    } else {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    val readNowContainerColor = accentColor
        ?: if (theme.type == Theme.ThemeType.LIGHT) Color(red = 43, green = 43, blue = 43)
        else navBarColor ?: MaterialTheme.colorScheme.primaryContainer

    val readNowContentColor = if (readNowContainerColor.luminance() > 0.5f) Color.Black else Color.White

    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        if (showReadActions) {
            SplitFabMenu(
                modifier = Modifier.offset(x = 20.dp, y = 20.dp),
                expanded = expanded,
                onExpandedChange = { expanded = it },
                primaryActionText = "Read",
                primaryActionIcon = Icons.AutoMirrored.Rounded.MenuBook,
                onPrimaryActionClick = {
                    expanded = false
                    onReadClick()
                },
                containerColor = readNowContainerColor,
                contentColor = readNowContentColor,
                menuItems = {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            expanded = false
                            onReadClick()
                        },
                        icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                        text = { Text("Read") },
                        containerColor = readNowContainerColor,
                        contentColor = readNowContentColor
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            expanded = false
                            onReadIncognitoClick()
                        },
                        icon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                        text = { Text("Read Incognito") },
                        containerColor = readNowContainerColor,
                        contentColor = readNowContentColor
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            expanded = false
                            onDownloadClick()
                        },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        text = { Text("Download") },
                        containerColor = readNowContainerColor,
                        contentColor = readNowContentColor
                    )
                }
            )
        } else {
            // Download FAB
            FloatingActionButton(
                onClick = onDownloadClick,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Download"
                )
            }
        }
    }
}
