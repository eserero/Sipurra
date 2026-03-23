# Plan: Immersive Screen Redesign

## Objective
Redesign the immersive screen into a unified layout (removing the bottom sheet metaphor). 
1. **Starting State**: The screen features a fixed-size Material 3 Elevated Card at the top. This card has a 15px margin from the top, left, and right screen edges. A large cover image completely fills this card (zoomed in, `ContentScale.Crop`, no inner margins). Directly below this card (not inside a card, just on the screen background), the rest of the book/series elements are laid out.
2. **Phase 1 Scroll (Morph)**: As the user swipes up, there are NO crossfading images. Instead, the Big Image Card physically **morphs**—its width, height, corner radius, and elevation continuously interpolate down to match the exact dimensions and position of the small thumbnail within the scrolling content. The text elements shift right seamlessly as they do today.
3. **Phase 2 Scroll**: Once the morph completes and the thumbnail is fully formed at the top, further swiping scrolls the remaining content normally.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailScaffold.kt`: Manages scroll state and will host the single morphing image overlay.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/book/immersive/ImmersiveBookContent.kt`: Provides the un-carded content and the placeholder for the target thumbnail.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/immersive/ImmersiveSeriesContent.kt`: Same as above for series.

## Proposed Layout Architecture
The architecture relies on continuous geometry interpolation driven by `expandFraction`:

1. **Target Measurement**:
   - In `ImmersiveBookContent` and `ImmersiveSeriesContent`, the existing `ThumbnailImage` component will be removed.
   - It will be replaced by a transparent placeholder `Box` of the exact target size (`110.dp` x `thumbnailHeight`).
   - Using `onGloballyPositioned` (or similar relative measurement), the content reports the `(X, Y)` coordinates of this placeholder relative to its parent container back to the Scaffold.

2. **Morphing Image Overlay (`ImmersiveDetailScaffold`)**:
   - A single `ThumbnailImage` is rendered inside an M3 `Card` at the top level of the Scaffold.
   - As `expandFraction` moves from 0 to 1, this Card's modifiers dynamically interpolate:
     - **Width**: `lerp(screenWidth - 30.dp, 110.dp)`
     - **Height**: `lerp(collapsedOffset - 15.dp, thumbnailHeight)`
     - **X Offset**: `lerp(15.dp, targetX)`
     - **Y Offset**: `lerp(15.dp, cardOffsetPx + targetY)` (Tracks the scrolling content)
     - **Corner Radius**: `lerp(16.dp, 8.dp)`
     - **Elevation**: `lerp(6.dp, 0.dp)`

3. **Content Area**:
   - A full-size `Box` holds `cardContent` and is shifted down by `cardOffsetPx`.
   - It is no longer wrapped in a background card or shadow. 
   - Because `cardOffsetPx` starts at `collapsedOffset`, it sits perfectly below the Big Image Card's initial bounding box.

## Implementation Steps
1. **Modify Scaffold Signature**: 
   - Add a mechanism (e.g., a callback `onThumbnailPositioned: (Offset) -> Unit`) to `cardContent` so the inner grid can report the placeholder's location.
2. **Update `ImmersiveDetailScaffold`**:
   - Remove `Layer 1` (background image) and the background/shadow styling from `Layer 2`.
   - Implement the Morphing Image Overlay at the top of the `BoxWithConstraints` using the `lerp` calculations described above.
3. **Update Content Files**:
   - In `ImmersiveBookContent` and `ImmersiveSeriesContent`, remove the `ThumbnailImage` inside the header.
   - Insert a `Spacer` or `Box` placeholder of identical size and attach the position reporting callback.
4. **Cleanup**: Remove unnecessary alpha/fade out logic for the old cover image, as the morphing single-image overlay replaces it entirely.

## Verification & Testing
- Open a Book or Series screen.
- Verify the large cover image is enclosed in an elevated card with 15px margins, completely filling the card.
- Verify the content below is rendered directly on the screen.
- **Phase 1**: Swipe up. Ensure there is only ONE image that smoothly shrinks, changes shape, and drops elevation to become the thumbnail.
- **Phase 2**: Swipe further. Ensure normal grid scrolling occurs with the thumbnail pinned properly.