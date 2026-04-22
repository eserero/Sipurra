# AUDIO_METADATA_BUTTON_PLAN.md

## Objective
Add a button to the EPUB3 reader's full-screen media player to display comprehensive audio file metadata and chapters. To support all possible audio file types reliably, we will integrate `FFmpegMediaMetadataRetriever` to extract the tags and chapters from the first available audio file.

## Scope & Impact
- **Affected Areas**: EPUB3 Reader Audio UI (`AudioFullScreenPlayer.kt`), Audio Controllers (`AudiobookFolderController.kt`).
- **Dependencies**: Adds `com.github.wseemann:FFmpegMediaMetadataRetriever` to the Android dependencies to fulfill the requirement of supporting "all possible audio file types" and reliable chapter extraction.

## Key Files & Context
- `komelia-ui/build.gradle.kts`: To add the `FFmpegMediaMetadataRetriever` dependencies for the Android target.
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioFullScreenPlayer.kt`: To add the new metadata button below the speed controls and manage the dialog state.
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudioMetadataDialog.kt` (New File): A new Compose UI component (BottomSheet or Dialog) to display the extracted tags and chapters.
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt`: To provide the path/URI of the first audio file and potentially house the metadata extraction logic.

## Implementation Steps

### 1. Add Dependencies
Add the FFmpegMediaMetadataRetriever dependencies to `komelia-ui/build.gradle.kts` within the `androidMain.dependencies` block:
```kotlin
implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-core:1.0.19")
implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native:1.0.19")
```

### 2. Data Models & Extraction Logic
- Create data classes: `AudioMetadataInfo` (holding a map of tags like Title, Artist, Album, Format, Bitrate) and `AudioChapter` (title, start time, end time).
- In `AudiobookFolderController.kt` (or a dedicated extractor class), write a suspend function:
  - Initialize `FFmpegMediaMetadataRetriever`.
  - Pass the URI or absolute path of the *first* audio file (`tracks.firstOrNull()`).
  - Extract standard tags (METADATA_KEY_ALBUM, ARTIST, TITLE, GENRE, etc.) and add them to a map.
  - Extract chapters using the retriever's chapter parsing methods.
  - Release the retriever and return the `AudioMetadataInfo` object.

### 3. Update the Full-Screen Player UI
- In `AudioFullScreenPlayer.kt`, locate the "Speed chips" column (the `Column` at the bottom containing `speeds.chunked(3).forEach`).
- Add a new button (e.g., `OutlinedButton` or `TextButton` with an Info icon) below the speed chips, aligned to the bottom right as requested.
- Maintain a mutable state `showMetadataDialog` (Boolean). When the button is clicked, set it to true.

### 4. Create the Metadata Dialog/Sheet
- Create `AudioMetadataDialog.kt` containing a `@Composable` function.
- The dialog will accept the `AudiobookFolderController` (or the extracted `AudioMetadataInfo` state).
- When opened, it will trigger a `LaunchedEffect` to run the extraction logic on a background thread.
- Display a `CircularProgressIndicator` while loading.
- Once loaded, display a scrollable layout (`LazyColumn`) with two main sections:
  1. **Tags**: A list of key-value pairs showing all extracted metadata tags.
  2. **Chapters**: A list showing chapter titles and their start times.

## Verification & Testing
- Open an EPUB or Audiobook with a known metadata-rich file (e.g., M4B or FLAC with ID3 tags and chapters).
- Verify the new button appears at the bottom right below the speed controls.
- Click the button and ensure the dialog successfully loads and displays the comprehensive list of tags and chapters.
- Ensure `FFmpegMediaMetadataRetriever` successfully releases resources to prevent memory leaks.
- Test across different audio formats (MP3, M4A, OGG, FLAC) to ensure format compatibility requirement is met.
