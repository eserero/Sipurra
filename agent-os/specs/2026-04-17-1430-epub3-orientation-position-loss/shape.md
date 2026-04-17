# EPUB3 Orientation Position Loss — Shaping Notes

## Scope

Bug fix: rotating portrait → landscape → portrait in the EPUB3 native reader resets the reading position to the beginning of the current chapter.

## Root Cause (Identified via Code Analysis)

**The problem is a stale `props.locator` reference in `EpubView`.**

### Full trace

1. `onEpubViewCreated()` sets `view.pendingProps.locator = savedLocator` and calls `finalizeProps()`.  
   After this, `EpubView.props.locator` = the server-fetched initial position.

2. The user turns pages. `onLocatorChange()` fires each time, updating `savedLocator` in `Epub3ReaderState`.  
   **But `EpubView.props.locator` is NEVER updated after step 1.** It always points to the initial (server-fetched) locator.

3. Orientation changes. The WebView inside `EpubNavigatorFragment` gets resized.  
   Readium reflows the paginated content and emits new `currentLocator` values via its StateFlow.

4. Each emission triggers `onLocatorChanged()` → `emitCurrentLocator()`.

5. `emitCurrentLocator()` evaluates whether `props?.locator` (the stale initial locator) is still on screen.  
   Since the user has read past it, `isPropLocatorOnPage = false`.

6. So `listener?.onLocatorChange(merged)` fires — with whatever `navigator.currentLocator.value` is during the reflow.  
   During a WebView reflow, Readium often emits `progression=0.0` (beginning of chapter) as an intermediate state before settling.

7. `onLocatorChange(beginning_of_chapter)` → `savedLocator = beginning_of_chapter`

8. On the **second** rotation (landscape → portrait), either:
   - The view is recreated (if `onDetachedFromWindow()` fires) and `onEpubViewCreated()` restores from `savedLocator = beginning`
   - Or `go(savedLocator)` is called with the beginning locator during `finalizeProps()`

The user sees the beginning of the chapter.

## Decisions

- Fix is targeted at the **stale `props.locator`** — keep `EpubView.props.locator` in sync after every confirmed page turn.
- Do **not** debounce or suppress `onLocatorChange` globally — that would break server-side progress tracking.
- Use `currentLocator.value ?: savedLocator` in `onEpubViewCreated()` so a view re-creation during or after reflow picks up the freshest known position.
- No changes to Readium's internals or the Fragment lifecycle.

## Context

- **Visuals:** None
- **References:** `EpubView.kt`, `Epub3ReaderState.kt`, `EpubFragment.kt`, `Epub3ReaderContent.android.kt`
- **Product alignment:** Core reading experience; position loss is a P1 regression

## Standards Applied

- compose-ui/view-models — StateScreenModel persistence pattern
