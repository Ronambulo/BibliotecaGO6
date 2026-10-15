package com.bibliotecago.reader.pdf

import android.content.Context
import androidx.core.content.edit

enum class PdfFit(val label: String) {
    /** Al ancho de la pantalla, por trozos de arriba abajo. */
    Width("Al ancho"),
    /** La página entera en pantalla. */
    Page("Página completa"),
}

/** Ajustes de lectura de PDF, comunes a todos los libros. */
data class PdfSettings(
    val fit: PdfFit = PdfFit.Width,
    /** Quitar los márgenes en blanco: en 6" el texto sale bastante más grande. */
    val crop: Boolean = true,
)

internal class PdfSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reader_pdf", Context.MODE_PRIVATE)

    fun load() = PdfSettings(
        fit = PdfFit.entries.firstOrNull { it.name == prefs.getString(FIT, null) } ?: PdfFit.Width,
        crop = prefs.getBoolean(CROP, true),
    )

    fun save(settings: PdfSettings) = prefs.edit {
        putString(FIT, settings.fit.name)
        putBoolean(CROP, settings.crop)
    }

    private companion object {
        const val FIT = "fit"
        const val CROP = "crop"
    }
}
