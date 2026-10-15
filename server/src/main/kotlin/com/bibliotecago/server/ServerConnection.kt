package com.bibliotecago.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Acceso al servidor configurado. Elige sola entre la URL local y la del
 * túnel: prueba primero la local y, si no hay red hasta ella, usa el túnel.
 * La elección se recuerda un rato para no esperar al timeout en cada petición.
 */
class ServerConnection(
    private val config: () -> ServerConfig?,
    baseClient: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Cliente con autenticación; también lo usan Coil (portadas) y las descargas. */
    val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(BasicAuthInterceptor(config))
        .addNetworkInterceptor(CleartextGuard)
        .build()

    /** Para la URL local: si en 3 s no hay conexión, no estamos en casa. */
    private val localClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()

    private class Choice(val config: ServerConfig, val base: HttpUrl, val at: Long)

    @Volatile private var choice: Choice? = null

    /** Credenciales configuradas; las usa el inicio de sesión web de [ReadStatusClient]. */
    val credentials: Credentials? get() = config()?.credentials

    /** Base en uso según la última petición, para resolver portadas y descargas. */
    val activeBase: HttpUrl? get() = choice?.takeIf { it.config == config() }?.base

    /**
     * Pide [href] (ruta del feed, o `null` para la raíz del catálogo) y entrega la
     * respuesta correcta a [read]. Los códigos de error se convierten en [ServerException].
     */
    suspend fun <T> get(href: String?, read: (base: HttpUrl, response: Response) -> T): T = withContext(Dispatchers.IO) {
        val cfg = config() ?: throw ServerException(ConnectionProblem.NotConfigured)
        var lastError: IOException? = null
        for (base in candidates(cfg)) {
            val url = if (href == null) ServerUrls.catalogRoot(base) else ServerUrls.resolve(base, href)
            val http = if (base == cfg.local) localClient else client
            val response = try {
                http.newCall(Request.Builder().url(url).build()).execute()
            } catch (e: IOException) {
                // Fallo de red: se prueba con la otra URL. Un error HTTP, en cambio, es definitivo.
                lastError = e
                continue
            }
            choice = Choice(cfg, base, clock())
            return@withContext response.use {
                if (!it.isSuccessful) {
                    throw ServerException(ConnectionProblem.fromHttpStatus(it.code), httpStatus = it.code)
                }
                read(base, it)
            }
        }
        choice = null
        val error = lastError ?: IOException("Sin URL")
        throw ServerException(ConnectionProblem.fromException(error), error)
    }

    /**
     * Para peticiones que no son del catálogo: ejecuta [block] con cada URL, en
     * el mismo orden que [get], hasta que una no falle por la red. Un
     * [ServerException] lanzado por [block] es definitivo.
     */
    suspend fun <T> withEndpoint(block: (base: HttpUrl, client: OkHttpClient) -> T): T = withContext(Dispatchers.IO) {
        val cfg = config() ?: throw ServerException(ConnectionProblem.NotConfigured)
        var lastError: IOException? = null
        for (base in candidates(cfg)) {
            val http = if (base == cfg.local) localClient else client
            val result = try {
                block(base, http)
            } catch (e: ServerException) {
                choice = Choice(cfg, base, clock())
                throw e
            } catch (e: IOException) {
                lastError = e
                continue
            }
            choice = Choice(cfg, base, clock())
            return@withContext result
        }
        choice = null
        val error = lastError ?: IOException("Sin URL")
        throw ServerException(ConnectionProblem.fromException(error), error)
    }

    private fun candidates(cfg: ServerConfig): List<HttpUrl> {
        val remembered = choice?.takeIf { it.config == cfg && clock() - it.at < REMEMBER_MS }?.base
        return if (remembered == null) cfg.endpoints else listOf(remembered) + (cfg.endpoints - remembered)
    }

    /** Olvida la URL elegida; p. ej. al cambiar de red. */
    fun forgetEndpoint() {
        choice = null
    }

    /**
     * «Probar conexión»: comprueba cada URL de [candidate] por separado, con sus
     * credenciales (aún sin guardar), y devuelve un informe por URL.
     */
    suspend fun test(candidate: ServerConfig): List<EndpointReport> = withContext(Dispatchers.IO) {
        candidate.endpoints.map { base ->
            val auth = okhttp3.Credentials.basic(candidate.credentials.username, candidate.credentials.password, Charsets.UTF_8)
            val request = Request.Builder()
                .url(ServerUrls.catalogRoot(base))
                .header(BasicAuthInterceptor.AUTHORIZATION, auth)
                .build()
            val problem = try {
                client.newCall(request).execute().use { checkCatalog(it) }
            } catch (e: IOException) {
                ConnectionProblem.fromException(e)
            }
            EndpointReport(base, isLocal = base == candidate.local, problem = problem)
        }
    }

    private fun checkCatalog(response: Response): ConnectionProblem? {
        if (!response.isSuccessful) return ConnectionProblem.fromHttpStatus(response.code)
        // Un portal de login o un desafío de Cloudflare también contestan 200, pero en HTML.
        val head = response.peekBody(4096).string()
        return if (head.contains("<feed")) null else ConnectionProblem.NotOpds
    }

    private companion object {
        const val REMEMBER_MS = 5 * 60 * 1000L
    }
}

/** Resultado de probar una URL: [problem] nulo si todo fue bien. */
data class EndpointReport(val base: HttpUrl, val isLocal: Boolean, val problem: ConnectionProblem?)
