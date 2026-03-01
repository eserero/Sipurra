# Adaptive Reader Background (Edge-Sampled Gradients) Implementation Plan

This plan outlines the implementation of an "Adaptive Background" feature for the comic reader in Komelia. This feature improves visual immersion by replacing solid letterbox/pillarbox bars with a two-color gradient sampled from the current page's edges.

## 1. Feature Overview
When a page does not perfectly fill the screen (due to "Fit to Screen" settings), the empty space (letterbox or pillarbox) will be filled with a gradient.
- **Top/Bottom gaps (Letterbox):** Vertical gradient from Top Edge Color to Bottom Edge Color.
- **Left/Right gaps (Pillarbox):** Horizontal gradient from Left Edge Color to Right Edge Color.
- **Panel Mode:** Uses the edge colors of the *full page* even when zoomed into a panel.
- **Configurability:** Independent toggles for Paged Mode and Panel Mode in Reader Settings.

## 2. Technical Strategy

### A. Color Sampling (Domain/Infra)
We need an efficient way to extract the average color of image edges using the `KomeliaImage` (libvips) abstraction.

1.  **Utility Function:** Create `getEdgeColors(image: KomeliaImage): Pair<Color, Color>` (or similar) in `komelia-infra/image-decoder`.
2.  **Implementation:**
    - To get Top/Bottom colors:
        - Extract a small horizontal strip from the top (e.g., full width, 10px height).
        - Shrink the strip to 1x1.
        - Repeat for the bottom.
    - To get Left/Right colors:
        - Extract a vertical strip from the left (e.g., 10px width, full height).
        - Shrink to 1x1.
        - Repeat for the right.
3.  **Efficiency:** Libvips is optimized for these operations; it will avoid full decompression where possible and perform the resize/averaging in a streaming fashion.

### B. State Management
1.  **Settings:**
    - Add `pagedReaderAdaptiveBackground` and `panelReaderAdaptiveBackground` to `ImageReaderSettingsRepository`.
    - Update `PagedReaderState` and `PanelsReaderState` to collect these settings.
2.  **Page State:**
    - Add `edgeColors: Pair<Color, Color>?` to the `Page` data class in `PagedReaderState`.
    - When a page is loaded, trigger the background sampling task asynchronously.
    - `PanelsReaderState` will track the edge colors of the *current page* it is showing panels for.

### C. UI Implementation (Compose)
1.  **AdaptiveBackground Composable:**
    - Location: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/`
    - Parameters: `topColor: Color`, `bottomColor: Color`, `orientation: Orientation`.
    - Use `animateColorAsState` for smooth transitions between pages.
    - Use `Brush.linearGradient` to draw the background.
2.  **Integration:**
    - Wrap `ReaderImageContent` in `PagedReaderContent` and `PanelsReaderContent` with the new `AdaptiveBackground` component.
    - Pass the colors based on whether the feature is enabled in the current mode's settings.

### D. Settings UI
1.  **Reader Settings:**
    - Add two new toggles in `SettingsContent.kt` (used by `BottomSheetSettingsOverlay` and `SettingsSideMenu`).
    - Labels: "Adaptive Background (Paged)" and "Adaptive Background (Panels)".
    - Position them near "Double tap to zoom" for consistency.

## 3. Implementation Steps

1.  **Infra:** Implement the color sampling logic in `komelia-infra/image-decoder`.
2.  **Domain:** Update `ImageReaderSettingsRepository` interface and its implementation (e.g., `AndroidImageReaderSettingsRepository`).
3.  **State:**
    - Update `PagedReaderState` to perform color sampling when images are loaded.
    - Update `PanelsReaderState` to share this logic for its current page.
4.  **UI:**
    - Create the `AdaptiveBackground` composable.
    - Update the settings screens to include the toggles.
    - Connect the state to the UI to render the gradients.

## 4. Edge Cases & Considerations
- **Transparent Images:** Sampled colors should consider the background (likely white) if the image has transparency.
- **Very Thin Margins:** If the "Fit to Screen" fills the entire screen, the background won't be visible (current behavior preserved).
- **Performance:** Ensure sampling happens on a background thread and doesn't block the UI or delay page rendering.
- **Color Consistency:** Sampled colors can be slightly desaturated or darkened if they are too bright and distracting.
