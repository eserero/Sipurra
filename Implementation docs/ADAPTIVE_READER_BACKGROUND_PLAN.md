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

## Phase 2: Per-Pixel Edge Gradients (Blooming Effect)
This phase improves the background by preserving the color variation along the image edges and fading them into the theme's background.

### 1. Technical Strategy
- **Sampling:** Instead of a single 1x1 average, sample a 1D line of colors.
    - Top/Bottom: Extract 10px strip, resize to `width x 1`.
    - Left/Right: Extract 10px strip, resize to `1 x height`.
- **Rendering:**
    - Draw the 1D sampled line stretched across the gap (creating color bars).
    - Apply a gradient overlay from `Transparent` (image side) to `ThemeBackground` (screen edge side).

## Phase 2.5: Image-Relative Bloom Gradients
This fix ensures the background "bloom" starts exactly at the image edges rather than the center of the screen.

### 1. Technical Strategy
- **Image Bounds:** Retrieve the actual display dimensions and position of the image within the container.
- **Top Bloom:** Start at the `image.top` with `Color.Transparent` (showing full colors) and fade to `MaterialTheme.colorScheme.background` at the screen `top (0)`.
- **Bottom Bloom:** Start at the `image.bottom` with `Color.Transparent` and fade to `MaterialTheme.colorScheme.background` at the screen `bottom (height)`.
- **Logic:** The "colorful" part of the background should "leak" from the image outward to the screen edges.

### 2. Implementation Steps
1. **UI:** Update `AdaptiveBackground` to accept the `imageSize` or `imageBounds`.
2. **Rendering:** Recalculate the `drawRect` and `Brush` coordinates to align with these bounds.

## Phase 3: Four-Side Sampling & Corner Blending (Panel Mode Optimization)
This phase addresses scenarios where gaps exist on all four sides of the image, which is common in Panel Mode.

### 1. Technical Strategy
- **Sampling:** Always sample all four edges (Top, Bottom, Left, Right).
- **Rendering:**
    - Create four independent gradient zones.
    - **Corner Miter:** Use a 45-degree clipping or alpha-blending in the corners so that adjacent edge colors (e.g., Top and Left) meet seamlessly.
- **Panel Padding:** Ensure the background fills the additional "safety margin" or padding added around panels, providing a consistent immersive feel even when the panel is much smaller than the screen.

### 2. Implementation Steps
1. **Infra:** Update sampling to return all four edge lines.
2. **UI:** Update `AdaptiveBackground` to render four zones with corner blending logic.
3. **Panel Mode:** Verify integration with panel zooming and padding logic.

### 2. Implementation Steps
1. **Infra:** Add `getEdgeColorLines()` to `KomeliaImage` / `ReaderImageUtils`.
2. **UI:** Create a version of `AdaptiveBackground` that handles `ImageBitmap` buffers and applies the "bloom" fade.
3. **Switching:** Maintain both Phase 1 and Phase 2 logic for easy comparison/toggling during development.
