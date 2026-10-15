package com.bibliotecago.app.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bibliotecago.app.AppContainer
import com.bibliotecago.catalog.CatalogListing
import com.bibliotecago.catalog.OpdsFeed
import com.bibliotecago.core.model.Book
import com.bibliotecago.server.ConnectionProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Failed(val problem: ConnectionProblem) : LibraryState
    data class Content(
        val books: List<Book>,
        /** Quedan páginas en el servidor (`rel="next"`). */
        val hasMore: Boolean,
        /** Fallo al pedir la página siguiente; lo ya cargado se sigue mostrando. */
        val moreProblem: ConnectionProblem? = null,
    ) : LibraryState
}

/**
 * Catálogo del servidor, cargado por páginas de OPDS según se avanza. Sin
 * Paging 3: la cuadrícula E Ink trabaja con listas y páginas fijas, y un
 * cursor `rel="next"` basta para pedir la siguiente.
 */
class LibraryViewModel(private val app: AppContainer) : ViewModel() {

    var listing by mutableStateOf(CatalogListing.Recent)
        private set
    var state by mutableStateOf<LibraryState>(LibraryState.Loading)
        private set
    /** Búsqueda en curso (RF-03); nula, se muestra el listado elegido. */
    var query by mutableStateOf<String?>(null)
        private set
    /** Libros con algún formato ya en el dispositivo, para marcarlos en la cuadrícula. */
    var downloaded by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Base con la que resolver portadas: la URL (local o túnel) de la última respuesta. */
    val coverBase: HttpUrl? get() = app.coverBase

    private var nextHref: String? = null
    private var job: Job? = null

    init {
        reload()
        // Al guardar otro servidor en Ajustes, se recarga desde cero.
        viewModelScope.launch { app.serverConfig.config.drop(1).collect { reload() } }
        viewModelScope.launch {
            app.downloadedBookIds().collect { downloaded = it }
        }
    }

    /** Busca por título o autor en el servidor. La búsqueda se queda al volver de una ficha. */
    fun search(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        reload()
    }

    fun clearSearch() {
        if (query == null) return
        query = null
        reload()
    }

    fun select(newListing: CatalogListing) {
        if (newListing == listing && query == null && state !is LibraryState.Failed) return
        listing = newListing
        query = null
        reload()
    }

    fun reload() {
        job?.cancel()
        nextHref = null
        state = LibraryState.Loading
        job = viewModelScope.launch {
            state = try {
                val feed = query?.let { app.catalog.search(it) } ?: app.catalog.listing(listing)
                accept(feed, previous = emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryState.Failed(ConnectionProblem.fromException(e))
            }
        }
    }

    fun loadMore() {
        val current = state as? LibraryState.Content ?: return
        val href = nextHref ?: return
        if (job?.isActive == true) return
        state = current.copy(moreProblem = null)
        job = viewModelScope.launch {
            state = try {
                accept(app.catalog.feed(href), previous = current.books)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                current.copy(moreProblem = ConnectionProblem.fromException(e))
            }
        }
    }

    private fun accept(feed: OpdsFeed, previous: List<Book>): LibraryState.Content {
        nextHref = feed.nextHref
        // Por si el catálogo cambia entre páginas (offset desplazado): sin duplicados.
        val books = (previous + feed.books).distinctBy { it.id }
        app.books.putAll(feed.books)
        return LibraryState.Content(books, hasMore = feed.nextHref != null)
    }
}
