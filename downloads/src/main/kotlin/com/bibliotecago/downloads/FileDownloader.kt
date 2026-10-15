package com.bibliotecago.downloads

import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.ServerConnection
import com.bibliotecago.server.ServerException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.io.File

/**
 * Baja un archivo del servidor. Escribe en un `.part` y solo lo renombra
 * cuando ha llegado entero: un libro a medias nunca parece descargado.
 */
class FileDownloader(private val connection: ServerConnection) {

    /**
     * Descarga [href] en [target] y devuelve su tamaño. [onProgress] recibe los
     * bytes escritos hasta el momento. Si algo falla no queda ningún archivo.
     */
    suspend fun download(href: String, target: File, onProgress: (Long) -> Unit = {}): Long {
        val job = currentCoroutineContext()[Job]
        val part = File(target.parentFile, target.name + PART_SUFFIX)
        target.parentFile?.mkdirs()
        try {
            val size = connection.get(href) { _, response ->
                val body = response.body
                // Un portal de login o un desafío de Cloudflare responden 200 con HTML.
                val type = body.contentType()
                if (type?.type == "text" && type.subtype == "html") throw ServerException(ConnectionProblem.NotOpds)

                val expected = body.contentLength()
                var written = 0L
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            // La lectura es bloqueante: se mira a mano si han cancelado la descarga.
                            if (job?.isActive == false) throw CancellationException("Descarga cancelada")
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written)
                        }
                    }
                }
                checkComplete(written, expected)
                written
            }
            target.delete()
            if (!part.renameTo(target)) throw ServerException(ConnectionProblem.Unknown)
            return size
        } finally {
            part.delete()
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val BUFFER_BYTES = 64 * 1024

        /** Vacío, o distinto de lo anunciado en `Content-Length` (-1 si no vino): incompleto. */
        fun checkComplete(written: Long, contentLength: Long) {
            if (written == 0L || (contentLength >= 0 && written != contentLength)) {
                throw ServerException(ConnectionProblem.Incomplete)
            }
        }
    }
}
