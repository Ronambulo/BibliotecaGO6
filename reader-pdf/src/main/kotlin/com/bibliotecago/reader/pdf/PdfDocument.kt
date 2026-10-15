package com.bibliotecago.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PDF abierto con el `PdfRenderer` de Android (PDFium por dentro). Solo admite
 * una página abierta a la vez, así que todo pasa por un candado; las llamadas
 * bloquean y deben hacerse fuera del hilo principal.
 */
internal class PdfDocument private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    /** Zona con contenido de cada página, en puntos; se calcula al pedirla. */
    private val contentBounds = arrayOfNulls<RectF>(pageCount)
    private var closed = false

    /**
     * Pinta la página [index] para una pantalla de [width]×[height] píxeles. Al
     * ancho, la imagen mide [width] de ancho y lo que haga falta de alto; a
     * página completa, cabe entera. Con [crop] se quitan los márgenes en blanco.
     */
    fun render(index: Int, width: Int, height: Int, fit: PdfFit, crop: Boolean): Bitmap = synchronized(renderer) {
        check(!closed) { "PDF cerrado" }
        renderer.openPage(index).use { page ->
            val full = RectF(0f, 0f, page.width.toFloat(), page.height.toFloat())
            val bounds = if (crop) contentBounds[index] ?: findContent(page).also { contentBounds[index] = it } else full
            val scale = when (fit) {
                PdfFit.Width -> width / bounds.width()
                PdfFit.Page -> min(width / bounds.width(), height / bounds.height())
            }
            val bitmapWidth = (bounds.width() * scale).roundToInt().coerceIn(1, width)
            // Páginas muy alargadas: se limita el alto para no agotar la memoria.
            val bitmapHeight = (bounds.height() * scale).roundToInt().coerceIn(1, MAX_SCREENS * height)
            val bitmap = createBitmap(bitmapWidth, bitmapHeight)
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply {
                postTranslate(-bounds.left, -bounds.top)
                postScale(scale, scale)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /**
     * Busca el rectángulo con algo pintado en una versión pequeña de la página.
     * Deja un pequeño borde para no pegar el texto al canto de la pantalla.
     */
    private fun findContent(page: PdfRenderer.Page): RectF {
        val full = RectF(0f, 0f, page.width.toFloat(), page.height.toFloat())
        val scale = SAMPLE_WIDTH.toFloat() / page.width
        val w = SAMPLE_WIDTH
        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
        val sample = createBitmap(w, h)
        sample.eraseColor(Color.WHITE)
        page.render(sample, null, Matrix().apply { postScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
        sample.recycle()

        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val luminance = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (luminance < INK_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return full
        val pad = SAMPLE_PADDING
        val found = RectF(
            ((left - pad) / scale).coerceAtLeast(0f),
            ((top - pad) / scale).coerceAtLeast(0f),
            ((right + 1 + pad) / scale).coerceAtMost(full.right),
            ((bottom + 1 + pad) / scale).coerceAtMost(full.bottom),
        )
        // Casi nada pintado (una mota, un número de página suelto): mejor no recortar.
        return if (found.width() < full.width() * MIN_CONTENT || found.height() < full.height() * MIN_CONTENT) full else found
    }

    override fun close() = synchronized(renderer) {
        if (closed) return
        closed = true
        renderer.close()
        fd.close()
    }

    companion object {
        private const val SAMPLE_WIDTH = 300
        private const val SAMPLE_PADDING = 4
        private const val INK_THRESHOLD = 220
        private const val MIN_CONTENT = 0.2f
        private const val MAX_SCREENS = 6

        fun open(file: File): PdfDocument {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfDocument(fd, PdfRenderer(fd))
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        }
    }
}
