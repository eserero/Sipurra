# Fix Clipping in Immersive UI2 (Revised)

The goal is to align the UI2 immersive screen elements (card, text, and image height) with UI1, fixing the "cut" top of the image while maintaining the existing gradient behavior.

## Problem Analysis
- In UI2, the `collapsedOffset` (card Y position) is higher than in UI1 by the height of the status bar.
- This causes the morphing cover image to be shorter than in UI1, which leads to `ContentScale.Crop` cropping out the top portion of the image.
- The user wants everything shifted down to match UI1 and for the gradient to be correctly aligned to the new position of the seam between the image and the card.
- Based on user feedback, the current gradient visual style is great and should not be changed, only its position.

## Proposed Changes

### `ImmersiveDetailScaffold.kt`

1.  **Adjust Card Position (Collapsed Offset):**
    - For UI2 (`useMorphingCover` is true), increase `collapsedOffset` by the height of the status bar (`statusBarDp`).
    - `val collapsedOffset = if (useMorphingCover) (stableScreenHeight * 0.65f) + statusBarDp else stableScreenHeight * 0.65f`
    - This moves the card down to match UI1's absolute position.

2.  **Adjust Morphing Cover (Layer 2.75):**
    - `startHeight` will naturally use the new `collapsedOffset`, making the image taller and fixing the "cut" top.
    - `startY` remains `0.dp` to stay flush with the top of the screen.

3.  **Adjust Gradient Alignment:**
    - The gradient is already aligned to the bottom of the morphing cover box (`Alignment.BottomCenter`). Since we've increased the box's height to exactly reach the new card position, the gradient will naturally sit exactly where the image meets the card. No change is needed to the gradient's visual logic (colors/fillHeight), only its container height which is handled by `startHeight`.

4.  **Adjust Hero Text:**
    - `startTextY` will use the new `collapsedOffset`, moving the text down by `statusBarDp` to match the card.

## Verification Plan

### Manual Verification
1.  Enable **New UI** and **New UI 2**.
2.  Navigate to a Series, Book, or Oneshot immersive screen.
3.  Verify that:
    - The card and text sit lower, matching UI1.
    - The top portion of the background image is visible.
    - The transition from the image to the card remains seamless and correctly aligned with the card's top edge.
    - Expanding/collapsing works smoothly.
