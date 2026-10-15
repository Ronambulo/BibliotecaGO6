package com.bibliotecago.downloads

import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.Credentials
import com.bibliotecago.server.ServerConfig
import com.bibliotecago.server.ServerConnection
import com.bibliotecago.server.ServerException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileDownloaderTest {

    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var downloader: FileDownloader
    private lateinit var target: File

    @Before fun setUp() {
        server.start()
        val base = "http://127.0.0.1:${server.port}/".toHttpUrl()
        val connection = ServerConnection({ ServerConfig(tunnel = null, local = base, Credentials("lector", "x")) })
        downloader = FileDownloader(connection)
        target = File(folder.root, "books/libro.epub")
    }

    @After fun tearDown() = server.close()

    private val content = ByteArray(200_000) { (it % 251).toByte() }

    private fun epub(body: ByteArray = content) = MockResponse.Builder()
        .setHeader("Content-Type", "application/epub+zip")
        .body(okio.Buffer().write(body))

    @Test fun `descarga el archivo completo y avisa del progreso`() = runTest {
        server.enqueue(epub().build())
        val progress = mutableListOf<Long>()

        val size = downloader.download("/opds/download/2/epub/", target) { progress += it }

        assertEquals(content.size.toLong(), size)
        assertArrayEquals(content, target.readBytes())
        assertEquals(content.size.toLong(), progress.last())
        assertEquals("/opds/download/2/epub/", server.takeRequest().url.encodedPath)
        assertFalse(File(target.parentFile, "libro.epub.part").exists())
    }

    @Test fun `un corte de red a mitad no deja archivo`() = runTest {
        server.enqueue(epub().onResponseBody(SocketEffect.ShutdownConnection).build())

        try {
            downloader.download("/opds/download/2/epub/", target)
            fail("Debería fallar")
        } catch (e: java.io.IOException) {
            val problem = ConnectionProblem.fromException(e)
            assertTrue(DownloadPolicy.shouldRetry(problem, attempt = 0))
        }
        assertFalse(target.exists())
        assertTrue(target.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test fun `un archivo vacio se rechaza`() = runTest {
        server.enqueue(epub(ByteArray(0)).build())

        val problem = runCatching { downloader.download("/opds/download/2/epub/", target) }.exceptionOrNull()

        assertEquals(ConnectionProblem.Incomplete, (problem as ServerException).problem)
        assertFalse(target.exists())
    }

    @Test fun `una pagina HTML no se guarda como libro`() = runTest {
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "text/html; charset=utf-8").body("<html>login</html>").build())

        val problem = runCatching { downloader.download("/opds/download/2/epub/", target) }.exceptionOrNull()

        assertEquals(ConnectionProblem.NotOpds, (problem as ServerException).problem)
        assertFalse(target.exists())
    }

    @Test fun `un error HTTP no se reintenta`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())

        val problem = runCatching { downloader.download("/opds/download/2/epub/", target) }.exceptionOrNull()

        assertEquals(ConnectionProblem.Forbidden, ConnectionProblem.fromException(problem!!))
        assertFalse(DownloadPolicy.shouldRetry(ConnectionProblem.Forbidden, attempt = 0))
    }

    @Test fun `la descarga repetida sustituye a la anterior sin duplicar`() = runTest {
        server.enqueue(epub().build())
        server.enqueue(epub().build())

        downloader.download("/opds/download/2/epub/", target)
        downloader.download("/opds/download/2/epub/", target)

        assertEquals(listOf("libro.epub"), target.parentFile!!.list()!!.toList())
        assertArrayEquals(content, target.readBytes())
    }

    @Test fun `tamano distinto del anunciado es incompleto`() {
        val problem = runCatching { FileDownloader.checkComplete(written = 99, contentLength = 100) }.exceptionOrNull()
        assertEquals(ConnectionProblem.Incomplete, (problem as ServerException).problem)
        FileDownloader.checkComplete(written = 100, contentLength = 100)
        FileDownloader.checkComplete(written = 100, contentLength = -1)
    }
}
