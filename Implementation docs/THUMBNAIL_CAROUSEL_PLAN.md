# Thumbnail Carousel Implementation Plan

## Objective
Implement an interactive, high-performance Thumbnail Carousel (filmstrip) in the image reader. This carousel will appear when the user taps the page label above the progress slider, replacing the standard control panel. It will reuse the highly performant image loading mechanics of the `ContinuousReaderContent` (using Compose `LazyRow` and Coil) to allow fast, debounce-free swiping through thumbnails for both local and remote files without crashing the server.

## Scope & Impact
- Modifies the reader controls UI to support a toggleable carousel view.
- Leverages existing Coil and Compose Lazy list behaviors to naturally cancel off-screen requests.
- Eliminates the need for artificial debouncing, providing a true "YACReader-like" instant visual feedback feel using existing local/remote fetching mechanisms.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ProgressSlider.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/SettingsContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt`

## Proposed Solution & Implementation Steps

### 1. Create the `ThumbnailCarousel` Composable
- Build a new Composable `ThumbnailCarousel` in `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ThumbnailCarousel.kt`.
- **Kinetic Motion**: Implement a `LazyRow` using a custom `FlingBehavior` with `exponentialDecay(frictionMultiplier = 0.4f)`. This matches the exact friction and "kinetic feel" of the continuous reader's custom scrolling logic.
- **Spacing**: Use `horizontalArrangement = Arrangement.spacedBy(8.dp)` and `contentPadding = PaddingValues(horizontal = 16.dp)` to ensure thumbnails have breathing room and don't clip against the edges.
- **Rendering**: Render each item using `BookPageThumbnail` (adapted for carousel dimensions, e.g., 150dp-200dp height).
- **Initial State**: Ensure the `LazyRow` starts centered on the `currentPageIndex`.

### 2. State Management for Carousel Visibility
- Add a UI state variable (e.g., `showCarousel: Boolean`) within the reader settings scope (either hoisted in `ReaderState` or locally in the control overlays like `SettingsContent`).

### 3. Make the Page Label Clickable
- In `ProgressSlider.kt`, locate the `Text(label, ...)` component (for standard UI).
- In `BottomSheetSettingsOverlay.kt`, locate the static "Page X of Y" text (for New UI 2).
- Apply a `.clickable { ... }` modifier to toggle the `showCarousel` state.

### 4. Smooth UI Transition Logic
- Wrap the standard control group (slider, settings buttons) and the `ThumbnailCarousel` in an `AnimatedContent`.
- **Transition Spec**:
    - **Enter**: `slideInVertically(initialOffsetY = { it }) + fadeIn()`
    - **Exit**: `slideOutVertically(targetOffsetY = { it }) + fadeOut()`
- This creates a smooth "slide up" effect where the carousel emerges from the bottom of the screen, replacing the slider.

### 5. Interaction & Synchronization
- **Scrolling**: Swiping left/right smoothly scrolls the `LazyRow`. Coil will automatically handle fetching visible thumbnails and cancelling requests for those that scroll off-screen.
- **Tapping**: Applying a click listener to the thumbnails so that tapping one triggers the reader's `onPageChange` callback to jump to that page, and subsequently closes the carousel (reverting `showCarousel` to false).

## Verification & Testing
- Open a remote Komga book and ensure the carousel opens instantly upon tapping the page label.
- Swipe rapidly left and right to verify the UI does not stutter, placeholders appear correctly, and network requests are safely cancelled/queued without overwhelming the Komga server.
- Test with a local archive (ZIP/CBZ) to verify memory and I/O remain stable during fast scrolling.
- Confirm that tapping a thumbnail navigates exactly to the selected page.
