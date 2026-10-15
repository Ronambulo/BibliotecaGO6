package com.bibliotecago.app

import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.library.FinishedBook
import com.bibliotecago.library.FinishedStore
import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.ReadStatusClient
import com.bibliotecago.server.ServerException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Sincroniza en los dos sentidos la marca de leído con Calibre-Web.
 *
 * - App → Calibre: en cuanto cambia algo, se envía; lo que no se puede enviar
 *   queda pendiente en Room para el siguiente intento.
 * - Calibre → app: al volver a la app, cada 5 minutos en pantalla y al volver la red se
 *   leen los leídos del servidor (`/opds/readbooks`). Antes se envía lo
 *   pendiente, y lo pendiente nunca se pisa: gana el último cambio hecho aquí.
 */
class ReadSync(
    private val finished: FinishedStore,
    private val client: ReadStatusClient,
    private val fetchRead: suspend () -> Set<String>,
    private val findBook: suspend (BookId) -> Book?,
) {
    private val attempts = MutableStateFlow(0)
    /** Envío y lectura no se solapan: un envío a medias no debe deshacerse al leer. */
    private val lock = Mutex()

    fun start(scope: CoroutineScope) {
        scope.launch {
            finished.observePending().collectLatest { lock.withLock { push(it) } }
        }
        scope.launch {
            attempts.collectLatest {
                lock.withLock {
                    if (push(finished.pending())) pull()
                }
            }
        }
    }

    /** Envía lo pendiente y trae lo marcado en Calibre-Web; p. ej. al recuperar la red. */
    fun retry() = attempts.update { it + 1 }

    private suspend fun pull() {
        val ids = try {
            fetchRead()
        } catch (e: IOException) {
            return
        }
        finished.applyRemote(ids)
    }

    /** `true` si se envió todo lo pendiente. */
    private suspend fun push(pending: List<FinishedBook>): Boolean {
        for (row in pending) {
            val calibreId = findBook(BookId(row.bookId))?.let(ReadStatusClient::calibreId)
            if (calibreId != null) {
                try {
                    client.setRead(calibreId, row.finished)
                } catch (e: IOException) {
                    // Borrado en el servidor: no hay nada que marcar. Lo demás (sin red,
                    // sin configurar, credenciales) se reintenta más tarde.
                    if ((e as? ServerException)?.problem != ConnectionProblem.NotFound) return false
                }
            }
            // Sin id de Calibre no hay forma de enviarlo: se da por hecho.
            finished.markSynced(row)
        }
        return true
    }
}
