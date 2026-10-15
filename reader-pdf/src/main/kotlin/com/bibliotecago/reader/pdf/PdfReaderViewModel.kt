package com.bibliotecago.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.library.readingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

sealed interface PdfState {
    data object Opening : PdfState
    data class Ready(val pageCount: Int) : PdfState
    data class Failed(val message: String) : PdfState
}

enum class PdfPanel { None, View, GoTo }

/** Lo que hay en pantalla: una página ya pintada y desde qué altura se ve. */
class ShownPage(val page: Int, val image: ImageBitmap, val offsetPx: Int)

/**
 * Abre el PDF, pinta las páginas fuera del hilo principal y guarda la
 * posición en cada cambio. Guarda en memoria la página actual y la siguiente
 * ya pintadas, para que pasar página sea inmediato.
 */
class PdfReaderViewModel(application: Application, state: SavedStateHandle) : AndroidViewModel(application) {

    private val file = File(checkNotNull(state.get<String>(EXTRA_PATH)))
    private val bookId = BookId(checkNotNull(state.get<String>(EXTRA_BOOK_ID)))
    val title: String = state.get<String>(EXTRA_TITLE).orEmpty()

    private val store = application.readingProgress
    private val settingsStore = PdfSettingsStore(application)

    private val _state = MutableStateFlow<PdfState>(PdfState.Opening)
    val state: StateFlow<PdfState> = _state.asStateFlow()

    var settings by mutableStateOf(settingsStore.load())
        private set

    var position by mutableStateOf(PdfPosition(0))
        private set

    var shown by mutableStateOf<ShownPage?>(null)
        private set

    /** Avance de 0 a 1 de lo que se ve. */
    var progression by mutableDoubleStateOf(0.0)
        private set

    var controlsVisible by mutableStateOf(false)
    var panel by mutableStateOf(PdfPanel.None)

    private var document: PdfDocument? = null
    private var viewport = IntSize.Zero

    /** Sube al cambiar pantalla o ajustes: invalida lo pintado antes. */
    private var generation = 0
    private val cache = LruCache<Pair<Int, Int>, Bitmap>(CACHED_PAGES)
    private var renderJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val opened = withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
            runCatching { PdfDocument.open(file) }.getOrNull()
        }
        if (opened == null) {
            _state.value = PdfState.Failed(
                if (!file.isFile) "No se encuentra el archivo. Vuelve a descargar el libro."
                else "No se ha podido abrir el PDF. Puede estar dañado o protegido con contraseña."
            )
            return
        }
        if (opened.pageCount == 0) {
            opened.close()
            _state.value = PdfState.Failed("El PDF no tiene páginas.")
            return
        }
        document = opened
        val saved = withContext(Dispatchers.IO) { store.get(bookId, BookFormat.PDF)?.locator }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { PdfPosition(it.optInt(KEY_PAGE), it.optDouble(KEY_OFFSET, 0.0).toFloat()) }
        position = saved?.let { it.copy(page = it.page.coerceIn(0, opened.pageCount - 1)) } ?: PdfPosition(0)
        _state.value = PdfState.Ready(opened.pageCount)
        refresh()
    }

    fun onViewport(size: IntSize) {
        if (size == viewport || size.width <= 0 || size.height <= 0) return
        viewport = size
        invalidate()
    }

    fun updateSettings(settings: PdfSettings) {
        if (settings == this.settings) return
        this.settings = settings
        settingsStore.save(settings)
        invalidate()
    }

    fun next() = move { s, h, count ->
        PdfPaging.next(s.page, s.offsetPx, h, viewport.height, count, settings.fit == PdfFit.Width)
    }

    fun previous() = move { s, h, _ ->
        PdfPaging.previous(s.page, s.offsetPx, h, viewport.height, settings.fit == PdfFit.Width)
    }

    /** [page] desde 1, como se ve en pantalla. */
    fun goToPage(page: Int) {
        val count = document?.pageCount ?: return
        go(PdfPosition((page - 1).coerceIn(0, count - 1)))
    }

    /** Pasar página solo con la actual ya en pantalla: los toques de más se ignoran. */
    private inline fun move(target: (ShownPage, Int, Int) -> PdfPosition?) {
        val s = shown ?: return
        val count = document?.pageCount ?: return
        if (s.page != position.page) return
        target(s, s.image.height, count)?.let(::go)
    }

    private fun go(target: PdfPosition) {
        position = target
        refresh()
    }

    private fun invalidate() {
        generation++
        cache.evictAll()
        refresh()
    }

    /** Muestra la página de [position]: al momento si ya está pintada; si no, al acabar de pintarla. */
    private fun refresh() {
        val doc = document ?: return
        if (viewport == IntSize.Zero) return
        val page = position.page
        cache.get(generation to page)?.let {
            renderJob?.cancel()
            show(page, it)
            prefetch(doc, page + 1)
            return
        }
        renderJob?.cancel()
        val gen = generation
        renderJob = viewModelScope.launch {
            val bitmap = render(doc, page, gen) ?: return@launch
            if (gen == generation && position.page == page) show(page, bitmap)
            prefetch(doc, page + 1)
        }
    }

    private fun prefetch(doc: PdfDocument, page: Int) {
        if (page >= doc.pageCount || cache.get(generation to page) != null) return
        prefetchJob?.cancel()
        val gen = generation
        prefetchJob = viewModelScope.launch { render(doc, page, gen) }
    }

    private suspend fun render(doc: PdfDocument, page: Int, gen: Int): Bitmap? {
        val size = viewport
        val current = settings
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { doc.render(page, size.width, size.height, current.fit, current.crop) }.getOrNull()
        } ?: return null
        if (gen == generation) cache.put(gen to page, bitmap)
        return bitmap
    }

    private fun show(page: Int, bitmap: Bitmap) {
        val fitWidth = settings.fit == PdfFit.Width
        val offsetPx = if (fitWidth) PdfPaging.offsetPx(position.offset, bitmap.height, viewport.height) else 0
        shown = ShownPage(page, bitmap.asImageBitmap(), offsetPx)
        // La fracción guardada pasa a ser la real (por ejemplo, «final de página» al retroceder).
        if (fitWidth) position = PdfPosition(page, offsetPx.toFloat() / bitmap.height)
        val count = document?.pageCount ?: return
        progression = if (fitWidth) {
            PdfPaging.progression(page, offsetPx, bitmap.height, viewport.height, count)
        } else {
            (page + 1).toDouble() / count
        }
        save()
    }

    private fun save() {
        val json = JSONObject().put(KEY_PAGE, position.page).put(KEY_OFFSET, position.offset.toDouble()).toString()
        val progress = progression
        viewModelScope.launch { store.save(bookId, BookFormat.PDF, json, progress) }
    }

    override fun onCleared() {
        renderJob?.cancel()
        prefetchJob?.cancel()
        document?.close()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"

        private const val KEY_PAGE = "page"
        private const val KEY_OFFSET = "offset"
        private const val CACHED_PAGES = 3
    }
}
