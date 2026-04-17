# References for EPUB3 Orientation Position Loss

## Key Files

### EpubView.kt
- **Location:** `epub-reader/src/main/java/com/storyteller/reader/EpubView.kt`
- **Relevance:** Core view wrapping `EpubNavigatorFragment`. Contains `props.locator` (the stale reference), `emitCurrentLocator()` (fires false locator change), `initializeNavigator()`, `onDetachedFromWindow()`.
- **Key patterns:**
  - `props: FinalizedProps?` — mutable var, holds last finalized state including `locator`
  - `emitCurrentLocator()` L311-371 — uses `props?.locator` as reference to suppress false emissions; broken when `props.locator` is stale
  - `onLocatorChanged()` L642-666 — called on every Readium `currentLocator` emission

### Epub3ReaderState.kt
- **Location:** `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt`
- **Relevance:** Holds `savedLocator` (in-memory position), `onEpubViewCreated()`, `onLocatorChange` callback.
- **Key patterns:**
  - `savedLocator: Locator?` L125 — updated by `onLocatorChange`, used to restore position
  - `onEpubViewCreated()` L487 — sets up listener and passes `savedLocator` to view
  - `onLocatorChange` callback L496-525 — updates `savedLocator` + posts to server

### EpubFragment.kt
- **Location:** `epub-reader/src/main/java/com/storyteller/reader/EpubFragment.kt`
- **Relevance:** Wraps `EpubNavigatorFragment`. `onCreate()` uses `lst.props!!.locator` as the initial locator for the Readium factory.

### Epub3ReaderContent.android.kt
- **Location:** `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderContent.android.kt`
- **Relevance:** Compose UI. `AndroidView` factory at L122-131 creates `EpubView` once and calls `onEpubViewCreated()`.
