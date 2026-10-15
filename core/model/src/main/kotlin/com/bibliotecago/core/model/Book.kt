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
    val files: List<BookFile> = emptyList(),
    /** Ruta relativa al servidor (`/opds/cover/2`): vale igual por la URL local que por el túnel. */
    val coverPath: String? = null,
    /** Código ISO 639-3 tal como lo da Calibre (`spa`, `eng`). */
    val language: String? = null,
    /** Valoración de 0 a 5 estrellas. */
    val rating: Int? = null,
) {
    val formats: List<BookFormat> get() = files.map { it.format }.distinct()
}

/** Un archivo descargable del libro. [path] es relativa al servidor, como [Book.coverPath]. */
data class BookFile(val format: BookFormat, val path: String, val sizeBytes: Long?)

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
