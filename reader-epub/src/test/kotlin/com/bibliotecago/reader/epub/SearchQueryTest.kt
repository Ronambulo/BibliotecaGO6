package com.bibliotecago.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `sin comillas no es literal`() {
        assertEquals(SearchQuery("la casa", literal = false), SearchQuery.parse("  la casa "))
    }

    @Test
    fun `entre comillas es literal y sin ellas`() {
        assertEquals(SearchQuery("La Casa", literal = true), SearchQuery.parse("\"La Casa\""))
        assertEquals(SearchQuery("acción", literal = true), SearchQuery.parse("“acción”"))
        assertEquals(SearchQuery("dijo", literal = true), SearchQuery.parse("«dijo»"))
        assertEquals(SearchQuery("dijo", literal = true), SearchQuery.parse("'dijo'"))
    }

    @Test
    fun `basta la comilla de apertura`() {
        assertEquals(SearchQuery("Sancho", literal = true), SearchQuery.parse("\"Sancho"))
    }

    @Test
    fun `las comillas de dentro son parte del texto`() {
        assertEquals(SearchQuery("el \"loco\" hidalgo", literal = false), SearchQuery.parse("el \"loco\" hidalgo"))
    }

    @Test
    fun `nada que buscar`() {
        assertNull(SearchQuery.parse("   "))
        assertNull(SearchQuery.parse("\"\""))
        assertNull(SearchQuery.parse("« »"))
    }
}
