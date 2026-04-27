# Cache Management Implementation Plan

This plan details the steps required to implement a separated, user-configurable cache management system for the Image Reader and the EPUB3 Reader.

## 1. Objective
Provide the user with granular control over the maximum disk space used by the application for caching comic/manga pages (Image Reader) and extracted EPUBs/Audiobooks.

### Cache Strategies:
- **Image Reader Cache (`komelia_reader_cache`)**: 
  - Range: 500 MB to 5 GB
  - Default: 1 GB
  - Management: Coil's `DiskCache`
- **EPUB3 Cache (`epub3` directory)**: 
  - Range: 1 GB to 10 GB
  - Default: 2 GB
  - Management: Custom LRU (Least Recently Used) directory trimmer.
- **Coil UI Cache (`coil3_disk_cache`)**: 
  - Remains unchanged at 64 MB.

---

## 2. Database & Repository Updates

### 2.1 Image Reader Settings
**File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/ImageReaderSettings.kt`
- Add `imageCacheSizeLimitMb: Long = 1024L` (1 GB) to the `ImageReaderSettings` data class.

**Files:** `ImageReaderSettingsRepository.kt`, `ReaderSettingsRepositoryWrapper.kt`, `ExposedImageReaderSettingsRepository.kt`
- Add methods to get and put the `imageCacheSizeLimitMb` setting as a `Flow<Long>` and `suspend fun putImageCacheSizeLimitMb(size: Long)`.

### 2.2 EPUB Reader Settings
**File:** `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/EpubReaderSettings.kt`
- Add `epubCacheSizeLimitMb: Long = 2048L` (2 GB) to the `EpubReaderSettings` data class.

**Files:** `EpubReaderSettingsRepository.kt`, `EpubReaderSettingsRepositoryWrapper.kt`, `ExposedEpubReaderSettingsRepository.kt`
- Add methods to get and put the `epubCacheSizeLimitMb` setting as a `Flow<Long>` and `suspend fun putEpubCacheSizeLimitMb(size: Long)`.

---

## 3. Implementation Details

### 3.1 Image Reader Cache Initialization
**File:** `komelia-app/src/commonMain/kotlin/snd/komelia/AppModule.kt` (and platform-specific modules)
- Update `createReaderImageLoader` to read the `imageCacheSizeLimitMb` setting synchronously during initialization (or via `runBlocking` / `first()`).
- Pass the limit to the `DiskCache.Builder().maxSizeBytes(limitInBytes)` when creating the `diskCache` for the `BookImageLoader`.
- *Note:* Since `DiskCache` size is set at creation, changing the setting will require an app restart to take effect on the cache limit, or the UI should indicate this.

### 3.2 EPUB3 LRU Cache Trimmer
**File:** `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt` (or a dedicated `EpubCacheManager` helper)
- Create a `trimEpubCache(limitBytes: Long, cacheDir: File)` function.
- **Logic:**
  1. Calculate the total size of `context.cacheDir/epub3/`.
  2. If `totalSize > limitBytes`, list all subdirectories (book UUIDs).
  3. Sort subdirectories by their `lastModified` timestamp in ascending order (oldest first).
  4. Iterate and `deleteRecursively()` the oldest directories until the calculated `totalSize` drops below `limitBytes`.
- Call this `trimEpubCache` method inside `prepareEpubDirectory()` right before or after extracting a new book.

---

## 4. UI Updates

### 4.1 Image Reader Settings Screen
**Files:** `ImageReaderSettingsViewModel.kt`, `ImageReaderSettingsContent.kt`
- **ViewModel:** Add state flows for `imageCacheSizeLimitMb` and a method to update the repository.
- **Content UI:** 
  - Add a slider ranging from 500 MB to 5000 MB (5 GB) with step intervals (e.g., 100 MB or 500 MB steps).
  - Place this slider near the existing "Clear image cache" button.
  - Display the currently selected size clearly (e.g., "Max Image Cache Size: 1.5 GB").

### 4.2 EPUB Reader Settings Screen
**Files:** `EpubReaderSettingsViewModel.kt`, `EpubReaderSettingsScreen.kt` (and `EpubReaderSettingsContent.kt` if applicable)
- **ViewModel:** 
  - Add state flows for `epubCacheSizeLimitMb` and a method to update the repository.
  - Implement a `clearEpubCache()` method that deletes the `context.cacheDir/epub3` directory (you may need to pass a callback from `ViewModelFactory` similar to the Image Reader cache clearing).
- **Content UI:**
  - Add a condition: `if (selectedEpubReader == EpubReaderType.EPUB3_READER) { ... }`
  - Inside the condition, add a "Clear EPUB cache" button.
  - Below the button, add a slider ranging from 1000 MB (1 GB) to 10000 MB (10 GB).
  - Display the currently selected size (e.g., "Max EPUB Cache Size: 2 GB").

---

## 5. Verification Steps
1. Verify database migrations or serialization handles the new fields correctly.
2. Ensure the Image Reader `DiskCache` respects the new limit when downloading pages.
3. Test opening multiple large EPUBs; verify that the `epub3` directory is trimmed according to LRU and the configured limit.
4. Verify the "Clear EPUB cache" button successfully deletes all cached EPUBs.
5. Confirm that changing the EPUB reader type in settings hides the EPUB cache controls when EPUB3 is not selected.