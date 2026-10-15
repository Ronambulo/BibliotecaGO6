package com.bibliotecago.app.screens

import com.bibliotecago.core.model.Book
import com.bibliotecago.downloads.DownloadState
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")

/** «850 KB», «1,2 MB», «2,05 GB». */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(spanish, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(spanish, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(spanish, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

/** Nombre del idioma a partir del código ISO 639 que da Calibre. */
fun languageName(code: String): String {
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(spanish)
    return if (name.isBlank() || name == code) code else name.replaceFirstChar { it.titlecase(spanish) }
}

fun ratingStars(rating: Int): String = "★".repeat(rating.coerceIn(0, 5)) + "☆".repeat(5 - rating.coerceIn(0, 5))

fun seriesLabel(book: Book): String? = book.series?.let { series ->
    val index = series.index?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    if (index == null) series.name else "${series.name} · nº $index"
}

/**
 * Estado de una descarga en una línea. El porcentaje va de 5 en 5: en E Ink
 * cada cambio de texto es un refresco, y no hace falta más precisión.
 */
fun describe(state: DownloadState): String = when (state) {
    is DownloadState.Queued -> state.retryProblem?.let { "Se reintentará: ${it.title.lowercase(spanish)}" } ?: "En cola"
    is DownloadState.Running -> state.fraction
        ?.let { "Descargando… ${(it * 20).toInt() * 5} %" }
        ?: "Descargando… ${formatBytes(state.downloadedBytes)}"
    is DownloadState.Done -> "En el dispositivo · ${formatBytes(state.sizeBytes)}"
    is DownloadState.Failed -> "Error: ${state.problem.title}"
}
