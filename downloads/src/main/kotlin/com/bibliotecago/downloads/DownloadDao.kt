package com.bibliotecago.downloads

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.bibliotecago.core.model.BookFormat
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    fun observeBook(bookId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    suspend fun forBook(bookId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId AND format = :format")
    suspend fun get(bookId: String, format: BookFormat): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = 0, problem = :problem WHERE bookId = :bookId AND format = :format")
    suspend fun setStatus(bookId: String, format: BookFormat, status: DownloadStatus, problem: String?)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE bookId = :bookId AND format = :format AND status = 'RUNNING'")
    suspend fun setProgress(bookId: String, format: BookFormat, bytes: Long)

    @Query(
        "UPDATE downloads SET status = 'DONE', downloadedBytes = :size, fileName = :fileName, problem = NULL, " +
            "completedAt = :at WHERE bookId = :bookId AND format = :format"
    )
    suspend fun markDone(bookId: String, format: BookFormat, size: Long, fileName: String, at: Long)

    @Query("DELETE FROM downloads WHERE bookId = :bookId AND format = :format")
    suspend fun delete(bookId: String, format: BookFormat)
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = true)
internal abstract class DownloadsDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
