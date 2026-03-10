package snd.komelia.ui.common.immersive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

fun normalizePublisherName(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberPublisherLogo(publisher: String?): ImageBitmap? {
    var bitmap by remember(publisher) { mutableStateOf<ImageBitmap?>(null) }
    if (!publisher.isNullOrBlank()) {
        LaunchedEffect(publisher) {
            val key = normalizePublisherName(publisher)
            bitmap = runCatching {
                Res.readBytes("files/publishers/$key.png").decodeToImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
