package com.bibliotecago.server

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade la autenticación básica solo a las peticiones dirigidas a las URL
 * configuradas, nunca a otros hosts. No registra nada: ni cabeceras ni URL
 * con credenciales llegan al log (apartado 10.3).
 */
internal class BasicAuthInterceptor(private val config: () -> ServerConfig?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cfg = config()
        if (cfg == null || request.header(AUTHORIZATION) != null || cfg.endpoints.none { request.url.isUnder(it) }) {
            return chain.proceed(request)
        }
        val header = okhttp3.Credentials.basic(cfg.credentials.username, cfg.credentials.password, Charsets.UTF_8)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, header).build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

/** Mismo esquema, host y puerto, y ruta dentro de la base. */
internal fun HttpUrl.isUnder(base: HttpUrl): Boolean =
    scheme == base.scheme && host == base.host && port == base.port && encodedPath.startsWith(base.encodedPath)

/**
 * Segunda barrera tras la validación de URL: va como interceptor de red, así
 * que también ve las redirecciones. Corta cualquier HTTP en claro hacia una
 * dirección que no sea de red privada.
 */
internal object CleartextGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !ServerUrls.isPrivateAddress(url.host)) throw CleartextBlockedException(url.host)
        return chain.proceed(chain.request())
    }
}
