package com.bibliotecago.server

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ServerConnectionTest {

    private val server = MockWebServer()
    private val credentials = Credentials("lector", "contraseña-ñ")

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    /** Con IP literal: en Windows MockWebServer puede anunciar el nombre del equipo. */
    private val base: HttpUrl get() = "http://127.0.0.1:${server.port}/".toHttpUrl()

    private fun feed() = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/atom+xml")
        .body("""<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>""")
        .build()

    @Test fun `envia autenticacion basica a la URL configurada`() = runTest {
        server.enqueue(feed())
        val connection = ServerConnection({ ServerConfig(tunnel = null, local = base, credentials) })

        val path = connection.get("/opds/new?offset=60") { _, response -> response.request.url.encodedPath }

        assertEquals("/opds/new", path)
        val recorded = server.takeRequest()
        assertEquals(okhttp3.Credentials.basic("lector", "contraseña-ñ", Charsets.UTF_8), recorded.headers["Authorization"])
        assertEquals(base, connection.activeBase)
    }

    @Test fun `no envia credenciales a otros hosts`() = runTest {
        server.enqueue(feed())
        val other = "https://calibre.example.com/".toHttpUrl()
        val connection = ServerConnection({ ServerConfig(tunnel = other, local = null, credentials) })

        connection.client.newCall(Request.Builder().url(base.resolve("opds")!!).build()).execute().close()

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test fun `401 se traduce a credenciales incorrectas`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).setHeader("WWW-Authenticate", "Basic realm=\"calibre-web\"").build())
        val connection = ServerConnection({ ServerConfig(tunnel = null, local = base, credentials) })

        try {
            connection.get(null) { _, _ -> Unit }
            fail("Debería fallar")
        } catch (e: ServerException) {
            assertEquals(ConnectionProblem.Unauthorized, e.problem)
        }
    }

    @Test fun `si la URL local no responde se usa el tunel`() = runTest {
        server.enqueue(feed())
        // Puerto 1 en loopback: conexión rechazada al instante.
        val deadLocal = "http://127.0.0.1:1/".toHttpUrl()
        val connection = ServerConnection({ ServerConfig(tunnel = base, local = deadLocal, credentials) })

        connection.get(null) { _, _ -> Unit }

        assertEquals(base, connection.activeBase)
    }

    @Test fun `sin ninguna URL accesible el fallo es de red`() = runTest {
        val deadLocal = "http://127.0.0.1:1/".toHttpUrl()
        val connection = ServerConnection({ ServerConfig(tunnel = null, local = deadLocal, credentials) })

        try {
            connection.get(null) { _, _ -> Unit }
            fail("Debería fallar")
        } catch (e: ServerException) {
            assertEquals(ConnectionProblem.Unreachable, e.problem)
            assertNull(connection.activeBase)
        }
    }

    @Test fun `probar conexion detecta una pagina que no es OPDS`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("<html>Cloudflare</html>").build())
        val connection = ServerConnection({ null })

        val report = connection.test(ServerConfig(tunnel = null, local = base, credentials)).single()

        assertEquals(ConnectionProblem.NotOpds, report.problem)
        assertTrue(report.isLocal)
    }

    @Test fun `probar conexion correcta usa las credenciales sin guardar`() = runTest {
        server.enqueue(feed())
        val connection = ServerConnection({ null })

        val report = connection.test(ServerConfig(tunnel = null, local = base, credentials)).single()

        assertNull(report.problem)
        val recorded = server.takeRequest()
        assertEquals("/opds", recorded.url.encodedPath)
        assertEquals(okhttp3.Credentials.basic("lector", "contraseña-ñ", Charsets.UTF_8), recorded.headers["Authorization"])
    }
}
