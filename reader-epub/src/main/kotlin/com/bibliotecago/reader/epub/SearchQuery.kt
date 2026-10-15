package com.bibliotecago.reader.epub

/**
 * Lo que se busca. Entre comillas es literal: tal cual se escribe, con
 * mayúsculas, acentos y palabras enteras. Sin comillas da igual cómo esté
 * escrito y vale dentro de otra palabra.
 */
data class SearchQuery(val text: String, val literal: Boolean) {

    companion object {
        /** Comillas de apertura y su cierre: rectas, inglesas y españolas. */
        private val QUOTES = mapOf('"' to '"', '“' to '”', '«' to '»', '\'' to '\'')

        /** Nula si no queda nada que buscar. Basta la comilla de apertura: la de cierre se puede olvidar. */
        fun parse(input: String): SearchQuery? {
            val trimmed = input.trim()
            val close = trimmed.firstOrNull()?.let(QUOTES::get)
            val query = if (close == null) {
                SearchQuery(trimmed, literal = false)
            } else {
                SearchQuery(trimmed.drop(1).removeSuffix(close.toString()).trim(), literal = true)
            }
            return query.takeIf { it.text.isNotEmpty() }
        }
    }
}
