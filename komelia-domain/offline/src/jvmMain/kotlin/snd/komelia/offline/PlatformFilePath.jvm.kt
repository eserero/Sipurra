package snd.komelia.offline

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

internal actual fun PlatformFile.localFilePath(): String? = this.path
