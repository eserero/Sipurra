# Detailed Implementation Plan: Panel Reader "Show Full Page" Settings

## 1. Domain Model
**File**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/PanelsFullPageDisplayMode.kt`
```kotlin
package snd.komelia.settings.model

enum class PanelsFullPageDisplayMode {
    NONE,
    BEFORE,
    AFTER,
    BOTH
}
```

## 2. Database Migration
**File**: `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/V16__panel_reader_settings.sql`
```sql
ALTER TABLE ImageReaderSettings ADD COLUMN panels_full_page_display_mode TEXT NOT NULL DEFAULT 'NONE';
```

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/migrations/AppMigrations.kt`
```kotlin
    private val migrations = listOf(
        // ... existing
        "V15__new_library_ui.sql",
        "V16__panel_reader_settings.sql"
    )
```

## 3. Persistence Layer Updates

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/ImageReaderSettingsTable.kt`
```kotlin
    val panelsFullPageDisplayMode = text("panels_full_page_display_mode").default("NONE")
```

**File**: `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/ImageReaderSettings.kt`
```kotlin
    val panelsFullPageDisplayMode: PanelsFullPageDisplayMode = PanelsFullPageDisplayMode.NONE,
```

**File**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/ImageReaderSettingsRepository.kt`
```kotlin
    fun getPanelsFullPageDisplayMode(): Flow<PanelsFullPageDisplayMode>
    suspend fun putPanelsFullPageDisplayMode(mode: PanelsFullPageDisplayMode)
```

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedImageReaderSettingsRepository.kt`
- **In `get()` mapping**:
```kotlin
    panelsFullPageDisplayMode = PanelsFullPageDisplayMode.valueOf(it[ImageReaderSettingsTable.panelsFullPageDisplayMode]),
```
- **In `save()` mapping**:
```kotlin
    it[panelsFullPageDisplayMode] = settings.panelsFullPageDisplayMode.name
```

## 4. State Management
**File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/panels/PanelsReaderState.kt`
- **Properties**:
```kotlin
    val fullPageDisplayMode = MutableStateFlow(PanelsFullPageDisplayMode.NONE)
```
- **In `initialize()`**:
```kotlin
    fullPageDisplayMode.value = settingsRepository.getPanelsFullPageDisplayMode().first()
```
- **Logic in `doPageLoad()`**:
```kotlin
    val mode = fullPageDisplayMode.value
    val showFirst = mode == PanelsFullPageDisplayMode.BEFORE || mode == PanelsFullPageDisplayMode.BOTH
    val showLast = mode == PanelsFullPageDisplayMode.AFTER || mode == PanelsFullPageDisplayMode.BOTH

    if (showFirst && !alreadyHasFullPage) finalPanels.add(fullPageRect)
    finalPanels.addAll(sortedPanels)
    if (showLast && !alreadyHasFullPage) finalPanels.add(fullPageRect)
```
- **Change Handler**:
```kotlin
    fun onFullPageDisplayModeChange(mode: PanelsFullPageDisplayMode) {
        this.fullPageDisplayMode.value = mode
        stateScope.launch { settingsRepository.putPanelsFullPageDisplayMode(mode) }
        launchPageLoad(currentPageIndex.value.page)
    }
```

## 5. UI Components

### Desktop UI
**File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/SettingsSideMenu.kt`
- Update `PanelsReaderSettingsContent` signature and body:
```kotlin
@Composable
private fun PanelsReaderSettingsContent(
    state: PanelsReaderState
) {
    val strings = LocalStrings.current.pagedReader
    val readingDirection = state.readingDirection.collectAsState().value
    val displayMode = state.fullPageDisplayMode.collectAsState().value
    
    Column {
        DropdownChoiceMenu(...) // Reading Direction
        
        DropdownChoiceMenu(
            selectedOption = LabeledEntry(displayMode, displayMode.name), // TODO: Add strings
            options = PanelsFullPageDisplayMode.entries.map { LabeledEntry(it, it.name) },
            onOptionChange = { state.onFullPageDisplayModeChange(it.value) },
            label = { Text("Show full page") },
            inputFieldModifier = Modifier.fillMaxWidth(),
            inputFieldColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
```

### Mobile UI
**File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt`
- Update `PanelsModeSettings`:
```kotlin
@Composable
private fun PanelsModeSettings(state: PanelsReaderState) {
    val displayMode = state.fullPageDisplayMode.collectAsState().value
    Column {
        Text("Reading direction")
        // ... existing chips
        
        Text("Show full page")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelsFullPageDisplayMode.entries.forEach { mode ->
                InputChip(
                    selected = displayMode == mode,
                    onClick = { state.onFullPageDisplayModeChange(mode) },
                    label = { Text(mode.name) }
                )
            }
        }
    }
}
```
