# OCR Text Selection Add Note Feature Plan

## Objective
Enhance the OCR text selection functionality by adding an "Add Note" option to the text selection context menu. Tapping this option will open the new note dialog, pinned to the top-left coordinate of the selected text segment, and pre-populated with the selected text.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationDialog.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/TextSelectionOverlay.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderImageContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/continuous/ContinuousReaderContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/panels/PanelsReaderContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`

## Implementation Steps

### 1. Update `ReaderState`
- Add `val pendingAnnotationNote = MutableStateFlow<String?>(null)` to `ReaderState` to store the pre-populated text before the note is saved.
- Update `saveComicAnnotation` to take an optional `note: String?` that overrides or is passed along with `pendingAnnotationNote`.

### 2. Update `AnnotationDialog`
- In `AnnotationDialog.kt`, add an `initialNote: String? = null` parameter.
- Initialize the `note` mutable state using `existingAnnotation?.note ?: initialNote ?: ""`.

### 3. Update Text Selection Components
- Add `onAddNote: (text: String, x: Float, y: Float) -> Unit` to `TextSelectionOverlay` and `ReaderImageContent`.
- In `TextSelectionOverlay.kt`, calculate normalized `x` and `y` coordinates using the bounding box of the selected text `currentOcrResults.firstOrNull { it.selected }?.imageRect` and the `intrinsicImageSize`.
- Add a new `DropdownMenuItem` for "Add Note" in the `AnimatedDropdownMenu`. When clicked, call `onAddNote(text, x, y)` and close the menu.

### 4. Propagate Callbacks in Layouts
- In `PagedReaderContent.kt`, `ContinuousReaderContent.kt`, and `PanelsReaderContent.kt`, pass the `onAddNote` callback down to `ReaderImageContent`.
- In these layouts, implement the `onAddNote` callback to set the appropriate `pendingAnnotationPage`, `pendingAnnotationX`, `pendingAnnotationY`, and the new `pendingAnnotationNote` in `ReaderState`, and then set `showAnnotationDialog.value = true`.

### 5. Update `ReaderContent`
- In `ReaderContent.kt`, pass the `pendingAnnotationNote.value` to the `initialNote` parameter of the `AnnotationDialog` when it is instantiated for comic annotations.
- Clear `pendingAnnotationNote` in `ReaderState` upon successful save or cancellation (when `onDismiss` or `onDelete` is triggered).

## Verification & Testing
- **Dialog Pre-population:** Select text in the reader, choose "Add Note," and verify that the `AnnotationDialog` opens with the selected text in the input field.
- **Pin Location:** Verify that the x/y coordinates of the annotation accurately match the top-left position of the selected text block.
- **Save Functionality:** Ensure that clicking "Save" on the annotation dialog properly persists the annotation and closes the dialog.
- **Cancel/Dismiss:** Ensure that dismissing the dialog clears the pending state and does not create an unintended annotation.
- **Fallback Verification:** Verify that the regular "Copy" and "Translate" options from the OCR context menu are unaffected.