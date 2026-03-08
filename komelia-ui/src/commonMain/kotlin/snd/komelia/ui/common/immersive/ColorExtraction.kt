package snd.komelia.ui.common.immersive

import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImagePainter

expect suspend fun extractDominantColor(painter: AsyncImagePainter): Color?
