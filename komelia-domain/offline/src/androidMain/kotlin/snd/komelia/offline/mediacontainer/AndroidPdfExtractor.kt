package snd.komelia.offline.mediacontainer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class AndroidPdfExtractor(private val context: Context) : PdfExtractor {
    override fun getPageCount(file: PlatformFile): Int {
        return openRenderer(file).use { it.pageCount }
    }

    override fun getPage(file: PlatformFile, pageNumber: Int): ByteArray {
        openRenderer(file).use { renderer ->
            renderer.openPage(pageNumber - 1).use { page ->
                val (width, height) = calculateSize(page.width, page.height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                bitmap.recycle()
                return outputStream.toByteArray()
            }
        }
    }

    private fun openRenderer(file: PlatformFile): PdfRenderer {
        val pfd = when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> ParcelFileDescriptor.open(
                androidFile.file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            is AndroidFile.UriWrapper -> context.contentResolver.openFileDescriptor(androidFile.uri, "r")
        } ?: error("Failed to open file descriptor for $file")
        return PdfRenderer(pfd)
    }

    private fun calculateSize(pageWidth: Int, pageHeight: Int): Pair<Int, Int> {
        val maxDimension = 2048f
        return if (pageWidth > maxDimension || pageHeight > maxDimension) {
            val ratio = pageWidth.toFloat() / pageHeight.toFloat()
            if (ratio > 1) {
                (maxDimension).roundToInt() to (maxDimension / ratio).roundToInt()
            } else {
                (maxDimension * ratio).roundToInt() to (maxDimension).roundToInt()
            }
        } else {
            pageWidth to pageHeight
        }
    }
}
