package com.bibliotecago.downloads

import androidx.room.Entity
import com.bibliotecago.core.model.BookFormat

/**
 * Una descarga: un formato de un libro. Guarda también los datos de la ficha
 * para que Descargas y la ficha funcionen sin conexión (RF-09).
 */
@Entity(tableName = "downloads", primaryKeys = ["bookId", "format"])
data class DownloadEntity(
    val bookId: String,
    val format: BookFormat,
    val title: String,
    /** Autores separados por saltos de línea. */
    val authors: String,
    val seriesName: String?,
    val seriesIndex: Double?,
    val summary: String?,
    /** Etiquetas separadas por saltos de línea. */
    val tags: String,
    val language: String?,
    val rating: Int?,
    val coverPath: String?,
    /** Ruta del archivo en el servidor, relativa como en OPDS. */
    val remotePath: String,
    /** Tamaño anunciado por OPDS (`length`), si lo hay. */
    val expectedBytes: Long?,
    val status: DownloadStatus,
    val downloadedBytes: Long = 0,
    /** Nombre de un [com.bibliotecago.server.ConnectionProblem] si falló o se va a reintentar. */
    val problem: String? = null,
    /** Nombre del archivo dentro de la carpeta de libros, una vez completo. */
    val fileName: String? = null,
    val addedAt: Long,
    val completedAt: Long? = null,
)

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED }
