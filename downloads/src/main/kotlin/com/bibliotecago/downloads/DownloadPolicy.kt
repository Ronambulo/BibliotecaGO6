package com.bibliotecago.downloads

import com.bibliotecago.server.ConnectionProblem

/** Reglas de las descargas, separadas de Room y WorkManager para poder probarlas. */
object DownloadPolicy {

    /** Intentos en total antes de dar una descarga por fallida. */
    const val MAX_ATTEMPTS = 3

    private val transient = setOf(
        ConnectionProblem.Dns,
        ConnectionProblem.Unreachable,
        ConnectionProblem.Timeout,
        ConnectionProblem.ServerError,
        ConnectionProblem.Incomplete,
    )

    /**
     * Los cortes de red se reintentan solos; credenciales, permisos o falta de
     * espacio no se arreglan reintentando. [attempt] empieza en 0.
     */
    fun shouldRetry(problem: ConnectionProblem, attempt: Int): Boolean =
        problem in transient && attempt + 1 < MAX_ATTEMPTS

    sealed interface Decision {
        /** Ya está en el dispositivo: no se descarga otra vez. */
        data object AlreadyDownloaded : Decision
        /** En cola o en curso: se vuelve a pedir el trabajo único, que no se duplica. */
        data object KeepCurrent : Decision
        /** Nueva, fallida o con el archivo perdido: se empieza de cero. */
        data object Start : Decision
    }

    fun decide(existing: DownloadStatus?, fileExists: Boolean): Decision = when (existing) {
        DownloadStatus.DONE -> if (fileExists) Decision.AlreadyDownloaded else Decision.Start
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> Decision.KeepCurrent
        DownloadStatus.FAILED, null -> Decision.Start
    }
}
