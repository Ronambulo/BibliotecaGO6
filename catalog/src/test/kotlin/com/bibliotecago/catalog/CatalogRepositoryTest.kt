package com.bibliotecago.catalog

import com.bibliotecago.server.Credentials
import com.bibliotecago.server.ServerConfig
import com.bibliotecago.server.ServerConnection
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun page(ids: List<String>, next: String?) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/atom+xml")
        .body(
            buildString {
                append("""<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom">""")
                if (next != null) append("""<link rel="next" href="$next" type="application/atom+xml;profile=opds-catalog"/>""")
                for (id in ids) {
                    append("""<entry><title>Libro $id</title><id>$id</id>""")
                    append("""<link rel="http://opds-spec.org/acquisition" href="/opds/download/1/epub/" type="application/epub+zip"/>""")
                    append("</entry>")
                }
                append("</feed>")
            }
        )
        .build()

    @Test fun `los leidos se leen de todas las paginas`() = runTest {
        server.enqueue(page(listOf("urn:uuid:a", "urn:uuid:b"), next = "/opds/readbooks?offset=2"))
        server.enqueue(page(listOf("urn:uuid:c"), next = null))
        val base = "http://127.0.0.1:${server.port}/".toHttpUrl()
        val repository = CatalogRepository(ServerConnection({ ServerConfig(tunnel = null, local = base, Credentials("lector", "x")) }))

        val ids = repository.readBookIds()

        assertEquals(setOf("urn:uuid:a", "urn:uuid:b", "urn:uuid:c"), ids)
        assertEquals("/opds/readbooks", server.takeRequest().url.encodedPath)
        assertEquals("offset=2", server.takeRequest().url.encodedQuery)
    }
}
