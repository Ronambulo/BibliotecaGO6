package com.bibliotecago.catalog

import com.bibliotecago.server.ServerConnection
import java.net.URLEncoder

/** Listados planos de libros que ofrece Calibre-Web, ya ordenados por el servidor. */
enum class CatalogListing(val label: String, val href: String) {
    // Rutas relativas a la base: así respetan un posible prefijo de proxy.
    Recent("Añadidos recientemente", "opds/new"),
    Title("Título", "opds/books/letter/00"),
}

/** Lee el catálogo del servidor configurado. Sin caché propia (eso es la Fase 4). */
class CatalogRepository(private val connection: ServerConnection) {

    /** Feed en [href] (ruta del servidor) o, si es nula, la raíz del catálogo. */
    suspend fun feed(href: String?): OpdsFeed =
        connection.get(href) { _, response -> response.body.byteStream().use(OpdsParser::parse) }

    suspend fun listing(listing: CatalogListing): OpdsFeed = feed(listing.href)

    /** Búsqueda con la plantilla del feed (`/opds/search/{searchTerms}`): el término va en la ruta. */
    suspend fun search(query: String, template: String = DEFAULT_SEARCH): OpdsFeed =
        feed(searchHref(template, query))

    companion object {
        const val DEFAULT_SEARCH = "opds/search/{searchTerms}"

        internal fun searchHref(template: String, query: String): String {
            // Como segmento de ruta: espacios como %20, no como «+».
            val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            return template.replace("{searchTerms}", encoded)
        }
    }
}
