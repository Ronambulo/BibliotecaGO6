package com.bibliotecago.server

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** Motivos de fallo que se le cuentan al usuario en lenguaje llano (RF-01). */
enum class ConnectionProblem(val title: String, val hint: String) {
    NotConfigured(
        "Servidor sin configurar",
        "Añade la URL y las credenciales en Ajustes.",
    ),
    Dns(
        "No se encuentra el servidor",
        "El nombre no se resuelve. Revisa la URL o la conexión a Internet.",
    ),
    Unreachable(
        "El servidor no responde",
        "No se pudo abrir la conexión. Comprueba que Calibre-Web está encendido y que estás en la red correcta.",
    ),
    Timeout(
        "Tiempo de espera agotado",
        "El servidor tarda demasiado en contestar. Inténtalo de nuevo en un momento.",
    ),
    Tls(
        "Conexión segura rechazada",
        "El certificado HTTPS no es válido para esta dirección.",
    ),
    Cleartext(
        "HTTP no permitido",
        "Solo se admite HTTP hacia direcciones de red privada. Usa https://.",
    ),
    Unauthorized(
        "Usuario o contraseña incorrectos",
        "Calibre-Web ha rechazado las credenciales (401).",
    ),
    Forbidden(
        "Acceso denegado",
        "El usuario no tiene permiso para el catálogo OPDS o las descargas (403).",
    ),
    NotFound(
        "No se encuentra el catálogo",
        "La dirección no tiene /opds. Comprueba la URL y que el catálogo OPDS está activado.",
    ),
    NotOpds(
        "La respuesta no es un catálogo OPDS",
        "Puede que delante haya una página de inicio de sesión o un desafío de Cloudflare.",
    ),
    ServerError(
        "Error del servidor",
        "Calibre-Web ha devuelto un error interno. Mira su registro.",
    ),
    Incomplete(
        "Descarga incompleta",
        "El archivo llegó vacío o cortado y se ha descartado. Vuelve a intentarlo.",
    ),
    NoSpace(
        "No queda espacio",
        "El dispositivo no tiene sitio para el libro. Quita algún libro descargado.",
    ),
    Unknown(
        "Error de conexión",
        "Ha ocurrido un error inesperado.",
    );

    companion object {
        fun fromHttpStatus(code: Int): ConnectionProblem = when (code) {
            401 -> Unauthorized
            403 -> Forbidden
            404 -> NotFound
            in 500..599 -> ServerError
            else -> Unknown
        }

        fun fromException(e: Throwable): ConnectionProblem = when (e) {
            is ServerException -> e.problem
            is CleartextBlockedException -> Cleartext
            is UnknownHostException -> Dns
            is SSLException -> Tls
            is ConnectException, is NoRouteToHostException -> Unreachable
            // SocketTimeoutException es un InterruptedIOException.
            is InterruptedIOException -> Timeout
            // OkHttp la lanza cuando la network_security_config bloquea HTTP.
            is UnknownServiceException -> Cleartext
            is IOException -> if (e.message.orEmpty().contains("ENOSPC")) NoSpace else Unreachable
            else -> Unknown
        }
    }
}

/** Fallo de una petición al servidor, ya clasificado. */
class ServerException(val problem: ConnectionProblem, cause: Throwable? = null, val httpStatus: Int? = null) :
    IOException("${problem.name}${httpStatus?.let { " ($it)" } ?: ""}", cause)

/** Petición en claro hacia una dirección pública: se corta antes de salir. */
class CleartextBlockedException(host: String) : IOException("HTTP en claro bloqueado hacia $host")
