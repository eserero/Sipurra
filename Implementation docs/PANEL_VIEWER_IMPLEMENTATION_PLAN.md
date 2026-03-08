# Implementation Plan: Panel-by-Panel "Full Page" Sequence

## Goal
Implement a consistent "Full Page -> Panels -> Full Page" sequence for the Panel Reader.
- When entering a new page, show the full page first (Optional).
- Navigate through all detected panels.
- After the last panel, show the full page again (Optional).
- Next click moves to the next physical page.

---

## 1. List-Based Injection (`PanelsReaderState.kt`)
**Change**: Inject a "Full Page" rectangle at the beginning and/or end of the detected panel list based on settings.

- **New State Properties** (Phase 2 Settings placeholder):
    - `showFullPageFirst: Boolean` (Default: `true`)
    - `showFullPageLast: Boolean` (Default: `true`)
- **Location**: `doPageLoad()` method.
- **Logic**:
    ```kotlin
    val fullPageRect = ImageRect(0, 0, imageSize.width, imageSize.height)
    val panels = mutableListOf<ImageRect>()
    
    if (showFullPageFirst) panels.add(fullPageRect)
    panels.addAll(sortedPanels)
    if (showFullPageLast) panels.add(fullPageRect)
    ```
- **Optimization**: If a detected panel is already >95% of the page size, skip injection for that specific slot to avoid "double-viewing" splash pages.

## 2. Simplify Navigation Logic
**Change**: Remove the "Smart" zoom-out logic from `nextPanel()` and `previousPanel()`.

- **`nextPanel()`**:
    - If `panelIndex + 1 < panels.size`, move to `panelIndex + 1`.
    - Else, call `nextPage()`.
- **`previousPanel()`**:
    - If `panelIndex - 1 >= 0`, move to `panelIndex - 1`.
    - Else, call `previousPage()`.

## 3. Directional Page Changes
**Change**: Ensure that moving backwards starts the user at the *end* of the previous page.

- **`nextPage()`**: Calls `onPageChange(currentPageIndex + 1)` (starts at index 0).
- **`previousPage()`**: Calls `onPageChange(currentPageIndex - 1, startAtLast = true)`.
- **`launchPageLoad`**: Update signature to `launchPageLoad(pageIndex: Int, startAtLast: Boolean = false)`.

---

## 4. Technical Steps
1. **Refactor `PageIndex`**: Remove the `isLastPanelZoomOutActive` flag.
2. **Update `launchPageLoad`**: Accept a `startAtLast: Boolean` flag.
3. **Modify `doPageLoad`**:
    - Perform detection.
    - Sort panels.
    - Apply Injection logic (First/Last).
    - Set `currentPageIndex` to `0` or `panels.lastIndex` based on `startAtLast`.
4. **Update `previousPage()`**: Call `onPageChange(index - 1, startAtLast = true)`.
