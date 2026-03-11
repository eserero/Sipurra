# Implementation Plan - Hide Parentheses in Names

This plan outlines the changes required to add a new option in the general settings to remove anything in parentheses when displaying series and oneshot names.

## 1. Data Layer Changes

### 1.1 Update `AppSettings`
Add a new property `hideParenthesesInNames` to the `AppSettings` data class.
- **File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt`
- **Change:** Add `val hideParenthesesInNames: Boolean = false`.

### 1.2 Update `CommonSettingsRepository`
Add getter and setter for the new setting.
- **File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/CommonSettingsRepository.kt`
- **Change:** Add `fun getHideParenthesesInNames(): Flow<Boolean>` and `suspend fun putHideParenthesesInNames(hide: Boolean)`.

### 1.3 Update `SettingsRepositoryWrapper`
Implement the new methods in the wrapper.
- **File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/SettingsRepositoryWrapper.kt`
- **Change:** Implement `getHideParenthesesInNames` and `putHideParenthesesInNames`.

## 2. Domain Logic Changes

### 2.1 Create String Utility
Create a utility function to filter the names.
- **Possible File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/utils/StringExtensions.kt` (or similar)
- **Function:**
  ```kotlin
  fun String.removeParentheses(): String {
      val index = this.indexOf('(')
      return if (index != -1) this.substring(0, index).trim() else this
  }
  ```

## 3. UI Changes - Settings

### 3.1 Update `AppSettingsViewModel`
Expose the new setting to the UI.
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/appearance/AppSettingsViewModel.kt`

### 3.2 Update `AppSettingsScreen`
Add a toggle in the General/Appearance settings.
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/appearance/AppSettingsScreen.kt`

## 4. UI Changes - Display

To avoid passing the setting through every component, the best approach might be to provide the setting via a `CompositionLocal` or use a helper that accesses the `LocalKomgaState` if it's available. However, since many components already have access to the model, we can apply the transformation there.

### 4.1 Thumbnails (Cards)
Update the following components to use the filtered name:
- **`SeriesItemCard.kt`**:
    - `SeriesImageOverlay`: Filter `series.metadata.title`.
    - `SeriesDetails`: Filter `series.metadata.title`.
    - `SeriesSimpleImageCard`: Filter `series.metadata.title`.
- **`BookItemCard.kt`**:
    - `BookImageOverlay`: 
        - Filter `book.seriesTitle`.
        - Filter `book.metadata.title` **ONLY** if `book.oneshot` is true.
    - `BookDetailedListDetails`: Filter `book.metadata.title` **ONLY** if `book.oneshot` is true.
    - `BookImageCard`: 
        - Filter `book.seriesTitle`.
        - Filter `book.metadata.title` **ONLY** if `book.oneshot` is true (in the branch where series title is not shown or for unavailable items).

### 4.2 Immersive Screens
- **`ImmersiveSeriesContent.kt`**:
    - Filter `series.metadata.title` in the main header and confirmation dialogs.
- **`ImmersiveBookContent.kt`**:
    - Filter `pageBook.seriesTitle` in the series link/header (e.g., "Series Name · #1").
- **`ImmersiveOneshotContent.kt`**:
    - Filter `book.metadata.title` in the main header (represents the oneshot name).

### 4.3 Standard Screens (Non-Immersive)
- **`SeriesContent.kt`**:
    - `SeriesToolBar`: Filter `series.metadata.title`.
- **`BookInfoContent.kt`**:
    - `BookInfoRow`: Filter `book.seriesTitle` in the series button.
- **`OneshotScreenContent.kt`**:
    - `OneshotToolBar`: Filter `book.metadata.title`.
    - `OneshotMainInfo`: The `BookInfoRow` already handles `seriesTitle` (which is null here), but ensure any direct title usage is filtered.

## 5. Implementation Strategy
1. Modify the settings repository and data models.
2. Add the toggle to the Settings screen.
3. Create a `CompositionLocal` for `HideParenthesesInNames` to make it easily accessible in UI components without prop drilling.
4. Update the identified UI components to respect this setting.
