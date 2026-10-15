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

    val coverBase: HttpUrl? get() = app.coverBase

    init {
        viewModelScope.launch {
            if (book == null) {
                book = app.downloads.savedBook(id)
                loading = false
            }
        }
        viewModelScope.launch {
            app.downloads.observe(id).collect { list -> downloads = list.associateBy { it.format } }
        }
    }

    fun download(format: BookFormat) {
        val current = book ?: return
        viewModelScope.launch { app.downloads.enqueue(current, format) }
    }

    fun cancel(format: BookFormat) {
        viewModelScope.launch { app.downloads.cancel(id, format) }
    }

    fun remove(format: BookFormat) {
        viewModelScope.launch { app.downloads.remove(id, format) }
    }
}
