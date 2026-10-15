package com.bibliotecago.core.model

/**
 * Libro del catálogo de Calibre-Web, tal como lo describe OPDS.
 * Modelo de dominio: no sabe nada de red, base de datos ni interfaz.
 */
data class Book(
    val id: BookId,
    val title: String,
    val authors: List<String>,
    val series: Series? = null,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val formats: List<BookFormat> = emptyList(),
    val coverUrl: String? = null,
)

/** Identificador estable del libro (el `id` de la entrada OPDS). */
@JvmInline
value class BookId(val value: String)

data class Series(val name: String, val index: Double?)

enum class BookFormat { EPUB, PDF }

/** Estado del libro en este dispositivo. */
sealed interface LocalState {
    data object OnlyOnServer : LocalState
    data class Downloading(val progress: Float) : LocalState
    data class Downloaded(val sizeBytes: Long, val readingProgress: Float?) : LocalState
}
