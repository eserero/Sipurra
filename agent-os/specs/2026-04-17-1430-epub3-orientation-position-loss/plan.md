# Plan: Fix EPUB3 Orientation Position Loss

## Context

When a user reads in portrait, rotates to landscape, then rotates back to portrait, the reading position jumps to the beginning of the current chapter.

**Root cause:** `EpubView.props.locator` is set once during view creation (`onEpubViewCreated`) and never updated as the user turns pages. The method `emitCurrentLocator()` uses `props?.locator` as a reference to decide whether to suppress false locator-change emissions. Because this reference is stale (points to the initial/server-fetched locator, not the user's current position), the suppression logic fails: on every WebView reflow triggered by orientation change, Readium emits an intermediate `progression=0.0` locator, `isPropLocatorOnPage` is `false` (user has read past the stale reference), and `onLocatorChange(beginning_of_chapter)` fires — corrupting `savedLocator`.

## Critical Files

| File | Path |
|------|------|
| `EpubView.kt` | `epub-reader/src/main/java/com/storyteller/reader/EpubView.kt` |
| `Epub3ReaderState.kt` | `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt` |

## Implementation Tasks

### Task 1: Keep `EpubView.props.locator` in sync after every page turn

**File:** `Epub3ReaderState.kt`

In the `onLocatorChange` callback inside `onEpubViewCreated()` (around L502), after updating `savedLocator`, also update the view's finalized props so `emitCurrentLocator()` uses the accurate current position as its reference:

```kotlin
override fun onLocatorChange(locator: Locator) {
    savedLocator = locator
    // Keep EpubView's props.locator in sync so emitCurrentLocator()
    // uses the current reading position (not the stale initial locator)
    // as its reference when deciding whether to suppress reflow emissions.
    view.props = view.props?.copy(locator = locator)
    currentLocator.value = locator
    // ... rest unchanged
}
```

`EpubView.props` is a `var FinalizedProps?` — assigning via `.copy()` updates the reference locator without triggering any navigation or `finalizeProps()` side effects.

### Task 2: Use the freshest known locator when restoring position in `onEpubViewCreated()`

**File:** `Epub3ReaderState.kt`

In `onEpubViewCreated()` (around L542-543), prefer `currentLocator.value` over `savedLocator` when setting up the view's initial position. `currentLocator` is updated by `onRawLocatorChange` (every Readium emission), so if the view is recreated mid-session it picks up the most recent confirmed position rather than the potentially stale `savedLocator`:

```kotlin
// Before (L542):
view.pendingProps.locator = savedLocator

// After:
view.pendingProps.locator = currentLocator.value ?: savedLocator
```

This is a safety net for the case where `onDetachedFromWindow()` fires during rotation and the factory is called again.

## Verification

1. Open the EPUB3 reader at chapter 2+ (so the server-saved position is not page 1).
2. Read several pages so `savedLocator` is well past the initial server position.
3. Rotate portrait → landscape. Confirm position is maintained (same paragraph visible).
4. Rotate landscape → portrait. Confirm position is maintained (does NOT jump to beginning).
5. Repeat 3-4 multiple times.
6. Enable Logcat filter `[EPUB-DIAG]` and `[komelia-epub]` to verify:
   - `[TEXT-MOVE]` entries show correct progression values (not 0.0) during and after rotation
   - `savedLocator` href+fragment remain stable through rotation events
