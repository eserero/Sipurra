# Plan: Thumbnail Presentation Improvements - Phase 2

## Objective
1. Reduce the height of the semi-transparent text background overlay from 60.dp to 48.dp to better fit 2 lines of text and eliminate the empty third line.
2. Add a new setting to toggle this semi-transparent background overlay.

## Changes

### 1. Data Layer
- **File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt`
    - Add `val cardLayoutOverlayBackground: Boolean = true` to the `AppSettings` data class.
- **File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/CommonSettingsRepository.kt`
    - Add `fun getCardLayoutOverlayBackground(): Flow<Boolean>`
    - Add `suspend fun putCardLayoutOverlayBackground(enabled: Boolean)`
- **File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/SettingsRepositoryWrapper.kt`
    - Implement the new repository methods by mapping to/from the `AppSettings` field.

### 2. Composition Locals
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/CompositionLocals.kt`
    - Add `val LocalCardLayoutOverlayBackground = compositionLocalOf { true }`

### 3. Settings UI
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/appearance/AppSettingsViewModel.kt`
    - Add a `var cardLayoutOverlayBackground` mutable state.
    - Update `initialize()` to load the value from the repository.
    - Add an `onCardLayoutOverlayBackgroundChange(enabled: Boolean)` function to update state and persist to the repository.
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/appearance/AppearanceSettingsContent.kt`
    - Add `cardLayoutOverlayBackground: Boolean` and `onCardLayoutOverlayBackgroundChange: (Boolean) -> Unit` to the parameters.
    - Add a `Row` with a `Switch` for "Card layout overlay background".
    - Update the internal `CompositionLocalProvider` (used for the preview card) to include `LocalCardLayoutOverlayBackground`.
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/appearance/AppSettingsScreen.kt`
    - Pass the new state and callback from the ViewModel to `AppearanceSettingsContent`.

### 4. Card UI
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/ItemCard.kt`
    - Update `CardTextBackground`:
        - Change height from `60.dp` to `48.dp`.
        - Use `LocalCardLayoutOverlayBackground.current` to determine if the background color should be applied. If `false`, the background should be `Color.Transparent`.

### 5. Application Shell
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/MainView.kt`
    - Add a `LaunchedEffect` to collect `getCardLayoutOverlayBackground()` into a local state.
    - Provide the value via `LocalCardLayoutOverlayBackground` in the root `CompositionLocalProvider`.

## Verification
- [ ] Verify that the background layer height is reduced to 48.dp.
- [ ] Verify that 2 lines of text still fit comfortably within 48.dp.
- [ ] Verify that the new "Card layout overlay background" setting appears in Appearance settings.
- [ ] Verify that toggling the setting immediately updates all thumbnails in the app.
