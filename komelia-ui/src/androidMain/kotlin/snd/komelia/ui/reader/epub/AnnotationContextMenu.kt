package snd.komelia.ui.reader.epub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import snd.komelia.ui.reader.common.AnnotationColorSwatches

/**
 * Floating popup shown when the user selects text in the EPUB reader.
 *
 * @param selectedText The text the user has selected (may be null if unavailable).
 * @param selectedColor The currently selected highlight color.
 * @param onColorSelected Updates the selected color.
 * @param onCopy Copy selectedText to clipboard and dismiss.
 * @param onHighlight Save highlight immediately with current color (no note).
 * @param onNote Open AnnotationDialog for adding a note.
 * @param onDismiss Dismiss the menu.
 */
@Composable
fun AnnotationContextMenu(
    selectedText: String?,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AnnotationColorSwatches(
                    selectedColor = selectedColor,
                    onColorSelected = onColorSelected,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        if (!selectedText.isNullOrBlank()) {
                            clipboard.setText(AnnotatedString(selectedText))
                        }
                        onCopy()
                    }) { Text("Copy") }
                    TextButton(onClick = onHighlight) { Text("Highlight") }
                    TextButton(onClick = onNote) { Text("Note") }
                }
            }
        }
    }
}
