package com.bibliotecago.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfPagingTest {

    @Test
    fun `al ancho avanza por la pagina antes de pasar a la siguiente`() {
        // Página de 2500 px en una pantalla de 1000: paso de 950 (5 % repetido).
        val second = PdfPaging.next(page = 0, offsetPx = 0, pageHeight = 2500, viewport = 1000, pageCount = 3, fitWidth = true)
        assertEquals(PdfPosition(0, 950f / 2500), second)
        // El último trozo se ajusta para acabar justo al pie de la página.
        assertEquals(1500, PdfPaging.offsetPx(1900f / 2500, pageHeight = 2500, viewport = 1000))
        assertEquals(PdfPosition(1, 0f), PdfPaging.next(0, 1500, 2500, 1000, 3, fitWidth = true))
    }

    @Test
    fun `retroceder abre la pagina anterior por su final`() {
        val back = PdfPaging.previous(page = 1, offsetPx = 0, pageHeight = 2500, viewport = 1000, fitWidth = true)
        assertEquals(PdfPosition(0, 1f), back)
        assertEquals(1500, PdfPaging.offsetPx(1f, pageHeight = 2500, viewport = 1000))
        assertEquals(PdfPosition(0, 550f / 2500), PdfPaging.previous(0, 1500, 2500, 1000, fitWidth = true))
        assertEquals(PdfPosition(0, 0f), PdfPaging.previous(0, 500, 2500, 1000, fitWidth = true))
    }

    @Test
    fun `pagina completa pasa de pagina en pagina`() {
        assertEquals(PdfPosition(1, 0f), PdfPaging.next(0, 0, 900, 1000, 2, fitWidth = false))
        assertEquals(PdfPosition(0, 0f), PdfPaging.previous(1, 0, 900, 1000, fitWidth = false))
    }

    @Test
    fun `no pasa de los extremos del libro`() {
        assertNull(PdfPaging.next(1, 0, 900, 1000, 2, fitWidth = true))
        assertNull(PdfPaging.previous(0, 0, 900, 1000, fitWidth = true))
    }

    @Test
    fun `el progreso llega al 100 al final del libro`() {
        assertEquals(1.0, PdfPaging.progression(page = 1, offsetPx = 1500, pageHeight = 2500, viewport = 1000, pageCount = 2), 1e-9)
        assertEquals(0.2, PdfPaging.progression(page = 0, offsetPx = 0, pageHeight = 2500, viewport = 1000, pageCount = 2), 1e-9)
    }
}
