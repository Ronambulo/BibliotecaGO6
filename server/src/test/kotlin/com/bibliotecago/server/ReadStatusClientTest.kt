package com.bibliotecago.server

import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ReadStatusClientTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private val base: HttpUrl get() = "http://127.0.0.1:${server.port}/".toHttpUrl()

    private fun client() = ReadStatusClient(
        ServerConnection({ ServerConfig(tunnel = null, local = base, Credentials("lector", "contraseña")) })
    )

    private fun html(body: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "text/html").body(body).build()

    private fun redirect(to: String) = MockResponse.Builder().code(302).setHeader("Location", to).build()

    /** Como la plantilla de Calibre-Web: `data-checked` no debe confundirse con `checked`. */
    private fun detail(read: Boolean) = html(
        """
        <form id="have_read_form" action="/ajax/toggleread/7" method="POST">
          <input type="hidden" name="csrf_token" value="tok-ficha">
          <input id="have_read_cb" data-checked="Marcar como no leído"
            data-unchecked="Marcar como leído" type="checkbox"
            ${if (read) "checked" else ""}>
        </form>
        """.trimIndent()
    )

    @Test fun `inicia sesion y marca como leido`() = runTest {
        server.enqueue(redirect("/login?next=%2Fbook%2F7"))
        server.enqueue(
            html("""<form method="POST"><input type="hidden" name="csrf_token" value="tok-login"></form>""")
                .newBuilder().setHeader("Set-Cookie", "session=anonima; Path=/").build()
        )
        server.enqueue(redirect("/").newBuilder().setHeader("Set-Cookie", "session=abierta; Path=/").build())
        server.enqueue(detail(read = false))
        server.enqueue(html(""))

        client().setRead(7, true)

        assertEquals("/book/7", server.takeRequest().url.encodedPath)
        assertEquals("/login", server.takeRequest().url.encodedPath)
        val login = server.takeRequest()
        assertEquals("POST", login.method)
        val form = login.body!!.utf8()
        assertTrue(form.contains("csrf_token=tok-login"))
        assertTrue(form.contains("username=lector"))
        assertTrue(form.contains("remember_me=on"))
        assertFalse(form.contains("forgot"))
        assertEquals("session=abierta", server.takeRequest().headers["Cookie"])
        val toggle = server.takeRequest()
        assertEquals("/ajax/toggleread/7", toggle.url.encodedPath)
        assertEquals("tok-ficha", toggle.headers["X-CSRFToken"])
        assertEquals("session=abierta", toggle.headers["Cookie"])
    }

    @Test fun `si ya esta en ese estado no lo cambia`() = runTest {
        server.enqueue(detail(read = true))

        client().setRead(7, true)

        assertEquals("/book/7", server.takeRequest().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test fun `desmarca un libro leido`() = runTest {
        server.enqueue(detail(read = true))
        server.enqueue(html(""))

        client().setRead(7, false)

        server.takeRequest()
        assertEquals("/ajax/toggleread/7", server.takeRequest().url.encodedPath)
    }

    @Test fun `credenciales rechazadas`() = runTest {
        server.enqueue(redirect("/login"))
        server.enqueue(html("""<input type="hidden" name="csrf_token" value="tok-login">"""))
        server.enqueue(html("""<input type="hidden" name="csrf_token" value="tok-login"> Usuario o contraseña incorrectos"""))

        try {
            client().setRead(7, true)
            fail("Debía fallar")
        } catch (e: ServerException) {
            assertEquals(ConnectionProblem.Unauthorized, e.problem)
        }
    }

    @Test fun `id de Calibre sacado de la descarga`() {
        val book = Book(
            id = BookId("urn:uuid:abc"),
            title = "Dune",
            authors = emptyList(),
            files = listOf(BookFile(BookFormat.EPUB, "/opds/download/42/epub/", null)),
        )
        assertEquals(42, ReadStatusClient.calibreId(book))
        assertNull(ReadStatusClient.calibreId(book.copy(files = emptyList())))
    }
}
