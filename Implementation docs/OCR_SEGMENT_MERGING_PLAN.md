# OCR Segment Merging Plan

## Objective
Improve the OCR user experience by reducing the fragmentation of text segments. Currently, a single continuous block of text may be mistakenly split into two or more separate bounding boxes (`blockRect`). This plan introduces an algorithm to detect and merge bounding boxes that logically belong to the same text flow, using a dynamic threshold based on the median size of the words within those boxes.

## Definitions
*   **Segment Box (`blockRect`)**: The large bounding box that wraps around a whole block or paragraph of text. This is used to draw the UI border around a text block.
*   **Word Box (`imageRect`)**: The smaller bounding box that wraps around a single, individual word. This is used for highlighting text selections.

*Note: In the code, `OcrElementBox` represents a Word Box but also contains the coordinates of its parent Segment Box (`blockRect`).*

## The Merging Algorithm

### 1. Input
The algorithm takes a list of recognized `OcrElementBox` objects for a given image.

### 2. Grouping by Segment
First, group the `OcrElementBox` items by their parent `blockRect`. Each distinct `blockRect` represents a "Segment" containing one or more Word Boxes.

### 3. Iterative Merging Loop
The merging process runs in a continuous loop because merging two segments might make the new, larger segment close enough to merge with a third segment.
*   Initialize a flag: `hasMerged = true`.
*   `while (hasMerged)`:
    *   Set `hasMerged = false`.
    *   Compare every possible pair of Segments (Segment A and Segment B) in the current list.

### 4. Touch or Overlap Check
For a given pair (Segment A, Segment B), calculate the physical gap between their `blockRect`s:
*   `horizontalGap = max(0, max(A.left, B.left) - min(A.right, B.right))`
*   `verticalGap = max(0, max(A.top, B.top) - min(A.bottom, B.bottom))`

If **both** `horizontalGap` and `verticalGap` are `0`, the segments are touching or overlapping.
*   **Action:** Proceed immediately to **Step 7 (Execute the Merge)** without calculating any word sizes.

### 5. Calculate the Distance Threshold (Median Word Size)
If the segments are not touching, determine if they are close enough by finding the typical word size within them.
*   Gather all Word Boxes (`imageRect`) from **both** Segment A and Segment B.
*   For each Word Box, calculate its "shortest side": `min(width, height)`.
    *   *Rationale: For horizontal text (e.g., English), the shortest side is the height. For vertical text (e.g., Japanese), the shortest side is the width. This dynamically finds the thickness of a single character/word.*
*   Sort the list of shortest side values from all words in both segments.
*   Find the **Median** value.
*   This median value becomes the **Distance Threshold**. Using the median ensures that tiny punctuation marks (like periods or commas) or unusually large letters do not skew the threshold.

### 6. Distance Check
Check if the gap between the segments is smaller than the size of a typical word.
*   If `horizontalGap <= Distance Threshold` AND `verticalGap <= Distance Threshold`:
    *   **Action:** Proceed to **Step 7 (Execute the Merge)**.
*   Otherwise, the segments remain separate. Check the next pair.

### 7. Execute the Merge
When Segment A and Segment B are deemed close enough (or are touching):
*   Calculate a new, unified `blockRect` that encompasses both segments:
    *   `newLeft = min(A.left, B.left)`
    *   `newTop = min(A.top, B.top)`
    *   `newRight = max(A.right, B.right)`
    *   `newBottom = max(A.bottom, B.bottom)`
*   For every `OcrElementBox` that belonged to Segment A and Segment B, update its `blockRect` property to be this `new` unified `blockRect`.
*   Combine the two segments into one in our tracking list.
*   Set `hasMerged = true`.
*   Break out of the current pairwise comparison loop and restart the main `while` loop (Step 3) with the updated list of segments.

### 8. Termination
The algorithm terminates when the `while` loop completes an entire pass over all pairs without finding any segments to merge (`hasMerged` remains `false`).
The algorithm then returns the refined, final list of `OcrElementBox` objects to be used by the UI.

## Technical Implementation Details

### Files to Modify / Create

1.  **`komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/OcrMergeUtils.kt` (New File)**
    *   **Additions**: We will create a new utility file to house the merging algorithm cleanly.
    *   **Functions**: 
        *   `fun mergeOcrBoxes(boxes: List<OcrElementBox>): List<OcrElementBox>`: The main entry point that implements the algorithm described above.
        *   Helper functions for gap calculation and median shortest-side computation to keep the code clean.

2.  **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`**
    *   **Modifications**: Locate the `scanCurrentPageForText` method.
    *   **Changes**: Wrap the result of the `ocrService.recognizeText` call with our new `mergeOcrBoxes` utility.
    *   **Before**:
        ```kotlin
        ocrResults.value = ocrService.recognizeText(image, ocrSettings.value.selectedLanguage)
        ```
    *   **After**:
        ```kotlin
        val rawBoxes = ocrService.recognizeText(image, ocrSettings.value.selectedLanguage)
        ocrResults.value = mergeOcrBoxes(rawBoxes)
        ```

This design keeps the core OCR parsing untouched and simply introduces a clean, reusable post-processing step exactly where the state manages the parsed results.
