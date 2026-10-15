package com.bibliotecago.app.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bibliotecago.app.AppContainer
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.Download
import com.bibliotecago.downloads.DownloadState
import com.bibliotecago.library.ReadingProgress
import com.bibliotecago.reader.epub.epubPageCount
import com.bibliotecago.reader.pdf.pdfPageCount
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/**
 * Ficha del libro (RF-04). El libro sale del catálogo ya cargado o, sin
 * conexión, de lo guardado al descargarlo; el estado local, de Room.
 */
class BookDetailViewModel(private val app: AppContainer, private val id: BookId) : ViewModel() {

    var book by mutableStateOf(app.books[id])
        private set
    /** Buscando la ficha guardada; si al terminar [book] es nulo, no está en ninguna parte. */
    var loading by mutableStateOf(book == null)
        private set
    var downloads by mutableStateOf<Map<BookFormat, Download>>(emptyMap())
        private set
    /** Marcado como leído, aquí o en Calibre-Web. */
    var finished by mutableStateOf(false)
        private set
    /** Último progreso guardado en cualquier formato; nulo si no se ha abierto. */
    var progress by mutableStateOf<ReadingProgress?>(null)
        private set
    /** Páginas contadas abriendo el archivo descargado, si el lector aún no las guardó. */
    private var countedPages by mutableStateOf<Int?>(null)
    private var counting = false

    /** Páginas del libro: las que guardó el lector o, si no, las del archivo descargado. */
    val pageCount: Int? get() = progress?.pageCount ?: countedPages

    val coverBase: HttpUrl? get() = app.coverBase

    init {
        viewModelScope.launch {
            if (book == null) {
                book = app.downloads.savedBook(id)
                loading = false
            }
        }
        viewModelScope.launch {
            app.downloads.observe(id).collect { list ->
                downloads = list.associateBy { it.format }
                countPages()
            }
        }
        viewModelScope.launch { app.library.finished.observe(id).collect { finished = it } }
        viewModelScope.launch {
            app.library.progress.observe(id).collect { rows ->
                progress = rows.firstOrNull()
                countPages()
            }
        }
    }

    /** Cuenta las páginas del archivo descargado una vez, si nadie las ha guardado. */
    private fun countPages() {
        if (counting || countedPages != null || progress?.pageCount != null) return
        val preferred = progress?.format
        val done = downloads.entries
            .sortedByDescending { it.key == preferred }
            .firstNotNullOfOrNull { (format, download) -> (download.state as? DownloadState.Done)?.let { format to it.file } }
            ?: return
        counting = true
        viewModelScope.launch {
            val (format, file) = done
            countedPages = when (format) {
                BookFormat.PDF -> pdfPageCount(file)
                BookFormat.EPUB -> epubPageCount(app.context, file)
            }
            counting = false
        }
    }

    fun download(format: BookFormat) {
        val current = book ?: return
        viewModelScope.launch { app.downloads.enqueue(current, format) }
    }

    /** Marca o desmarca como leído; se envía a Calibre-Web como desde el lector. */
    fun toggleFinished() {
        val now = !finished
        viewModelScope.launch { app.library.finished.set(id, now) }
    }

    fun cancel(format: BookFormat) {
        viewModelScope.launch { app.downloads.cancel(id, format) }
    }

    fun remove(format: BookFormat) {
        viewModelScope.launch { app.downloads.remove(id, format) }
    }
}
