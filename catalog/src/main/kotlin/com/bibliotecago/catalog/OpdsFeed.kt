package com.bibliotecago.catalog

import com.bibliotecago.core.model.Book

/**
 * Un feed OPDS ya interpretado. Todos los enlaces son rutas del servidor
 * (`/opds/new?offset=60`), sin host: valen tanto por la URL local como por el túnel.
 */
data class OpdsFeed(
    val title: String,
    /** Entradas de navegación (catálogo raíz, autores, series…). */
    val navigation: List<OpdsNavigation>,
    /** Entradas de adquisición: los libros. */
    val books: List<Book>,
    /** Siguiente página (`rel="next"`), o nula si es la última. */
    val nextHref: String?,
    /** Plantilla de búsqueda con `{searchTerms}`. */
    val searchTemplate: String?,
)

data class OpdsNavigation(val title: String, val href: String, val summary: String?)
