package com.bibliotecago.downloads

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bibliotecago.core.model.BookFormat

/** La aplicación da acceso al [DownloadManager] para que el trabajo lo encuentre. */
interface DownloadsHost {
    val downloadManager: DownloadManager
}

/** Trabajo de WorkManager: sobrevive al cierre de la app y espera a que haya red. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK) ?: return Result.failure()
        val format = inputData.getString(KEY_FORMAT)?.let(BookFormat::valueOf) ?: return Result.failure()
        val manager = (applicationContext as DownloadsHost).downloadManager
        return manager.run(bookId, format, runAttemptCount)
    }

    internal companion object {
        const val KEY_BOOK = "book"
        const val KEY_FORMAT = "format"
    }
}
