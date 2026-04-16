# Implementation Guide: Open Local Android Files (CBZ/EPUB) via Intent

This document is a step-by-step implementation guide to add support for opening local `.cbz` and `.epub` files on Android using the "Open with" intent. It uses a "Virtual Server Adapter" approach to feed local file data into the existing `KomgaBookApi` dependent Reader components.

## Objective
Intercept Android `ACTION_VIEW` intents for local book files, wrap the file URI in a virtual `KomgaBookApi`, and navigate directly to the Reader screen.

---

### Step 1: Update AndroidManifest.xml
**File:** `komelia-app/src/androidMain/AndroidManifest.xml`

Update the `MainActivity` declaration to include `intent-filter`s for `.cbz` and `.epub` files.

1. Locate the `<activity android:name="snd.komelia.MainActivity" ...>` tag.
2. Add the following `<intent-filter>` below the existing `MAIN` intent filter:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="file" />
    <data android:scheme="content" />
    <data android:host="*" />
    <!-- CBZ/ZIP extensions and mime types -->
    <data android:mimeType="application/zip" />
    <data android:mimeType="application/x-cbz" />
    <data android:mimeType="application/x-zip-compressed" />
    <data android:pathPattern=".*\\.cbz" />
    <data android:pathPattern=".*\\.zip" />
    <!-- EPUB extensions and mime types -->
    <data android:mimeType="application/epub+zip" />
    <data android:pathPattern=".*\\.epub" />
</intent-filter>
```

---

### Step 2: Handle Intents in MainActivity
**File:** `komelia-app/src/androidMain/kotlin/snd/komelia/MainActivity.kt`

We need to extract the `Uri` from the intent and expose it to the Compose layer.

1. Add a globally accessible `MutableSharedFlow` to broadcast incoming URIs. Add this at the top level of the file (outside the class):
```kotlin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.content.Intent

private val _incomingFileUriFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
val incomingFileUriFlow = _incomingFileUriFlow.asSharedFlow()
```

2. Inside `MainActivity.onCreate`, after `super.onCreate(null)`, add logic to check the initial intent:
```kotlin
handleIntent(intent)
```

3. Override `onNewIntent` to handle files opened while the app is already in the background:
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
}

private fun handleIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
        intent.data?.toString()?.let { uriString ->
            _incomingFileUriFlow.tryEmit(uriString)
        }
    }
}
```

---

### Step 3: Create the Virtual `LocalFileBookApi`
**Create New File:** `komelia-domain/core/src/androidMain/kotlin/snd/komelia/api/LocalFileBookApi.kt`

Create a virtual `KomgaBookApi` implementation that reads from a local URI using Android's `ContentResolver` or `ZipExtractor`.

```kotlin
package snd.komelia.api

import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomgaBookId
import snd.komelia.komga.api.model.PageDto
// ... add necessary imports (Context, Uri, ZipExtractor, etc.) ...

class LocalFileBookApi(
    private val context: android.content.Context,
    private val fileUriString: String
) : KomgaBookApi {
    
    // Constant ID to identify the virtual book
    val virtualBookId = KomgaBookId("virtual-local-file")

    override suspend fun getOne(bookId: KomgaBookId): KomeliaBook {
        require(bookId == virtualBookId) { "Virtual API only supports virtualBookId" }
        val isEpub = fileUriString.endsWith(".epub", ignoreCase = true) || fileUriString.contains("epub")
        val mediaProfile = if (isEpub) "EPUB" else "DIVINA"
        
        // Return a mock KomeliaBook constructed with the virtualBookId and determined mediaProfile
        // Fill other required fields with dummy data or derive from the file name.
        TODO("Implement mock KomeliaBook construction")
    }

    override suspend fun getPages(bookId: KomgaBookId): List<PageDto> {
        // If DIVINA/CBZ, use ZipExtractor to list entries, filter for images, and return as PageDto
        // Return empty list for EPUB.
        TODO("Implement zip parsing for CBZ")
    }

    override suspend fun getPage(bookId: KomgaBookId, pageNumber: Int): ByteArray {
        // Read the exact byte array from the Zip file entry corresponding to the pageNumber
        TODO("Implement zip entry extraction")
    }

    override suspend fun getBookLocalFilePath(bookId: KomgaBookId): String? {
        return fileUriString
    }

    override suspend fun hasLocalFile(bookId: KomgaBookId): Boolean = true
    
    // Implement other required interface methods with stub implementations 
    // throwing UnsupportedOperationException or returning empty/null as appropriate.
}
```
*(Instruct the implementing AI to fill in the `TODO` sections using `ZipExtractor` for CBZ and Android `ContentResolver` for reading the Uri).*

---

### Step 4: Inject Virtual API into Reader
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/ViewModelFactory.kt`

We need a way to intercept the creation of Reader view models and provide our `LocalFileBookApi` instead of the remote API.

1. Add a mutable reference to the virtual API in `ViewModelFactory` (or manage it via `DependencyContainer` if preferred):
```kotlin
// Add property to ViewModelFactory
var activeLocalFileBookApi: KomgaBookApi? = null
```

2. Update `getBookReaderViewModel` and `getEpubReaderViewModel`. Before injecting `bookApi` or `bookClient`, check if the `bookId` matches the virtual ID. If so, use `activeLocalFileBookApi`.

```kotlin
fun getBookReaderViewModel(bookId: KomgaBookId, ...): ReaderViewModel {
    val apiToUse = if (bookId.value == "virtual-local-file") {
        activeLocalFileBookApi ?: error("Local API not initialized")
    } else {
        bookApi
    }
    
    // Pass `apiToUse` to ReaderViewModel constructor and BookImageLoader
    // ...
}
```
*(Instruct the implementing AI to carefully trace and replace `bookApi` usages inside the factory methods for the virtual book).*

---

### Step 5: Navigation Integration
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/MainView.kt`

Collect the intent URI flow and push the Reader screen.

1. Ensure you have access to the context (if on Android) to initialize the `LocalFileBookApi`.
2. Inside `MainView` (or where the root `Navigator` is defined):

```kotlin
// In Android MainView or MainScreen:
LaunchedEffect(Unit) {
    // Assuming incomingFileUriFlow is accessible here (e.g. via expect/actual or direct import in androidMain)
    snd.komelia.incomingFileUriFlow.collect { uriString ->
        // 1. Initialize LocalFileBookApi
        // val localApi = LocalFileBookApi(context, uriString)
        
        // 2. Set it in the ViewModelFactory
        // dependencies?.viewModelFactory?.activeLocalFileBookApi = localApi
        
        // 3. Push Reader Screen
        // navigator.push(readerScreen(KomgaBookId("virtual-local-file")))
    }
}
```
*(Instruct the implementing AI to adjust this step based on the exact platform-specific dependency injection wiring in `MainView.kt` and `AndroidAppModule.kt`)*.

---

## Verification
The implementing AI should confirm:
1. Opening a `.cbz` from a file manager launches Komelia and loads the images directly from the local zip.
2. Opening an `.epub` launches Komelia and the internal Epub reader parses the local URI.
3. The rest of the app (Komga remote connections) remains completely unaffected.