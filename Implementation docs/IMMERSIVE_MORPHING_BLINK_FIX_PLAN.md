# Plan: Fix Immersive Screen Blinking Issues

## Objective
Address multiple sources of visual blinking and flickering on immersive screens (Series, Book, Oneshot) in both morphing and non-morphing cover modes.

## Root Causes
1. **Hero Text Alpha/Position Mismatch (Morphing):** `heroTextHeight` is measured after the first frame, causing a position jump. Previous fix hid it but it still blinks "in place" because of hand-over between overlay and card text.
2. **Overlay vs. Card Text Styles:** The Overlay text uses the actual `expandFraction` (starts at 0, has shadow), while the Card text is rendered with `expandFraction = 1f` (no shadow, smaller font). If the Card text is briefly visible during the first frame (due to lambda evaluation delay or `useMorphingCover` state propagation), it flickers "in place".
3. **Double Color Jump (Non-Morphing):** In non-morphing mode, the background starts as the Material Surface (Black), then jumps to `surfaceVariant` (Gray) because `cardColor` is initially null, and finally jumps to the dominant color (Colored) once extracted. This causes 2 distinct "blinks".
4. **Auto-Size Multiplier (Both):** `ImmersiveHeroText` uses `onTextLayout` to reduce font size if it overflows. This happens over several frames, causing the text to "shrink-blink".

## Step-by-Step Implementation

### 1. Animate Background Color in `ImmersiveDetailScaffold.kt`
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailScaffold.kt`
- **Changes:**
  - Wrap the `backgroundColor` calculation in `animateColorAsState` with a short duration (e.g., 300ms) to smooth out the transition from `surfaceVariant` to the dominant color.
  - This fixes the "double blink" of the card background.

### 2. Stabilize Card Text Alpha in Immersive Content Files
- **Files:**
  - `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/immersive/ImmersiveSeriesContent.kt`
  - `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/oneshot/immersive/ImmersiveOneshotContent.kt`
  - `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/book/immersive/ImmersiveBookContent.kt`
- **Changes:**
  - Change the hero text `Box`'s `graphicsLayer` from a lambda to a direct parameter: `.graphicsLayer(alpha = if (useMorphingCover && expandFraction <= 0.99f) 0f else 1f)`.
  - This ensures the Card text (which has no shadow and is "expanded" style) is HIDDEN at composition time on the first frame, preventing it from flickering over the Overlay text.

### 3. Refine Overlay Text Visibility in `ImmersiveDetailScaffold.kt`
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailScaffold.kt`
- **Changes:**
  - Update the overlay hero text alpha to also wait for `targetTextOffset` to be measured (non-zero).
  - `.graphicsLayer(alpha = if (heroTextHeight > 0.dp && (!useMorphingCover || targetTextOffset != Offset.Zero)) 1f else 0f)`
  - This ensures the floating text only appears when both its height and its destination are known, further preventing jumps.

## Verification
- Test morphing cover mode: Verify the hero text appears smoothly without blinking "below" or "in place".
- Test non-morphing cover mode: Verify the Card background color transition is smooth (no "double blink") and the text doesn't flicker.
- Verify that the transitions (expanding/collapsing) remain smooth.