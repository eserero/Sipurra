# Plan: Local-First Reader + Server-Unavailable Dialog

## Context

When opening a book in the image reader, `ReaderState.initialize()` always fetches page metadata and images from the remote server — even when the book is fully downloaded locally. This causes:
- **Performance penalty**: fetching page images over the network when a local copy exists
- **Failure at startup**: a 30-second `ConnectTimeoutException` hang if the server is unreachable, showing a raw technical error with no path to recovery

The fix has two parts:
1. Make `BookImageLoader` use the local file when the book is downloaded (online or offline mode)
2. Show a "server unavailable" dialog offering to switch to offline mode when metadata calls fail with network errors

---

## Feature 1: Local-First Page Image Loading

**Principle:** If a book is in the local offline store, serve its page images from disk — no network call.

### `BookImageLoader`
**File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/BookImageLoader.kt`

Add two nullable constructor params (nullable = safe for non-Android/test callsites):
```kotlin
class BookImageLoader(
    private val bookClient: StateFlow<KomgaBookApi>,
    private val imageDecoder: KomeliaImageDecoder,
    private val readerImageFactory: ReaderImageFactory,
    val diskCache: DiskCache?,
    private val offlineBookRepository: OfflineBookRepository? = null,  // NEW
    private val offlineBookApi: KomgaBookApi? = null,                  // NEW
)
```

Add a `fetchPage()` private helper that replaces both `bookClient.value.getPage()` callsites in `doLoad()`:
```kotlin
private suspend fun fetchPage(bookId: KomgaBookId, page: Int): ByteArray {
    if (offlineBookRepository?.find(bookId) != null && offlineBookApi != null) {
        return try {
            offlineBookApi.getPage(bookId, page)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.warn(e) { "Local page read failed for $bookId page $page, falling back to network" }
            bookClient.value.getPage(bookId, page)
        }
    }
    return bookClient.value.getPage(bookId, page)
}
```

Replace lines 59 and 69 (`bookClient.value.getPage(bookId, page)`) with `fetchPage(bookId, page)`.

Add import: `import snd.komelia.offline.book.repository.OfflineBookRepository`
(`komelia-domain/core` already depends on `komelia-domain/offline` — `RemoteBookApi` uses the same import.)

### `AppModule`
**File:** `komelia-app/src/commonMain/kotlin/snd/komelia/AppModule.kt`

Update `createReaderImageLoader()` to accept and pass the offline deps:
```kotlin
protected fun createReaderImageLoader(
    bookApi: StateFlow<KomgaBookApi>,
    imageFactory: ReaderImageFactory,
    imageDecoder: KomeliaImageDecoder,
    offlineBookRepository: OfflineBookRepository,   // NEW
    offlineBookApi: KomgaBookApi,                   // NEW
): BookImageLoader
```

At the callsite (currently lines ~223-227):
```kotlin
bookImageLoader = createReaderImageLoader(
    bookApi = komgaNoRemoteCacheApi.map { it.bookApi }.stateIn(initScope),
    imageFactory = readerImageFactory,
    imageDecoder = createImageDecoder(),
    offlineBookRepository = offlineRepositories.bookRepository,     // NEW
    offlineBookApi = offlineModule.komgaApi.bookApi,                // NEW
)
```

Both `offlineRepositories` and `offlineModule` are already available in `initDependencies()`.

---

## Feature 2: Server-Unavailable Dialog in Image Reader

**Principle:** Distinguish network errors (server unreachable) from HTTP errors (404, 401). On network error, show a dialog: **"Server not available — Retry | Go Offline | Cancel"**.

### `ReaderState`
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`

`import io.ktor.client.plugins.*` is already present (line 6), covering `HttpRequestTimeoutException` and `ResponseException`.

Add one new state field after the existing `state` flow:
```kotlin
val serverUnavailableDialogVisible = MutableStateFlow(false)
```

Add a private helper (file-level or member, after the class):
```kotlin
private fun Throwable.isNetworkError(): Boolean =
    this is java.io.IOException || this is HttpRequestTimeoutException
```
(`ConnectTimeoutException` extends `IOException`; `ResponseException` does not — so this safely distinguishes network failures from HTTP errors.)

Modify the `.onFailure` in `initialize()` (currently line 168):
```kotlin
.onFailure { throwable ->
    state.value = LoadState.Error(throwable)
    if (throwable.isNetworkError()) serverUnavailableDialogVisible.value = true
}
```

Add a dismiss helper (called by both Retry and Cancel buttons):
```kotlin
fun dismissServerUnavailableDialog() {
    serverUnavailableDialogVisible.value = false
}
```

No other changes to `ReaderState`, `ReaderViewModel`, `ViewModelFactory`, or `DependencyContainer` are needed — the dialog state is exposed on the existing `vm.readerState` reference.

### `ImageReaderScreen`
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/ImageReaderScreen.kt`

Inside `Content()`, after collecting `vmState` (currently line 122), add:
```kotlin
val serverUnavailableDialogVisible by vm.readerState.serverUnavailableDialogVisible
    .collectAsState(Dispatchers.Main.immediate)
```

After the closing `}` of the `Column { ... }` block (line 148), add:
```kotlin
if (serverUnavailableDialogVisible) {
    ServerUnavailableDialog(
        onDismiss = { vm.readerState.dismissServerUnavailableDialog() },
        onRetry = {
            vm.readerState.dismissServerUnavailableDialog()
            coroutineScope.launch { vm.initialize(bookId) }
        },
        onGoOffline = {
            vm.readerState.dismissServerUnavailableDialog()
            val rootNavigator = navigator.parent ?: navigator
            rootNavigator.replaceAll(LoginScreen())
        }
    )
}
```

Add a private composable in the same file:
```kotlin
@Composable
private fun ServerUnavailableDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onGoOffline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Unavailable") },
        text = { Text("Could not connect to the server. Would you like to switch to offline mode?") },
        confirmButton = {
            TextButton(onClick = onGoOffline) { Text("Go Offline") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    )
}
```

New imports needed in `ImageReaderScreen.kt`:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import snd.komelia.ui.login.LoginScreen
```

`AlertDialog` renders as a window overlay — no `Box` wrapper needed around the `Column`.

**Navigation note:** `navigator.parent` is the root navigator, matching the pattern in `MainScreenViewModel.goOnline()`. `replaceAll(LoginScreen())` takes the user to the login screen where they can pick their offline profile via `OfflineLoginScreen`. This reuses the existing offline login flow without needing to inject `OfflineSettingsRepository` into the reader chain.

---

## Files Changed Summary

| File | Change |
|---|---|
| `komelia-domain/core/.../image/BookImageLoader.kt` | Add `offlineBookRepository` + `offlineBookApi` params; add `fetchPage()` helper; replace 2 `getPage` callsites |
| `komelia-app/.../AppModule.kt` | Add 2 params to `createReaderImageLoader()`; wire `offlineRepositories.bookRepository` and `offlineModule.komgaApi.bookApi` |
| `komelia-ui/.../reader/image/ReaderState.kt` | Add `serverUnavailableDialogVisible`; add `isNetworkError()` helper; modify `onFailure`; add `dismissServerUnavailableDialog()` |
| `komelia-ui/.../reader/ImageReaderScreen.kt` | Collect dialog state; add `ServerUnavailableDialog` composable; wire retry/go-offline/cancel |

No changes to `ReaderViewModel`, `ViewModelFactory`, or `DependencyContainer`.

---

## Verification

1. **Local-first loading**: Download a book for offline use (blue pin icon). Turn off WiFi or stop the server. Open the book in the reader — pages should load immediately from disk with no network error.

2. **Out-of-sync book (red pin)**: Open a book with a red pin — it should still read from the local file. The user must manually re-download to get the updated version.

3. **Non-downloaded book, server down**: Open a book that is NOT downloaded while the server is unreachable — after ~30s the "Server Unavailable" dialog should appear. "Retry" dismisses and re-attempts; "Go Offline" navigates to `LoginScreen`; "Cancel" dismisses and shows the error screen.

4. **HTTP errors (404, 401)**: These should NOT trigger the dialog — just the existing `ErrorContent` with reload/exit buttons.

5. **Normal online operation**: With the server available and no local copy, behavior is unchanged — images are fetched from the server as before.
