# Phase 1: Offline PDF Support in Image Reader (Android)

## Objective
Enable reading local and offline PDF files directly inside Superra's Image Reader on Android without relying on the Komga server for page rasterization. This will allow users to download PDF books/comics for offline reading and open standalone `.pdf` files via Android Intents. **Online mode will remain unchanged and continue using server-side rendering.**

## Key Files & Context
- **New Interface & Implementation:**
  - `komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/mediacontainer/PdfExtractor.kt` (NEW)
  - `komelia-domain/offline/src/androidMain/kotlin/snd/komelia/offline/mediacontainer/AndroidPdfExtractor.kt` (NEW)
- **Modified Files:**
  - `komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/mediacontainer/BookContentExtractors.kt`
  - `komelia-domain/offline/src/androidMain/kotlin/snd/komelia/offline/AndroidModule.kt` (Instantiation of the Android implementation)
  - `komelia-app/src/androidMain/kotlin/snd/komelia/localfile/LocalFileBookApi.kt`

## Implementation Steps

### 1. Define `PdfExtractor` Interface (commonMain)
Create a new multiplatform interface `PdfExtractor` in `komelia-domain/offline/src/commonMain/kotlin/snd/komelia/offline/mediacontainer/`.
- `fun getPage(file: PlatformFile, pageNumber: Int): ByteArray`
- `fun getPageCount(file: PlatformFile): Int`

### 2. Implement Android PDF Engine (`androidMain`)
Create `AndroidPdfExtractor` utilizing Android's native `android.graphics.pdf.PdfRenderer`.
- **Initialization:** Use `ParcelFileDescriptor.open()` on the `PlatformFile`.
- **Metadata:** Implement `getPageCount()` by reading `PdfRenderer.pageCount`.
- **Rendering:**
  - Open the requested `PdfRenderer.Page`.
  - Create a `Bitmap` (e.g., scale to a reasonable maximum width/height like 2048px or use the native page size).
  - Use `page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)`.
  - Compress the `Bitmap` to a `ByteArray` (JPEG 90% or WEBP) and return it.
  - **Memory Safety:** Ensure `PdfRenderer`, `Page`, and `ParcelFileDescriptor` are closed in a `finally` block or handled via a reusable pool if performance requires it.

### 3. Integrate `PdfExtractor` into `BookContentExtractors`
Modify `BookContentExtractors.kt` to use the platform-specific extractor.
- Inject the `PdfExtractor` into the constructor.
- Update `getBookPage()`:
  - Locate the `MediaProfile.PDF` branch.
  - Replace the `TODO()` with `pdfExtractor.getPage(book.fileDownloadPath, page)`.
- Update `getFileContent()`:
  - For PDF profiles, throw an error or handle accordingly (PDFs are typically treated as single-file books, not containers for other files).

### 4. Update Android Local File Intents (`LocalFileBookApi.kt`)
Ensure standalone PDFs opened via Android intents use the new local rendering logic.
- **MIME Recognition:** Update `isEpub` logic to also identify `isPdf` via file extension or MIME type `application/pdf`.
- **Metadata Fetching:** In `getOne()`, if the file is a PDF, call `pdfExtractor.getPageCount()` to set the correct total page count for the reader.
- **Page Extraction:** In `getPage()`, if `isPdf` is true, call `pdfExtractor.getPage()` instead of the ZIP-based logic.

## Verification & Testing
1. **Offline Mode:**
   - Download a PDF while online.
   - Enter Airplane Mode (Offline).
   - Open the PDF and verify swiping through pages works via local `PdfRenderer`.
2. **Local Intent:**
   - Use an Android File Manager (like "Files") to open a `.pdf` with Superra.
   - Verify the book loads and displays correctly without a server connection.
3. **Regression Check:**
   - Open an online PDF and ensure it still uses the standard server-side image loading (verify via logs/network inspector).
