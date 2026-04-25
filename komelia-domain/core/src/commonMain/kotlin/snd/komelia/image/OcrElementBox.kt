package snd.komelia.image
import androidx.compose.ui.geometry.Rect

data class OcrElementBox(
    val text: String,
    val imageRect: Rect,
    val blockIndex: Int,
    val lineIndex: Int,
    val elementIndex: Int,
    var selected: Boolean = false
)
