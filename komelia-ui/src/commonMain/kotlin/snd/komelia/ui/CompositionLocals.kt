package snd.komelia.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import snd.komelia.AppWindowState
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.strings.EnStrings
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.sse.KomgaEvent

val LocalViewModelFactory = compositionLocalOf<ViewModelFactory> { error("ViewModel factory is not set") }
val LocalMainScreenViewModel = compositionLocalOf<MainScreenViewModel> { error("MainScreenViewModel is not set") }

val LocalToaster = compositionLocalOf<ToasterState> { error("Toaster is not set") }
val LocalKomgaEvents = compositionLocalOf<SharedFlow<KomgaEvent>> { error("Komga events are not set") }
val LocalKomfIntegration = compositionLocalOf { flowOf(false) }
val LocalKeyEvents = compositionLocalOf<SharedFlow<KeyEvent>> { error("Key events are not set") }
val LocalWindowWidth = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalWindowHeight = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalStrings = staticCompositionLocalOf { EnStrings }
val LocalPlatform = compositionLocalOf<PlatformType> { error("Platform type is not set") }
val LocalTheme = compositionLocalOf { Theme.DARK }
val LocalWindowState = compositionLocalOf<AppWindowState> { error("Window state was not initialized") }
val LocalLibraries = compositionLocalOf<StateFlow<List<KomgaLibrary>>> { error("Libraries were not initialized") }
val LocalReloadEvents = staticCompositionLocalOf<SharedFlow<Unit>> { error("Reload event flow was not initialized") }
val LocalBookDownloadEvents =
    staticCompositionLocalOf<SharedFlow<DownloadEvent>?> { error("Book download event flow was not initialized") }
val LocalOfflineMode = staticCompositionLocalOf<StateFlow<Boolean>> { error("offline mode flow was not initialized") }
val LocalKomgaState = staticCompositionLocalOf<KomgaAuthenticationState> { error("komga state was not initialized") }
val LocalNavBarColor = compositionLocalOf<Color?> { null }
val LocalAccentColor = compositionLocalOf<Color?> { null }
val LocalUseNewLibraryUI = compositionLocalOf { true }
val LocalCardLayoutBelow = compositionLocalOf { false }
val LocalImmersiveColorEnabled = compositionLocalOf { true }
val LocalImmersiveColorAlpha = compositionLocalOf { 0.12f }
val LocalRawStatusBarHeight = staticCompositionLocalOf { 0.dp }
val LocalRawNavBarHeight = staticCompositionLocalOf { 0.dp }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
