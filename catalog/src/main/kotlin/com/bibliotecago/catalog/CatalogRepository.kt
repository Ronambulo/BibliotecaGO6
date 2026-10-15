package com.bibliotecago.catalog

import com.bibliotecago.server.ServerConnection
import java.net.URLEncoder

/** Listados planos de libros que ofrece Calibre-Web, ya ordenados por el servidor. */
enum class CatalogListing(val label: String, val href: String) {
    // Rutas relativas a la base: así respetan un posible prefijo de proxy.
    Recent("Añadidos recientemente", "opds/new"),
    Title("Título", "opds/books/letter/00"),
}

/**
 * Índices de navegación de Calibre-Web. Primero dan letras, luego autores,
 * series o etiquetas y al final los libros: se recorren siguiendo enlaces.
 */
enum class CatalogBrowse(val label: String, val title: String, val href: String) {
    Authors("Autor", "Autores", "opds/author"),
    Series("Serie", "Series", "opds/series"),
    Tags("Etiqueta", "Etiquetas", "opds/category"),
}

/** Lee el catálogo del servidor configurado. Sin caché propia (eso es la Fase 4). */
class CatalogRepository(private val connection: ServerConnection) {

    /** Feed en [href] (ruta del servidor) o, si es nula, la raíz del catálogo. */
    suspend fun feed(href: String?): OpdsFeed =
        connection.get(href) { _, response -> response.body.byteStream().use(OpdsParser::parse) }

    suspend fun listing(listing: CatalogListing): OpdsFeed = feed(listing.href)

    /** Búsqueda con la plantilla del feed (`/opds/search/{searchTerms}`): el término va en la ruta. */
    suspend fun search(query: String, template: String = DEFAULT_SEARCH): OpdsFeed =
        feed(searchHref(query, template))

    companion object {
        const val DEFAULT_SEARCH = "opds/search/{searchTerms}"

        /** Ruta de búsqueda para [query]; la usa también la ficha para «Más del autor». */
        fun searchHref(query: String, template: String = DEFAULT_SEARCH): String {
            // Como segmento de ruta: espacios como %20, no como «+».
            val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            return template.replace("{searchTerms}", encoded)
        }
    }
}
