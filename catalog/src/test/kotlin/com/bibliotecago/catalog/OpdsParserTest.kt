package com.bibliotecago.catalog

import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas sobre respuestas reales de Calibre-Web 0.6.27 (Fase 0). */
class OpdsParserTest {

    private fun fixture(name: String): OpdsFeed =
        javaClass.getResourceAsStream("/opds/$name")!!.use(OpdsParser::parse)

    @Test fun `raiz de navegacion`() {
        val feed = fixture("root.xml")

        assertTrue(feed.books.isEmpty())
        assertEquals(15, feed.navigation.size)
        assertEquals("/opds/books", feed.navigation.first().href)
        assertEquals("Libros ordenados alfabéticamente", feed.navigation.first().summary)
        assertTrue(feed.navigation.any { it.href == "/opds/new" })
        assertTrue(feed.navigation.any { it.href == "/opds/language/" })
        assertEquals("/opds/search/{searchTerms}", feed.searchTemplate)
        assertNull(feed.nextHref)
    }

    @Test fun `libro EPUB con todos los metadatos`() {
        val book = fixture("new.xml").books.first()

        assertEquals("urn:uuid:de884aab-b034-46d9-b486-2471a759515a", book.id.value)
        assertEquals("Harry Potter and the Sorcerer's Stone", book.title)
        assertEquals(listOf("J.K. Rowling", "Mary GrandPré"), book.authors)
        assertEquals(listOf("harry potter"), book.tags)
        assertEquals("urd", book.language)
        assertEquals(4, book.rating)
        assertEquals("/opds/cover/2", book.coverPath)
        assertEquals(listOf(BookFile(BookFormat.EPUB, "/opds/download/2/epub/", 156_715_033)), book.files)
        assertEquals(listOf(BookFormat.EPUB), book.formats)
    }

    @Test fun `la sinopsis se desescapa y pierde las etiquetas`() {
        val summary = fixture("new.xml").books.first().summary!!

        assertEquals(
            "Sinopsis de prueba con 'comillas', comillas tipográficas ’ y una lista • uno • dos. Segunda frase de relleno.",
            summary,
        )
    }

    @Test fun `libro PDF sin sinopsis ni valoracion`() {
        val book = fixture("new.xml").books[1]

        assertEquals("TFG - Enrique Rodríguez del Real", book.title)
        assertNull(book.summary)
        assertNull(book.rating)
        assertNull(book.language)
        assertTrue(book.tags.isEmpty())
        assertEquals(listOf(BookFile(BookFormat.PDF, "/opds/download/1/pdf/", 620_626)), book.files)
    }

    @Test fun `la fecha de publicacion sin rellenar no rompe nada`() {
        // <published>101-01-01T00:00:00+00:00</published> en ambos libros.
        assertEquals(2, fixture("new.xml").books.size)
    }

    @Test fun `paginacion por rel next`() {
        val feed = fixture("new-page1.xml")

        assertEquals(1, feed.books.size)
        assertEquals("/opds/new?offset=1", feed.nextHref)
        assertNull(fixture("new.xml").nextHref)
    }

    @Test fun `resultado de busqueda`() {
        val feed = fixture("search.xml")

        assertEquals(listOf("TFG - Enrique Rodríguez del Real"), feed.books.map { it.title })
        assertTrue(feed.navigation.isEmpty())
    }

    @Test fun `Calibre-Web-Automated manda la sinopsis en summary`() {
        val book = fixture("cwa-search.xml").books.single()

        assertTrue(book.summary!!.startsWith("Turning the envelope over"))
        assertTrue("a large letter 'H'." in book.summary!!)
        assertEquals(listOf("J.K. Rowling"), book.authors)
        assertEquals("eng", book.language)
        assertNull(book.rating)
        assertEquals(listOf(BookFile(BookFormat.EPUB, "/opds/download/2/epub/", 156_715_033)), book.files)
    }

    @Test fun `termino de busqueda como segmento de ruta`() {
        assertEquals(
            "/opds/search/el%20nombre%20del%20viento",
            CatalogRepository.searchHref(" el nombre del viento ", "/opds/search/{searchTerms}"),
        )
        assertEquals("/opds/search/Garc%C3%ADa%20M%C3%A1rquez", CatalogRepository.searchHref("García Márquez", "/opds/search/{searchTerms}"))
    }
}
