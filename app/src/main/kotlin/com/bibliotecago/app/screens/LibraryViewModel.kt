package com.bibliotecago.app.screens

import android.content.Context
import androidx.core.content.edit
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
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /** El orden elegido se guarda (junto a lista o cuadrícula) y se recupera al abrir la app. */
    private val prefs = app.context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val savedOrder = prefs.getString(KEY_ORDER, null)

    var listing by mutableStateOf(
        CatalogListing.entries.firstOrNull { it.name == savedOrder } ?: CatalogListing.Recent
    )
        private set
    /**
     * Orden «Último abierto»: todo el catálogo, primero los libros abiertos en
     * el lector (del más reciente al más antiguo) y luego el resto por título.
     */
    var lastOpened by mutableStateOf(savedOrder == ORDER_LAST_OPENED)
        private set
    var state by mutableStateOf<LibraryState>(LibraryState.Loading)
        private set
    /** Búsqueda en curso (RF-03); nula, se muestra el listado elegido. */
    var query by mutableStateOf<String?>(null)
        private set
    /** Libros con algún formato ya en el dispositivo, para marcarlos en la cuadrícula. */
    var downloaded by mutableStateOf<Set<String>>(emptySet())
        private set
    var finished by mutableStateOf<Set<String>>(emptySet())
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
        viewModelScope.launch { app.finishedBookIds().collect { finished = it } }
        // Libros nuevos de la consulta periódica. En «Añadidos recientemente» suben al
        // principio sin recargar; en otro orden se vuelve a pedir lo ya cargado y se
        // cambia sin pasar por «Cargando», para no mover lo que se está mirando.
        viewModelScope.launch {
            var knownRecent: Set<BookId>? = null
            app.recentBooks.collect { fresh ->
                if (fresh == null) {
                    knownRecent = null
                    return@collect
                }
                val ids = fresh.mapTo(HashSet()) { it.id }
                val arrived = knownRecent?.let { (ids - it).isNotEmpty() } ?: false
                knownRecent = ids
                val current = state as? LibraryState.Content ?: return@collect
                if (query != null) return@collect
                if (listing == CatalogListing.Recent && !lastOpened) {
                    val known = current.books.mapTo(HashSet()) { it.id }
                    val added = fresh.filter { it.id !in known }
                    if (added.isNotEmpty()) state = current.copy(books = added + current.books)
                } else if (arrived) {
                    refreshInPlace()
                }
            }
        }
        // Vuelve la conexión: si la pantalla se quedó en error, se reintenta sola.
        viewModelScope.launch {
            app.reconnected.collect {
                when (val current = state) {
                    is LibraryState.Failed -> reload()
                    is LibraryState.Content -> if (current.moreProblem != null) loadMore()
                    LibraryState.Loading -> Unit
                }
            }
        }
        // Al volver del lector, el libro abierto sube al principio sin recargar.
        viewModelScope.launch {
            openedIds().drop(1).collect { ids ->
                val current = state as? LibraryState.Content ?: return@collect
                if (lastOpened && query == null) state = current.copy(books = openedFirst(current.books, ids))
            }
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
        if (newListing == listing && !lastOpened && query == null && state !is LibraryState.Failed) return
        listing = newListing
        lastOpened = false
        query = null
        prefs.edit { putString(KEY_ORDER, newListing.name) }
        reload()
    }

    fun selectLastOpened() {
        if (lastOpened && query == null) return
        lastOpened = true
        query = null
        prefs.edit { putString(KEY_ORDER, ORDER_LAST_OPENED) }
        reload()
    }

    fun reload() {
        job?.cancel()
        nextHref = null
        state = LibraryState.Loading
        job = viewModelScope.launch {
            state = try {
                if (lastOpened && query == null) {
                    accept(app.catalog.listing(CatalogListing.Title), previous = openedBooks())
                } else {
                    val feed = query?.let { app.catalog.search(it) } ?: app.catalog.listing(listing)
                    accept(feed, previous = emptyList())
                }
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

    /**
     * Vuelve a pedir el listado desde la primera página hasta tener al menos lo
     * que ya había cargado, y lo cambia de una vez. Si falla, se queda lo que había.
     */
    private fun refreshInPlace() {
        val current = state as? LibraryState.Content ?: return
        if (job?.isActive == true) return
        val wanted = current.books.size
        val oldNext = nextHref
        job = viewModelScope.launch {
            try {
                var fresh = if (lastOpened) {
                    accept(app.catalog.listing(CatalogListing.Title), previous = openedBooks())
                } else {
                    accept(app.catalog.listing(listing), previous = emptyList())
                }
                while (fresh.books.size < wanted) {
                    val href = nextHref ?: break
                    fresh = accept(app.catalog.feed(href), previous = fresh.books)
                }
                state = fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nextHref = oldNext
            }
        }
    }

    /** Libros abiertos en el lector, del más reciente al más antiguo. */
    private suspend fun openedBooks() = openedIds().first().mapNotNull { id ->
        BookId(id).let { app.books[it] ?: app.downloads.savedBook(it) }
    }

    /** Ids de los libros abiertos en el lector, del más reciente al más antiguo. */
    private fun openedIds() = app.library.progress.observeAll()
        .map { rows -> rows.map { it.bookId }.distinct() }
        .distinctUntilChanged()

    /** Sube al principio los libros ya cargados que estén en [ids], en ese orden. */
    private fun openedFirst(books: List<Book>, ids: List<String>): List<Book> {
        val rank = ids.withIndex().associate { (index, id) -> id to index }
        val (opened, rest) = books.partition { it.id.value in rank }
        return opened.sortedBy { rank.getValue(it.id.value) } + rest
    }

    private fun accept(feed: OpdsFeed, previous: List<Book>): LibraryState.Content {
        nextHref = feed.nextHref
        // Por si el catálogo cambia entre páginas (offset desplazado): sin duplicados.
        val books = (previous + feed.books).distinctBy { it.id }
        app.books.putAll(feed.books)
        return LibraryState.Content(books, hasMore = feed.nextHref != null)
    }
}

private const val KEY_ORDER = "library_order"
private const val ORDER_LAST_OPENED = "lastOpened"
