package com.bibliotecago.server

import okhttp3.HttpUrl

/**
 * Configuración del servidor. [tunnel] es la URL pública (HTTPS) y [local] la
 * de la red de casa; al menos una debe existir. Las URL ya están validadas
 * con [ServerUrls.check].
 */
data class ServerConfig(
    val tunnel: HttpUrl?,
    val local: HttpUrl?,
    val credentials: Credentials,
) {
    init {
        require(tunnel != null || local != null) { "Hace falta al menos una URL" }
    }

    /** Orden de prueba: primero la local, que es más rápida cuando se está en casa. */
    val endpoints: List<HttpUrl> get() = listOfNotNull(local, tunnel)
}

/** Usuario y contraseña de Calibre-Web. `toString` nunca muestra la contraseña. */
class Credentials(val username: String, val password: String) {
    override fun toString(): String = "Credentials(username=$username, password=***)"
    override fun equals(other: Any?): Boolean =
        other is Credentials && other.username == username && other.password == password
    override fun hashCode(): Int = 31 * username.hashCode() + password.hashCode()
}
