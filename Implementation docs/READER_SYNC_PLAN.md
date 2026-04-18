# Implementation Plan: Cross-Device Bookmark & Annotation Sync (The KoboSpan Hack)

This document outlines the strategy to enable cross-device synchronization of bookmarks and annotations in Komelia by repurposing existing Komga API fields.

## 1. Objective
- Synchronize EPUB3 bookmarks and annotations across all Komelia instances.
- Synchronize Audiobook bookmarks (for books with embedded audio folders) across all instances.
- Synchronize Comic (Image) reader annotations across all instances.
- Achieve this without server-side changes to Komga.

## 2. The Strategy: "KoboSpan Hijacking"
The Komga server supports a "Kobo Sync" protocol which uses high-precision locators. These locators include a field called `koboSpan` (a string). 
- **Server Behavior:** The Komga server stores the entire `R2Locator` object (including `koboSpan`) as a JSON blob in its `READ_PROGRESS` table.
- **Database Type:** The column is a `TEXT` type in SQLite, allowing for large string values.
- **The Hack:** We will serialize all local bookmarks and annotations into a JSON "Sync Blob" and store it inside the `koboSpan` field. When the progression is synced to the server, the entire list of bookmarks/annotations goes with it.

## 3. Data Structure & Capacity Optimization
To maximize the number of items we can store within a safe 32KB–64KB limit, we will use a compact DTO for the sync blob.

**Compact Sync DTOs:**
```kotlin
@Serializable
data class SyncBlob(
    @SerialName("v") val version: Int = 1,
    @SerialName("b") val bookmarks: List<CompactBookmark> = emptyList(),
    @SerialName("a") val annotations: List<CompactAnnotation> = emptyList(),
    @SerialName("au") val audioBookmarks: List<CompactAudioBookmark> = emptyList()
)

@Serializable
data class CompactBookmark(
    @SerialName("i") val id: String,
    @SerialName("l") val locatorJson: String,
    @SerialName("c") val createdAt: Long
)

@Serializable
data class CompactAudioBookmark(
    @SerialName("i") val id: String,
    @SerialName("t") val track: Int,
    @SerialName("p") val pos: Double,
    @SerialName("c") val createdAt: Long
)

@Serializable
data class CompactAnnotation(
    @SerialName("i") val id: String,
    @SerialName("t") val type: Int, // 0 for EPUB, 1 for Comic
    @SerialName("l") val loc: String, // locatorJson for EPUB, or "page,x,y" for Comic
    @SerialName("h") val color: Int?,
    @SerialName("n") val note: String?,
    @SerialName("c") val createdAt: Long
)
```
- **Optimization:** By using single-character keys and removing the `bookId` (which is already known from the API context), we can fit approximately **50–100 items** in a 32KB blob.

## 4. Implementation Steps

### Phase 1: Core Synchronization Service
1. **Location:** Create `komelia-domain/core/src/commonMain/kotlin/snd/komelia/sync/ReaderSyncService.kt`.
2. **Merging Logic:** Implement a robust merge strategy:
    - **ID-Based Union:** Use the unique IDs of bookmarks and annotations.
    - **Conflict Resolution:** If the same ID exists both locally and in the remote blob, compare `createdAt` timestamps and keep the newest one.

### Phase 2: Caching & Interception Strategy
To avoid redundant processing and heavy network payloads, we implement a **Sync Cache**:

1. **The Cache:** Each `ReaderState` (EPUB and Image) will maintain a `currentSyncBlob: String?` in memory.
2. **Initial Load:**
    - Call `bookApi.getReadiumProgression()`.
    - `currentSyncBlob = progression.locator.koboSpan`.
    - Deserialize, merge (Bookmarks, Annotations, AND Audio Bookmarks), and update local database/UI.
3. **Triggering Cache Update & Push:**
    - Any change to local data MUST call a shared `updateCacheAndPush()` helper.
    - **Triggers in EPUB Reader:** `addBookmark`, `deleteBookmark`, `saveAnnotation`, `deleteAnnotation`, `addAudioBookmark`, `deleteAudioBookmark`.
    - **Triggers in Comic Reader:** `saveComicAnnotation`, `updateComicAnnotation`, `deleteComicAnnotation`.
4. **On Standard Progress Save (Page Turns):**
    - When saving page progress, always include the `currentSyncBlob` from memory in the `koboSpan` field.
    - This ensures that regular progress updates **preserve** the synchronized data instead of wiping it out.

### Phase 3: Real-time Concurrency
To handle cases where two devices have the same book open:
1. **Listen to Events:** Subscribe to `KomgaEvent.ReadProgressChanged` via the `ManagedKomgaEvents` (available in the project).
2. **On Event:** If the event `bookId` matches the current book and the event was triggered by *another* device:
    - Re-fetch `getReadiumProgression`.
    - If the `koboSpan` has changed, perform a new merge and update the UI.

### Phase 4: EPUB Reader Integration
1. **File:** `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt`.
2. **Implementation:** Update the persistence logic to use the cache.

### Phase 5: Comic (Image) Reader Integration
1. **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`.
2. **Switch Sync Method:** Change from `markReadProgress(page)` to `updateReadiumProgression()`.
3. **Implementation:** Use a dummy locator: `R2Locator(href = "p$page", ..., koboSpan = currentSyncBlob)`.

### Phase 6: Dependency Injection
1. **File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/CoreModule.kt`.
2. Register `ReaderSyncService` and provide it to the UI layer.

## 5. Merging Algorithm Detail
```kotlin
fun merge(local: List<T>, remote: List<T>): List<T> {
    val all = (local + remote).groupBy { it.id }
    return all.map { (id, items) ->
        items.maxBy { it.createdAt }
    }
}
```

## 6. Safety & Limits
- **Size Limit:** While SQLite supports large text, we should keep the blob under **32KB** to avoid network/memory issues on low-end devices.
- **Base64:** If the JSON contains characters that might break the server's parser, we should Base64-encode the `SyncBlob` string before putting it into `koboSpan`.
