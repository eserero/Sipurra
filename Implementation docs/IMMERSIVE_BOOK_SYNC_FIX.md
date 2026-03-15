# Implementation Plan: Immersive Book Synchronization Fix

This document outlines the root cause and resolution plan for the issue where closing the reader returns the user to the original book detail screen instead of the book they were last reading.

## 1. Root Cause Analysis (RCA)

When a user opens a book from the "Immersive Book Window" (`BookScreen`), the following occurs:

1.  **Navigation Stack:** The `ReaderScreen` (either `ImageReaderScreen` or `EpubScreen`) is pushed onto the navigation stack. The `BookScreen` instance for the original book (e.g., Book 32) remains in the backstack.
2.  **Internal State Divergence:** Both `ImageReaderScreen` and `EpubScreen` support internal navigation to "Next" and "Previous" books. This updates the reader's internal `currentBook` state but does not affect the `BookScreen` sitting in the backstack.
3.  **Simple Pop on Exit:** When the user closes the reader, the exit logic executes `navigator.pop()`. This simply removes the reader and reveals the `BookScreen` exactly as it was when it was pushed—still displaying Book 32.
4.  **No Feedback Loop:** There is currently no mechanism for the reader to notify the initiating screen that the "active" book has changed during the session.

## 2. Proposed Resolution

The resolution involves adding an `onExit` callback to the reader screens. This callback will allow the initiating screen to synchronize its own state with the reader's final state before the reader is dismissed.

### Architectural Changes

1.  **Reader Callback:** Update `readerScreen` and its implementations to accept an `onExit` lambda that provides the `KomeliaBook` instance being read at the time of exit.
2.  **ViewModel Synchronization:** The `BookScreen` will provide an `onExit` implementation that updates its `BookViewModel` with the new book data.

---

## 3. Implementation Details

### Phase 1: Reader API Updates

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/ImageReaderScreen.kt`
- Update the `readerScreen` factory function to include `onExit: (KomeliaBook) -> Unit = {}`.
- Update the `ImageReaderScreen` class to accept this `onExit` callback.
- In `ImageReaderScreen.onExit(navigator, book)`, if `navigator.canPop` is true, call `onExit(book)` before calling `navigator.pop()`.

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/EpubScreen.kt`
- Add `onExit: (KomeliaBook) -> Unit = {}` to the `EpubScreen` constructor.
- Pass this callback into the `EpubReaderViewModel` via the `ViewModelFactory`.

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubReaderState.kt`
- Add `val onExit: (KomeliaBook) -> Unit` to the `EpubReaderState` interface.
- Implement this in `KomgaEpubReaderState` and `TtsuReaderState`.
- In `closeWebview()`, call `onExit(currentBook)` before popping the navigator.

### Phase 2: Screen Integration

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/book/BookScreen.kt`
- In the `Content()` function, locate the reader launch points:
    - **Immersive UI:** Inside `onReadBook`.
    - **Standard UI:** Inside `onBookReadPress`.
- Update the `readerScreen()` call to include:
  ```kotlin
  onExit = { lastReadBook ->
      if (lastReadBook.id != book.id) {
          vm.setCurrentBook(lastReadBook)
      }
  }
  ```
- *Note:* Since `BookViewModel.setCurrentBook()` already exists and updates the state flows, the UI will automatically refresh to the new book when the reader is popped.

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/oneshot/OneshotScreen.kt`
- Similar to `BookScreen`, update `onBookReadClick` and `OneshotScreenContent`'s read click handler.
- If the `lastReadBook` has a different `seriesId` (possible in Read Lists), the callback should handle a screen replacement:
  ```kotlin
  onExit = { lastReadBook ->
      if (lastReadBook.id != vm.book.value?.id) {
          // If series changed, we might need to replace the whole screen context
          navigator.replace(bookScreen(lastReadBook, bookSiblingsContext))
      }
  }
  ```

#### File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/SeriesScreen.kt`
- Update `onBookReadClick` to provide an `onExit` callback.
- If the user read a different book, the `SeriesScreen` could potentially scroll to the last read book in the list to improve the user experience.

---

## 4. Verification Plan

1.  **Reproduction Test:**
    - Open Book 32 in a series from the Immersive UI.
    - Read until you transition to Book 33.
    - Close the reader using the "Exit" button in the title bar or the back gesture.
    - **Expected Result:** The screen should now display the details for Book 33.
2.  **Multi-Book Transition:**
    - Open Book 32, read through 33 and 34.
    - Close the reader.
    - **Expected Result:** The screen should display Book 34.
3.  **Read List Transition:**
    - Open a book from a Read List.
    - Navigate to the next book in the Read List (which belongs to a different series).
    - Close the reader.
    - **Expected Result:** The UI should correctly transition to the new book's detail screen, even if the series context has changed.
4.  **Epub Check:**
    - Repeat the tests above with Epub books to ensure `KomgaEpubReaderState` and `TtsuReaderState` correctly propagate the exit event.
