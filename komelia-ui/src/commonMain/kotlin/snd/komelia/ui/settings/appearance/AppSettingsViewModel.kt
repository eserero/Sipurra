package snd.komelia.ui.settings.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth

class AppSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    var cardWidth by mutableStateOf(defaultCardWidth.dp)
    var currentTheme by mutableStateOf(AppTheme.DARK)
    var accentColor by mutableStateOf<Color?>(null)
    var useNewLibraryUI by mutableStateOf(true)
    var cardLayoutBelow by mutableStateOf(false)
    var immersiveColorEnabled by mutableStateOf(true)
    var immersiveColorAlpha by mutableStateOf(0.12f)
    var showImmersiveNavBar by mutableStateOf(false)
    var hideParenthesesInNames by mutableStateOf(false)
    var lockScreenRotation by mutableStateOf(false)
    var cardLayoutOverlayBackground by mutableStateOf(true)
    var useNewLibraryUI2 by mutableStateOf(false)
    var useImmersiveMorphingCover by mutableStateOf(false)

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        cardWidth = settingsRepository.getCardWidth().map { it.dp }.first()
        currentTheme = settingsRepository.getAppTheme().first()
        accentColor = settingsRepository.getAccentColor().first()?.let { Color(it.toInt()) }
        useNewLibraryUI = settingsRepository.getUseNewLibraryUI().first()
        cardLayoutBelow = settingsRepository.getCardLayoutBelow().first()
        immersiveColorEnabled = settingsRepository.getImmersiveColorEnabled().first()
        immersiveColorAlpha = settingsRepository.getImmersiveColorAlpha().first()
        showImmersiveNavBar = settingsRepository.getShowImmersiveNavBar().first()
        hideParenthesesInNames = settingsRepository.getHideParenthesesInNames().first()
        lockScreenRotation = settingsRepository.getLockScreenRotation().first()
        cardLayoutOverlayBackground = settingsRepository.getCardLayoutOverlayBackground().first()
        useNewLibraryUI2 = settingsRepository.getUseNewLibraryUI2().first()
        useImmersiveMorphingCover = settingsRepository.getUseImmersiveMorphingCover().first()

        settingsRepository.putNavBarColor(null)
        mutableState.value = LoadState.Success(Unit)
    }

    fun onCardWidthChange(cardWidth: Dp) {
        this.cardWidth = cardWidth
        screenModelScope.launch { settingsRepository.putCardWidth(cardWidth.value.toInt()) }
    }

    fun onAppThemeChange(theme: AppTheme) {
        this.currentTheme = theme
        screenModelScope.launch {
            settingsRepository.putAppTheme(theme)
            when (theme) {
                AppTheme.LIGHT_MODERN -> {
                    this@AppSettingsViewModel.accentColor = Color(0xFF6A1CF6.toInt())
                    settingsRepository.putAccentColor(Color(0xFF6A1CF6.toInt()).toArgb().toLong())
                }
                AppTheme.DARK_MODERN -> {
                    this@AppSettingsViewModel.accentColor = Color(0xFFBA9EFF.toInt())
                    settingsRepository.putAccentColor(Color(0xFFBA9EFF.toInt()).toArgb().toLong())
                }
                else -> {}
            }
        }
    }

    fun onAccentColorChange(color: Color?) {
        this.accentColor = color
        screenModelScope.launch { settingsRepository.putAccentColor(color?.toArgb()?.toLong()) }
    }

    fun onUseNewLibraryUIChange(enabled: Boolean) {
        this.useNewLibraryUI = enabled
        screenModelScope.launch { settingsRepository.putUseNewLibraryUI(enabled) }
    }

    fun onCardLayoutBelowChange(enabled: Boolean) {
        this.cardLayoutBelow = enabled
        screenModelScope.launch { settingsRepository.putCardLayoutBelow(enabled) }
    }

    fun onImmersiveColorEnabledChange(enabled: Boolean) {
        this.immersiveColorEnabled = enabled
        screenModelScope.launch { settingsRepository.putImmersiveColorEnabled(enabled) }
    }

    fun onImmersiveColorAlphaChange(alpha: Float) {
        this.immersiveColorAlpha = alpha
        screenModelScope.launch { settingsRepository.putImmersiveColorAlpha(alpha) }
    }

    fun onShowImmersiveNavBarChange(enabled: Boolean) {
        this.showImmersiveNavBar = enabled
        screenModelScope.launch { settingsRepository.putShowImmersiveNavBar(enabled) }
    }

    fun onHideParenthesesInNamesChange(hide: Boolean) {
        this.hideParenthesesInNames = hide
        screenModelScope.launch { settingsRepository.putHideParenthesesInNames(hide) }
    }

    fun onLockScreenRotationChange(locked: Boolean) {
        this.lockScreenRotation = locked
        screenModelScope.launch { settingsRepository.putLockScreenRotation(locked) }
    }

    fun onCardLayoutOverlayBackgroundChange(enabled: Boolean) {
        this.cardLayoutOverlayBackground = enabled
        screenModelScope.launch { settingsRepository.putCardLayoutOverlayBackground(enabled) }
    }

    fun onUseNewLibraryUI2Change(enabled: Boolean) {
        this.useNewLibraryUI2 = enabled
        screenModelScope.launch { settingsRepository.putUseNewLibraryUI2(enabled) }
    }

    fun onUseImmersiveMorphingCoverChange(enabled: Boolean) {
        this.useImmersiveMorphingCover = enabled
        screenModelScope.launch { settingsRepository.putUseImmersiveMorphingCover(enabled) }
    }

}