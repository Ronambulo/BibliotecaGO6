package com.bibliotecago.downloads

import android.content.Context
import androidx.room.Room
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.core.model.Series
import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Un formato de un libro guardado (o guardándose) en el dispositivo. */
data class Download(val book: Book, val format: BookFormat, val state: DownloadState, val addedAt: Long)

sealed interface DownloadState {
    /** Esperando turno o red. [retryProblem]: por qué falló el intento anterior. */
    data class Queued(val retryProblem: ConnectionProblem?) : DownloadState
    data class Running(val downloadedBytes: Long, val totalBytes: Long?) : DownloadState {
        val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }
    data class Done(val sizeBytes: Long, val file: File) : DownloadState
    data class Failed(val problem: ConnectionProblem) : DownloadState
}

/**
 * Descargas de EPUB y PDF al almacenamiento privado de la app (RF-05). El
 * estado vive en Room y el trabajo en WorkManager, con un trabajo único por
 * libro y formato: pedir dos veces la misma descarga no la duplica.
 */
class DownloadManager(
    context: Context,
    connection: ServerConnection,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val dao = Room.databaseBuilder(appContext, DownloadsDatabase::class.java, "downloads.db")
        .build()
        .downloads()
    private val downloader = FileDownloader(connection)

    /** Carpeta privada de los libros: no la ve ninguna otra app ni entra en copias de seguridad. */
    val directory = File(appContext.filesDir, "books")

    val downloads: Flow<List<Download>> = dao.observeAll().map { rows -> rows.map(::toDownload) }

    fun observe(bookId: BookId): Flow<List<Download>> =
        dao.observeBook(bookId.value).map { rows -> rows.map(::toDownload) }

    /** Libro reconstruido con lo guardado al descargarlo, para la ficha sin conexión. */
    suspend fun savedBook(bookId: BookId): Book? = dao.forBook(bookId.value).takeIf { it.isNotEmpty() }?.let(::toBook)

    suspend fun enqueue(book: Book, format: BookFormat) {
        val file = book.files.firstOrNull { it.format == format } ?: return
        val existing = dao.get(book.id.value, format)
        val exists = existing?.fileName?.let { File(directory, it).exists() } == true
        when (DownloadPolicy.decide(existing?.status, exists)) {
            DownloadPolicy.Decision.AlreadyDownloaded -> return
            DownloadPolicy.Decision.KeepCurrent -> Unit
            DownloadPolicy.Decision.Start -> dao.upsert(entityFor(book, file, existing?.addedAt ?: clock()))
        }
        schedule(book.id.value, format)
    }

    /** Cancela una descarga en curso o en cola, o descarta una fallida. */
    suspend fun cancel(bookId: BookId, format: BookFormat) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(bookId.value, format))
        remove(bookId, format)
    }

    /** Quita el libro del dispositivo. En el servidor no se toca nada. */
    suspend fun remove(bookId: BookId, format: BookFormat) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(bookId.value, format))
        val target = File(directory, fileName(bookId.value, format))
        target.delete()
        File(directory, target.name + FileDownloader.PART_SUFFIX).delete()
        dao.delete(bookId.value, format)
    }

    /** Bytes que ocupan los libros descargados. */
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Repara el estado al arrancar: descargas terminadas cuyo archivo ya no
     * está y restos de archivos que no pertenecen a ninguna descarga.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val rows = dao.all()
        rows.filter { it.status == DownloadStatus.DONE && it.fileName?.let { name -> File(directory, name).exists() } != true }
            .forEach { dao.delete(it.bookId, it.format) }
        val known = rows.map { fileName(it.bookId, it.format) }.toSet()
        val active = rows.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
            .map { fileName(it.bookId, it.format) + FileDownloader.PART_SUFFIX }.toSet()
        directory.listFiles()?.filter { it.name !in known && it.name !in active }?.forEach { it.delete() }
    }

    /** Lo ejecuta [DownloadWorker]. [attempt] empieza en 0. */
    internal suspend fun run(bookId: String, format: BookFormat, attempt: Int): ListenableWorker.Result {
        val row = dao.get(bookId, format) ?: return ListenableWorker.Result.success()
        val target = File(directory, fileName(bookId, format))
        if (row.status == DownloadStatus.DONE && target.exists()) return ListenableWorker.Result.success()

        dao.setStatus(bookId, format, DownloadStatus.RUNNING, problem = null)
        val progress = MutableStateFlow(0L)
        return try {
            val size = coroutineScope {
                // Pocas actualizaciones: cada cambio en pantalla es un refresco de tinta.
                val writer = launch {
                    progress.collect {
                        dao.setProgress(bookId, format, it)
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
                try {
                    downloader.download(row.remotePath, target) { progress.value = it }
                } finally {
                    writer.cancel()
                }
            }
            dao.markDone(bookId, format, size, target.name, clock())
            ListenableWorker.Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = ConnectionProblem.fromException(e)
            if (DownloadPolicy.shouldRetry(problem, attempt)) {
                dao.setStatus(bookId, format, DownloadStatus.QUEUED, problem.name)
                ListenableWorker.Result.retry()
            } else {
                dao.setStatus(bookId, format, DownloadStatus.FAILED, problem.name)
                ListenableWorker.Result.failure()
            }
        }
    }

    private fun schedule(bookId: String, format: BookFormat) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK to bookId, DownloadWorker.KEY_FORMAT to format.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        // KEEP: si ya hay un trabajo para este libro y formato, no se crea otro.
        WorkManager.getInstance(appContext).enqueueUniqueWork(workName(bookId, format), ExistingWorkPolicy.KEEP, request)
    }

    private fun entityFor(book: Book, file: BookFile, addedAt: Long) = DownloadEntity(
        bookId = book.id.value,
        format = file.format,
        title = book.title,
        authors = book.authors.joinToString("\n"),
        seriesName = book.series?.name,
        seriesIndex = book.series?.index,
        summary = book.summary,
        tags = book.tags.joinToString("\n"),
        language = book.language,
        rating = book.rating,
        coverPath = book.coverPath,
        remotePath = file.path,
        expectedBytes = file.sizeBytes,
        status = DownloadStatus.QUEUED,
        addedAt = addedAt,
    )

    private fun toDownload(row: DownloadEntity): Download {
        val problem = row.problem?.let { name -> ConnectionProblem.entries.firstOrNull { it.name == name } }
        val state = when (row.status) {
            DownloadStatus.QUEUED -> DownloadState.Queued(problem)
            DownloadStatus.RUNNING -> DownloadState.Running(row.downloadedBytes, row.expectedBytes)
            DownloadStatus.DONE -> DownloadState.Done(row.downloadedBytes, File(directory, row.fileName ?: fileName(row.bookId, row.format)))
            DownloadStatus.FAILED -> DownloadState.Failed(problem ?: ConnectionProblem.Unknown)
        }
        return Download(toBook(listOf(row)), row.format, state, row.addedAt)
    }

    private fun toBook(rows: List<DownloadEntity>): Book {
        val first = rows.first()
        return Book(
            id = BookId(first.bookId),
            title = first.title,
            authors = first.authors.split("\n").filter { it.isNotBlank() },
            series = first.seriesName?.let { Series(it, first.seriesIndex) },
            summary = first.summary,
            tags = first.tags.split("\n").filter { it.isNotBlank() },
            files = rows.map { BookFile(it.format, it.remotePath, it.expectedBytes) },
            coverPath = first.coverPath,
            language = first.language,
            rating = first.rating,
        )
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 1_000L

        private fun workName(bookId: String, format: BookFormat) = "download:$bookId:${format.name}"

        /** Nombre estable y seguro para el sistema de archivos: `<id>.epub`. */
        fun fileName(bookId: String, format: BookFormat): String {
            val safe = bookId.substringAfterLast(':').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
            val stem = safe.ifEmpty { Integer.toHexString(bookId.hashCode()) }
            return "$stem.${format.name.lowercase()}"
        }
    }
}
