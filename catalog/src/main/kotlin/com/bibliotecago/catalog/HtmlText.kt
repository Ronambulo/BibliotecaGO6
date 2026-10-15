package com.bibliotecago.catalog

/**
 * Convierte el HTML de una sinopsis de Calibre en texto plano: los saltos de
 * párrafo se conservan, las etiquetas se quitan y las entidades se resuelven.
 */
internal object HtmlText {
    private val lineBreak = Regex("""(?i)<\s*br\s*/?\s*>""")
    private val blockEnd = Regex("""(?i)<\s*/(p|div|h[1-6]|ul|ol)\s*>""")
    private val listItem = Regex("""(?i)<\s*li[^>]*>""")
    private val tag = Regex("""<[^>]*>""")
    private val entity = Regex("""&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);""")
    private val spaces = Regex("""[ \t ]+""")
    private val blankLines = Regex("""\n\s*\n+""")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "laquo" to "«", "raquo" to "»", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·",
    )

    fun toPlainText(html: String): String {
        val text = html
            .replace(lineBreak, "\n")
            .replace(blockEnd, "\n\n")
            .replace(listItem, "\n• ")
            .replace(tag, "")
            .replace(entity) { decode(it.groupValues[1]) ?: it.value }
        return text.lines()
            .joinToString("\n") { it.replace(spaces, " ").trim() }
            .replace(blankLines, "\n\n")
            .trim()
    }

    private fun decode(name: String): String? = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
        else -> named[name]
    }

    private fun codePoint(cp: Int): String? = if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null
}
