package com.bibliotecago.reader.epub

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.library.Bookmark
import com.bibliotecago.library.bookmarks
import com.bibliotecago.library.finishedBooks
import com.bibliotecago.library.readingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

sealed interface ReaderState {
    data object Opening : ReaderState
    class Ready(val publication: Publication, val savedLocator: Locator?, val positionCount: Int) : ReaderState
    data class Failed(val message: String) : ReaderState
}

enum class ReaderPanel { None, Text, Contents, Bookmarks, Search }

/** Búsqueda en curso: resultados encontrados hasta ahora, en orden de lectura. */
data class SearchState(
    val query: SearchQuery,
    val results: List<Locator> = emptyList(),
    val loading: Boolean = true,
    /** Ya se ha recorrido el libro entero. */
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * Abre el EPUB con Readium y guarda la posición en cada cambio de página. Vive
 * más que la actividad: la publicación no se vuelve a abrir al recrearla.
 */
class EpubReaderViewModel(application: Application, state: SavedStateHandle) : AndroidViewModel(application) {

    private val file = File(checkNotNull(state.get<String>(EXTRA_PATH)))
    private val bookId = BookId(checkNotNull(state.get<String>(EXTRA_BOOK_ID)))
    val title: String = state.get<String>(EXTRA_TITLE).orEmpty()

    private val store = application.readingProgress

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Opening)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /** Posición actual; nula hasta que el navegador muestra la primera página. */
    var locator by mutableStateOf<Locator?>(null)
        private set

    var controlsVisible by mutableStateOf(false)

    /** Panel abierto sobre el libro, además del menú. */
    var panel by mutableStateOf(ReaderPanel.None)

    private val bookmarkStore = application.bookmarks

    /** Marcadores del libro, en orden de lectura. */
    val bookmarks: StateFlow<List<Bookmark>> = bookmarkStore.observe(bookId, BookFormat.EPUB)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val finishedStore = application.finishedBooks

    /** Si el libro está marcado como leído. */
    val finished: StateFlow<Boolean> = finishedStore.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFinished() {
        val now = !finished.value
        viewModelScope.launch { finishedStore.set(bookId, now) }
    }

    private val settingsStore = ReaderSettingsStore(application)

    var settings by mutableStateOf(settingsStore.load())
        private set

    fun updateSettings(settings: ReaderSettings) {
        this.settings = settings
        settingsStore.save(settings)
    }

    init {
        viewModelScope.launch { _state.value = open() }
    }

    private suspend fun open(): ReaderState = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext ReaderState.Failed("No se encuentra el archivo. Vuelve a descargar el libro.")
        val app = getApplication<Application>()
        val http = DefaultHttpClient()
        val retriever = AssetRetriever(app.contentResolver, http)
        val opener = PublicationOpener(DefaultPublicationParser(app, http, retriever, pdfFactory = null))

        val asset = retriever.retrieve(file).getOrNull()
            ?: return@withContext ReaderState.Failed("El archivo no parece un EPUB válido.")
        val publication = opener.open(asset, allowUserInteraction = false).getOrNull()
        if (publication == null) {
            asset.close()
            return@withContext ReaderState.Failed("No se ha podido abrir el EPUB. Puede estar dañado o protegido con DRM.")
        }
        val saved = store.get(bookId, BookFormat.EPUB)?.locator
            ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        ReaderState.Ready(publication, saved, publication.positions().size)
    }

    fun onLocatorChanged(locator: Locator) {
        this.locator = locator
        viewModelScope.launch {
            store.save(bookId, BookFormat.EPUB, locator.toJSON().toString(), locator.locations.totalProgression)
        }
    }

    /** Marcador de la página que se ve, si lo hay. */
    fun bookmarkHere(bookmarks: List<Bookmark>, here: Locator? = locator): Bookmark? {
        here ?: return null
        return bookmarks.firstOrNull { bookmark -> bookmark.toLocator()?.let { samePage(it, here) } == true }
    }

    /** Marca la página actual o, si ya lo estaba, le quita el marcador. */
    fun toggleBookmark() {
        val here = locator ?: return
        val existing = bookmarkHere(bookmarks.value, here)
        viewModelScope.launch {
            if (existing != null) {
                bookmarkStore.delete(existing)
            } else {
                bookmarkStore.add(
                    bookId,
                    BookFormat.EPUB,
                    here.toJSON().toString(),
                    title = here.title,
                    text = here.text.highlight?.trim()?.replace(Regex("\\s+"), " ")?.take(BOOKMARK_TEXT),
                    progression = here.locations.totalProgression,
                )
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkStore.delete(bookmark) }
    }

    /** Lo escrito en el buscador; se conserva al cerrar el panel. */
    var searchInput by mutableStateOf("")

    var search by mutableStateOf<SearchState?>(null)
        private set

    /** Resultado al que se ha saltado: se subraya en la página. */
    var searchSelected by mutableStateOf<Locator?>(null)

    private var searchIterator: SearchIterator? = null
    private var searchJob: Job? = null

    /** Empieza una búsqueda nueva; sin texto, borra la anterior. */
    @OptIn(ExperimentalReadiumApi::class)
    fun search(query: String = searchInput) {
        val publication = (state.value as? ReaderState.Ready)?.publication ?: return
        val parsed = SearchQuery.parse(query)
        stopSearch()
        searchSelected = null
        if (parsed == null) {
            search = null
            return
        }
        search = SearchState(parsed)
        searchJob = viewModelScope.launch {
            val options = if (parsed.literal) {
                SearchService.Options(caseSensitive = true, diacriticSensitive = true, wholeWord = true)
            } else {
                SearchService.Options(caseSensitive = false, diacriticSensitive = false, wholeWord = false)
            }
            val iterator = publication.search(parsed.text, options)
            if (iterator == null) {
                search = SearchState(parsed, loading = false, done = true, error = "Este libro no permite buscar.")
                return@launch
            }
            searchIterator = iterator
            loadResults(iterator)
        }
    }

    /** Sigue recorriendo el libro: la lista pide más al acercarse al final. */
    fun loadMoreResults() {
        val current = search ?: return
        val iterator = searchIterator ?: return
        if (current.loading || current.done) return
        search = current.copy(loading = true)
        searchJob = viewModelScope.launch { loadResults(iterator) }
    }

    /**
     * Readium da los resultados capítulo a capítulo; se avanza hasta tener una
     * tanda o acabar el libro, mostrando lo que va saliendo.
     */
    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun loadResults(iterator: SearchIterator) {
        var found = 0
        while (found < SEARCH_BATCH) {
            when (val result = withContext(Dispatchers.Default) { iterator.next() }) {
                is Try.Success -> {
                    val locators = result.value?.locators
                    if (locators == null) {
                        search = search?.copy(loading = false, done = true)
                        return
                    }
                    found += locators.size
                    search = search?.let { it.copy(results = it.results + locators) }
                }
                is Try.Failure -> {
                    search = search?.copy(loading = false, done = true, error = "No se ha podido buscar en todo el libro.")
                    return
                }
            }
        }
        search = search?.copy(loading = false)
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        searchIterator?.close()
        searchIterator = null
    }

    override fun onCleared() {
        stopSearch()
        (_state.value as? ReaderState.Ready)?.publication?.close()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"

        private const val BOOKMARK_TEXT = 160
        private const val SEARCH_BATCH = 30
    }
}

fun Bookmark.toLocator(): Locator? = runCatching { Locator.fromJSON(JSONObject(locator)) }.getOrNull()

/**
 * Misma página: mismo archivo y misma posición de Readium (unos 1024
 * caracteres). Una pantalla puede abarcar dos posiciones; vale como aproximación.
 */
private fun samePage(a: Locator, b: Locator): Boolean {
    if (a.href.toString() != b.href.toString()) return false
    val pa = a.locations.position
    val pb = b.locations.position
    return if (pa != null && pb != null) pa == pb else a.locations.progression == b.locations.progression
}
