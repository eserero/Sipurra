package snd.komelia.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalTheme

@Composable
fun ReaderControlsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalTheme.current
    val hazeState = LocalHazeState.current
    val hazeStyle = if (hazeState != null) {
        HazeMaterials.thin(theme.colorScheme.surface.copy(alpha = 0.4f))
    } else null

    val commonModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 16.dp)
        .navigationBarsPadding()

    if (hazeState != null && hazeStyle != null) {
        Box(
            modifier = commonModifier
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .hazeEffect(hazeState) { style = hazeStyle }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                content()
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = commonModifier
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                content()
            }
        }
    }
}
