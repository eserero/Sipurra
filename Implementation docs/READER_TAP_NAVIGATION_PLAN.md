# Implementation Plan: Reader Tap Navigation Modes

## Objective
Add customizable tap-zone navigation modes to the image reader. This allows users to reconfigure how tapping the left and right sides of the screen triggers "Next" and "Previous" actions, including horizontal splits for one-handed reading.

## 1. Domain & Persistence Layer

### New Enum
Create `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/ReaderTapNavigationMode.kt`:
```kotlin
enum class ReaderTapNavigationMode {
    LEFT_RIGHT,           // Default: Left=Prev, Right=Next
    RIGHT_LEFT,           // Reversed: Left=Next, Right=Prev
    HORIZONTAL_SPLIT,     // Sides Split: Top=Prev, Bottom=Next
    REVERSED_HORIZONTAL_SPLIT // Sides Split: Top=Next, Bottom=Prev
}
```

### Database & Settings Update
1.  **Table**: Update `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/ImageReaderSettingsTable.kt`.
    *   Add: `val tapNavigationMode = text("tap_navigation_mode").default("LEFT_RIGHT")`
2.  **Data Class**: Update `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/ImageReaderSettings.kt`.
    *   Add `val tapNavigationMode: ReaderTapNavigationMode = ReaderTapNavigationMode.LEFT_RIGHT`
3.  **Repository Interface**: Update `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/ImageReaderSettingsRepository.kt`.
    *   Add `getReaderTapNavigationMode(): Flow<ReaderTapNavigationMode>`
    *   Add `putReaderTapNavigationMode(mode: ReaderTapNavigationMode)`
4.  **Implementation**: Update `ExposedImageReaderSettingsRepository.kt` and `ReaderSettingsRepositoryWrapper.kt` to handle the new field.

## 2. Navigation Logic Update

Modify `ReaderControlsOverlay` in `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`.

### Logic Requirements:
The screen is divided into 3 horizontal columns: **Left (0-33%)**, **Center (33-66%)**, and **Right (66-100%)**.
*   **Center Column**: Always toggles settings menu.
*   **Side Columns (Left & Right)**: Behavior depends on `ReaderTapNavigationMode` and `LayoutDirection` (LTR/RTL).

**Mapping logic for Side Columns (where `isLeft = offset.x < width/3` and `isRight = offset.x > width*2/3`):**

1.  **LEFT_RIGHT**: 
    *   If LTR: Left = Prev, Right = Next.
    *   If RTL: Left = Next, Right = Prev.
2.  **RIGHT_LEFT**: 
    *   If LTR: Left = Next, Right = Prev.
    *   If RTL: Left = Prev, Right = Next.
3.  **HORIZONTAL_SPLIT**:
    *   If Top Half (`offset.y < height/2`): Prev.
    *   If Bottom Half (`offset.y >= height/2`): Next.
4.  **REVERSED_HORIZONTAL_SPLIT**:
    *   If Top Half: Next.
    *   If Bottom Half: Prev.

## 3. UI Implementation (Settings)

### New Tab
In `BottomSheetSettingsOverlay.kt` (Mobile) and `SettingsSideMenu.kt` (Desktop), add a **Navigation** tab/section.

### Navigation Tab Content:
*   Use a `Column` with `RadioButton` options for the 4 modes.
*   Each option must include a **Diagram**.

### Diagram Component (`TapNavigationDiagram`):
Create a Composable using `Canvas` to draw a simplified representation of the tap zones:
*   **Shape**: A small rounded rectangle representing the screen.
*   **Zones**: Draw dotted lines separating the 1/3 columns and, for split modes, a horizontal line in the side columns.
*   **Indicators**: Use simple text (e.g., "P" and "N") or arrows inside the zones to show what they do.
*   **Colors**: Use `MaterialTheme.colorScheme.outline` for borders/lines and `onSurface` for text.

## 4. Localization
Update `AppStrings.kt` and `EnStrings.kt` (and other languages if possible) to include:
*   `tapNavigation`: "Tap Navigation"
*   `modeLeftRight`: "Default"
*   `modeRightLeft`: "Reversed"
*   `modeHorizontalSplit`: "Top/Bottom Split"
*   `modeReversedHorizontalSplit`: "Reversed Split"

## 5. Migrations
Exposed handles SQLite migrations automatically when a column with a `.default()` value is added to the `Table` object. No manual SQL scripts are required.
