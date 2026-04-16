# Audio Full-Screen Player Improvements — Implementation Plan

## Goal

Improve the full-screen audio player in the epub3 reader with the following changes:

1. **Layout**: player fills the entire screen (currently stops just short of the top)
2. **Folder audiobook mode**:
   - Seek slider spans the full book duration (not just the current chapter)
   - Current-time display updates live while dragging the slider
   - Bug fix: slider no longer jumps back to old position after seek while paused
   - Two new skip buttons: −20 s and +30 s, flanking the play button
3. **SMIL audiobook mode**:
   - Seek slider is plain (same size as folder mode) — the old +/− buttons move off the slider
   - The moved +/− page-navigation buttons take the inner positions flanking the play button
   - The outer SkipPrevious / SkipNext buttons now navigate epub chapters, not individual clips
4. **General polish**:
   - Volume slider width matches the seek slider width
   - Play button respects the user's accent color setting
   - Bookmark button (when selected) has a gap between it and the chapter chip

---

## Architecture

All UI changes are confined to **`AudioFullScreenPlayer.kt`**.  
Logic changes span the controller layer:

- `EpubAudioController.kt` — add `seekRelative()` to the interface
- `AudiobookFolderController.kt` — implement `seekRelative()` + fix `seekToTrackPosition()`
- `MediaOverlayController.kt` — no changes needed (interface default no-op suffices)

No new files are required.

---

## Critical Files

| File | Role |
|------|------|
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt` | Main UI — all visual changes |
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/EpubAudioController.kt` | Interface — add `seekRelative` |
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt` | Folder seek logic |
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/MediaOverlayController.kt` | SMIL controller (read for reference) |
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ControlsCard.kt` | Source of `Epub3LocationLabel` and `locatorToPositionIndex` (read-only) |

---

## Current State (what the code looks like before these changes)

### AudioFullScreenPlayer.kt — key sections

**Surface modifier (line ~137):**
```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(Alignment.Bottom)   // ← causes the gap at top
        ...
```

**Inner Column (line ~193):**
```kotlin
Column(
    modifier = Modifier
        .navigationBarsPadding()
        .padding(bottom = 16.dp),
```

**Chapter chip + bookmark row (lines ~252-278):**
```kotlin
Row(...) {
    SuggestionChip(modifier = Modifier.weight(1f), ...)
    Epub3BookmarkToggleButton(...)   // ← no gap between them
}
```

**Folder mode seek slider (lines ~280-302):**
```kotlin
if (audioTracks.isNotEmpty() && onSeekToTrackPosition != null) {
    val prevTrackDuration = audioTracks.take(currentAudioTrackIndex).sumOf { it.durationSeconds }
    val currentTrackDuration = audioTracks.getOrNull(currentAudioTrackIndex)?.durationSeconds ?: 0.0
    val positionInTrack = (elapsedSeconds - prevTrackDuration).coerceIn(0.0, currentTrackDuration)

    var sliderDraft by remember(currentAudioTrackIndex) { mutableStateOf(positionInTrack.toFloat()) }
    var isInteracting by remember { mutableStateOf(false) }

    AppSlider(
        value = if (isInteracting) sliderDraft else positionInTrack.toFloat(),
        onValueChange = { isInteracting = true; sliderDraft = it },
        onValueChangeFinished = {
            isInteracting = false
            onSeekToTrackPosition(currentAudioTrackIndex, sliderDraft.toDouble())
        },
        valueRange = 0f..currentTrackDuration.toFloat().coerceAtLeast(1f),  // ← only current track
        ...
    )
```

**SMIL mode (lines ~304-315):**
```kotlin
} else if (positions.size > 1) {
    Epub3PageNavigatorRow(   // ← has flanking +/− buttons
        positions = positions,
        currentLocator = currentLocator,
        onNavigateToPosition = onNavigateToPosition,
        modifier = ...,
    )
}
```

**Time display (lines ~318-345):**
```kotlin
if (totalDurationSeconds > 0) {
    val remaining = (totalDurationSeconds - elapsedSeconds).coerceAtLeast(0.0)
    Row(...) {
        Text(text = formatHMS(elapsedSeconds), ...)   // ← not updated during drag
        Text(text = formatTimeLeft(remaining), ...)
        Text(text = formatHMS(totalDurationSeconds), ...)
    }
}
```

**Controls row (lines ~347-371):**
```kotlin
Row(...) {
    IconButton(onClick = controller::seekToPrev) {
        Icon(Icons.Filled.SkipPrevious, ...)
    }
    FilledIconButton(
        onClick = controller::togglePlayPause,
        modifier = Modifier.size(72.dp),   // ← no colors override, ignores accent
    ) { ... }
    IconButton(onClick = controller::seekToNext) {
        Icon(Icons.Filled.SkipNext, ...)
    }
}
```

**Volume row (lines ~373-401):**
```kotlin
Row(
    modifier = fadeModifier
        .fillMaxWidth()
        .padding(horizontal = 48.dp)    // ← wider than the 32.dp seek slider
        ...
```

### AudiobookFolderController.kt — seekToTrackPosition (line ~240)

```kotlin
fun seekToTrackPosition(index: Int, positionSeconds: Double) {
    val tracks = loadedTracks
    if (index < 0 || index >= tracks.size) return
    val track = tracks[index]
    player.seekTo(track.relativeUri, positionSeconds, skipEmit = false)
    updateIsCurrentPositionBookmarked()
    // ← _elapsedSeconds not updated here; polling loop takes up to 500ms to catch up
    //   causing the slider to jump back visually when paused
}
```

### EpubAudioController.kt — interface

```kotlin
interface EpubAudioController {
    val isPlaying: StateFlow<Boolean>
    val volume: StateFlow<Float>
    val elapsedSeconds: StateFlow<Double>
    val totalDurationSeconds: StateFlow<Double>

    fun togglePlayPause()
    fun seekToNext()
    fun seekToPrev()
    fun setVolume(v: Float)
    fun applyAudioSettings(settings: Epub3NativeSettings)
    fun release()
    // ← no seekRelative
}
```

---

## Task Ordering

- Tasks 1–5 are independent; any order works.
- **Task 6 before Task 8** — Task 8 hoists `smilCurrentIndex` that the Task 6 SMIL block references.
- **Task 7 before Task 8** — Task 8 calls `controller.seekRelative()`.
- **Skip Task 2** if doing in order — Task 8 replaces the same `FilledIconButton` with the accent color already included.

---

## Task 1: Fix full-screen height

**File:** `AudioFullScreenPlayer.kt`

Change the `Surface` modifier and the inner `Column` modifier.

### Surface modifier — replace

```kotlin
// Before (line ~139-140):
.fillMaxWidth()
.wrapContentHeight(Alignment.Bottom)

// After:
.fillMaxWidth()
.fillMaxHeight()
```

Remove import `androidx.compose.foundation.layout.wrapContentHeight` if it becomes unused.

### Inner Column — replace

```kotlin
// Before (line ~194-196):
modifier = Modifier
    .navigationBarsPadding()
    .padding(bottom = 16.dp),

// After:
modifier = Modifier
    .statusBarsPadding()
    .navigationBarsPadding()
    .padding(bottom = 16.dp),
```

Add import: `androidx.compose.foundation.layout.statusBarsPadding`

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "fix: expand full-screen audio player to fill entire screen height"
```

---

## Task 2: Play button uses accent color *(can be skipped — Task 8 handles this)*

If you want an isolated early commit, find `FilledIconButton(` (line ~358) and add the `colors` parameter:

```kotlin
FilledIconButton(
    onClick = controller::togglePlayPause,
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
    ),
    modifier = Modifier.size(72.dp),
) {
```

Add import: `androidx.compose.material3.IconButtonDefaults`

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "fix: play button now follows accent color setting"
```

---

## Task 3: Bookmark button gap from chapter chip

**File:** `AudioFullScreenPlayer.kt` (lines ~252-278)

Add a `Spacer` between `SuggestionChip` and `Epub3BookmarkToggleButton`:

```kotlin
// Before:
            SuggestionChip(
                modifier = Modifier.weight(1f),
                ...
            )
            Epub3BookmarkToggleButton(

// After:
            SuggestionChip(
                modifier = Modifier.weight(1f),
                ...
            )
            Spacer(modifier = Modifier.width(8.dp))
            Epub3BookmarkToggleButton(
```

Add imports:
```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
```

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "fix: add gap between chapter chip and bookmark button"
```

---

## Task 4: Volume slider width matches seek slider

**File:** `AudioFullScreenPlayer.kt` (line ~378)

Change the volume `Row` horizontal padding from `48.dp` to `32.dp`:

```kotlin
// Before:
.padding(horizontal = 48.dp)

// After:
.padding(horizontal = 32.dp)
```

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "fix: align volume slider width with seek slider"
```

---

## Task 5: Fix paused-seek jump-back bug

**File:** `AudiobookFolderController.kt` (line ~240)

**Root cause:** `seekToTrackPosition()` calls `player.seekTo()` (async). The polling loop updates `_elapsedSeconds` every 500 ms. When the user releases the slider, `isInteracting = false` is set immediately, so the UI reverts to `positionInTrack = elapsedSeconds - prevDuration` — which is still the old value. Fix: update `_elapsedSeconds` synchronously inside `seekToTrackPosition`.

```kotlin
// Replace current implementation:
fun seekToTrackPosition(index: Int, positionSeconds: Double) {
    val tracks = loadedTracks
    if (index < 0 || index >= tracks.size) return
    val track = tracks[index]
    player.seekTo(track.relativeUri, positionSeconds, skipEmit = false)
    updateIsCurrentPositionBookmarked()
}

// With:
fun seekToTrackPosition(index: Int, positionSeconds: Double) {
    val tracks = loadedTracks
    if (index < 0 || index >= tracks.size) return
    val track = tracks[index]
    player.seekTo(track.relativeUri, positionSeconds, skipEmit = false)
    // Immediately update StateFlows so the UI doesn't jump back when paused
    val prevDuration = _tracks.value.take(index).sumOf { it.durationSeconds }
    _elapsedSeconds.value = prevDuration + positionSeconds
    _currentTrackIndex.value = index
    updateIsCurrentPositionBookmarked()
}
```

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt
git commit -m "fix: seeking while paused no longer snaps slider back to old position"
```

---

## Task 6: Folder mode — full-book seek slider + live time display; plain SMIL slider

**File:** `AudioFullScreenPlayer.kt` (lines ~280-345)

Replace the entire slider + time-display block. Key changes:

- `sliderDraft` / `isInteracting` hoisted above both slider modes so the time display can read them.
- Folder slider: `valueRange` is `0..totalDurationSeconds`; on release, binary-search the track list to find the target track and call `onSeekToTrackPosition(trackIdx, offsetInTrack)`.
- SMIL slider: plain `AppSlider` (no `Epub3PageNavigatorRow` wrapper); `smilCurrentIndex` is **not** defined here — it will be hoisted in Task 8 Step 2. Reference `smilCurrentIndex` as if it exists above this block.
- Time display: uses `sliderDraft` while `isInteracting` is true, so it updates live.

### New code block (replaces lines ~280-345)

```kotlin
                        // Hoisted drag state — used by both the slider and the time display below
                        var sliderDraft by remember { mutableStateOf(elapsedSeconds.toFloat()) }
                        var isInteracting by remember { mutableStateOf(false) }

                        if (audioTracks.isNotEmpty() && onSeekToTrackPosition != null) {
                            // Folder-mode: full-book seek slider
                            AppSlider(
                                value = if (isInteracting) sliderDraft else elapsedSeconds.toFloat(),
                                onValueChange = { isInteracting = true; sliderDraft = it },
                                onValueChangeFinished = {
                                    isInteracting = false
                                    val target = sliderDraft.toDouble()
                                        .coerceIn(0.0, totalDurationSeconds)
                                    var cumulative = 0.0
                                    for ((idx, track) in audioTracks.withIndex()) {
                                        val trackEnd = cumulative + track.durationSeconds
                                        if (trackEnd >= target || idx == audioTracks.lastIndex) {
                                            onSeekToTrackPosition(idx, target - cumulative)
                                            break
                                        }
                                        cumulative = trackEnd
                                    }
                                },
                                valueRange = 0f..totalDurationSeconds.toFloat().coerceAtLeast(1f),
                                accentColor = accentColor,
                                colors = AppSliderDefaults.colors(accentColor = accentColor),
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 16.dp),
                            )
                        } else if (positions.size > 1) {
                            // SMIL mode slider — plain, no +/− buttons (they move to controls row)
                            // smilCurrentIndex is declared above this block (Task 8 Step 2)
                            val smilScope = rememberCoroutineScope()
                            var smilEndJob by remember { mutableStateOf<Job?>(null) }
                            var smilInteracting by remember { mutableStateOf(false) }
                            var smilDraft by remember { mutableStateOf(smilCurrentIndex.toFloat()) }

                            LaunchedEffect(smilCurrentIndex) {
                                if (!smilInteracting) smilDraft = smilCurrentIndex.toFloat()
                            }

                            fun navigateSmil(newIndex: Int) {
                                smilDraft = newIndex.toFloat()
                                onNavigateToPosition(newIndex)
                                smilInteracting = true
                                smilEndJob?.cancel()
                                smilEndJob = smilScope.launch {
                                    delay(700)
                                    smilInteracting = false
                                }
                            }

                            Column(
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 16.dp),
                            ) {
                                Epub3LocationLabel(
                                    positions = positions,
                                    currentLocator = currentLocator,
                                    overrideIndex = smilDraft.roundToInt(),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AppSlider(
                                    value = smilDraft,
                                    onValueChange = { smilInteracting = true; smilDraft = it },
                                    onValueChangeFinished = { navigateSmil(smilDraft.roundToInt()) },
                                    valueRange = 0f..(positions.size - 1).toFloat(),
                                    steps = 0,
                                    accentColor = accentColor,
                                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Time display — live during folder-mode drag; static otherwise
                        if (totalDurationSeconds > 0) {
                            val displayedElapsed = if (isInteracting && audioTracks.isNotEmpty()) {
                                sliderDraft.toDouble()
                            } else {
                                elapsedSeconds
                            }
                            val remaining = (totalDurationSeconds - displayedElapsed).coerceAtLeast(0.0)
                            Row(
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp)
                                    .padding(top = if (positions.size > 1) 0.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatHMS(displayedElapsed),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatTimeLeft(remaining),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatHMS(totalDurationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
```

### New imports to add

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import snd.komelia.ui.reader.epub.Epub3LocationLabel
import snd.komelia.ui.reader.epub.locatorToPositionIndex
```

Remove if now unused: `import snd.komelia.ui.reader.epub.Epub3PageNavigatorRow`

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "feat: full-book seek slider with live time display in folder mode; plain SMIL slider"
```

---

## Task 7: Add seekRelative to interface and folder controller

**Files:** `EpubAudioController.kt`, `AudiobookFolderController.kt`

`seekRelative` is only wired to UI in folder mode (±20 s/+30 s buttons). The SMIL inner buttons use epub-position navigation instead, so `MediaOverlayController` doesn't need an override — the interface's default no-op is sufficient.

### EpubAudioController.kt — add after `release()`

```kotlin
    /**
     * Seeks relative to the current global playback position.
     * Positive = forward, negative = backward. Crosses track boundaries.
     * Default no-op — only meaningful for folder-mode audiobooks.
     */
    fun seekRelative(deltaSeconds: Double) {}
```

### AudiobookFolderController.kt — add after `seekToTrackPosition()`

```kotlin
    override fun seekRelative(deltaSeconds: Double) {
        val targetElapsed = (_elapsedSeconds.value + deltaSeconds)
            .coerceIn(0.0, _totalDurationSeconds.value)
        var cumulative = 0.0
        for ((index, track) in _tracks.value.withIndex()) {
            val trackEnd = cumulative + track.durationSeconds
            if (trackEnd >= targetElapsed || index == _tracks.value.lastIndex) {
                seekToTrackPosition(index, targetElapsed - cumulative)
                return
            }
            cumulative = trackEnd
        }
    }
```

### Commit
```bash
git add \
  komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/EpubAudioController.kt \
  komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt
git commit -m "feat: add seekRelative to EpubAudioController, implement in folder mode controller"
```

---

## Task 8: Rebuild the controls row (5 buttons) + hoist smilCurrentIndex

**File:** `AudioFullScreenPlayer.kt`

### New button layout

```
[SkipPrevious]  [Inner-Left]  [Play/Pause 72dp]  [Inner-Right]  [SkipNext]
```

| Button | Folder mode | SMIL mode |
|--------|-------------|-----------|
| `SkipPrevious` (outer-left) | `controller.seekToPrev()` — prev track | navigate to start of prev epub chapter |
| Inner-Left | `controller.seekRelative(-20.0)` — `Replay20` icon | `onNavigateToPosition(smilCurrentIndex - 1)` — `Remove` icon |
| `SkipNext` (outer-right) | `controller.seekToNext()` — next track | navigate to start of next epub chapter |
| Inner-Right | `controller.seekRelative(30.0)` — `Forward30` icon | `onNavigateToPosition(smilCurrentIndex + 1)` — `Add` icon |

### Step 1 — Add icon imports

```kotlin
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay20
import androidx.compose.material3.IconButtonDefaults
```

### Step 2 — Hoist smilCurrentIndex (insert before `var sliderDraft` from Task 6)

Insert this block immediately before the `var sliderDraft by remember { ... }` line that Task 6 introduced:

```kotlin
                        // SMIL current page index — needed by slider (Task 6) AND inner controls
                        val smilCurrentIndex = if (positions.size > 1) {
                            remember(currentLocator, positions) {
                                locatorToPositionIndex(positions, currentLocator)
                            }
                        } else 0
```

### Step 3 — Insert mode-detection + chapter-nav callbacks (before the `// Controls` comment)

```kotlin
                        // Mode detection
                        val isSMILMode = audioTracks.isEmpty() && positions.size > 1

                        // Chapter start indices (used for SMIL outer buttons)
                        val chapterStartIndices = remember(positions) {
                            positions.mapIndexedNotNull { idx, loc ->
                                if (idx == 0 || loc.href != positions[idx - 1].href) idx else null
                            }
                        }

                        val onPrevChapter: () -> Unit = if (isSMILMode) {
                            {
                                val currentChapterStart =
                                    chapterStartIndices.lastOrNull { it <= smilCurrentIndex } ?: 0
                                val target = if (smilCurrentIndex > currentChapterStart) {
                                    currentChapterStart
                                } else {
                                    chapterStartIndices.lastOrNull { it < currentChapterStart } ?: 0
                                }
                                onNavigateToPosition(target)
                            }
                        } else controller::seekToPrev

                        val onNextChapter: () -> Unit = if (isSMILMode) {
                            {
                                val nextIdx = chapterStartIndices.firstOrNull { it > smilCurrentIndex }
                                if (nextIdx != null) onNavigateToPosition(nextIdx)
                            }
                        } else controller::seekToNext
```

### Step 4 — Replace the `// Controls` Row

Find the old 3-button `// Controls` Row (lines ~347-371) and replace it entirely with:

```kotlin
                        // Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            // Outer left: prev chapter (folder = prev track; SMIL = prev epub chapter)
                            IconButton(onClick = onPrevChapter) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter")
                            }
                            // Inner left: folder = −20 s; SMIL = prev epub position
                            if (isSMILMode) {
                                IconButton(
                                    onClick = {
                                        onNavigateToPosition((smilCurrentIndex - 1).coerceAtLeast(0))
                                    },
                                    enabled = smilCurrentIndex > 0,
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Previous page")
                                }
                            } else {
                                IconButton(onClick = { controller.seekRelative(-20.0) }) {
                                    Icon(Icons.Filled.Replay20, contentDescription = "Rewind 20 seconds")
                                }
                            }
                            // Play / Pause
                            FilledIconButton(
                                onClick = controller::togglePlayPause,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                                ),
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            // Inner right: folder = +30 s; SMIL = next epub position
                            if (isSMILMode) {
                                IconButton(
                                    onClick = {
                                        onNavigateToPosition(
                                            (smilCurrentIndex + 1).coerceAtMost(positions.size - 1)
                                        )
                                    },
                                    enabled = smilCurrentIndex < positions.size - 1,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Next page")
                                }
                            } else {
                                IconButton(onClick = { controller.seekRelative(30.0) }) {
                                    Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds")
                                }
                            }
                            // Outer right: next chapter (folder = next track; SMIL = next epub chapter)
                            IconButton(onClick = onNextChapter) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter")
                            }
                        }
```

### Commit
```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt
git commit -m "feat: 5-button controls row — ±20s/30s (folder), page nav (SMIL), chapter nav outer"
```

---

## Verification Checklist

Build and install:
```bash
./gradlew :komelia-ui:assembleAndroidDebug
adb install -r komelia-ui/build/outputs/apk/android/debug/*.apk
```

| # | Test | Expected |
|---|------|----------|
| 1 | Open any audiobook full player | Fills entire screen; content starts below status bar |
| 2 | Settings → change accent color | Play button background follows accent color |
| 3 | Tap bookmark button (full player) | When selected, visible gap between it and chapter chip |
| 4 | Open full player | Volume slider row is the same width as the seek slider |
| 5 | Folder audiobook, multi-track | Seek slider shows `0` to total book duration (e.g. `3:45:00`) |
| 6 | Folder audiobook — drag slider to different chapter | Audio seeks to that chapter |
| 7 | Folder audiobook — drag slider | Left time number updates live (second-by-second) |
| 8 | Folder audiobook, paused — drag and release | Slider stays at release position; no snap-back |
| 9 | Folder audiobook, paused — drag, release, then play | Playback starts from seek position |
| 10 | Folder audiobook — tap Replay20 (⟲20) | Rewinds 20 s; crosses track boundaries correctly |
| 11 | Folder audiobook — tap Forward30 (30⟳) | Advances 30 s; crosses track boundaries correctly |
| 12 | SMIL audiobook — inner-left button (− icon) | Goes to previous epub position; disabled at start |
| 13 | SMIL audiobook — inner-right button (+ icon) | Goes to next epub position; disabled at end |
| 14 | SMIL audiobook — SkipPrevious / SkipNext | Jumps to previous/next epub chapter (different href), not individual clip |
| 15 | SMIL audiobook — seek slider | No +/− flanking buttons; full width matching folder slider |
