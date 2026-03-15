package snd.komelia.ui.reader.image.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpscaleStatus
import snd.komelia.image.UpsamplingMode
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderType
import snd.komelia.settings.model.ReaderType.CONTINUOUS
import snd.komelia.settings.model.ReaderType.PAGED
import snd.komelia.settings.model.ReaderType.PANELS
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.components.accentInputChipColors
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsState
import snd.komelia.ui.settings.imagereader.ncnn.isNcnnSupported
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetSettingsOverlay(
    book: KomeliaBook?,
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    isColorCorrectionsActive: Boolean,
    onColorCorrectionClick: () -> Unit,
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,
    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
    stretchToFit: Boolean,
    onStretchToFitChange: (Boolean) -> Unit,
    cropBorders: Boolean,
    onCropBordersChange: (Boolean) -> Unit,
    zoom: Float,

    flashEnabled: Boolean,
    onFlashEnabledChange: (Boolean) -> Unit,
    flashEveryNPages: Int,
    onFlashEveryNPagesChange: (Int) -> Unit,
    flashWith: ReaderFlashColor,
    onFlashWithChange: (ReaderFlashColor) -> Unit,
    flashDuration: Long,
    onFlashDurationChange: (Long) -> Unit,

    tapNavigationMode: ReaderTapNavigationMode,
    onTapNavigationModeChange: (ReaderTapNavigationMode) -> Unit,

    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    ncnnSettingsState: NcnnSettingsState,
    onBackPress: () -> Unit,
) {

    val windowWidth = LocalWindowWidth.current
    val accentColor = LocalAccentColor.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    val allUpscaleActivities by ncnnSettingsState.globalUpscaleActivities.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .add(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackPress,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }

                book?.let {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            val titleStyle =
                                if (windowWidth == COMPACT) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.titleLarge

                            Text(
                                it.seriesTitle,
                                maxLines = 1,
                                style = titleStyle,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                it.metadata.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = allUpscaleActivities.isNotEmpty()) {
                UpscaleActivityIndicator(allUpscaleActivities)
            }
        }

        FloatingActionButton(
            onClick = { showSettingsDialog = true },
            containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (accentColor != null) {
                if (accentColor.luminance() > 0.5f) Color.Black else Color.White
            } else MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 80.dp, end = 16.dp)
        ) {
            Icon(Icons.Rounded.Tune, null)
        }

        ReaderFloatingToolbar(
            readerType = readerType,
            onReaderTypeChange = onReaderTypeChange,
            panelsReaderState = panelsReaderState,
            ncnnSettingsState = ncnnSettingsState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 80.dp, end = 80.dp),
        )
    }

    BoxWithConstraints {

        val maxHeight = this.maxHeight
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        if (showSettingsDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsDialog = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                var selectedTab by remember { mutableStateOf(0) }
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Reading mode")
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Navigation")
                    }
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.heightIn(min = 40.dp).cursorForHand(),
                    ) {
                        Text("Image settings")
                    }
                }
                val focusManager = LocalFocusManager.current
                val width = LocalWindowWidth.current
                val contentPadding = remember(width) {
                    when (width) {
                        COMPACT -> 10.dp
                        else -> 20.dp
                    }
                }
                Column(
                    Modifier
                        .padding(contentPadding)
                        .heightIn(max = maxHeight * 0.8f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                ) {

                    when (selectedTab) {
                        0 -> {
                            BottomSheetReadingModeSettings(
                                readerType = readerType,
                                onReaderTypeChange = onReaderTypeChange,
                                pagedReaderState = pagedReaderState,
                                continuousReaderState = continuousReaderState,
                                panelsReaderState = panelsReaderState,
                            )
                        }

                        1 -> NavigationSettings(
                            currentMode = tapNavigationMode,
                            onModeChange = onTapNavigationModeChange
                        )

                        2 -> BottomSheetImageSettings(
                            readerType = readerType,
                            pagedReaderState = pagedReaderState,
                            continuousReaderState = continuousReaderState,
                            panelsReaderState = panelsReaderState,
                            availableUpsamplingModes = availableUpsamplingModes,
                            upsamplingMode = upsamplingMode,
                            onUpsamplingModeChange = onUpsamplingModeChange,
                            availableDownsamplingKernels = availableDownsamplingKernels,
                            downsamplingKernel = downsamplingKernel,
                            onDownsamplingKernelChange = onDownsamplingKernelChange,
                            linearLightDownsampling = linearLightDownsampling,
                            onLinearLightDownsamplingChange = onLinearLightDownsamplingChange,
                            stretchToFit = stretchToFit,
                            onStretchToFitChange = onStretchToFitChange,
                            cropBorders = cropBorders,
                            onCropBordersChange = onCropBordersChange,
                            isColorCorrectionsActive = isColorCorrectionsActive,
                            onColorCorrectionClick = onColorCorrectionClick,
                            zoom = zoom,
                            flashEnabled = flashEnabled,
                            onFlashEnabledChange = onFlashEnabledChange,
                            flashEveryNPages = flashEveryNPages,
                            onFlashEveryNPagesChange = onFlashEveryNPagesChange,
                            flashWith = flashWith,
                            onFlashWithChange = onFlashWithChange,
                            flashDuration = flashDuration,
                            onFlashDurationChange = onFlashDurationChange,
                            ncnnSettingsState = ncnnSettingsState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetReadingModeSettings(
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
) {
    Column {
        Text("Reading mode")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InputChip(
                selected = readerType == PAGED,
                onClick = { onReaderTypeChange(PAGED) },
                colors = accentInputChipColors(),
                label = { Text("Paged") }
            )
            InputChip(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                colors = accentInputChipColors(),
                label = { Text("Continuous") }
            )
            if (panelsReaderState != null)
                InputChip(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    colors = accentInputChipColors(),
                    label = { Text("Panels") }
                )
        }

        when (readerType) {
            PAGED -> PagedModeSettings(pageState = pagedReaderState)
            PANELS -> if (panelsReaderState != null) PanelsModeSettings(state = panelsReaderState)
            CONTINUOUS -> ContinuousModeSettings(state = continuousReaderState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagedModeSettings(
    pageState: PagedReaderState,
) {
    val strings = LocalStrings.current.pagedReader
    val scaleType = pageState.scaleType.collectAsState().value
    val tapToZoom = pageState.tapToZoom.collectAsState().value
    val adaptiveBackground = pageState.adaptiveBackground.collectAsState().value
    Column {

        Text(strings.scaleType)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = scaleType == LayoutScaleType.SCREEN,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.SCREEN) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.SCREEN)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.FIT_WIDTH,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.FIT_WIDTH) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.FIT_WIDTH)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.FIT_HEIGHT,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.FIT_HEIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.FIT_HEIGHT)) }
            )
            InputChip(
                selected = scaleType == LayoutScaleType.ORIGINAL,
                onClick = { pageState.onScaleTypeChange(LayoutScaleType.ORIGINAL) },
                colors = accentInputChipColors(),
                label = { Text(strings.forScaleType(LayoutScaleType.ORIGINAL)) }
            )
        }

        val readingDirection = pageState.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == PagedReadingDirection.RIGHT_TO_LEFT,
                onClick = { pageState.onReadingDirectionChange(PagedReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.RIGHT_TO_LEFT)) }
            )
            InputChip(
                selected = readingDirection == PagedReadingDirection.LEFT_TO_RIGHT,
                onClick = { pageState.onReadingDirectionChange(PagedReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.LEFT_TO_RIGHT)) }
            )
        }

        val layout = pageState.layout.collectAsState().value
        Text(strings.layout)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = layout == PageDisplayLayout.SINGLE_PAGE,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.SINGLE_PAGE) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.SINGLE_PAGE)) }
            )
            InputChip(
                selected = layout == PageDisplayLayout.DOUBLE_PAGES,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.DOUBLE_PAGES) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.DOUBLE_PAGES)) }
            )
            InputChip(
                selected = layout == PageDisplayLayout.DOUBLE_PAGES_NO_COVER,
                onClick = { pageState.onLayoutChange(PageDisplayLayout.DOUBLE_PAGES_NO_COVER) },
                colors = accentInputChipColors(),
                label = { Text(strings.forLayout(PageDisplayLayout.DOUBLE_PAGES_NO_COVER)) }
            )
        }
        AnimatedVisibility(layout == PageDisplayLayout.DOUBLE_PAGES || layout == PageDisplayLayout.DOUBLE_PAGES_NO_COVER) {
            HorizontalDivider()
            val layoutOffset = pageState.layoutOffset.collectAsState().value
            SwitchWithLabel(
                checked = layoutOffset,
                onCheckedChange = pageState::onLayoutOffsetChange,
                label = { Text(strings.offsetPages) },
                contentPadding = PaddingValues(horizontal = 10.dp),
            )
        }

        SwitchWithLabel(
            checked = tapToZoom,
            onCheckedChange = pageState::onTapToZoomChange,
            label = { Text("Tap to zoom") },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = adaptiveBackground,
            onCheckedChange = pageState::onAdaptiveBackgroundChange,
            label = { Text(strings.adaptiveBackground) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelsModeSettings(
    state: PanelsReaderState,
) {
    val strings = LocalStrings.current.pagedReader
    val tapToZoom = state.tapToZoom.collectAsState().value
    val adaptiveBackground = state.adaptiveBackground.collectAsState().value
    Column {

        val readingDirection = state.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == PagedReadingDirection.RIGHT_TO_LEFT,
                onClick = { state.onReadingDirectionChange(PagedReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.RIGHT_TO_LEFT)) }
            )
            InputChip(
                selected = readingDirection == PagedReadingDirection.LEFT_TO_RIGHT,
                onClick = { state.onReadingDirectionChange(PagedReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(PagedReadingDirection.LEFT_TO_RIGHT)) }
            )
        }

        val displayMode = state.fullPageDisplayMode.collectAsState().value
        Text("Show full page")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelsFullPageDisplayMode.entries.forEach { mode ->
                InputChip(
                    selected = displayMode == mode,
                    onClick = { state.onFullPageDisplayModeChange(mode) },
                    colors = accentInputChipColors(),
                    label = { Text(mode.name) }
                )
            }
        }

        SwitchWithLabel(
            checked = tapToZoom,
            onCheckedChange = state::onTapToZoomChange,
            label = { Text("Tap to zoom") },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )

        SwitchWithLabel(
            checked = adaptiveBackground,
            onCheckedChange = state::onAdaptiveBackgroundChange,
            label = { Text(strings.adaptiveBackground) },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuousModeSettings(
    state: ContinuousReaderState,
) {
    val strings = LocalStrings.current.continuousReader
    val windowWidth = LocalWindowWidth.current
    val accentColor = LocalAccentColor.current
    Column {
        val readingDirection = state.readingDirection.collectAsState().value
        Text(strings.readingDirection)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.TOP_TO_BOTTOM,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.TOP_TO_BOTTOM) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.TOP_TO_BOTTOM)) }
            )
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.LEFT_TO_RIGHT,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.LEFT_TO_RIGHT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.LEFT_TO_RIGHT)) }
            )
            InputChip(
                selected = readingDirection == ContinuousReadingDirection.RIGHT_TO_LEFT,
                onClick = { state.onReadingDirectionChange(ContinuousReadingDirection.RIGHT_TO_LEFT) },
                colors = accentInputChipColors(),
                label = { Text(strings.forReadingDirection(ContinuousReadingDirection.RIGHT_TO_LEFT)) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val sidePadding = state.sidePaddingFraction.collectAsState().value
            val paddingPercentage = remember(sidePadding) { (sidePadding * 200).roundToInt() }
            Column(Modifier.width(100.dp)) {
                Text("Side padding", style = MaterialTheme.typography.labelLarge)
                Text("$paddingPercentage%", style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = sidePadding,
                onValueChange = state::onSidePaddingChange,
                steps = 15,
                valueRange = 0f..0.4f,
                colors = AppSliderDefaults.colors(accentColor = accentColor)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val spacing = state.pageSpacing.collectAsState(Dispatchers.Main.immediate).value
            Column(Modifier.width(100.dp)) {
                Text("Page spacing", style = MaterialTheme.typography.labelLarge)
                Text("$spacing", style = MaterialTheme.typography.labelMedium)
            }
            when (windowWidth) {
                COMPACT -> Slider(
                    value = spacing.toFloat(),
                    onValueChange = { state.onPageSpacingChange(it.roundToInt()) },
                    steps = 24,
                    valueRange = 0f..250f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor)
                )

                else -> Slider(
                    value = spacing.toFloat(),
                    onValueChange = { state.onPageSpacingChange(it.roundToInt()) },
                    steps = 49,
                    valueRange = 0f..500f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor)
                )
            }

        }
        Spacer(Modifier.heightIn(30.dp))
    }
}

@Composable
private fun BottomSheetImageSettings(
    readerType: ReaderType,
    pagedReaderState: PagedReaderState,
    continuousReaderState: ContinuousReaderState,
    panelsReaderState: PanelsReaderState?,
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,

    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
    stretchToFit: Boolean,
    onStretchToFitChange: (Boolean) -> Unit,
    cropBorders: Boolean,
    onCropBordersChange: (Boolean) -> Unit,
    isColorCorrectionsActive: Boolean,
    onColorCorrectionClick: () -> Unit,
    zoom: Float,

    flashEnabled: Boolean,
    onFlashEnabledChange: (Boolean) -> Unit,
    flashEveryNPages: Int,
    onFlashEveryNPagesChange: (Int) -> Unit,
    flashWith: ReaderFlashColor,
    onFlashWithChange: (ReaderFlashColor) -> Unit,
    flashDuration: Long,
    onFlashDurationChange: (Long) -> Unit,
    ncnnSettingsState: NcnnSettingsState,
) {
    Column {
        SamplingModeSettings(
            availableUpsamplingModes = availableUpsamplingModes,
            upsamplingMode = upsamplingMode,
            onUpsamplingModeChange = onUpsamplingModeChange,
            availableDownsamplingKernels = availableDownsamplingKernels,
            downsamplingKernel = downsamplingKernel,
            onDownsamplingKernelChange = onDownsamplingKernelChange,
            linearLightDownsampling = linearLightDownsampling,
            onLinearLightDownsamplingChange = onLinearLightDownsamplingChange,
        )
        CommonImageSettings(
            stretchToFit = stretchToFit,
            onStretchToFitChange = onStretchToFitChange,
            cropBorders = cropBorders,
            onCropBordersChange = onCropBordersChange,
            isColorCorrectionsActive = isColorCorrectionsActive,
            onColorCorrectionClick = onColorCorrectionClick,
            flashEnabled = flashEnabled,
            onFlashEnabledChange = onFlashEnabledChange,
            flashEveryNPages = flashEveryNPages,
            onFlashEveryNPagesChange = onFlashEveryNPagesChange,
            flashWith = flashWith,
            onFlashWithChange = onFlashWithChange,
            flashDuration = flashDuration,
            onFlashDurationChange = onFlashDurationChange,
        )

        if (snd.komelia.ui.settings.imagereader.ncnn.isNcnnSupported()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            snd.komelia.ui.settings.imagereader.ncnn.NcnnSettingsContent(
                settings = ncnnSettingsState.ncnnUpscalerSettings.collectAsState().value,
                onSettingsChange = ncnnSettingsState::onSettingsChange,
                onDownloadRequest = ncnnSettingsState::onNcnnDownloadRequest
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 5.dp))

        val strings = LocalStrings.current.reader
        val zoomPercentage = remember(zoom) { (zoom * 100).roundToInt() }
        Text("${strings.zoom}: $zoomPercentage%")
        when (readerType) {
            PAGED ->
                PagedReaderPagesInfo(
                    pages = pagedReaderState.currentSpread.collectAsState().value.pages,
                    modifier = Modifier.animateContentSize()
                )

            PANELS -> {
                if (panelsReaderState != null) {
                    val panelsPage = panelsReaderState.currentPage.collectAsState().value
                    val pages = remember(panelsPage) {
                        panelsPage?.let { listOf(PagedReaderState.Page(it.metadata, it.imageResult)) } ?: emptyList()
                    }
                    PagedReaderPagesInfo(
                        pages = pages,
                        modifier = Modifier.animateContentSize()
                    )
                }
            }

            CONTINUOUS -> ContinuousReaderPagesInfo(
                lazyListState = continuousReaderState.lazyListState,
                waitForImage = continuousReaderState::waitForImage,
                modifier = Modifier.animateContentSize()
            )
        }
    }

}

@Composable
private fun UpscaleActivityIndicator(activities: Map<Int, UpscaleStatus>) {
    if (activities.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        activities.entries.sortedBy { it.key }.forEach { (page, status) ->
            when (status) {
                UpscaleStatus.Upscaling -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(2.dp))
                    Text("p$page", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                }
                UpscaleStatus.Upscaled -> {
                    Text("p$page ✓", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                }
                UpscaleStatus.Idle -> {}
            }
        }
    }
}

@Composable
private fun ReaderFloatingToolbar(
    readerType: ReaderType,
    onReaderTypeChange: (ReaderType) -> Unit,
    panelsReaderState: PanelsReaderState?,
    ncnnSettingsState: NcnnSettingsState,
    modifier: Modifier = Modifier,
) {
    val ncnnSettings by ncnnSettingsState.ncnnUpscalerSettings.collectAsState()
    val showUpscale = isNcnnSupported()

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReaderModeIconButton(
                selected = readerType == PAGED,
                onClick = { onReaderTypeChange(PAGED) },
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "Paged",
            )
            ReaderModeIconButton(
                selected = readerType == CONTINUOUS,
                onClick = { onReaderTypeChange(CONTINUOUS) },
                icon = Icons.Rounded.ViewStream,
                contentDescription = "Continuous",
            )
            if (panelsReaderState != null) {
                ReaderModeIconButton(
                    selected = readerType == PANELS,
                    onClick = { onReaderTypeChange(PANELS) },
                    icon = Icons.Rounded.GridView,
                    contentDescription = "Panels",
                )
            }

            if (showUpscale) {
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                )
                ReaderModeIconButton(
                    selected = ncnnSettings.enabled,
                    onClick = { ncnnSettingsState.onSettingsChange(ncnnSettings.copy(enabled = !ncnnSettings.enabled)) },
                    icon = Icons.Rounded.AutoAwesome,
                    contentDescription = "Upscaling",
                )
            }
        }
    }
}

@Composable
private fun ReaderModeIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
) {
    val accentColor = LocalAccentColor.current
    val indicatorColor = accentColor ?: MaterialTheme.colorScheme.secondaryContainer
    val selectedIconTint = if (accentColor != null) {
        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    } else MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) indicatorColor else Color.Transparent)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) selectedIconTint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SamplingModeSettings(
    availableUpsamplingModes: List<UpsamplingMode>,
    upsamplingMode: UpsamplingMode,
    onUpsamplingModeChange: (UpsamplingMode) -> Unit,
    availableDownsamplingKernels: List<ReduceKernel>,
    downsamplingKernel: ReduceKernel,
    onDownsamplingKernelChange: (ReduceKernel) -> Unit,
    linearLightDownsampling: Boolean,
    onLinearLightDownsamplingChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current.imageSettings

    if (availableUpsamplingModes.size > 1) {
        Column {
            Text(strings.upsamplingMode)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableUpsamplingModes.forEach { mode ->
                    InputChip(
                        selected = upsamplingMode == mode,
                        onClick = { onUpsamplingModeChange(mode) },
                        colors = accentInputChipColors(),
                        label = { Text(strings.forUpsamplingMode(mode)) }
                    )

                }
            }
        }
    }

    if (availableDownsamplingKernels.size > 1) {
        Column {
            Text(strings.downsamplingKernel)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableDownsamplingKernels.forEach { kernel ->
                    InputChip(
                        selected = downsamplingKernel == kernel,
                        onClick = { onDownsamplingKernelChange(kernel) },
                        colors = accentInputChipColors(),
                        label = { Text(strings.forDownsamplingKernel(kernel)) }
                    )

                }
            }
        }
    }


    SwitchWithLabel(
        checked = linearLightDownsampling,
        onCheckedChange = onLinearLightDownsamplingChange,
        label = { Text("Linear light downsampling") },
        supportingText = {
            Text("slower but potentially more accurate", style = MaterialTheme.typography.labelMedium)
        },
        contentPadding = PaddingValues(horizontal = 10.dp)
    )
}
