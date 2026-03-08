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

        settingsRepository.putNavBarColor(null)
        mutableState.value = LoadState.Success(Unit)
    }

    fun onCardWidthChange(cardWidth: Dp) {
        this.cardWidth = cardWidth
        screenModelScope.launch { settingsRepository.putCardWidth(cardWidth.value.toInt()) }
    }

    fun onAppThemeChange(theme: AppTheme) {
        this.currentTheme = theme
        screenModelScope.launch { settingsRepository.putAppTheme(theme) }
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

}