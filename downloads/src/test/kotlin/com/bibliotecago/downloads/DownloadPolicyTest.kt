package com.bibliotecago.downloads

import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.downloads.DownloadPolicy.Decision
import com.bibliotecago.server.ConnectionProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test fun `los cortes de red se reintentan hasta el limite`() {
        assertTrue(DownloadPolicy.shouldRetry(ConnectionProblem.Timeout, attempt = 0))
        assertTrue(DownloadPolicy.shouldRetry(ConnectionProblem.Unreachable, attempt = 1))
        assertFalse(DownloadPolicy.shouldRetry(ConnectionProblem.Unreachable, attempt = DownloadPolicy.MAX_ATTEMPTS - 1))
    }

    @Test fun `los errores que no se arreglan solos no se reintentan`() {
        listOf(ConnectionProblem.Unauthorized, ConnectionProblem.Forbidden, ConnectionProblem.NotFound, ConnectionProblem.NoSpace)
            .forEach { assertFalse(it.name, DownloadPolicy.shouldRetry(it, attempt = 0)) }
    }

    @Test fun `no se duplica una descarga terminada ni una en curso`() {
        assertEquals(Decision.AlreadyDownloaded, DownloadPolicy.decide(DownloadStatus.DONE, fileExists = true))
        assertEquals(Decision.KeepCurrent, DownloadPolicy.decide(DownloadStatus.RUNNING, fileExists = false))
        assertEquals(Decision.KeepCurrent, DownloadPolicy.decide(DownloadStatus.QUEUED, fileExists = false))
    }

    @Test fun `se descarga de nuevo si fallo o si el archivo se perdio`() {
        assertEquals(Decision.Start, DownloadPolicy.decide(null, fileExists = false))
        assertEquals(Decision.Start, DownloadPolicy.decide(DownloadStatus.FAILED, fileExists = false))
        assertEquals(Decision.Start, DownloadPolicy.decide(DownloadStatus.DONE, fileExists = false))
    }

    @Test fun `nombres de archivo estables y seguros`() {
        assertEquals(
            "0c5a6b1e-2f6d-4b1a-9a43-1f0a7c2d9e11.epub",
            DownloadManager.fileName("urn:uuid:0c5a6b1e-2f6d-4b1a-9a43-1f0a7c2d9e11", BookFormat.EPUB),
        )
        assertEquals("a_b.pdf", DownloadManager.fileName("../a/b", BookFormat.PDF))
        assertTrue(DownloadManager.fileName("::", BookFormat.PDF).endsWith(".pdf"))
    }
}
