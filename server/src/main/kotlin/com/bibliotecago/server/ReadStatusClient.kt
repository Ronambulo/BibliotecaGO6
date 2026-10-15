package com.bibliotecago.server

import com.bibliotecago.core.model.Book
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Marca libros como leídos (o no) en Calibre-Web. OPDS no lo permite, así que
 * se usa la web: `/ajax/toggleread/<id>` exige sesión iniciada (la
 * autenticación básica del OPDS no vale) y token CSRF. Como solo invierte el
 * estado, antes se lee el actual en la ficha `/book/<id>`.
 *
 * La sesión se guarda en memoria y se reutiliza: Calibre-Web limita los
 * inicios de sesión a 3 por minuto.
 */
class ReadStatusClient(private val connection: ServerConnection) {

    private val cookies = MemoryCookieJar()

    /** Deja el libro [calibreId] como [read] en el servidor. */
    suspend fun setRead(calibreId: Int, read: Boolean) = connection.withEndpoint { base, client ->
        val http = client.newBuilder().cookieJar(cookies).followRedirects(false).build()
        var page = detail(http, base, calibreId)
        if (page == null) {
            login(http, base)
            page = detail(http, base, calibreId) ?: throw ServerException(ConnectionProblem.Unauthorized)
        }
        if (readState(page) == read) return@withEndpoint
        val request = Request.Builder()
            .url(base.resolve("ajax/toggleread/$calibreId")!!)
            .header(CSRF_HEADER, csrfToken(page))
            .post(FormBody.Builder().add("csrf_token", csrfToken(page)).build())
            .build()
        http.newCall(request).execute().use { check(it) }
    }

    /** HTML de la ficha, o `null` si hace falta iniciar sesión. */
    private fun detail(http: OkHttpClient, base: HttpUrl, calibreId: Int): String? {
        val request = Request.Builder().url(base.resolve("book/$calibreId")!!).build()
        return http.newCall(request).execute().use { response ->
            if (response.isRedirect || response.code == 401) return null
            check(response)
            response.body.string()
        }
    }

    private fun login(http: OkHttpClient, base: HttpUrl) {
        val url = base.resolve("login")!!
        val page = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response)
            response.body.string()
        }
        val cfg = connection.credentials ?: throw ServerException(ConnectionProblem.NotConfigured)
        val form = FormBody.Builder()
            .add("csrf_token", csrfToken(page))
            .add("next", "/")
            .add("username", cfg.username)
            .add("password", cfg.password)
            .add("remember_me", "on")
            .add("submit", "")
            .build()
        http.newCall(Request.Builder().url(url).post(form).build()).execute().use { response ->
            // Bien: redirige fuera del login. Mal: vuelve a pintar el formulario (200).
            val location = response.header("Location").orEmpty()
            if (!response.isRedirect || location.contains("login")) {
                if (response.code == 429) throw ServerException(ConnectionProblem.ServerError, httpStatus = 429)
                throw ServerException(ConnectionProblem.Unauthorized, httpStatus = response.code)
            }
        }
    }

    private fun check(response: Response) {
        if (!response.isSuccessful) {
            throw ServerException(ConnectionProblem.fromHttpStatus(response.code), httpStatus = response.code)
        }
    }

    companion object {
        private const val CSRF_HEADER = "X-CSRFToken"

        private val DOWNLOAD_ID = Regex("""/download/(\d+)/""")
        private val TOKEN = Regex("""name="csrf_token"\s+value="([^"]+)"""")
        private val READ_CHECKBOX = Regex("""<input[^>]*id="have_read_cb"[^>]*>""")
        private val CHECKED = Regex("""\schecked(?=[\s>/])""")

        /** Id numérico de Calibre: OPDS identifica por uuid, pero las descargas llevan el id. */
        fun calibreId(book: Book): Int? =
            book.files.firstNotNullOfOrNull { DOWNLOAD_ID.find(it.path)?.groupValues?.get(1)?.toIntOrNull() }

        internal fun csrfToken(html: String): String =
            TOKEN.find(html)?.groupValues?.get(1) ?: throw ServerException(ConnectionProblem.Unknown)

        /** Estado de la casilla «leído» de la ficha. */
        internal fun readState(html: String): Boolean {
            val input = READ_CHECKBOX.find(html)?.value ?: throw ServerException(ConnectionProblem.Unknown)
            return CHECKED.containsMatchIn(input)
        }
    }
}

/** Cookies de sesión en memoria, por host. */
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
        this.cookies += cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}
