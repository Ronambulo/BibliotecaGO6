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
import com.bibliotecago.library.readingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
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

enum class ReaderPanel { None, Text, Contents }

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

    override fun onCleared() {
        (_state.value as? ReaderState.Ready)?.publication?.close()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"
    }
}
