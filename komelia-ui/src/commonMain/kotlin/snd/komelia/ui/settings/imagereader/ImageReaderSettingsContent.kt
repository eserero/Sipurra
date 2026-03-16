package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.settings.imagereader.ncnn.*
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsContent
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.ui.settings.imagereader.onnxruntime.isOnnxRuntimeSupported

@Composable
fun ImageReaderSettingsContent(
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,

    volumeKeysNavigation: Boolean,
    onVolumeKeysNavigationChange: (Boolean) -> Unit,

    keepReaderScreenOn: Boolean,
    onKeepReaderScreenOnChange: (Boolean) -> Unit,

    onCacheClear: () -> Unit,
    onnxRuntimeSettingsState: OnnxRuntimeSettingsState,
    ncnnSettingsState: NcnnSettingsState,
) {
    var showLogs by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val platform = LocalPlatform.current
        SwitchWithLabel(
            checked = loadThumbnailPreviews,
            onCheckedChange = onLoadThumbnailPreviewsChange,
            label = { Text("Load small previews when dragging navigation slider") },
            supportingText = { Text("can be slow for high resolution images") },
        )

        if (platform == PlatformType.MOBILE) {
            SwitchWithLabel(
                checked = volumeKeysNavigation,
                onCheckedChange = onVolumeKeysNavigationChange,
                label = { Text("Volume keys navigation") },
            )
            SwitchWithLabel(
                checked = keepReaderScreenOn,
                onCheckedChange = onKeepReaderScreenOnChange,
                label = { Text("Keep screen on while reading") },
            )
        }

        FilledTonalButton(
            onClick = onCacheClear,
            colors = accentColor?.let {
                val contentColor = if (it.luminance() > 0.5f) Color.Black else Color.White
                ButtonDefaults.filledTonalButtonColors(containerColor = it, contentColor = contentColor)
            } ?: ButtonDefaults.filledTonalButtonColors()
        ) { Text("Clear image cache") }

        if (isOnnxRuntimeSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            OnnxRuntimeSettingsContent(
                executionProvider = onnxRuntimeSettingsState.currentExecutionProvider,
                availableDevices = onnxRuntimeSettingsState.availableDevices,
                deviceId = onnxRuntimeSettingsState.deviceId.collectAsState().value,
                onDeviceIdChange = onnxRuntimeSettingsState::onDeviceIdChange,
                upscaleMode = onnxRuntimeSettingsState.upscaleMode.collectAsState().value,
                onUpscaleModeChange = onnxRuntimeSettingsState::onUpscaleModeChange,
                upscalerTileSize = onnxRuntimeSettingsState.upscalerTileSize.collectAsState().value,
                onUpscalerTileSizeChange = onnxRuntimeSettingsState::onTileSizeChange,
                upscaleModelPath = onnxRuntimeSettingsState.upscaleModelPath.collectAsState().value,
                onUpscaleModelPathChange = onnxRuntimeSettingsState::onUpscaleModelPathChange,
                onOrtInstall = onnxRuntimeSettingsState::onInstallRequest,
                mangaJaNaiIsInstalled = onnxRuntimeSettingsState.mangaJaNaiIsInstalled.collectAsState().value,
                onMangaJaNaiDownload = onnxRuntimeSettingsState::onMangaJaNaiDownloadRequest,
                panelModelIsDownloaded = onnxRuntimeSettingsState.panelModelIsDownloaded.collectAsState().value,
                panelDetectionUrl = onnxRuntimeSettingsState.panelDetectionUrl.collectAsState().value,
                onPanelDetectionUrlChange = onnxRuntimeSettingsState::onPanelDetectionUrlChange,
                onPanelDetectionModelDownloadRequest = onnxRuntimeSettingsState::onPanelDetectionModelDownloadRequest
            )
        }

        if (isNcnnSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            NcnnSettingsContent(
                settings = ncnnSettingsState.ncnnUpscalerSettings.collectAsState().value,
                onSettingsChange = ncnnSettingsState::onSettingsChange,
                onDownloadRequest = ncnnSettingsState::onNcnnDownloadRequest
            )
        }

        if (isNcnnSupported()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showLogs = true },
                    colors = accentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) }
                        ?: ButtonDefaults.textButtonColors()
                ) {
                    Text("View Logs")
                }
                TextButton(
                    onClick = { showCrashLogs = true },
                    colors = accentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) }
                        ?: ButtonDefaults.textButtonColors()
                ) {
                    Text("Crash Logs")
                }
            }
        }

        if (showLogs) {
            NcnnLogViewerDialog(onDismiss = { showLogs = false })
        }
        if (showCrashLogs) {
            NcnnCrashLogViewerDialog(onDismiss = { showCrashLogs = false })
        }
    }
}
