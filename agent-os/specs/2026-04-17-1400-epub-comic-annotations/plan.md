# Annotation Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add text highlighting and note annotations to the EPUB3 reader, and pin annotations to the comic (image) reader, with a shared SQLite table and management UI in both readers.

**Architecture:** Unified `BookAnnotation` domain model with a sealed `AnnotationLocation` (epub or comic). A single `BookAnnotationsTable` (Exposed ORM) stores both types. EPUB annotations hook into existing `onSelection`/`onHighlightTap` callbacks in `EpubView`. Comic annotations extend the existing long-press context menu. Each reader gains an Annotations tab in its content dialog.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose (Material3), Readium SDK (`org.readium.r2`), Exposed ORM v1, SQLite (Flyway migrations), AndroidX Compose Foundation, `kotlinx.coroutines`

---

## File Map

**Create:**
- `komelia-domain/core/src/commonMain/kotlin/snd/komelia/annotations/BookAnnotation.kt`
- `komelia-domain/core/src/commonMain/kotlin/snd/komelia/annotations/BookAnnotationRepository.kt`
- `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/BookAnnotationsTable.kt`
- `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/annotations/ExposedBookAnnotationRepository.kt`
- `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/V43__book_annotations.sql`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationColorSwatches.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationDialog.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationRow.kt`
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/AnnotationContextMenu.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicAnnotationOverlay.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicContentDialog.kt`

**Modify:**
- `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt`
- `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/AppSettingsTable.kt`
- `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedSettingsRepository.kt`
- `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/KomeliaDatabase.kt` *(no change needed — Flyway picks up new migration automatically)*
- `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/CommonSettingsRepository.kt`
- `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/SettingsRepositoryWrapper.kt`
- `komelia-domain/core/src/commonMain/kotlin/snd/komelia/CoreModule.kt`
- `komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt`
- `komelia-app/src/jvmMain/kotlin/snd/komelia/DesktopAppModule.kt`
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt`
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.android.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.kt`
- `komelia-ui/src/jvmMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.jvm.kt`
- `komelia-ui/src/wasmJsMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.wasmJs.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubReaderViewModel.kt`
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ContentDialog.kt`
- `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderContent.android.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderState.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderContent.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`

---

## Task 1: Domain Model

**Files:**
- Create: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/annotations/BookAnnotation.kt`
- Create: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/annotations/BookAnnotationRepository.kt`

- [ ] **Step 1: Create `BookAnnotation.kt`**

```kotlin
package snd.komelia.annotations

import snd.komga.client.book.KomgaBookId

sealed class AnnotationLocation {
    /** EPUB3 annotation. locatorJson is a Readium Locator serialized to JSON (contains
     *  locations.fragments with CSS selectors identifying the exact text range). */
    data class EpubLocation(
        val locatorJson: String,
        val selectedText: String?, // Display only — the highlighted text snippet
    ) : AnnotationLocation()

    /** Comic/image reader annotation. x and y are 0.0–1.0 fractions of image dimensions. */
    data class ComicLocation(
        val page: Int,
        val x: Float,
        val y: Float,
    ) : AnnotationLocation()
}

data class BookAnnotation(
    val id: String,
    val bookId: KomgaBookId,
    val location: AnnotationLocation,
    /** Null means note-only (no visual highlight). Comic pins always have a color. */
    val highlightColor: Int?,
    /** Null means pure highlight with no note text. */
    val note: String?,
    val createdAt: Long,
)
```

- [ ] **Step 2: Create `BookAnnotationRepository.kt`**

```kotlin
package snd.komelia.annotations

import kotlinx.coroutines.flow.Flow
import snd.komga.client.book.KomgaBookId

interface BookAnnotationRepository {
    fun getAnnotations(bookId: KomgaBookId): Flow<List<BookAnnotation>>
    suspend fun saveAnnotation(annotation: BookAnnotation)
    suspend fun deleteAnnotation(id: String)
}
```

- [ ] **Step 3: Commit**

```bash
git add komelia-domain/core/src/commonMain/kotlin/snd/komelia/annotations/
git commit -m "feat: add BookAnnotation domain model and repository interface"
```

---

## Task 2: Database Table and Migration

**Files:**
- Create: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/BookAnnotationsTable.kt`
- Create: `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/V43__book_annotations.sql`
- Modify: `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt`
- Modify: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/AppSettingsTable.kt`

- [ ] **Step 1: Create `BookAnnotationsTable.kt`**

```kotlin
package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object BookAnnotationsTable : Table("book_annotations") {
    val id = text("id")
    val bookId = text("book_id")
    val readerType = text("reader_type") // "EPUB3" or "COMIC"
    val locatorJson = text("locator_json").nullable()
    val selectedText = text("selected_text").nullable()
    val highlightColor = integer("highlight_color").nullable()
    val pageNumber = integer("page_number").nullable()
    val x = float("x").nullable()
    val y = float("y").nullable()
    val note = text("note").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 2: Add `lastHighlightColor` to `AppSettingsTable.kt`**

In `AppSettingsTable.kt`, add this line at the end of the table columns (before `primaryKey`):

```kotlin
val lastHighlightColor = integer("last_highlight_color").nullable()
```

- [ ] **Step 3: Add `lastHighlightColor` to `AppSettings.kt`**

In `AppSettings.kt`, add at the end of the data class parameters (after `useFloatingNavigationBar`):

```kotlin
/** Null means use default yellow (0xFFFFEB3B.toInt()). */
val lastHighlightColor: Int? = null,
```

- [ ] **Step 4: Create `V43__book_annotations.sql`**

```sql
CREATE TABLE book_annotations
(
    id              TEXT    NOT NULL,
    book_id         TEXT    NOT NULL,
    reader_type     TEXT    NOT NULL,
    locator_json    TEXT,
    selected_text   TEXT,
    highlight_color INTEGER,
    page_number     INTEGER,
    x               REAL,
    y               REAL,
    note            TEXT,
    created_at      INTEGER NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE AppSettings ADD COLUMN last_highlight_color INTEGER;
```

- [ ] **Step 5: Commit**

```bash
git add komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/BookAnnotationsTable.kt
git add komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/V43__book_annotations.sql
git add komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt
git add komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/AppSettingsTable.kt
git commit -m "feat: add BookAnnotationsTable and V43 migration"
```

---

## Task 3: Exposed Repository Implementation

**Files:**
- Create: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/annotations/ExposedBookAnnotationRepository.kt`

- [ ] **Step 1: Create `ExposedBookAnnotationRepository.kt`**

```kotlin
package snd.komelia.db.annotations

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.annotations.BookAnnotationRepository
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.BookAnnotationsTable
import snd.komga.client.book.KomgaBookId

class ExposedBookAnnotationRepository(database: Database) :
    ExposedRepository(database), BookAnnotationRepository {

    private val annotationsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getAnnotations(bookId: KomgaBookId): Flow<List<BookAnnotation>> {
        return annotationsChanged.onStart { emit(Unit) }.map {
            transaction {
                BookAnnotationsTable.selectAll()
                    .where { BookAnnotationsTable.bookId eq bookId.value }
                    .orderBy(BookAnnotationsTable.createdAt, org.jetbrains.exposed.v1.core.SortOrder.ASC)
                    .mapNotNull { row ->
                        val readerType = row[BookAnnotationsTable.readerType]
                        val location: AnnotationLocation = when (readerType) {
                            "EPUB3" -> AnnotationLocation.EpubLocation(
                                locatorJson = row[BookAnnotationsTable.locatorJson] ?: return@mapNotNull null,
                                selectedText = row[BookAnnotationsTable.selectedText],
                            )
                            "COMIC" -> AnnotationLocation.ComicLocation(
                                page = row[BookAnnotationsTable.pageNumber] ?: return@mapNotNull null,
                                x = row[BookAnnotationsTable.x] ?: return@mapNotNull null,
                                y = row[BookAnnotationsTable.y] ?: return@mapNotNull null,
                            )
                            else -> return@mapNotNull null
                        }
                        BookAnnotation(
                            id = row[BookAnnotationsTable.id],
                            bookId = KomgaBookId(row[BookAnnotationsTable.bookId]),
                            location = location,
                            highlightColor = row[BookAnnotationsTable.highlightColor],
                            note = row[BookAnnotationsTable.note],
                            createdAt = row[BookAnnotationsTable.createdAt],
                        )
                    }
            }
        }
    }

    override suspend fun saveAnnotation(annotation: BookAnnotation) {
        transaction {
            BookAnnotationsTable.insert {
                it[id] = annotation.id
                it[bookId] = annotation.bookId.value
                it[highlightColor] = annotation.highlightColor
                it[note] = annotation.note
                it[createdAt] = annotation.createdAt
                when (val loc = annotation.location) {
                    is AnnotationLocation.EpubLocation -> {
                        it[readerType] = "EPUB3"
                        it[locatorJson] = loc.locatorJson
                        it[selectedText] = loc.selectedText
                    }
                    is AnnotationLocation.ComicLocation -> {
                        it[readerType] = "COMIC"
                        it[pageNumber] = loc.page
                        it[x] = loc.x
                        it[y] = loc.y
                    }
                }
            }
        }
        annotationsChanged.tryEmit(Unit)
    }

    override suspend fun deleteAnnotation(id: String) {
        transaction {
            BookAnnotationsTable.deleteWhere { BookAnnotationsTable.id eq id }
        }
        annotationsChanged.tryEmit(Unit)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/annotations/
git commit -m "feat: add ExposedBookAnnotationRepository"
```

---

## Task 4: Wire Repository into App Modules

**Files:**
- Modify: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/CoreModule.kt`
- Modify: `komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt`
- Modify: `komelia-app/src/jvmMain/kotlin/snd/komelia/DesktopAppModule.kt`

- [ ] **Step 1: Add `bookAnnotationRepository` to `AppRepositories` in `CoreModule.kt`**

In `AppRepositories`, add after `audioBookmarkRepository`:

```kotlin
val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
```

- [ ] **Step 2: Wire in `AndroidAppModule.kt`**

In `AndroidAppModule.kt`, after the `audioBookmarkRepository` line (line ~172), add:

```kotlin
bookAnnotationRepository = snd.komelia.db.annotations.ExposedBookAnnotationRepository(databases.app),
```

- [ ] **Step 3: Wire in `DesktopAppModule.kt`**

Apply the same change in `DesktopAppModule.kt` at the equivalent location (after `audioBookmarkRepository`):

```kotlin
bookAnnotationRepository = snd.komelia.db.annotations.ExposedBookAnnotationRepository(databases.app),
```

- [ ] **Step 4: Build and verify compilation**

```bash
./gradlew :komelia-domain:core:compileKotlinJvm :komelia-app:compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add komelia-domain/core/src/commonMain/kotlin/snd/komelia/CoreModule.kt
git add komelia-app/src/androidMain/kotlin/snd/komelia/AndroidAppModule.kt
git add komelia-app/src/jvmMain/kotlin/snd/komelia/DesktopAppModule.kt
git commit -m "feat: wire BookAnnotationRepository into app modules"
```

---

## Task 5: Last-Used Highlight Color in Settings

**Files:**
- Modify: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/CommonSettingsRepository.kt`
- Modify: `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/SettingsRepositoryWrapper.kt`
- Modify: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedSettingsRepository.kt`

The default highlight color when none is saved is yellow: `0xFFFFEB3B.toInt()` = `-5317`.

- [ ] **Step 1: Add methods to `CommonSettingsRepository`**

Add at the end of the interface body in `CommonSettingsRepository.kt`:

```kotlin
fun getLastHighlightColor(): Flow<Int>
suspend fun putLastHighlightColor(color: Int)
```

- [ ] **Step 2: Add implementations to `SettingsRepositoryWrapper.kt`**

Add at the end of the class body (before the closing `}`):

```kotlin
override fun getLastHighlightColor(): Flow<Int> {
    return wrapper.state.map { it.lastHighlightColor ?: 0xFFFFEB3B.toInt() }.distinctUntilChanged()
}

override suspend fun putLastHighlightColor(color: Int) {
    wrapper.transform { it.copy(lastHighlightColor = color) }
}
```

- [ ] **Step 3: Update `ExposedSettingsRepository.kt`**

In `save()`, add after the `useFloatingNavigationBar` line:

```kotlin
it[lastHighlightColor] = settings.lastHighlightColor
```

In `toAppSettings()`, add after the `useFloatingNavigationBar` line:

```kotlin
lastHighlightColor = get(AppSettingsTable.lastHighlightColor),
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :komelia-infra:database:shared:compileKotlinJvm 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/CommonSettingsRepository.kt
git add komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/SettingsRepositoryWrapper.kt
git add komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedSettingsRepository.kt
git commit -m "feat: add lastHighlightColor to app settings"
```

---

## Task 6: `AnnotationColorSwatches` Composable

**Files:**
- Create: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationColorSwatches.kt`

- [ ] **Step 1: Create the file**

```kotlin
package snd.komelia.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 5 preset annotation highlight colors. */
val AnnotationColors = listOf(
    0xFFFFEB3B.toInt(), // Yellow
    0xFF4CAF50.toInt(), // Green
    0xFF2196F3.toInt(), // Blue
    0xFFE91E63.toInt(), // Pink
    0xFFFF9800.toInt(), // Orange
)

@Composable
fun AnnotationColorSwatches(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnnotationColors.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationColorSwatches.kt
git commit -m "feat: add AnnotationColorSwatches composable"
```

---

## Task 7: `AnnotationDialog` Composable

**Files:**
- Create: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationDialog.kt`

This is a `ModalBottomSheet` used for both create and edit flows in both readers.

- [ ] **Step 1: Create the file**

```kotlin
package snd.komelia.ui.reader.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.annotations.BookAnnotation

/**
 * Shared annotation dialog used for create and edit in both EPUB3 and comic readers.
 *
 * @param referenceText For EPUB: the selected text. For comic: "Page N · (x%, y%)".
 * @param existingAnnotation Non-null when editing; null when creating new.
 * @param initialColor The pre-selected highlight color (last used).
 * @param onSave Called with the note text and chosen color when user taps Save.
 * @param onDelete Called when user taps Delete (only shown when existingAnnotation != null).
 * @param onDismiss Called when user taps Cancel or dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationDialog(
    referenceText: String,
    existingAnnotation: BookAnnotation?,
    initialColor: Int,
    onSave: (note: String?, color: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartialExpansion = true)
    val coroutineScope = rememberCoroutineScope()

    var note by remember { mutableStateOf(existingAnnotation?.note ?: "") }
    var selectedColor by remember { mutableIntStateOf(existingAnnotation?.highlightColor ?: initialColor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Reference text (quoted excerpt or page location)
            Text(
                text = referenceText,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            // Color swatches
            AnnotationColorSwatches(
                selectedColor = selectedColor,
                onColorSelected = { selectedColor = it },
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Note text field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note…") },
                minLines = 3,
                maxLines = 6,
            )

            Spacer(Modifier.height(12.dp))

            // Action row
            Row(modifier = Modifier.fillMaxWidth()) {
                if (existingAnnotation != null) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            onDelete()
                        }
                    }) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onSave(note.ifBlank { null }, selectedColor)
                    }
                }) { Text("Save") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationDialog.kt
git commit -m "feat: add shared AnnotationDialog composable"
```

---

## Task 8: `AnnotationRow` Composable

**Files:**
- Create: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationRow.kt`

- [ ] **Step 1: Create the file**

```kotlin
package snd.komelia.ui.reader.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation

/**
 * A row in the Annotations management tab. Works for both EPUB and comic annotations.
 *
 * @param annotation The annotation to display.
 * @param locationLabel For EPUB: "Chapter · Location X of N". For comic: "Page N".
 * @param onTap Navigate to annotation location and open edit dialog.
 * @param onDelete Remove annotation.
 */
@Composable
fun AnnotationRow(
    annotation: BookAnnotation,
    locationLabel: String,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color chip
        val chipColor = annotation.highlightColor
        if (chipColor != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(chipColor))
                    .padding(end = 10.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (chipColor != null) 10.dp else 0.dp),
        ) {
            // Location label
            Text(
                text = locationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // For EPUB: show the highlighted text snippet
            if (annotation.location is AnnotationLocation.EpubLocation) {
                val snippet = annotation.location.selectedText
                if (!snippet.isNullOrBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Note preview (up to 2 lines)
            val noteText = annotation.note
            if (!noteText.isNullOrBlank()) {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete annotation")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/AnnotationRow.kt
git commit -m "feat: add AnnotationRow composable"
```

---

## Task 9: EPUB `AnnotationContextMenu`

**Files:**
- Create: `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/AnnotationContextMenu.kt`

This floating menu appears after text selection in the EPUB reader.

- [ ] **Step 1: Create the file**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/AnnotationContextMenu.kt
git commit -m "feat: add EPUB AnnotationContextMenu composable"
```

---

## Task 10: Wire EPUB3 Reader State

**Files:**
- Modify: `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt`

This task injects `BookAnnotationRepository` and `CommonSettingsRepository` (for `lastHighlightColor`) into `Epub3ReaderState`, adds annotation state flows, wires `onSelection` and `onHighlightTap` callbacks, and maps annotations to `EpubView` highlights.

- [ ] **Step 1: Add import and constructor parameter to `Epub3ReaderState`**

Add to the import section:

```kotlin
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.annotations.BookAnnotationRepository
import com.storyteller.reader.Highlight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```

Add to the constructor (after `audioBookmarkRepository`):

```kotlin
private val bookAnnotationRepository: BookAnnotationRepository,
private val settingsRepository: snd.komelia.settings.CommonSettingsRepository,
```

- [ ] **Step 2: Add state flows and UI state in `Epub3ReaderState` body**

Add after the existing `bookmarks` state flow declaration:

```kotlin
val annotations = MutableStateFlow<List<BookAnnotation>>(emptyList())
val lastHighlightColor = MutableStateFlow(0xFFFFEB3B.toInt())
val showAnnotationContextMenu = MutableStateFlow(false)
val showAnnotationDialog = MutableStateFlow(false)
val pendingSelectionLocator = MutableStateFlow<org.readium.r2.shared.publication.Locator?>(null)
val editingAnnotation = MutableStateFlow<BookAnnotation?>(null)
```

- [ ] **Step 3: Collect annotation repository in `initialize()`**

Inside the `initialize()` coroutine (find the block that collects bookmarks and add alongside it), add:

```kotlin
coroutineScope.launch {
    bookAnnotationRepository.getAnnotations(bookId.value).collect { list ->
        annotations.value = list
    }
}
coroutineScope.launch {
    settingsRepository.getLastHighlightColor().collect { color ->
        lastHighlightColor.value = color
    }
}
```

- [ ] **Step 4: Add annotation CRUD methods**

Add after `deleteBookmark()`:

```kotlin
fun saveAnnotation(locator: org.readium.r2.shared.publication.Locator, selectedText: String?, color: Int, note: String?) {
    val annotation = BookAnnotation(
        id = UUID.randomUUID().toString(),
        bookId = bookId.value,
        location = AnnotationLocation.EpubLocation(
            locatorJson = locator.toJSON().toString(),
            selectedText = selectedText,
        ),
        highlightColor = color,
        note = note,
        createdAt = Clock.System.now().toEpochMilliseconds(),
    )
    coroutineScope.launch {
        bookAnnotationRepository.saveAnnotation(annotation)
        settingsRepository.putLastHighlightColor(color)
        lastHighlightColor.value = color
    }
}

fun updateAnnotation(existing: BookAnnotation, note: String?, color: Int) {
    val updated = existing.copy(
        highlightColor = color,
        note = note,
    )
    coroutineScope.launch {
        bookAnnotationRepository.deleteAnnotation(existing.id)
        bookAnnotationRepository.saveAnnotation(updated)
        settingsRepository.putLastHighlightColor(color)
        lastHighlightColor.value = color
    }
}

fun deleteAnnotation(annotation: BookAnnotation) {
    coroutineScope.launch {
        bookAnnotationRepository.deleteAnnotation(annotation.id)
    }
}
```

- [ ] **Step 5: Update highlights in EpubView when annotations change**

In `onEpubViewCreated()`, add a coroutine that keeps EpubView highlights in sync. Add after `view.finalizeProps()`:

```kotlin
coroutineScope.launch {
    annotations.collect { list ->
        val highlights = list
            .filter { it.location is AnnotationLocation.EpubLocation && it.highlightColor != null }
            .mapNotNull { annotation ->
                val loc = annotation.location as AnnotationLocation.EpubLocation
                val locator = runCatching {
                    org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(loc.locatorJson))
                }.getOrNull()
                locator?.let { Highlight(annotation.id, annotation.highlightColor!!, it) }
            }
        epubView?.let { v ->
            v.props = v.props?.copy(highlights = highlights)
        }
    }
}
```

- [ ] **Step 6: Wire `onSelection` and `onHighlightTap` in `onEpubViewCreated()`**

Inside `view.listener = object : EpubViewListener {`, add:

```kotlin
override fun onSelection(locator: Locator, x: Int, y: Int) {
    pendingSelectionLocator.value = locator
    showAnnotationContextMenu.value = true
}

override fun onSelectionCleared() {
    showAnnotationContextMenu.value = false
    pendingSelectionLocator.value = null
}

override fun onHighlightTap(decorationId: String, x: Int, y: Int) {
    val annotation = annotations.value.find { it.id == decorationId }
    if (annotation != null) {
        editingAnnotation.value = annotation
        showAnnotationDialog.value = true
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt
git commit -m "feat: wire annotation state and callbacks in Epub3ReaderState"
```

---

## Task 11: Update EpubReaderFactory and ViewModel

**Files:**
- Modify: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.kt`
- Modify: `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.android.kt`
- Modify: `komelia-ui/src/jvmMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.jvm.kt`
- Modify: `komelia-ui/src/wasmJsMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.wasmJs.kt`
- Modify: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubReaderViewModel.kt`

- [ ] **Step 1: Add `bookAnnotationRepository` and `settingsRepository` params to `Epub3ReaderFactory.kt` expect function**

In `Epub3ReaderFactory.kt` (the common `expect fun createEpub3ReaderState`), add after `epubBookmarkRepository`:

```kotlin
bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
settingsRepository: snd.komelia.settings.CommonSettingsRepository,
```

- [ ] **Step 2: Update `Epub3ReaderFactory.android.kt` actual function**

Add the same two parameters to the `actual fun createEpub3ReaderState(...)` signature, and pass them when constructing `Epub3ReaderState`:

```kotlin
bookAnnotationRepository = bookAnnotationRepository,
settingsRepository = settingsRepository,
```

- [ ] **Step 3: Update `Epub3ReaderFactory.jvm.kt` and `Epub3ReaderFactory.wasmJs.kt`**

Add the same two parameters to both `actual` function signatures. For the JVM/Wasm implementations that may not yet support EPUB3, just add the parameters to the signature (they may throw `NotImplementedError` already).

- [ ] **Step 4: Update `EpubReaderViewModel.kt`**

Add to constructor after `epubBookmarkRepository`:

```kotlin
private val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
private val settingsRepository: snd.komelia.settings.CommonSettingsRepository,
```

In the `createEpub3ReaderState(...)` call, add:

```kotlin
bookAnnotationRepository = bookAnnotationRepository,
settingsRepository = settingsRepository,
```

- [ ] **Step 5: Update all callsites of `EpubReaderViewModel` to pass the new params**

Search for `EpubReaderViewModel(` in the codebase:

```bash
grep -r "EpubReaderViewModel(" --include="*.kt" -l
```

For each callsite, add:

```kotlin
bookAnnotationRepository = appRepositories.bookAnnotationRepository,
settingsRepository = appRepositories.settingsRepository,
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew :komelia-ui:compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.kt
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.android.kt
git add komelia-ui/src/jvmMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.jvm.kt
git add komelia-ui/src/wasmJsMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.wasmJs.kt
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubReaderViewModel.kt
git commit -m "feat: thread BookAnnotationRepository and settingsRepository through EPUB factory chain"
```

---

## Task 12: EPUB Annotations Tab in `Epub3ContentDialog`

**Files:**
- Modify: `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ContentDialog.kt`

- [ ] **Step 1: Add `annotations` and callback params to `Epub3ContentDialog`**

Update the function signature to add (after `onDeleteBookmark`):

```kotlin
annotations: List<snd.komelia.annotations.BookAnnotation>,
onAnnotationTap: (snd.komelia.annotations.BookAnnotation) -> Unit,
onDeleteAnnotation: (snd.komelia.annotations.BookAnnotation) -> Unit,
```

- [ ] **Step 2: Change `pageCount` from 3 to 4**

Change:
```kotlin
val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 3 })
```
To:
```kotlin
val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 4 })
```

- [ ] **Step 3: Add the Annotations tab button in `SecondaryTabRow`**

After the "Bookmarks" tab and before the "Search" tab, insert:

```kotlin
Tab(
    selected = pagerState.currentPage == 2,
    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
    text = { Text("Annotations") },
)
```

Change the Search tab from index 2 to index 3:
```kotlin
Tab(
    selected = pagerState.currentPage == 3,
    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
    text = { Text("Search") },
)
```

- [ ] **Step 4: Add page case in `HorizontalPager`**

Change the `when (page)` block:

```kotlin
when (page) {
    0 -> ContentsTab(toc, currentHref, onNavigateLink)
    1 -> BookmarksTab(bookmarks, positions, onNavigateLocator, onDeleteBookmark)
    2 -> AnnotationsTab(annotations, positions, onAnnotationTap, onDeleteAnnotation)
    3 -> SearchTab(searchQuery, searchResults, positions, isSearching, onSearch, onNavigateLocator)
}
```

- [ ] **Step 5: Add `AnnotationsTab` composable (private) at the bottom of the file**

```kotlin
@Composable
private fun AnnotationsTab(
    annotations: List<snd.komelia.annotations.BookAnnotation>,
    positions: List<Locator>,
    onTap: (snd.komelia.annotations.BookAnnotation) -> Unit,
    onDelete: (snd.komelia.annotations.BookAnnotation) -> Unit,
) {
    if (annotations.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No annotations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        // Sort by totalProgression ascending
        val sorted = remember(annotations) {
            annotations.sortedBy { annotation ->
                (annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
                    ?.let { loc ->
                        runCatching {
                            Locator.fromJSON(JSONObject(loc.locatorJson))
                        }.getOrNull()?.locations?.totalProgression
                    } ?: 0.0
            }
        }
        LazyColumn {
            itemsIndexed(sorted) { index, annotation ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor
                )
                val location = annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation
                val locator = remember(location?.locatorJson) {
                    location?.let { runCatching { Locator.fromJSON(JSONObject(it.locatorJson)) }.getOrNull() }
                }
                val positionIndex = if (locator != null) locatorToPositionIndex(positions, locator) else -1
                val locationLabel = buildString {
                    append(locator?.title ?: "Unknown chapter")
                    if (positions.isNotEmpty() && positionIndex >= 0) {
                        append(" · Location ${positionIndex + 1} of ${positions.size}")
                    }
                }
                snd.komelia.ui.reader.common.AnnotationRow(
                    annotation = annotation,
                    locationLabel = locationLabel,
                    onTap = { onTap(annotation) },
                    onDelete = { onDelete(annotation) },
                )
            }
        }
    }
}
```

- [ ] **Step 6: Wire `Epub3ContentDialog` callsite in `Epub3ReaderContent.android.kt`**

Find where `Epub3ContentDialog` is called and add:

```kotlin
annotations = readerState.annotations.collectAsState().value,
onAnnotationTap = { annotation ->
    readerState.editingAnnotation.value = annotation
    readerState.showContentDialog.value = false
    readerState.showAnnotationDialog.value = true
    // navigate to annotation location
    val loc = (annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
    loc?.let { readerState.navigateToLocator(
        runCatching { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it.locatorJson)) }.getOrNull()
            ?: return@let
    ) }
},
onDeleteAnnotation = { readerState.deleteAnnotation(it) },
```

- [ ] **Step 7: Wire `AnnotationContextMenu` and `AnnotationDialog` in `Epub3ReaderContent.android.kt`**

After the existing controls card / content dialog rendering, add:

```kotlin
// Annotation context menu (shown after text selection)
val showContextMenu by readerState.showAnnotationContextMenu.collectAsState()
val pendingLocator by readerState.pendingSelectionLocator.collectAsState()
val lastColor by readerState.lastHighlightColor.collectAsState()

if (showContextMenu) {
    AnnotationContextMenu(
        selectedText = pendingLocator?.text?.highlight,
        selectedColor = lastColor,
        onColorSelected = { readerState.lastHighlightColor.value = it },
        onCopy = { readerState.showAnnotationContextMenu.value = false },
        onHighlight = {
            pendingLocator?.let { locator ->
                readerState.saveAnnotation(
                    locator = locator,
                    selectedText = locator.text?.highlight,
                    color = readerState.lastHighlightColor.value,
                    note = null,
                )
            }
            readerState.showAnnotationContextMenu.value = false
        },
        onNote = {
            readerState.showAnnotationContextMenu.value = false
            readerState.showAnnotationDialog.value = true
        },
        onDismiss = { readerState.showAnnotationContextMenu.value = false },
    )
}

// Annotation create/edit dialog
val showAnnotationDialog by readerState.showAnnotationDialog.collectAsState()
val editingAnnotation by readerState.editingAnnotation.collectAsState()

if (showAnnotationDialog) {
    val isEditing = editingAnnotation != null
    val referenceText = if (isEditing) {
        (editingAnnotation!!.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
            ?.selectedText ?: ""
    } else {
        pendingLocator?.text?.highlight ?: ""
    }
    snd.komelia.ui.reader.common.AnnotationDialog(
        referenceText = referenceText,
        existingAnnotation = editingAnnotation,
        initialColor = lastColor,
        onSave = { note, color ->
            if (isEditing) {
                readerState.updateAnnotation(editingAnnotation!!, note, color)
            } else {
                pendingLocator?.let { locator ->
                    readerState.saveAnnotation(locator, locator.text?.highlight, color, note)
                }
            }
            readerState.showAnnotationDialog.value = false
            readerState.editingAnnotation.value = null
            readerState.pendingSelectionLocator.value = null
        },
        onDelete = {
            editingAnnotation?.let { readerState.deleteAnnotation(it) }
            readerState.showAnnotationDialog.value = false
            readerState.editingAnnotation.value = null
        },
        onDismiss = {
            readerState.showAnnotationDialog.value = false
            readerState.editingAnnotation.value = null
        },
    )
}
```

- [ ] **Step 8: Build and verify**

```bash
./gradlew :komelia-ui:compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ContentDialog.kt
git add komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderContent.android.kt
git commit -m "feat: add EPUB Annotations tab and wire annotation UI"
```

---

## Task 13: Comic Annotation State in `ReaderState`

**Files:**
- Modify: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`
- Modify: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderState.kt`

- [ ] **Step 1: Add `bookAnnotationRepository` to `ReaderState` constructor**

Add to `ReaderState` constructor (after `colorCorrectionRepository`):

```kotlin
private val bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
```

Add state flows:

```kotlin
val annotations = MutableStateFlow<List<snd.komelia.annotations.BookAnnotation>>(emptyList())
val showAnnotationDialog = MutableStateFlow(false)
val editingComicAnnotation = MutableStateFlow<snd.komelia.annotations.BookAnnotation?>(null)
val pendingAnnotationPage = MutableStateFlow(0)
val pendingAnnotationX = MutableStateFlow(0f)
val pendingAnnotationY = MutableStateFlow(0f)
val lastHighlightColor = MutableStateFlow(0xFFFFEB3B.toInt())
```

- [ ] **Step 2: Collect annotations in `initialize()` within `ReaderState`**

Find where the book is loaded in `stateScope` and add alongside:

```kotlin
stateScope.launch {
    currentBookId.filterNotNull().collectLatest { bookId ->
        bookAnnotationRepository.getAnnotations(bookId).collect { list ->
            annotations.value = list
        }
    }
}
```

Note: add `import kotlinx.coroutines.flow.filterNotNull` and `import kotlinx.coroutines.flow.collectLatest` if not present.

- [ ] **Step 3: Add annotation CRUD methods to `ReaderState`**

```kotlin
fun saveComicAnnotation(page: Int, x: Float, y: Float, color: Int, note: String?) {
    val bookId = currentBookId.value ?: return
    val annotation = snd.komelia.annotations.BookAnnotation(
        id = java.util.UUID.randomUUID().toString(),
        bookId = bookId,
        location = snd.komelia.annotations.AnnotationLocation.ComicLocation(page, x, y),
        highlightColor = color,
        note = note,
        createdAt = System.currentTimeMillis(),
    )
    stateScope.launch {
        bookAnnotationRepository.saveAnnotation(annotation)
        lastHighlightColor.value = color
    }
}

fun updateComicAnnotation(existing: snd.komelia.annotations.BookAnnotation, note: String?, color: Int) {
    val updated = existing.copy(highlightColor = color, note = note)
    stateScope.launch {
        bookAnnotationRepository.deleteAnnotation(existing.id)
        bookAnnotationRepository.saveAnnotation(updated)
        lastHighlightColor.value = color
    }
}

fun deleteComicAnnotation(annotation: snd.komelia.annotations.BookAnnotation) {
    stateScope.launch { bookAnnotationRepository.deleteAnnotation(annotation.id) }
}
```

- [ ] **Step 4: Add `imageBounds` to `PagedReaderState`**

In `PagedReaderState.kt`, add:

```kotlin
import androidx.compose.ui.geometry.Rect
val lastImageBounds = MutableStateFlow<Rect?>(null)
```

Add a helper to get the current page number:

```kotlin
fun getCurrentPageNumber(): Int {
    val spreads = pageSpreads.value
    val idx = currentSpreadIndex.value
    return spreads.getOrNull(idx)?.first()?.pageNumber ?: 0
}
```

- [ ] **Step 5: Update `PagedReaderContent.kt` to publish `imageBounds`**

In `PagedReaderContent.kt`, after the `imageBounds` remember block (around line 215), add:

```kotlin
LaunchedEffect(imageBounds) {
    pagedReaderState.lastImageBounds.value = imageBounds
}
```

- [ ] **Step 6: Update all `ReaderState` callsites to pass `bookAnnotationRepository`**

Search for `ReaderState(` in the codebase:

```bash
grep -r "ReaderState(" --include="*.kt" -l
```

Add `bookAnnotationRepository = appRepositories.bookAnnotationRepository` to each constructor call.

- [ ] **Step 7: Build and verify**

```bash
./gradlew :komelia-ui:compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderState.kt
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderContent.kt
git commit -m "feat: add comic annotation state to ReaderState and PagedReaderState"
```

---

## Task 14: `ComicAnnotationOverlay` Composable

**Files:**
- Create: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicAnnotationOverlay.kt`

Renders colored pin icons over the comic page image at the stored fractional positions.

- [ ] **Step 1: Create the file**

```kotlin
package snd.komelia.ui.reader.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation

/**
 * Transparent overlay composable that renders pin markers for comic annotations.
 * Should be placed in a Box that fills the same area as the page image.
 *
 * @param annotations Annotations for the current page (pre-filtered by page number).
 * @param imageBounds The Rect of the displayed image within the container (in pixels).
 * @param onAnnotationTap Called when user taps an existing pin.
 */
@Composable
fun ComicAnnotationOverlay(
    annotations: List<BookAnnotation>,
    imageBounds: Rect?,
    onAnnotationTap: (BookAnnotation) -> Unit,
) {
    if (imageBounds == null || annotations.isEmpty()) return

    val density = LocalDensity.current
    val pinSizePx = with(density) { 24.dp.toPx() }

    annotations.forEach { annotation ->
        val loc = annotation.location as? AnnotationLocation.ComicLocation ?: return@forEach
        val pinX = imageBounds.left + loc.x * imageBounds.width - pinSizePx / 2
        val pinY = imageBounds.top + loc.y * imageBounds.height - pinSizePx / 2

        Box(
            modifier = Modifier
                .offset { IntOffset(pinX.toInt(), pinY.toInt()) }
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(annotation.highlightColor ?: 0xFFFFEB3B.toInt()).copy(alpha = 0.85f))
                .pointerInput(annotation.id) {
                    detectTapGestures(onTap = { onAnnotationTap(annotation) })
                }
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Annotation pin",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Wire `ComicAnnotationOverlay` into `PagedReaderContent.kt`**

Inside the `HorizontalPager` page content, within the `AdaptiveBackground` block where `SinglePageLayout` / `DoublePageLayout` is rendered, add the overlay after the layout:

```kotlin
AdaptiveBackground(
    edgeSampling = edgeSampling,
    imageBounds = imageBounds,
) {
    when (layout) {
        SINGLE_PAGE -> pages.firstOrNull()?.let { SinglePageLayout(it) }
        DOUBLE_PAGES, DOUBLE_PAGES_NO_COVER -> DoublePageLayout(pages, readingDirection)
    }
    // Annotation overlay
    val pageNumber = if (pageIdx < spreads.size) spreads[pageIdx].firstOrNull()?.pageNumber ?: -1 else -1
    val pageAnnotations = annotationsForPage(annotations, pageNumber)
    ComicAnnotationOverlay(
        annotations = pageAnnotations,
        imageBounds = imageBounds,
        onAnnotationTap = onAnnotationTap,
    )
}
```

Add these parameters to `PagedReaderContent`:

```kotlin
annotations: List<snd.komelia.annotations.BookAnnotation> = emptyList(),
onAnnotationTap: (snd.komelia.annotations.BookAnnotation) -> Unit = {},
```

Add helper at file level:

```kotlin
private fun annotationsForPage(
    annotations: List<snd.komelia.annotations.BookAnnotation>,
    pageNumber: Int,
): List<snd.komelia.annotations.BookAnnotation> {
    return annotations.filter { annotation ->
        (annotation.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation)?.page == pageNumber
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicAnnotationOverlay.kt
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderContent.kt
git commit -m "feat: add ComicAnnotationOverlay and wire into PagedReaderContent"
```

---

## Task 15: `ComicContentDialog` and Final Wiring in `ReaderContent`

**Files:**
- Create: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicContentDialog.kt`
- Modify: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`

- [ ] **Step 1: Create `ComicContentDialog.kt`**

```kotlin
package snd.komelia.ui.reader.image

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.reader.common.AnnotationRow
import kotlin.math.roundToInt

@Composable
fun ComicContentDialog(
    annotations: List<BookAnnotation>,
    onAnnotationTap: (BookAnnotation) -> Unit,
    onDeleteAnnotation: (BookAnnotation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 2f / 3f).dp
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 1 })
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalTheme.current
    val surfaceColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (dragOffsetY > 120f) onDismiss() else dragOffsetY = 0f },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Annotations") },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ComicAnnotationsTab(annotations, onAnnotationTap, onDeleteAnnotation)
                }
            }
        }
    }
}

@Composable
private fun ComicAnnotationsTab(
    annotations: List<BookAnnotation>,
    onTap: (BookAnnotation) -> Unit,
    onDelete: (BookAnnotation) -> Unit,
) {
    if (annotations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No annotations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val sorted = remember(annotations) {
            annotations.sortedWith(compareBy(
                { (it.location as? AnnotationLocation.ComicLocation)?.page ?: 0 },
                { (it.location as? AnnotationLocation.ComicLocation)?.y ?: 0f },
                { (it.location as? AnnotationLocation.ComicLocation)?.x ?: 0f },
            ))
        }
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn {
            itemsIndexed(sorted) { index, annotation ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor,
                )
                val loc = annotation.location as? AnnotationLocation.ComicLocation
                val locationLabel = if (loc != null) "Page ${loc.page + 1}" else "Unknown"
                AnnotationRow(
                    annotation = annotation,
                    locationLabel = locationLabel,
                    onTap = { onTap(annotation) },
                    onDelete = { onDelete(annotation) },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add "Add Annotation" to the long-press context menu in `ReaderContent.kt`**

Find the `AnimatedDropdownMenu` block (around line 236) with the "Save image" `DropdownMenuItem`. Add a second item:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
// ... existing imports ...

DropdownMenuItem(
    text = { Text("Add annotation") },
    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
    onClick = {
        showImageContextMenu = false
        val bounds = pagedReaderState.lastImageBounds.value
        if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            val x = ((contextMenuAnchorOffset.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
            val y = ((contextMenuAnchorOffset.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
            val page = pagedReaderState.getCurrentPageNumber()
            commonReaderState.pendingAnnotationPage.value = page
            commonReaderState.pendingAnnotationX.value = x
            commonReaderState.pendingAnnotationY.value = y
            commonReaderState.showAnnotationDialog.value = true
        }
    }
)
```

- [ ] **Step 3: Add button to open `ComicContentDialog` in the controls overlay in `ReaderContent.kt`**

Find where the settings/overlay buttons are rendered (near `SettingsOverlay` at line ~252). Add a state var and button:

```kotlin
var showComicContentDialog by remember { mutableStateOf(false) }
```

In the controls area (within the visible controls overlay), add an icon button:

```kotlin
import androidx.compose.material.icons.filled.BookmarkBorder
// ...
IconButton(onClick = { showComicContentDialog = true }) {
    Icon(Icons.Default.BookmarkBorder, contentDescription = "Annotations")
}
```

Then render the dialog:

```kotlin
if (showComicContentDialog) {
    ComicContentDialog(
        annotations = commonReaderState.annotations.collectAsState().value,
        onAnnotationTap = { annotation ->
            showComicContentDialog = false
            val loc = annotation.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation
            if (loc != null) {
                // Navigate to the page
                // pagedReaderState.onPageChange(loc.page) — map page number to spread index
                commonReaderState.editingComicAnnotation.value = annotation
                commonReaderState.showAnnotationDialog.value = true
            }
        },
        onDeleteAnnotation = { commonReaderState.deleteComicAnnotation(it) },
        onDismiss = { showComicContentDialog = false },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}
```

- [ ] **Step 4: Render `AnnotationDialog` for comic in `ReaderContent.kt`**

Add after the `ComicContentDialog` block:

```kotlin
val showAnnotationDialogComic by commonReaderState.showAnnotationDialog.collectAsState()
val editingComicAnnotation by commonReaderState.editingComicAnnotation.collectAsState()
val lastColorComic by commonReaderState.lastHighlightColor.collectAsState()

if (showAnnotationDialogComic) {
    val isEditing = editingComicAnnotation != null
    val loc = editingComicAnnotation?.location as? snd.komelia.annotations.AnnotationLocation.ComicLocation
    val pendingPage = commonReaderState.pendingAnnotationPage.value
    val pendingX = commonReaderState.pendingAnnotationX.value
    val pendingY = commonReaderState.pendingAnnotationY.value
    val referenceText = if (isEditing && loc != null) {
        "Page ${loc.page + 1} · (${(loc.x * 100).toInt()}%, ${(loc.y * 100).toInt()}%)"
    } else {
        "Page ${pendingPage + 1} · (${(pendingX * 100).toInt()}%, ${(pendingY * 100).toInt()}%)"
    }
    snd.komelia.ui.reader.common.AnnotationDialog(
        referenceText = referenceText,
        existingAnnotation = editingComicAnnotation,
        initialColor = lastColorComic,
        onSave = { note, color ->
            if (isEditing) {
                editingComicAnnotation?.let { commonReaderState.updateComicAnnotation(it, note, color) }
            } else {
                commonReaderState.saveComicAnnotation(pendingPage, pendingX, pendingY, color, note)
            }
            commonReaderState.showAnnotationDialog.value = false
            commonReaderState.editingComicAnnotation.value = null
        },
        onDelete = {
            editingComicAnnotation?.let { commonReaderState.deleteComicAnnotation(it) }
            commonReaderState.showAnnotationDialog.value = false
            commonReaderState.editingComicAnnotation.value = null
        },
        onDismiss = {
            commonReaderState.showAnnotationDialog.value = false
            commonReaderState.editingComicAnnotation.value = null
        },
    )
}
```

- [ ] **Step 5: Pass `annotations` and `onAnnotationTap` from `ReaderContent` through to `PagedReaderContent`**

In `ReaderContent.kt`, update the `PagedReaderContent(...)` call:

```kotlin
PagedReaderContent(
    // ... existing params ...
    annotations = commonReaderState.annotations.collectAsState().value,
    onAnnotationTap = { annotation ->
        commonReaderState.editingComicAnnotation.value = annotation
        commonReaderState.showAnnotationDialog.value = true
    },
    onLongPress = onLongPress
)
```

- [ ] **Step 6: Full build and verify**

```bash
./gradlew :komelia-app:assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ComicContentDialog.kt
git add komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt
git commit -m "feat: add ComicContentDialog and wire comic annotation UI"
```

---

## Verification Checklist

1. **EPUB highlights:** Select text → floating menu appears with 5 color swatches, Copy, Highlight, Note. Tap Highlight → text highlighted in chosen color. Close and reopen book → highlights persist and re-render.
2. **EPUB notes:** Tap Note → `AnnotationDialog` opens with selected text shown. Type a note, save. Tap the rendered highlight → dialog opens pre-filled with note and color.
3. **EPUB color persistence:** Change color → next highlight/annotation uses that color as default.
4. **EPUB Annotations tab:** Open content dialog → 4 tabs: Contents / Bookmarks / Annotations / Search. Annotations tab shows all annotations sorted by location. Each row: colored chip + chapter + location + text snippet + note preview (up to 2 lines). Tap row → navigates + opens edit dialog.
5. **EPUB delete:** Delete from Annotations tab or from edit dialog → annotation removed, highlight disappears from text.
6. **Comic long-press:** Long-press on a comic page → context menu with "Save Image" AND "Add Annotation". "Save Image" still works unchanged.
7. **Comic annotation create:** Tap "Add Annotation" → `AnnotationDialog` opens with "Page N · (x%, y%)" reference. Save → pin appears on the page.
8. **Comic pin tap:** Tap an existing pin → `AnnotationDialog` opens pre-filled. Edit or delete works.
9. **Comic Annotations tab:** Open `ComicContentDialog` → Annotations tab lists annotations sorted by page. Tap row → navigates to page + opens edit dialog.
10. **Page number display:** Comic annotation rows show "Page N" using 1-based page numbers (display: `loc.page + 1`). Verify this matches the actual page the pin was placed on.
