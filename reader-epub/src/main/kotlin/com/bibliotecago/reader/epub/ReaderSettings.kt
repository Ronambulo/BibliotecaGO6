package com.bibliotecago.reader.epub

import android.content.Context
import androidx.core.content.edit
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import kotlin.math.roundToInt

enum class ReaderFont(val label: String, val family: FontFamily?) {
    Original("Original", null),
    Serif("Con serifa", FontFamily.SERIF),
    Sans("Sin serifa", FontFamily.SANS_SERIF),
}

/**
 * Cómo se distinguen los párrafos. Muchos EPUB quitan la sangría y el espacio
 * entre párrafos y el punto y aparte no se ve; fuera de «Libro» se fuerza en
 * todos (en rem, como pide Readium).
 */
enum class ParagraphStyle(val label: String, val indent: Double?, val spacing: Double?) {
    Book("Libro", null, null),
    Indent("Sangría", 1.5, 0.0),
    Spaced("Separados", 0.0, 0.75),
}

/**
 * Ajustes de lectura de EPUB, comunes a todos los libros. Pocas opciones y
 * con valores fijos: en E Ink cada cambio repinta la página entera.
 */
data class ReaderSettings(
    /** Escala sobre el tamaño del libro: 1.0 = 100 %. */
    val fontScale: Double = 1.0,
    val font: ReaderFont = ReaderFont.Original,
    /** Nulo: el interlineado del libro. */
    val lineHeight: Double? = null,
    val margins: Double = 1.0,
    val paragraphs: ParagraphStyle = ParagraphStyle.Spaced,
    val dark: Boolean = false,
) {
    val fontPercent: Int get() = (fontScale * 100).roundToInt()

    fun toPreferences() = EpubPreferences(
        fontSize = fontScale,
        fontFamily = font.family,
        lineHeight = lineHeight,
        pageMargins = margins,
        paragraphIndent = paragraphs.indent,
        paragraphSpacing = paragraphs.spacing,
        theme = if (dark) Theme.DARK else Theme.LIGHT,
        // Readium solo aplica el interlineado y los párrafos propios sin los estilos del editor.
        publisherStyles = lineHeight == null && paragraphs == ParagraphStyle.Book,
        scroll = false,
    )

    fun smallerFont() = withFontScale(fontScale - FONT_STEP)
    fun largerFont() = withFontScale(fontScale + FONT_STEP)

    // Redondeo a décimas: sumar 0.1 muchas veces en coma flotante se desvía.
    private fun withFontScale(scale: Double) =
        copy(fontScale = ((scale * 10).roundToInt() / 10.0).coerceIn(MIN_FONT, MAX_FONT))

    companion object {
        const val MIN_FONT = 0.7
        const val MAX_FONT = 2.5
        private const val FONT_STEP = 0.1

        val LINE_HEIGHTS: List<Pair<String, Double?>> = listOf("Libro" to null, "1,2" to 1.2, "1,5" to 1.5, "1,8" to 1.8)
        val MARGINS: List<Pair<String, Double>> = listOf("Estrechos" to 0.5, "Normales" to 1.0, "Anchos" to 1.75)
    }
}

/**
 * Guarda los ajustes en unas SharedPreferences propias del lector. Pública para
 * que Ajustes los cambie; el lector los lee al abrir cada libro.
 */
class ReaderSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reader_epub", Context.MODE_PRIVATE)

    fun load() = ReaderSettings(
        fontScale = prefs.getFloat(FONT_SCALE, 1f).toDouble(),
        font = ReaderFont.entries.firstOrNull { it.name == prefs.getString(FONT, null) } ?: ReaderFont.Original,
        lineHeight = prefs.getFloat(LINE_HEIGHT, 0f).takeIf { it > 0f }?.toDouble(),
        margins = prefs.getFloat(MARGINS, 1f).toDouble(),
        paragraphs = ParagraphStyle.entries.firstOrNull { it.name == prefs.getString(PARAGRAPHS, null) } ?: ParagraphStyle.Spaced,
        dark = prefs.getBoolean(DARK, false),
    )

    fun save(settings: ReaderSettings) = prefs.edit {
        putFloat(FONT_SCALE, settings.fontScale.toFloat())
        putString(FONT, settings.font.name)
        putFloat(LINE_HEIGHT, settings.lineHeight?.toFloat() ?: 0f)
        putFloat(MARGINS, settings.margins.toFloat())
        putString(PARAGRAPHS, settings.paragraphs.name)
        putBoolean(DARK, settings.dark)
    }

    private companion object {
        const val FONT_SCALE = "fontScale"
        const val FONT = "font"
        const val LINE_HEIGHT = "lineHeight"
        const val MARGINS = "margins"
        const val PARAGRAPHS = "paragraphs"
        const val DARK = "dark"
    }
}
