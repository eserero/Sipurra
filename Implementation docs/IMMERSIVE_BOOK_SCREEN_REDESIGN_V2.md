# Plan: Immersive Screen Redesign (Refined)

## Objective
Redesign the immersive screen into a unified layout (removing the bottom sheet metaphor) with a seamless morphing animation.
1. **Starting State**: The screen features a large cover image at the top. It is completely **flush** with the top, left, and right screen edges (0px margins, 0 elevation). It completely fills the top of the screen, extending behind the Android top bar. The bottom corners are rounded. Directly below this image, the rest of the book/series elements are laid out. The entire screen background uses the immersive color derived from the cover.
2. **Phase 1 Scroll (Morph)**: As the user swipes up, there are NO crossfading images. Instead, the Big Image physically **morphs**—its width, height, corner radii, and elevation continuously interpolate down to match the exact dimensions and position of the small thumbnail within the scrolling content. The text elements shift right seamlessly as they do today.
3. **Phase 2 Scroll**: Once the morph completes (`expandFraction == 1.0`), the morphing overlay is hidden, and the actual thumbnail within the scrolling grid is revealed. This ensures the small image naturally scrolls up with the rest of the content rather than floating statically. The fully expanded small image should have a distinct elevation (e.g., 6.dp).

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailScaffold.kt`: Manages scroll state, background color, and will host the single morphing image overlay.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/book/immersive/ImmersiveBookContent.kt`: Provides the content and the target thumbnail.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/immersive/ImmersiveSeriesContent.kt`: Same as above for series.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/oneshot/immersive/ImmersiveOneshotContent.kt`: Same as above for oneshots.

## Proposed Layout Architecture
The architecture relies on continuous geometry interpolation driven by `expandFraction`:

1. **Target Measurement**:
   - In the content files (`ImmersiveBookContent`, etc.), a placeholder `Box` (or the actual `ThumbnailImage` when expanded) of the exact target size (`110.dp` x `thumbnailHeight`) is rendered.
   - Using `onGloballyPositioned`, the content reports the `(X, Y)` coordinates of this placeholder relative to its parent container back to the Scaffold.

2. **Morphing Image Overlay (`ImmersiveDetailScaffold`)**:
   - A single `ThumbnailImage` is rendered inside an M3 `Card` at the top level of the Scaffold.
   - It is only visible when `expandFraction < 1f`.
   - As `expandFraction` moves from 0 to 1, this Card's modifiers dynamically interpolate:
     - **Width**: `lerp(screenWidth, 110.dp)` (starts flush)
     - **Height**: `lerp(collapsedOffset, thumbnailHeight)`
     - **X Offset**: `lerp(0.dp, targetX)`
     - **Y Offset**: `lerp(0.dp, cardOffsetPx + targetY)` (Tracks the scrolling content)
     - **Corner Radius**: `lerp(0.dp, 8.dp)` (top corners), `lerp(28.dp, 8.dp)` (bottom corners)
     - **Elevation**: `lerp(0.dp, 6.dp)` (starts flush, gains shadow as it shrinks)

3. **Content Area**:
   - A full-size `Box` holds `cardContent` and is shifted down by `cardOffsetPx`.
   - The root container of `ImmersiveDetailScaffold` will have its background set to `backgroundColor` (derived from the dominant color), ensuring the immersive effect covers the whole screen.
   - Inside the grid, the actual `ThumbnailImage` is rendered but its alpha is set to `1f` only when `expandFraction == 1f` (or > 0.99f). Otherwise, it is hidden/transparent to let the overlay show.

## Implementation Steps
1. **Update `ImmersiveDetailScaffold`**:
   - Apply `backgroundColor` to the outermost `Box` instead of just the inner `Column`.
   - Adjust the starting values for the morphing `Card` overlay: 0 padding, 0 starting elevation, screen width.
   - Adjust corner radii: top corners start at 0 and morph to 8.dp; bottom corners start at 28.dp and morph to 8.dp.
   - Add condition to hide the morphing overlay when `expandFraction >= 0.99f`.
2. **Update Content Files (`ImmersiveBookContent`, `ImmersiveSeriesContent`, `ImmersiveOneshotContent`)**:
   - Change the header placeholder back to the actual `ThumbnailImage` with the standard shadow/clip.
   - Make the actual `ThumbnailImage` in the grid visible *only* when `expandFraction >= 0.99f`. This ensures it takes over seamlessly from the overlay and scrolls correctly.
   - Keep the `onGloballyPositioned` logic on this element to report its position for the overlay.
3. **Status Bar Handling**: Ensure the initial image size and offset correctly account for `LocalRawStatusBarHeight.current` so it draws completely under the status bar.

## Verification & Testing
- Open a Book, Series, or Oneshot screen.
- Verify the large cover image is completely flush with the top, left, and right (no margins).
- Verify the background color matches the cover's immersive theme.
- **Phase 1**: Swipe up. Ensure the image smoothly shrinks and gains elevation as it morphs into the thumbnail slot.
- **Phase 2**: Swipe fully up (`expandFraction == 1.0`). Ensure the small image has a shadow/elevation.
- **Phase 3**: Scroll down the grid. Verify the small image scrolls naturally with the text, instead of remaining stuck on screen.
