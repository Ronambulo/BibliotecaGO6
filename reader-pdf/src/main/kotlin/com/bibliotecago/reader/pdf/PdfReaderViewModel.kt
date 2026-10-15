package com.bibliotecago.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
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
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

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
 *
 * Con zoom se pinta solo el trozo de página que cabe en pantalla. Pasar
 * pantalla recorre la página ampliada en orden de lectura: hacia la derecha,
 * luego la fila de abajo y al final la página siguiente.
 */
class PdfReaderViewModel(application: Application, state: SavedStateHandle) : AndroidViewModel(application) {

    private val file = File(checkNotNull(state.get<String>(EXTRA_PATH)))
    private val bookId = BookId(checkNotNull(state.get<String>(EXTRA_BOOK_ID)))
    val title: String = state.get<String>(EXTRA_TITLE).orEmpty()

    private val store = application.readingProgress

    private val finishedStore = application.finishedBooks

    /** Si el libro está marcado como leído. */
    val finished: StateFlow<Boolean> = finishedStore.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFinished() {
        val now = !finished.value
        viewModelScope.launch { finishedStore.set(bookId, now) }
    }
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

    /** Aumento sobre el ajuste elegido; 1 es sin zoom. */
    var zoom by mutableFloatStateOf(1f)
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

    /** Trozo ampliado en pantalla; nulo sin zoom. */
    private var tile: ZoomedTile? = null
    private var zoomJob: Job? = null

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
        store.touch(bookId, BookFormat.PDF)
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

    fun next() {
        val t = tile ?: return move { s, h, count ->
            PdfPaging.next(s.page, s.offsetPx, h, viewport.height, count, settings.fit == PdfFit.Width)
        }
        val page = shown?.page ?: return
        val count = document?.pageCount ?: return
        when {
            t.x + viewport.width < t.pageWidth -> renderZoom(page, zoom, t.x + step(viewport.width), t.y)
            t.y + viewport.height < t.pageHeight -> renderZoom(page, zoom, 0, t.y + step(viewport.height))
            page + 1 < count -> renderZoom(page + 1, zoom, 0, 0)
        }
    }

    fun previous() {
        val t = tile ?: return move { s, h, _ ->
            PdfPaging.previous(s.page, s.offsetPx, h, viewport.height, settings.fit == PdfFit.Width)
        }
        val page = shown?.page ?: return
        when {
            t.x > 0 -> renderZoom(page, zoom, t.x - step(viewport.width), t.y)
            t.y > 0 -> renderZoom(page, zoom, FAR, t.y - step(viewport.height))
            // La página anterior se abre por su final: abajo a la derecha.
            page > 0 -> renderZoom(page - 1, zoom, FAR, FAR)
        }
    }

    /** Doble toque: amplía al doble alrededor de [point] o vuelve a la página entera. */
    fun toggleZoom(point: Offset) {
        if (tile != null) unzoom() else zoomAt(DOUBLE_TAP_ZOOM, point)
    }

    /**
     * Pellizco terminado: el aumento cambia en la proporción [ratio] entre la
     * separación final y la inicial de los dedos, redondeado a medios, y
     * [focus] queda en el mismo punto de la pantalla.
     */
    fun pinch(ratio: Float, focus: Offset) {
        val target = ((zoom * ratio * 2).roundToInt() / 2f).coerceIn(1f, MAX_ZOOM)
        when {
            target == zoom -> Unit
            target == 1f -> unzoom()
            else -> zoomAt(target, focus)
        }
    }

    /** Arrastre con zoom: la página se mueve lo que se ha movido el dedo. */
    fun pan(dx: Float, dy: Float) {
        val t = tile ?: return
        val page = shown?.page ?: return
        renderZoom(page, zoom, t.x - dx.roundToInt(), t.y - dy.roundToInt())
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
        resetZoom()
        position = target
        refresh()
    }

    private fun zoomAt(target: Float, point: Offset) {
        val s = shown ?: return
        if (s.page != position.page) return
        val f = pageFraction(s, point)
        val t = tile
        // Tamaño de la página sin zoom, y con el nuevo aumento.
        val baseWidth = (t?.pageWidth ?: s.image.width) / zoom
        val baseHeight = (t?.pageHeight ?: s.image.height) / zoom
        val x = f.x * baseWidth * target - point.x
        val y = f.y * baseHeight * target - point.y
        renderZoom(s.page, target, x.roundToInt(), y.roundToInt())
    }

    /** Punto de la pantalla como fracción de la página, según lo que se ve y dónde se dibuja. */
    private fun pageFraction(s: ShownPage, point: Offset): Offset {
        val image = s.image
        val visible = min(image.height - s.offsetPx, viewport.height)
        val left = ((viewport.width - image.width) / 2).coerceAtLeast(0)
        val top = ((viewport.height - visible) / 2).coerceAtLeast(0)
        val t = tile
        val x = point.x - left + (t?.x ?: 0)
        val y = point.y - top + (t?.y ?: s.offsetPx)
        return Offset(
            (x / (t?.pageWidth ?: image.width)).coerceIn(0f, 1f),
            (y / (t?.pageHeight ?: image.height)).coerceIn(0f, 1f),
        )
    }

    /** Pinta el trozo de [page] con esquina en ([x], [y]); los toques mientras tanto se ignoran. */
    private fun renderZoom(page: Int, target: Float, x: Int, y: Int) {
        val doc = document ?: return
        if (viewport == IntSize.Zero || zoomJob?.isActive == true) return
        renderJob?.cancel()
        prefetchJob?.cancel()
        val size = viewport
        val current = settings
        val gen = generation
        zoomJob = viewModelScope.launch {
            val t = withContext(Dispatchers.IO) {
                runCatching { doc.renderZoomed(page, size.width, size.height, current.fit, current.crop, target, x, y) }.getOrNull()
            } ?: return@launch
            if (gen != generation) return@launch
            zoom = target
            tile = t
            position = PdfPosition(page, t.y.toFloat() / t.pageHeight)
            shown = ShownPage(page, t.bitmap.asImageBitmap(), 0)
            progression = (page + min(1f, (t.y + size.height).toFloat() / t.pageHeight)).toDouble() / doc.pageCount
            save()
        }
    }

    /** Vuelve a la página entera, desde la altura que se veía. */
    private fun unzoom() {
        if (zoomJob?.isActive == true) return
        resetZoom()
        refresh()
    }

    private fun resetZoom() {
        zoomJob?.cancel()
        tile = null
        zoom = 1f
    }

    private fun step(viewport: Int) = (viewport * ZOOM_STEP).roundToInt()

    private fun invalidate() {
        resetZoom()
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
        val page = position.page + 1
        val count = document?.pageCount
        viewModelScope.launch { store.save(bookId, BookFormat.PDF, json, progress, page, count) }
    }

    override fun onCleared() {
        renderJob?.cancel()
        prefetchJob?.cancel()
        zoomJob?.cancel()
        document?.close()
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"

        private const val KEY_PAGE = "page"
        private const val KEY_OFFSET = "offset"
        private const val CACHED_PAGES = 3
        private const val MAX_ZOOM = 4f
        private const val DOUBLE_TAP_ZOOM = 2f
        /** Con zoom, cada pantalla avanza el 90 % para que quede un poco de la anterior. */
        private const val ZOOM_STEP = 0.9f
        /** Esquina «lo más lejos posible»: el pintado la ajusta al borde de la página. */
        private const val FAR = Int.MAX_VALUE / 2
    }
}
