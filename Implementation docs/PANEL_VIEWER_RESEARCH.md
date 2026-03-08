# Comic Viewer: Panel-by-Panel Navigation Options

This document outlines two strategies for implementing a "Full Page -> Panels -> Full Page" navigation flow in the comic book reader.

## Option 1: State-Based Logic (Explicit Flags)
This approach involves adding explicit state flags to track whether the user is currently viewing the "intro" or "outro" full-page view for any given page.

### Key Changes
- **`PageIndex` Data Class**: Add `isInitialFullPageActive` and `isLastPanelZoomOutActive` (already exists but needs more consistent use).
- **`nextPanel()` / `previousPanel()`**: Add conditional logic to check these flags. 
    - `nextPanel()`: If `isInitialFullPageActive`, move to panel index 0. If at the last panel, move to full-page zoom and set `isLastPanelZoomOutActive`.
- **`doPageLoad()`**: Initialize the state based on whether the user is moving forward (start at full page) or backward (start at "outro" full page).

### Pros & Cons
- **Pros**: Very precise control; easy to add granular settings (e.g., "Only show full page at start").
- **Cons**: More complex logic branches in the navigation methods; requires passing "navigation direction" through several method calls.

---

## Option 2: List-Based Injection (Surgical approach)
This approach involves injecting a "full page" coordinate rectangle into the beginning and end of the panel list returned by the AI.

### Key Changes
- **`doPageLoad()`**: After the AI detects panels and they are sorted, manually insert a rectangle covering `(0, 0, imageWidth, imageHeight)` at index `0` and at the end of the list.
- **`nextPanel()` / `previousPanel()`**: Simplify these methods to just move through the list. Remove the existing "smart" logic that skips the zoom-out based on page coverage.
- **Backward Navigation**: Update `previousPage()` and `onPageChange()` to accept a starting index, so when navigating back from Page 5, Page 4 starts at its last panel (the injected full-page view).

### Pros & Cons
- **Pros**: Simplest implementation; leverages the existing "move to next panel" infrastructure with zero changes to the UI layer.
- **Cons**: Potential for "double views" on splash pages where the AI already detected a full-page panel (requires a simple `if` check before injection).

---

## Technical Locations
- **File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/panels/PanelsReaderState.kt`
- **Detection Logic**: `launchDownload`
- **Sorting & List Prep**: `doPageLoad`
- **Navigation Logic**: `nextPanel` and `previousPanel`
