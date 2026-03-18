# Storyteller EPUB Reader — Standalone Android Library

This directory contains a self-contained Android library module extracted from the Storyteller
mobile app. It provides a full-featured EPUB 3 reader with synchronized audio (Media Overlays /
SMIL), built on the **Readium Kotlin Toolkit v3.1.2** and **ExoPlayer (Media3)**.

---

## Table of Contents

1. [What This Library Is](#1-what-this-library-is)
2. [Technology Stack](#2-technology-stack)
3. [File Inventory](#3-file-inventory)
4. [What Was Adapted From the Original Source](#4-what-was-adapted-from-the-original-source)
5. [Required Font Assets](#5-required-font-assets)
6. [Integrating Into Another Android App](#6-integrating-into-another-android-app)
   - [Option A — Android Library Module (recommended)](#option-a--android-library-module-recommended)
   - [Option B — Copy files directly into your app module](#option-b--copy-files-directly-into-your-app-module)
7. [Public API Reference](#7-public-api-reference)
   - [BookService](#bookservice)
   - [EpubView](#epubview)
   - [EpubViewListener](#epubviewlistener)
   - [AudiobookPlayer](#audiobookplayer)
   - [PlaybackService](#playbackservice)
   - [Data classes](#data-classes)
8. [What an EPUB Must Look Like for Synced Audio](#8-what-an-epub-must-look-like-for-synced-audio)
9. [Step-by-Step Usage Guide](#9-step-by-step-usage-guide)
10. [Full Example Activity](#10-full-example-activity)
11. [Verification Checklist](#11-verification-checklist)
12. [Known Limitations / TODOs](#12-known-limitations--todos)
13. [Configuration & Actions Reference](#13-configuration--actions-reference)

---

## 1. What This Library Is

Storyteller is a platform for reading ebooks and listening to their audiobooks simultaneously.
The EPUB reader in this library:

- Renders EPUB 3 content in a Readium `EpubNavigatorFragment` (a WebView-backed pager).
- Parses EPUB 3 **Media Overlays** (SMIL files) to extract per-sentence audio↔text mappings.
- Highlights the sentence currently being spoken using Readium **Decorations**.
- Plays audio through an **ExoPlayer MediaLibraryService** foreground service, which keeps
  audio alive in the background and integrates with the Android lock-screen/notification player.
- Fires clip-change events from the service back to the player via `broadcastCustomCommand`, so
  the UI can scroll the reader and update the highlight in real time.
- Supports user highlights, bookmarks, custom fonts, and full typography preferences.

---

## 2. Technology Stack

| Dependency | Version | Role |
|---|---|---|
| `readium-shared` | 3.1.2 | Publication model, Locator, manifests |
| `readium-streamer` | 3.1.2 | EPUB parsing, OPF/container handling |
| `readium-navigator` | 3.1.2 | `EpubNavigatorFragment`, Decorations, Preferences |
| `media3-exoplayer` | 1.9.0 | Audio playback |
| `media3-session` | 1.9.0 | `MediaLibraryService`, lock-screen integration |
| `media3-common` | 1.9.0 | Shared Media3 types |
| `kotlinx-coroutines-android` | 1.7.3 | Coroutines |
| `kotlinx-coroutines-guava` | 1.10.2 | `await()` on `ListenableFuture` |
| `kotlinx-serialization-json` | 1.6.3 | JSON decoding for JS↔Kotlin bridge |
| `fragment-ktx` | 1.8.5 | `commitNow`, `lifecycleScope` |
| `core-ktx` | 1.13.1 | `toColorInt`, etc. |
| `desugar_jdk_libs` | 2.0.3 | `java.time` / `kotlin.time` on API < 26 |

Minimum SDK: **24**. Compile SDK: **35**.

---

## 3. File Inventory

```
epub-reader/
├── build.gradle
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/storyteller/reader/
│   │   ├── BookService.kt           — Singleton: opens publications, parses SMIL, caches clips
│   │   ├── SmilParser.kt            — Parses EPUB 3 SMIL XML → List<MediaOverlayNode>
│   │   ├── STMediaOverlays.kt       — Holds all clips for one SMIL file; OverlayPar data class
│   │   ├── STMediaOverlayNode.kt    — Individual SMIL <par> node (Clip, MediaOverlayNode)
│   │   ├── DirectoryContainer.kt   — Readium Container backed by an extracted EPUB directory
│   │   ├── PackageDocument.kt       — Parses OPF package document (spine, manifest, overlays)
│   │   ├── MetadataParser.kt        — Parses OPF <metadata>
│   │   ├── ClockValueParser.kt      — Parses SMIL clock values ("00:01:23.456" → seconds)
│   │   ├── Constants.kt             — XML namespace + vocabulary constants
│   │   ├── PropertyDataType.kt      — OPF property/prefix resolution helpers
│   │   ├── EpubView.kt              — FrameLayout hosting EpubNavigatorFragment (ADAPTED)
│   │   ├── EpubFragment.kt          — Fragment that creates EpubNavigatorFragment (ADAPTED)
│   │   ├── AudiobookPlayer.kt       — ExoPlayer controller + PlaybackService (ADAPTED)
│   │   └── ReadingActivity.kt       — Example Activity showing how to wire everything together
│   └── res/
│       ├── layout/activity_reading.xml
│       ├── layout/fragment_reader.xml
│       └── values/strings.xml
```

---

## 4. What Was Adapted From the Original Source

The original source lives in `mobile/modules/readium/android/src/main/java/expo/modules/readium/`
and was written as an **Expo module** (React Native native bridge). Three files had Expo-specific
code that needed to be replaced.

### 4.1 `EpubView.kt` — base class + activity + events

**Original:**
```kotlin
class EpubView(context: Context, appContext: AppContext)
    : ExpoView(context, appContext),
      EpubNavigatorFragment.Listener, DecorableNavigator.Listener {

    override val shouldUseAndroidLayout = true   // Expo-only flag

    val onLocatorChange   by EventDispatcher()   // fires to JavaScript
    val onMiddleTouch     by EventDispatcher()
    val onBookmarksActivate by EventDispatcher()
    val onDoubleTouch     by EventDispatcher()
    val onSelection       by EventDispatcher()
    val onHighlightTap    by EventDispatcher()

    // every call to the activity:
    val activity = appContext.currentActivity as FragmentActivity?
```

**After adaptation:**
```kotlin
// New listener interface (replaces all 6 EventDispatchers)
interface EpubViewListener {
    fun onLocatorChange(locator: Locator) {}
    fun onMiddleTouch() {}
    fun onSelection(locator: Locator, x: Int, y: Int) {}
    fun onSelectionCleared() {}
    fun onDoubleTouch(locator: Locator) {}
    fun onHighlightTap(decorationId: String, x: Int, y: Int) {}
    fun onBookmarksActivate(activeBookmarks: List<Locator>) {}
}

class EpubView(
    context: Context,
    val activity: FragmentActivity,          // injected directly
    var listener: EpubViewListener? = null   // replaces EventDispatchers
) : FrameLayout(context),
    EpubNavigatorFragment.Listener, DecorableNavigator.Listener {

    // shouldUseAndroidLayout removed — not needed outside Expo
```

All 6 `EventDispatcher` call sites were updated:
- `onLocatorChange(locator.toJSON().toMap())` → `listener?.onLocatorChange(locator)`
- `onMiddleTouch(mapOf())` → `listener?.onMiddleTouch()`
- `onBookmarksActivate(mapOf("activeBookmarks" to found.map{...}))` → `listener?.onBookmarksActivate(found)`
- `onDoubleTouch(locator.toJSON().toMap())` → `listener?.onDoubleTouch(locator)`
- `onSelection(mapOf("cleared" to true))` → `listener?.onSelectionCleared()`
- `onHighlightTap(mapOf("decoration" to id, "x" to x, "y" to y))` → `listener?.onHighlightTap(id, x, y)`

All `appContext.currentActivity as FragmentActivity?` references were replaced with `activity`.

### 4.2 `EpubFragment.kt` — selection action mode

**Original:**
```kotlin
class SelectionActionModeCallback(private val epubView: EpubView) : BaseActionModeCallback() {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        val activity: FragmentActivity? = epubView.appContext.currentActivity as FragmentActivity?
        activity?.lifecycleScope?.launch {
            // ...
            epubView.onSelection(mapOf("locator" to ..., "x" to x, "y" to y))
        }
```

**After adaptation:**
```kotlin
        epubView.activity.lifecycleScope.launch {
            // ...
            epubView.listener?.onSelection(selection.locator, x, y)
        }
```

### 4.3 `AudiobookPlayer.kt` — AppContext → Context + CoroutineScope

**Original:**
```kotlin
class AudiobookPlayer(
    val appContext: AppContext,
    val listener: Listener
) {
    suspend fun loadTracks(tracks: List<Track>) {
        val context = appContext.reactContext ?: throw Exceptions.ReactContextLost()
        // ...
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            audioProgressCollector = appContext.backgroundCoroutineScope.launch { ... }
        }
    }
}
```

**After adaptation:**
```kotlin
class AudiobookPlayer(
    val context: Context,           // plain Android Context — no Expo runtime needed
    val coroutineScope: CoroutineScope,  // caller-supplied scope (e.g. lifecycleScope)
    val listener: Listener
) {
    suspend fun loadTracks(tracks: List<Track>) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // context is used directly — no reactContext unwrapping
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            audioProgressCollector = coroutineScope.launch { ... }
        }
    }
}
```

`ReadiumModule.kt` (the React Native bridge entry point) was dropped entirely — it has no role
outside of a React Native project.

---

## 5. Required Font Assets

`EpubFragment` serves fonts to the Readium WebView from the Android `assets/` directory.
The files must be placed at these exact paths inside the `epub-reader` module:

```
epub-reader/src/main/assets/fonts/
├── OpenDyslexic-Regular.otf       ← from this repo
├── OpenDyslexic-Bold.otf          ← from this repo
├── OpenDyslexic-Bold-Italic.otf   ← from this repo
├── OpenDyslexic-Italic.otf        ← from this repo
└── Literata_500Medium.ttf         ← download from Google Fonts (see below)
```

### OpenDyslexic (in this repo)

The `.otf` files are already in the Storyteller repo at `mobile/assets/fonts/`. Copy them:

```bash
mkdir -p epub-reader/src/main/assets/fonts
cp mobile/assets/fonts/OpenDyslexic-Regular.otf     epub-reader/src/main/assets/fonts/
cp mobile/assets/fonts/OpenDyslexic-Bold.otf         epub-reader/src/main/assets/fonts/
cp mobile/assets/fonts/OpenDyslexic-Bold-Italic.otf  epub-reader/src/main/assets/fonts/
cp mobile/assets/fonts/OpenDyslexic-Italic.otf       epub-reader/src/main/assets/fonts/
```

### Literata (download required)

Literata is not included in this repo. Download `Literata_500Medium.ttf` from Google Fonts:

1. Go to https://fonts.google.com/specimen/Literata
2. Click **Download family**
3. Unzip and find `static/Literata_500Medium.ttf` (or `Literata-Medium.ttf` depending on version)
4. Rename it to `Literata_500Medium.ttf` and place it in `epub-reader/src/main/assets/fonts/`

If you omit either font family, the app will **not** crash — those fonts will silently fall back
to the system sans-serif — but `FontFamily("OpenDyslexic")` and `FontFamily("Literata")` will
have no visible effect.

---

## 6. Integrating Into Another Android App

### TL;DR — the complete checklist

There are exactly **4 things** you need to do beyond copying the folder. Miss any one of them
and you will get either a build error or a runtime crash.

| # | What | Where | Consequence if skipped |
|---|---|---|---|
| 1 | Copy `epub-reader/` into project root | filesystem | nothing to build |
| 2 | Register module in `settings.gradle` | `settings.gradle` | Gradle can't find the module |
| 3 | Add `implementation project(':epub-reader')` | `app/build.gradle` | app can't see the classes |
| 4a | Copy OpenDyslexic `.otf` files from `mobile/assets/fonts/` → `epub-reader/src/main/assets/fonts/` | filesystem | font silently falls back to system sans-serif |
| 4b | Download `Literata_500Medium.ttf` from Google Fonts → `epub-reader/src/main/assets/fonts/` | filesystem | font silently falls back to system sans-serif |
| 5 | Add `FOREGROUND_SERVICE` permissions | `app/AndroidManifest.xml` | `PlaybackService` crashes on Android 9+ |

Steps 1–5 are detailed below. That is the complete list — nothing else is required.

---

### Option A — Android Library Module (recommended)

This keeps the reader code cleanly separated from your app code and lets you upgrade it
independently.

#### Step 1 — Copy the module

Copy the entire `epub-reader/` directory into the root of your Android project:

```
your-app/
├── app/
├── epub-reader/     ← paste here
└── settings.gradle
```

#### Step 2 — Register it in `settings.gradle`

Open your project's `settings.gradle` (at the project root, not inside `app/`) and add
`':epub-reader'` to the `include` line:

```groovy
// settings.gradle
include ':app', ':epub-reader'
```

Kotlin DSL (`settings.gradle.kts`):
```kotlin
include(":app", ":epub-reader")
```

#### Step 3 — Add the dependency in `app/build.gradle`

Open `app/build.gradle` (the one inside the `app/` folder, not the root one) and add the
`implementation` line inside the existing `dependencies` block:

```groovy
// app/build.gradle
dependencies {
    implementation project(':epub-reader')
    // ... your other existing deps
}
```

Kotlin DSL (`app/build.gradle.kts`):
```kotlin
dependencies {
    implementation(project(":epub-reader"))
}
```

All Readium and Media3 dependencies declared in `epub-reader/build.gradle` are pulled in
transitively — you do **not** need to repeat them in `app/build.gradle`.

Readium 3.x is published to Maven Central, which every Android project already includes by
default, so no extra repository configuration is needed.

#### Step 4 — Copy font assets ⚠️ easy to miss

The reader references two font families (`OpenDyslexic` and `Literata`) served from
`assets/fonts/`. These are binary files not included in the Kotlin source and must be added
manually.

**OpenDyslexic** — copy from this repo (`mobile/assets/fonts/`):

```bash
mkdir -p your-app/epub-reader/src/main/assets/fonts
cp storyteller/mobile/assets/fonts/OpenDyslexic-Regular.otf    your-app/epub-reader/src/main/assets/fonts/
cp storyteller/mobile/assets/fonts/OpenDyslexic-Bold.otf        your-app/epub-reader/src/main/assets/fonts/
cp storyteller/mobile/assets/fonts/OpenDyslexic-Bold-Italic.otf your-app/epub-reader/src/main/assets/fonts/
cp storyteller/mobile/assets/fonts/OpenDyslexic-Italic.otf      your-app/epub-reader/src/main/assets/fonts/
```

**Literata** — not in this repo, download from Google Fonts:
1. Go to https://fonts.google.com/specimen/Literata → **Download family**
2. Unzip, find `static/Literata_500Medium.ttf` (may be named `Literata-Medium.ttf`)
3. Rename to `Literata_500Medium.ttf` and place it in `your-app/epub-reader/src/main/assets/fonts/`

The final directory must contain:

```
epub-reader/src/main/assets/fonts/
├── OpenDyslexic-Regular.otf
├── OpenDyslexic-Bold.otf
├── OpenDyslexic-Bold-Italic.otf
├── OpenDyslexic-Italic.otf
└── Literata_500Medium.ttf
```

If you skip this step the app will **not** crash — the fonts silently fall back to the system
sans-serif — but `FontFamily("OpenDyslexic")` and `FontFamily("Literata")` will have no effect.

#### Step 5 — Declare foreground service permissions in your app's manifest ⚠️ easy to miss

`PlaybackService` is a foreground service (required to keep audio alive while the screen is
off). Android requires explicit permission declarations in the **app** module's manifest.
The service declaration itself is already in the library's `AndroidManifest.xml` and merges
automatically — you only need to add these two lines to `app/src/main/AndroidManifest.xml`:

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest ...>

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application ...>
        ...
    </application>
</manifest>
```

Without these lines, `PlaybackService` will crash with a `SecurityException` the first time
audio playback is started on Android 9 (API 28) and above.

---

### Option B — Copy files directly into your app module

Simpler setup, but the reader code lives alongside your app code.

1. Copy all `.kt` files from `epub-reader/src/main/java/com/storyteller/reader/` into
   your app's source tree, e.g. `app/src/main/java/com/yourpackage/reader/`.
2. Update the `package` declaration at the top of each file to match.
3. Copy the two res files:
   - `fragment_reader.xml` → `app/src/main/res/layout/`
   - `strings.xml` entries → merge into `app/src/main/res/values/strings.xml`
4. Copy font assets into `app/src/main/assets/fonts/`.
5. Add all dependencies from `epub-reader/build.gradle` into `app/build.gradle`.
6. Copy the `<service>` declaration from the library's `AndroidManifest.xml` into your app's.

---

## 7. Public API Reference

### `BookService`

Singleton object. All state is in-memory; it survives the Activity lifecycle but is lost on
process death.

```kotlin
// Unzip a .epub archive to a directory.
// Must be called before openPublication if the EPUB is still a ZIP.
BookService.extractArchive(archiveUrl: URL, extractedUrl: URL)

// Open an extracted EPUB directory and cache the Publication.
// clips = null → auto-parse all SMIL files in the EPUB.
// clips = listOf(...) → use pre-built clips (e.g. restored from persistence).
// Returns the opened Publication.
suspend fun BookService.openPublication(
    bookUuid: String,
    url: URL,             // URL pointing to the extracted directory
    clips: List<OverlayPar>?
): Publication

// Retrieve the cached Publication (null if not opened yet).
BookService.getPublication(bookUuid: String): Publication?

// All OverlayPar clips for a book (empty list if SMIL had none / not yet opened).
BookService.getOverlayClips(bookUuid: String): List<OverlayPar>

// Clips for a specific chapter (matching locator.href).
BookService.getFragments(bookUuid: String, locator: Locator): List<OverlayPar>

// Find the clip at a given audio URL + playback position (binary search).
BookService.getFragment(bookUuid: String, clipUrl: String, position: Double): OverlayPar?

// Find the clip matching a locator + fragment ID.
BookService.getClip(bookUuid: String, locator: Locator): OverlayPar?

// Previous/next clip relative to the current locator.
BookService.getPreviousFragment(bookUuid: String, locator: Locator): OverlayPar?
BookService.getNextFragment(bookUuid: String, locator: Locator): OverlayPar?

// Build a precise Locator (with totalProgression) for an HTML element ID in a chapter.
suspend fun BookService.buildFragmentLocator(
    bookUuid: String, href: Url, fragment: String
): Locator

// All Locators (reading positions) in the publication.
suspend fun BookService.getPositions(bookUuid: String): List<Locator>

// Build an audiobook-style Manifest (for external players).
BookService.buildAudiobookManifest(bookUuid: String): Manifest
```

---

### `EpubView`

```kotlin
class EpubView(
    context: Context,
    val activity: FragmentActivity,
    var listener: EpubViewListener? = null
) : FrameLayout(context)
```

**Usage pattern:**

```kotlin
val epubView = EpubView(context = this, activity = this, listener = myListener)

// Set props before calling finalizeProps()
epubView.pendingProps.bookUuid      = "my-book-id"
epubView.pendingProps.locator       = savedLocator    // null = start of book
epubView.pendingProps.isPlaying     = false
epubView.pendingProps.highlights    = listOf(...)
epubView.pendingProps.bookmarks     = listOf(...)
epubView.pendingProps.readaloudColor = Color.YELLOW
epubView.pendingProps.foreground    = Color.BLACK
epubView.pendingProps.background    = Color.WHITE
epubView.pendingProps.fontFamily    = FontFamily("Literata")
epubView.pendingProps.fontSize      = 1.0
epubView.pendingProps.lineHeight    = 1.4
epubView.pendingProps.paragraphSpacing = 0.5
epubView.pendingProps.textAlign     = TextAlign.JUSTIFY

// Commit: this creates the navigator fragment and starts rendering.
// Must only be called AFTER BookService.openPublication() has returned.
epubView.finalizeProps()

// Add to your layout
container.addView(epubView)

// To update (e.g. when a new audio clip fires):
epubView.pendingProps.locator    = newLocator
epubView.pendingProps.isPlaying  = true
epubView.finalizeProps()
```

**Important:** `finalizeProps()` is idempotent in the sense that calling it multiple times with
the same `bookUuid` will NOT recreate the navigator. The navigator is only recreated when
`bookUuid` or `customFonts` changes.

**Key fields:**
- `pendingProps: Props` — mutable staging area for the next `finalizeProps()` call.
- `props: FinalizedProps?` — the last committed props (read-only after commit).
- `navigator: EpubNavigatorFragment?` — the live Readium navigator (null until initialized).

---

### `EpubViewListener`

All methods have default no-op implementations; override only the ones you need.

```kotlin
interface EpubViewListener {
    // The reading position changed (page turn, programmatic navigation, etc.).
    // Persist this locator to restore the position on next launch.
    fun onLocatorChange(locator: Locator) {}

    // The user tapped the center of the screen (used to toggle toolbar visibility).
    fun onMiddleTouch() {}

    // The user selected text. Show a highlight/copy toolbar at (x, y) dp from top-left.
    fun onSelection(locator: Locator, x: Int, y: Int) {}

    // The user dismissed a text selection.
    fun onSelectionCleared() {}

    // The user double-tapped a sentence fragment (used to add a bookmark/highlight).
    fun onDoubleTouch(locator: Locator) {}

    // The user tapped an existing highlight decoration.
    // Show a context menu at (x, y) dp with edit/delete options.
    fun onHighlightTap(decorationId: String, x: Int, y: Int) {}

    // The bookmarks that are currently visible on the page changed.
    // activeBookmarks is the subset of pendingProps.bookmarks that are on screen.
    fun onBookmarksActivate(activeBookmarks: List<Locator>) {}
}
```

---

### `AudiobookPlayer`

Controls audio playback and bridges clip-change events from `PlaybackService` to the UI.

```kotlin
class AudiobookPlayer(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val listener: Listener          // see below
)
```

**`Listener` interface** (extend `Player.Listener` from Media3):
```kotlin
interface Listener : Player.Listener {
    // A new SMIL <par> clip has started — update EpubView with clip.locator.
    fun onClipChanged(overlayPar: OverlayPar)

    // Playback position changed (fires ~every second while playing).
    fun onPositionChanged(position: Double)

    // The audio track changed (next chapter file started).
    fun onTrackChanged(track: Track, position: Double, index: Int)
}
```

**Key methods:**
```kotlin
// Build the track list from BookService.buildAudiobookManifest() or manually,
// then load it. Connects to PlaybackService via MediaController.
suspend fun AudiobookPlayer.loadTracks(tracks: List<Track>)

fun AudiobookPlayer.play(automaticRewind: Boolean = true)
fun AudiobookPlayer.pause()
fun AudiobookPlayer.seekTo(relativeUri: String, position: Double, skipEmit: Boolean?)
fun AudiobookPlayer.seekBy(amount: Double, bounded: Boolean = false)
fun AudiobookPlayer.next()         // next track
fun AudiobookPlayer.prev()         // previous track
fun AudiobookPlayer.skip(position: Double)  // seek within current track
fun AudiobookPlayer.setRate(rate: Double)
fun AudiobookPlayer.setAutomaticRewind(enabled: Boolean, afterInterruption: Double, afterBreak: Double)
fun AudiobookPlayer.unload()       // releases MediaController + clears state
fun AudiobookPlayer.getIsPlaying(): Boolean
fun AudiobookPlayer.getPosition(): Double
fun AudiobookPlayer.getCurrentTrack(): Track?
fun AudiobookPlayer.getCurrentClip(): OverlayPar?
fun AudiobookPlayer.getTracks(): List<Track>
```

---

### `PlaybackService`

An `ExoPlayer` `MediaLibraryService` that runs as a foreground service. It:
- Holds a single `ExoPlayer` instance for the lifetime of the service.
- Schedules ExoPlayer "messages" at every SMIL clip boundary to fire `CLIP_CHANGED` custom
  commands to connected `MediaController` clients (i.e. `AudiobookPlayer`).
- Supports Android Auto and Automotive via `MediaLibrarySession`.
- Grants URI permissions for artwork to automotive controllers.

You do not interact with `PlaybackService` directly. `AudiobookPlayer.loadTracks()` connects to
it via a `SessionToken`.

---

### Data classes

```kotlin
// One SMIL <par> element: maps a text fragment ID to an audio clip.
data class OverlayPar(
    val audioResource: String,  // audio file path relative to EPUB root
    val fragmentId: String,     // HTML element id= attribute in the text chapter
    val textResource: String,   // text chapter path relative to EPUB root
    val start: Double,          // clip start in seconds
    val end: Double,            // clip end in seconds
    val locator: Locator        // Readium Locator (href + progression + totalProgression)
)

// A highlight shown in the EPUB WebView.
data class Highlight(
    val id: String,
    @ColorInt val color: Int,
    val locator: Locator
)

// A user-supplied font file to register with Readium.
data class CustomFont(
    val uri: String,   // content:// or file:// URI
    val name: String,  // font-family name to use in pendingProps.fontFamily
    val type: String   // MIME type, e.g. "font/ttf"
)

// An audio track for AudiobookPlayer.
data class Track(
    val uri: Uri,          // absolute URI to the audio file
    val bookUuid: String,
    val title: String,     // chapter title
    val duration: Double,  // seconds
    val bookTitle: String,
    val author: String?,
    val coverUri: Uri?,
    val relativeUri: String,   // path relative to EPUB root (used as MediaItem.mediaId)
    val narrator: String?,
    val mimeType: String
)

// Mutable props staged before each finalizeProps() call.
data class Props(
    var bookUuid: String?,
    var locator: Locator?,
    var isPlaying: Boolean?,
    var highlights: List<Highlight>?,
    var bookmarks: List<Locator>?,
    var readaloudColor: Int?,
    var customFonts: List<CustomFont>?,
    @ColorInt var foreground: Int?,
    @ColorInt var background: Int?,
    var fontFamily: FontFamily?,
    var lineHeight: Double?,
    var paragraphSpacing: Double?,
    var fontSize: Double?,
    var textAlign: TextAlign?
)
```

---

## 8. What an EPUB Must Look Like for Synced Audio

The reader requires an **extracted (unzipped) EPUB directory**, not a `.epub` ZIP file. Call
`BookService.extractArchive()` to unzip it first.

### Required EPUB structure

```
META-INF/
  container.xml

OEBPS/
  content.opf         ← spine items must have  media:overlay="smil-id"
  chapter1.smil       ← SMIL file (one per chapter)
  chapter1.html       ← text chapter (HTML/XHTML)
  Audio/
    track01.mp3
```

### Minimal `content.opf` (relevant parts)

```xml
<manifest>
  <item id="ch1"      href="chapter1.html" media-type="application/xhtml+xml"
        media-overlay="ch1-smil"/>
  <item id="ch1-smil" href="chapter1.smil" media-type="application/smil+xml"/>
  <item id="audio1"   href="Audio/track01.mp3" media-type="audio/mpeg"/>
</manifest>
<spine>
  <itemref idref="ch1"/>
</spine>
```

### Minimal SMIL file (`chapter1.smil`)

```xml
<smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops">
  <body>
    <seq epub:textref="chapter1.html" epub:type="chapter">
      <par>
        <text src="chapter1.html#s0"/>
        <audio src="Audio/track01.mp3" clipBegin="0s" clipEnd="2.500s"/>
      </par>
      <par>
        <text src="chapter1.html#s1"/>
        <audio src="Audio/track01.mp3" clipBegin="2.500s" clipEnd="5.100s"/>
      </par>
    </seq>
  </body>
</smil>
```

### Minimal HTML chapter

```html
<html>
  <body>
    <p id="s0">First sentence of the chapter.</p>
    <p id="s1">Second sentence.</p>
  </body>
</html>
```

Storyteller's `align` CLI (`yarn align` in this repo) produces EPUBs in exactly this format.

---

## 9. Step-by-Step Usage Guide

### Step 1 — Extract the EPUB

```kotlin
val bookUuid = "my-unique-book-id"
val epubZipFile = File(context.filesDir, "downloads/mybook.epub")
val extractedDir = File(context.filesDir, "books/$bookUuid").also { it.mkdirs() }

// Only extract once
if (extractedDir.list().isNullOrEmpty()) {
    BookService.extractArchive(
        epubZipFile.toURI().toURL(),
        extractedDir.toURI().toURL()
    )
}
```

### Step 2 — Open the publication

Must be called before creating `EpubView`. This call:
- Opens the `DirectoryContainer` over the extracted directory.
- Lets Readium parse the OPF and build a `Publication`.
- Parses all `.smil` files and populates the clip list.

```kotlin
lifecycleScope.launch {
    BookService.openPublication(
        bookUuid = bookUuid,
        url = extractedDir.toURI().toURL(),
        clips = null   // null = auto-parse SMIL
    )
    // safe to create EpubView now
}
```

### Step 3 — Create and mount EpubView

`EpubView` is a `FrameLayout` so it can be added programmatically or used in a layout XML
(see the note about `@SuppressLint("ViewConstructor")` — you must pass `activity` at construction
time, so XML inflation is not supported without a factory).

```kotlin
val epubView = EpubView(
    context = this,
    activity = this,   // the FragmentActivity that will own the navigator fragment
    listener = object : EpubViewListener {
        override fun onLocatorChange(locator: Locator) {
            savePosition(locator.toJSON().toString())
        }
        override fun onMiddleTouch() { toggleToolbar() }
        override fun onHighlightTap(decorationId: String, x: Int, y: Int) {
            showHighlightMenu(decorationId, x, y)
        }
    }
)

// Restore saved position
val savedJson = prefs.getString("locator_$bookUuid", null)
val savedLocator = savedJson?.let {
    Locator.fromJSON(JSONObject(it))
}

epubView.pendingProps.bookUuid = bookUuid
epubView.pendingProps.locator  = savedLocator
epubView.finalizeProps()

findViewById<FrameLayout>(R.id.epub_container).addView(epubView)
```

### Step 4 — Build the track list for audio playback

```kotlin
val manifest = BookService.buildAudiobookManifest(bookUuid)

val tracks = manifest.readingOrder.map { link ->
    val audioFile = File(extractedDir, link.href.toString())
    Track(
        uri          = audioFile.toUri(),
        bookUuid     = bookUuid,
        title        = link.title ?: "",
        duration     = link.duration ?: 0.0,
        bookTitle    = manifest.metadata.title ?: "",
        author       = manifest.metadata.authors.firstOrNull()?.name,
        coverUri     = null,
        relativeUri  = link.href.toString(),
        narrator     = null,
        mimeType     = link.mediaType?.toString() ?: "audio/mpeg"
    )
}
```

### Step 5 — Create AudiobookPlayer and start playback

```kotlin
val player = AudiobookPlayer(
    context        = this,
    coroutineScope = lifecycleScope,
    listener       = object : Listener {
        override fun onClipChanged(overlayPar: OverlayPar) {
            // Update the reader highlight in real time
            epubView.pendingProps.locator   = overlayPar.locator
            epubView.pendingProps.isPlaying = true
            epubView.finalizeProps()
        }
        override fun onPositionChanged(position: Double) {
            updateSeekBar(position)
        }
        override fun onTrackChanged(track: Track, position: Double, index: Int) {
            updateNowPlayingTitle(track.title)
        }
    }
)

lifecycleScope.launch {
    player.loadTracks(tracks)

    // Seek to saved audio position if any
    val savedTrackUri = prefs.getString("audio_track_$bookUuid", null)
    val savedPosition = prefs.getFloat("audio_pos_$bookUuid", 0f).toDouble()
    if (savedTrackUri != null) {
        player.seekTo(savedTrackUri, savedPosition, skipEmit = true)
    }

    player.play()
}
```

### Step 6 — Save state on pause/destroy

```kotlin
override fun onPause() {
    super.onPause()
    player.pause()

    // Save audio position
    player.getCurrentTrack()?.let { track ->
        prefs.edit()
            .putString("audio_track_$bookUuid", track.relativeUri)
            .putFloat("audio_pos_$bookUuid", player.getPosition().toFloat())
            .apply()
    }
}

override fun onDestroy() {
    super.onDestroy()
    player.unload()
}
```

---

## 10. Full Example Activity

This is the `ReadingActivity.kt` included in the library. It demonstrates the full lifecycle:

```kotlin
package com.storyteller.reader

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.net.URL

class ReadingActivity : FragmentActivity() {

    private var epubView: EpubView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading)

        val bookUuid = intent.getStringExtra("bookUuid")
            ?: error("ReadingActivity requires 'bookUuid' in Intent extras")
        val epubZipPath = intent.getStringExtra("epubPath")
            ?: error("ReadingActivity requires 'epubPath' in Intent extras")

        lifecycleScope.launch {
            // 1. Extract .epub ZIP to a local directory (idempotent)
            val extractedDir = File(filesDir, "books/$bookUuid").also { it.mkdirs() }
            if (extractedDir.list().isNullOrEmpty()) {
                BookService.extractArchive(
                    URL("file://$epubZipPath"),
                    extractedDir.toURI().toURL()
                )
            }

            // 2. Open publication — auto-parses all SMIL media overlay files
            BookService.openPublication(bookUuid, extractedDir.toURI().toURL(), clips = null)

            // 3. Restore saved position (null = start of book)
            val savedLocator: Locator? = intent.getStringExtra("locatorJson")
                ?.let { Locator.fromJSON(org.json.JSONObject(it)) }

            // 4. Create and mount EpubView
            val view = EpubView(
                context = this@ReadingActivity,
                activity = this@ReadingActivity,
                listener = object : EpubViewListener {
                    override fun onLocatorChange(locator: Locator) {
                        // Persist the reading position here
                    }
                    override fun onMiddleTouch() {
                        // Toggle toolbar visibility, etc.
                    }
                    override fun onDoubleTouch(locator: Locator) {
                        // Show bookmark/highlight controls
                    }
                    override fun onHighlightTap(decorationId: String, x: Int, y: Int) {
                        // Show a context menu for the tapped highlight
                    }
                }
            ).also { epubView = it }

            view.pendingProps.bookUuid = bookUuid
            view.pendingProps.locator = savedLocator
            view.finalizeProps()

            findViewById<FrameLayout>(R.id.epub_container).addView(view)
        }
    }

    // Call from AudiobookPlayer.Listener.onClipChanged to sync reader with audio
    fun onClipChanged(clip: OverlayPar) {
        val view = epubView ?: return
        view.pendingProps.locator   = clip.locator
        view.pendingProps.isPlaying = true
        view.finalizeProps()
    }

    override fun onDestroy() {
        super.onDestroy()
        epubView = null
    }
}
```

**`activity_reading.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <FrameLayout
        android:id="@+id/epub_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

**Launching the activity:**
```kotlin
startActivity(
    Intent(this, ReadingActivity::class.java).apply {
        putExtra("bookUuid", "my-book-uuid-123")
        putExtra("epubPath", "/data/user/0/com.myapp/files/downloads/mybook.epub")
        putExtra("locatorJson", savedLocator?.toJSON()?.toString())  // optional
    }
)
```

---

## 11. Verification Checklist

After integration, verify in order:

1. **Extraction** — call `BookService.extractArchive()` and confirm the output directory
   contains `META-INF/container.xml` and `OEBPS/content.opf`.

2. **SMIL parsing** — call `BookService.openPublication()` then
   `BookService.getOverlayClips(bookUuid)`. Confirm it returns a non-empty list. An empty list
   means either the EPUB has no Media Overlays, or the OPF `media:overlay` attributes are missing.

3. **Rendering** — mount `EpubView` and call `finalizeProps()`. The first chapter should appear
   in the WebView within a second or two.

4. **Navigation** — set `pendingProps.locator` to a locator from `getPositions()` and call
   `finalizeProps()`. The reader should scroll to that position.

5. **Highlight** — set `pendingProps.isPlaying = true` and confirm the sentence is highlighted
   in the `readaloudColor`.

6. **Audio** — call `AudiobookPlayer.loadTracks()` followed by `play()`. Confirm
   `onClipChanged` fires and the reader scrolls + highlights the correct sentence.

7. **Background audio** — press the Home button while audio is playing. The notification
   player should remain visible and audio should continue.

---

## 12. Known Limitations / TODOs

- **`onExternalLinkActivated`** is a stub (`TODO("Not yet implemented")`). Tapping external links
  in the EPUB will crash with a `NotImplementedError`. Implement it to open a `CustomTabsIntent`
  or `WebView`.

- **Process death** — `BookService` stores publications in a plain `MutableMap`. After process
  death you must call `openPublication()` again before using `EpubView`. Consider using a
  `ViewModel` or persistent cache to detect this case.

- **Seek-bar precision** — `AudiobookPlayer.onPositionChanged` fires once per second. For a
  smoother seek bar, reduce the `delay(1000)` in `audioProgress()` at the cost of more CPU.

- **Playback speed and rewind** — `setAutomaticRewind()` divides rewind into two buckets
  (short interruption vs long break). The `afterInterruption`/`afterBreak` amounts are in
  seconds and must be set by the caller; they default to 0 (no rewind).

- **Custom fonts** — `CustomFont.uri` must be a `file://` or `content://` URI that Readium's
  WebView can fetch. Fonts bundled in `assets/` should be served via `servedAssets` (already
  done for OpenDyslexic and Literata). For dynamically downloaded fonts use `file://`.

- **Large books (memory)** — Two things to be aware of for books with many chapters:
  1. `openPublication(bookUuid, url, null)` parses all SMIL files on-device, loading the
     full HTML of every chapter into memory during parsing. For books with 100+ chapters this
     can briefly consume 50–100 MB. After parsing, the HTML is freed; only the `OverlayPar`
     clip list (~0.5 KB per clip) is retained. To avoid the parsing spike on subsequent opens,
     persist the clips between sessions: call `BookService.getOverlayClips(bookUuid)` after
     the first open, serialize via `OverlayPar.toJson()`, and pass them back via
     `openPublication(bookUuid, url, clips)` on future launches.
  2. ExoPlayer messages are scheduled lazily (current track + one lookahead). For a 5000-clip
     book this keeps the live message count at ~100 instead of ~5000.

- **Pagination vs scroll** — The current configuration uses the Readium default (paginated).
  To switch to scrollable view, configure `EpubPreferences(scroll = true)` in
  `EpubFragment.onCreate` and pass it to `initialPreferences`.

- **`PlaybackService` app name** — The `setName("Storyteller")` call in `PlaybackService` sets
  the ExoPlayer thread name. Change it to your app name.

- **Session intent deep-link** — `PlaybackService` sets the notification tap intent to
  `"storyteller://notification.click"`. Replace this URI with your own deep-link scheme.

---

## 13. Configuration & Actions Reference

A complete inventory of every setting and action available through the library's public API,
derived from the Storyteller mobile UI.

---

### 13.1 EpubView display settings (`pendingProps`)

All display settings are applied by setting fields on `view.pendingProps` and calling
`view.finalizeProps()`. Changes take effect immediately in the live reader.

| Property | Type | Range / Values | Default | Notes |
|----------|------|----------------|---------|-------|
| `fontFamily` | `FontFamily` | `FontFamily("Literata")`, `FontFamily("OpenDyslexic")`, any custom name | `FontFamily("Literata")` | Built-in fonts require assets — see Section 5 |
| `fontSize` | `Double` | 0.7 – 2.0, step 0.05 | 1.0 | Multiplier relative to the EPUB's base size |
| `lineHeight` | `Double` | 1.0 – 2.0, step 0.05 | 1.4 | |
| `textAlign` | `TextAlign` | `TextAlign.JUSTIFY`, `TextAlign.LEFT` | `TextAlign.JUSTIFY` | |
| `foreground` | `@ColorInt Int` | Any ARGB | `0xFF111111` | Text color; set together with `background` |
| `background` | `@ColorInt Int` | Any ARGB | `0xFFFFFFFF` | Page background color |
| `readaloudColor` | `@ColorInt Int` | Any ARGB | `0xFFFFFF00` | Sentence highlight color during read-aloud |
| `isPlaying` | `Boolean` | `true` / `false` | `false` | When `true`, enables the moving sentence highlight |
| `highlights` | `List<Highlight>` | Each: `id: String`, `color: @ColorInt Int`, `locator: Locator` | `emptyList()` | Persistent user highlights |
| `bookmarks` | `List<Locator>` | — | `emptyList()` | Bookmark positions; displayed as decorations |
| `customFonts` | `List<CustomFont>` | Each: `uri: String`, `name: String`, `type: String` | `emptyList()` | Fonts loaded at runtime; `uri` must be `file://` or `content://` |
| `paragraphSpacing` | `Double` | Any positive `Double` | `0.5` | Spacing between paragraphs — available in the Readium engine but not exposed in the Storyteller UI |

#### Built-in theme presets

Set `foreground` and `background` together to match one of the Storyteller themes:

| Theme | `foreground` | `background` |
|-------|-------------|-------------|
| Day | `0xFF111111` | `0xFFFFFFFF` |
| Sepia | `0xFF78350F` | `0xFFFEF9C3` |
| Crisp White | `0xFF000000` | `0xFFFFFFFF` |
| Night | `0xFFD1D5DB` | `0xFF111827` |

```kotlin
// Example: Night mode
view.pendingProps.foreground  = 0xFFD1D5DB.toInt()
view.pendingProps.background  = 0xFF111827.toInt()
view.finalizeProps()
```

#### Standard highlight colors

The Storyteller UI offers five tints for user highlights:

| Name | `@ColorInt` value | Hex |
|------|------------------|-----|
| Yellow | `0x4DFFFF00` | semi-transparent yellow |
| Red | `0x4DFF0000` | semi-transparent red |
| Green | `0x4D00FF00` | semi-transparent green |
| Blue | `0x4D0000FF` | semi-transparent blue |
| Magenta | `0x4DFF00FF` | semi-transparent magenta |

---

### 13.2 AudiobookPlayer actions

Constructor: `AudiobookPlayer(context: Context, coroutineScope: CoroutineScope, listener: Listener)`

#### Playback control

| Method | Signature | Notes |
|--------|-----------|-------|
| `play` | `play(automaticRewind: Boolean = true)` | Starts or resumes playback; optionally rewinds a few seconds on resume |
| `pause` | `pause()` | Pauses playback |
| `unload` | `unload()` | Stops playback and releases ExoPlayer + media session |

#### Navigation

| Method | Signature | Notes |
|--------|-----------|-------|
| `seekBy` | `seekBy(amount: Double, bounded: Boolean = false)` | Seek forward (positive) or backward (negative) by `amount` seconds. Pass `bounded = true` to clamp within the current track |
| `seekTo` | `seekTo(relativeUri: String, position: Double, skipEmit: Boolean? = null)` | Seek to an absolute position within a specific track identified by its relative URI |
| `next` | `next()` | Skip to the next track/chapter |
| `prev` | `prev()` | Skip to the previous track/chapter |
| `skip` | `skip(position: Double)` | Jump to an absolute position (seconds) within the current track |

#### Speed & rewind settings

| Method | Signature | Storyteller UI values |
|--------|-----------|----------------------|
| `setRate` | `setRate(rate: Double)` | Range 0.5 – 4.0, step 0.1. Quick presets: 0.75, 1, 1.25, 1.5, 1.75, 2 |
| `setAutomaticRewind` | `setAutomaticRewind(enabled: Boolean, afterInterruption: Double, afterBreak: Double)` | Defaults: `true`, 3.0 s, 10.0 s |

`afterInterruption` applies when audio focus is lost briefly (e.g. notification).
`afterBreak` applies when playback is paused for a longer period.

#### State queries

| Method | Returns | Notes |
|--------|---------|-------|
| `getIsPlaying` | `Boolean` | Whether the player is currently playing |
| `getPosition` | `Double` | Current position in seconds within the active clip |
| `getCurrentTrack` | `Track?` | Active `Track` object (audio file + metadata) |
| `getCurrentClip` | `OverlayPar?` | Active `OverlayPar` — text↔audio mapping for the current sentence |
| `getTracks` | `List<Track>` | All tracks in the loaded audiobook |

#### Listener callbacks

Implement `AudiobookPlayer.Listener` to react to player events:

```kotlin
interface Listener {
    fun onClipChanged(clip: OverlayPar)        // fired at each sentence boundary → update EpubView
    fun onPositionChanged(position: Double)    // fired ~every second → update seek bar
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onTracksChanged(tracks: List<Track>)
}
```

---

### 13.3 Sleep timer

The library has no built-in sleep timer. Implement one client-side using a coroutine:

```kotlin
var sleepTimerJob: Job? = null

fun setSleepTimer(minutes: Long) {
    sleepTimerJob?.cancel()
    sleepTimerJob = lifecycleScope.launch {
        delay(minutes * 60_000L)
        player.pause()
    }
}

fun cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
}
```

Storyteller's UI offers these presets (minutes): **5, 10, 15, 30, 45, 60, 90, 120**.

---

### 13.4 Settings available in the Readium engine but not yet wired

The following `EpubPreferences` fields exist in Readium v3.1.2 but are not currently exposed
through `EpubView.pendingProps` in this library:

| Preference | Type | Notes |
|-----------|------|-------|
| `scroll` | `Boolean` | `false` = paginated (default); `true` = continuous scroll |
| `columnCount` | `ColumnCount` | `ColumnCount.AUTO`, `ColumnCount.ONE`, `ColumnCount.TWO` |
| `pageMargins` | `Double` | Margin multiplier |
| `publisherStyles` | `Boolean` | `true` = respect publisher CSS; `false` = apply reader defaults |

To expose any of these, add a field to `FinalizedProps` in `EpubView.kt`, then pass it to
`EpubPreferences(...)` inside `EpubFragment.onCreate`.
