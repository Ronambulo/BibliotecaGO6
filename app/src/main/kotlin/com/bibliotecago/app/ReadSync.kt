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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Lleva a Calibre-Web los libros marcados (o desmarcados) como leídos. Se
 * intenta en cuanto cambia algo, al arrancar y al volver la red; lo que no se
 * puede enviar queda pendiente en Room para el siguiente intento.
 */
class ReadSync(
    private val finished: FinishedStore,
    private val client: ReadStatusClient,
    private val findBook: suspend (BookId) -> Book?,
) {
    private val attempts = MutableStateFlow(0)

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(finished.observePending(), attempts) { pending, _ -> pending }
                .collectLatest(::push)
        }
    }

    /** Vuelve a intentar lo pendiente; p. ej. al recuperar la red. */
    fun retry() = attempts.update { it + 1 }

    private suspend fun push(pending: List<FinishedBook>) {
        for (row in pending) {
            val calibreId = findBook(BookId(row.bookId))?.let(ReadStatusClient::calibreId)
            if (calibreId != null) {
                try {
                    client.setRead(calibreId, row.finished)
                } catch (e: IOException) {
                    // Borrado en el servidor: no hay nada que marcar. Lo demás (sin red,
                    // sin configurar, credenciales) se reintenta más tarde.
                    if ((e as? ServerException)?.problem != ConnectionProblem.NotFound) return
                }
            }
            // Sin id de Calibre no hay forma de enviarlo: se da por hecho.
            finished.markSynced(row)
        }
    }
}
