package com.bibliotecago.reader.pdf

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Posición en el PDF: página (desde 0) y, en ajuste al ancho, qué parte de la
 * página está arriba de la pantalla, como fracción de su alto. Una fracción
 * no depende del tamaño de pantalla ni del recorte, así que sirve para guardar.
 */
data class PdfPosition(val page: Int, val offset: Float = 0f)

/**
 * Paso de pantalla en ajuste al ancho: la página se ve a trozos de arriba
 * abajo y luego se pasa a la siguiente. Cada trozo repite un poco del anterior
 * para no perder la línea que quedaba cortada.
 */
internal object PdfPaging {

    /** Parte de la pantalla que se repite al avanzar. */
    private const val OVERLAP = 0.05f

    /** Píxeles desde arriba de la página, sin pasar del último trozo. */
    fun offsetPx(offset: Float, pageHeight: Int, viewport: Int): Int =
        (offset * pageHeight).roundToInt().coerceIn(0, max(0, pageHeight - viewport))

    /** Siguiente pantalla; nula al final del libro. */
    fun next(page: Int, offsetPx: Int, pageHeight: Int, viewport: Int, pageCount: Int, fitWidth: Boolean): PdfPosition? = when {
        fitWidth && offsetPx + viewport < pageHeight -> PdfPosition(page, (offsetPx + step(viewport)).toFloat() / pageHeight)
        page + 1 < pageCount -> PdfPosition(page + 1, 0f)
        else -> null
    }

    /** Pantalla anterior; nula al principio. La página anterior se abre por su final. */
    fun previous(page: Int, offsetPx: Int, pageHeight: Int, viewport: Int, fitWidth: Boolean): PdfPosition? = when {
        fitWidth && offsetPx > 0 -> PdfPosition(page, max(0, offsetPx - step(viewport)).toFloat() / pageHeight)
        page > 0 -> PdfPosition(page - 1, if (fitWidth) 1f else 0f)
        else -> null
    }

    /** Avance del libro de 0 a 1, contando hasta lo último que se ve. */
    fun progression(page: Int, offsetPx: Int, pageHeight: Int, viewport: Int, pageCount: Int): Double {
        val seen = if (pageHeight <= 0) 1.0 else minOf(1.0, (offsetPx + viewport).toDouble() / pageHeight)
        return ((page + seen) / pageCount).coerceIn(0.0, 1.0)
    }

    private fun step(viewport: Int) = max(1, (viewport * (1 - OVERLAP)).roundToInt())
}
