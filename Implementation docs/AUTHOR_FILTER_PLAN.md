# Implementation Plan: Dialog-Based Author Filter

## 1. Goal
Improve the user experience of the author filter on mobile devices. The current implementation uses an anchored `DropdownMenu`, which is often obscured by the software keyboard or closed unexpectedly when the keyboard is dismissed. Replacing this with a centralized `AppDialog` will provide a more stable and intuitive interface.

---

## 2. Technical Overview

### New Component: `FilterDialogMultiChoiceWithSearch`
A new reusable component will be added to `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/components/DropdownChoiceMenu.kt`.

**Function Signature:**
```kotlin
@Composable
fun <T> FilterDialogMultiChoiceWithSearch(
    selectedOptions: List<LabeledEntry<T>>,
    options: List<LabeledEntry<T>>,
    onOptionSelect: (LabeledEntry<T>) -> Unit,
    onSearch: suspend (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    onClearAll: (() -> Unit)? = null,
)
```

**Behavioral Requirements:**
1.  **Anchor View (Always Visible):** 
    *   Uses the existing `InputField` component.
    *   Displays a comma-separated list of `selectedOptions.label`.
    *   Shows the `label` and a count of selected items using `FilterLabelAndCount`.
    *   Opens the selection dialog when clicked.
2.  **Selection Dialog (`AppDialog`):**
    *   **Header:** Displays the filter label (e.g., "Authors").
    *   **Search Field:** 
        *   A `NoPaddingTextField` located at the top of the dialog.
        *   Automatically requests focus when the dialog opens.
        *   Triggers the `onSearch` callback with a debounce (as currently implemented).
    *   **Selected Items:**
        *   Displays `selectedOptions` as removable chips (using `NoPaddingChip` with a close icon).
    *   **Search Results:**
        *   A scrollable list of `options`.
        *   Each item uses `DropdownMultiChoiceItem` (checkbox-style selection).
    *   **Control Buttons:**
        *   A "Close" button to dismiss the dialog.
        *   If `onClearAll` is provided, a "Clear All" button to reset the selection.

---

## 3. Implementation Details

### Step 1: Update `DropdownChoiceMenu.kt`
*   Add `FilterDialogMultiChoiceWithSearch` to `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/components/DropdownChoiceMenu.kt`.
*   Ensure it uses `AppDialog` from `snd.komelia.ui.dialogs.AppDialog`.

**Implementation Reference (Skeleton):**
```kotlin
@Composable
fun <T> FilterDialogMultiChoiceWithSearch(...) {
    var isDialogOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Anchor
    InputField(
        content = { Text(selectedOptions.joinToString { it.label }.ifBlank { placeholder ?: "Any" }, maxLines = 1) },
        modifier = modifier.clickable { isDialogOpen = true },
        label = label?.let { { FilterLabelAndCount(label, selectedOptions.size) } },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, // Or similar
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = PaddingValues(5.dp)
    )

    if (isDialogOpen) {
        AppDialog(
            onDismissRequest = { isDialogOpen = false },
            header = { DialogSimpleHeader(label ?: "") },
            controlButtons = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onClearAll != null) {
                        TextButton(onClick = onClearAll) { Text("Clear All") }
                    }
                    TextButton(onClick = { isDialogOpen = false }) { Text("Close") }
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Search Field
                NoPaddingTextField(
                    text = searchText,
                    onTextChange = { searchText = it },
                    modifier = Modifier.focusRequester(focusRequester).fillMaxWidth(),
                    placeholder = { Text("Search...") }
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                // Selected Chips
                FlowRow(...) {
                    selectedOptions.forEach { option ->
                        NoPaddingChip(onClick = { onOptionSelect(option) }) {
                             Icon(Icons.Default.Close, null)
                             Text(option.label)
                        }
                    }
                }

                HorizontalDivider()

                // Results
                options.forEach { option ->
                    DropdownMultiChoiceItem(
                        option = option,
                        onOptionSelect = { onOptionSelect(it) },
                        selected = selectedOptions.contains(option)
                    )
                }
            }
        }
    }
}
```

### Step 2: Refactor `SeriesFilterContent.kt`
*   File: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/view/SeriesFilterContent.kt`
*   Locate the "Authors" filter section (around line 170).
*   Replace `FilterDropdownMultiChoiceWithSearch` with `FilterDialogMultiChoiceWithSearch`.
*   Pass `onClearAll = { filterState.onAuthorsSearch("") }` (or appropriate reset logic from `SeriesFilterState`).

---

## 4. Verification & Testing
*   **Desktop/Wasm:** Resize the window to mobile proportions (Compact width) and verify that the dialog opens correctly and doesn't conflict with the keyboard.
*   **Search Logic:** Verify that typing in the search box correctly triggers the `onSearch` API call and updates the result list.
*   **Selection Logic:** Verify that clicking results adds them to the selected list and clicking chips removes them.
*   **Persistence:** Ensure that closing and reopening the dialog preserves the current search results (if intended) and correctly shows the selected state.

---

## 5. UI Refinement: Aligning with Tag Filter Look & Feel

To ensure visual consistency with the existing Tag Filter, the following UI adjustments will be made to `FilterDialogMultiChoiceWithSearch`:

### 1. Dialog Layout & Padding
*   The main `Column` inside `AppDialog` should use `Modifier.padding(15.dp)` to match `TagFilterDropdownContent`.
*   The dialog background should consistently use `MaterialTheme.colorScheme.surface`.

### 2. Search Field Consistency
*   Set `NoPaddingTextField` height to `40.dp`.
*   Use `LocalStrings.current.filters.filterTagsSearch` or a localized equivalent for the placeholder.

### 3. Sectioning & Dividers
*   Introduce section headers (e.g., "Selected Authors", "Search Results") using the same pattern as Tag Filter:
    ```kotlin
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Selected Authors", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider(Modifier.padding(start = 10.dp))
    }
    ```
*   Only show the "Selected Authors" section if `selectedOptions` is not empty.

### 4. Chip Aesthetics (Selected Items)
*   Update `NoPaddingChip` for selected authors to match `TagFilterChip` (Include style):
    *   **Border Color:** `MaterialTheme.colorScheme.secondaryContainer`.
    *   **Text Color:** `MaterialTheme.colorScheme.secondary`.
    *   **Font Style:** `MaterialTheme.typography.labelLarge`.
*   Remove the manual `Row` inside `NoPaddingChip` as `NoPaddingChip` already provides a `Row` with `Arrangement.spacedBy(5.dp)`.

### 5. Search Results (Options List)
*   Ensure the results list uses `MaterialTheme.typography.bodyLarge` for item labels.
*   Maintain the `DropdownMultiChoiceItem` style (checkbox-style) but ensure spacing and alignment match the rest of the app's list components.
*   The scrollable area should have a maximum height (e.g., `400.dp`) and use the `scrollbar` modifier.

### 6. Iconography
*   Ensure `Icons.Default.ArrowDropDown` and `Icons.Default.Close` are sized appropriately (default size is usually fine).
