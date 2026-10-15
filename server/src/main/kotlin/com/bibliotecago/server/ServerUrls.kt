package com.bibliotecago.server

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validación de las URL del servidor (apartado 10.3): HTTPS para todo lo
 * público; HTTP solo hacia direcciones IPv4 de red privada.
 */
object ServerUrls {

    sealed interface Check {
        /** URL base normalizada: sin `/opds` final y terminada en `/`. */
        data class Valid(val base: HttpUrl) : Check
        data class Invalid(val reason: String) : Check
    }

    fun check(raw: String): Check {
        val text = raw.trim()
        if (text.isEmpty()) return Check.Invalid("Escribe una URL.")
        if (!text.contains("://")) {
            return Check.Invalid("Falta el esquema: empieza por https:// (o http:// en la red local).")
        }
        val url = text.toHttpUrlOrNull()
            ?: return Check.Invalid("No es una URL válida.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return Check.Invalid("No pongas usuario ni contraseña en la URL; usa sus campos.")
        }
        if (!url.isHttps && !isPrivateAddress(url.host)) {
            return Check.Invalid("HTTP solo se admite hacia direcciones de red privada (10.x, 172.16–31.x, 192.168.x). Usa https://.")
        }
        return Check.Valid(normalize(url))
    }

    /** Peticiones en claro solo hacia 10/8, 172.16/12, 192.168/16 y el propio dispositivo (127/8). */
    fun isPrivateAddress(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        val bytes = octets.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return false
            part.toInt().takeIf { it <= 255 } ?: return false
        }
        return when {
            bytes[0] == 10 || bytes[0] == 127 -> true
            bytes[0] == 172 && bytes[1] in 16..31 -> true
            bytes[0] == 192 && bytes[1] == 168 -> true
            else -> false
        }
    }

    /** Admite que se pegue la URL del catálogo (`…/opds`) en vez de la del servidor. */
    private fun normalize(url: HttpUrl): HttpUrl {
        val segments = url.encodedPathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull().equals("opds", ignoreCase = true)) segments.removeAt(segments.lastIndex)
        return url.newBuilder()
            .query(null)
            .fragment(null)
            .encodedPath("/" + segments.joinToString("") { "$it/" })
            .build()
    }

    /** Raíz del catálogo OPDS bajo la base (relativa: respeta un posible prefijo de proxy). */
    fun catalogRoot(base: HttpUrl): HttpUrl = base.resolve("opds")!!

    /**
     * Resuelve un enlace del feed (`/opds/new?offset=60`) contra la base activa.
     * Calibre-Web emite rutas absolutas que ya incluyen el prefijo del proxy, si lo hay,
     * así que la misma ruta vale para la URL local y para el túnel.
     */
    fun resolve(base: HttpUrl, href: String): HttpUrl =
        base.resolve(href) ?: throw IllegalArgumentException("Enlace no válido: $href")
}
