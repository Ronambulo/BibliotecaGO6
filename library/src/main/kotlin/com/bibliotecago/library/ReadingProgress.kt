package com.bibliotecago.library

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.bibliotecago.core.model.BookFormat
import kotlinx.coroutines.flow.Flow

/**
 * Por dónde va la lectura de un formato de un libro. [locator] es el JSON de
 * un `Locator` de Readium: así esta capa no depende del lector.
 */
@Entity(tableName = "reading_progress", primaryKeys = ["bookId", "format"])
data class ReadingProgress(
    val bookId: String,
    val format: BookFormat,
    val locator: String,
    /** De 0 a 1 en todo el libro, si el lector lo sabe. */
    val progression: Double?,
    val updatedAt: Long,
    /** Página que se ve (desde 1) y total, si el lector los sabe; para la ficha del libro. */
    val page: Int? = null,
    val pageCount: Int? = null,
)

@Dao
internal interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND format = :format")
    suspend fun get(bookId: String, format: BookFormat): ReadingProgress?

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ReadingProgress>>

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observe(bookId: String): Flow<List<ReadingProgress>>

    @Upsert
    suspend fun upsert(progress: ReadingProgress)

    @Query("UPDATE reading_progress SET updatedAt = :now WHERE bookId = :bookId AND format = :format")
    suspend fun touch(bookId: String, format: BookFormat, now: Long)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId AND format = :format")
    suspend fun delete(bookId: String, format: BookFormat)
}

@Database(
    entities = [ReadingProgress::class, Bookmark::class, FinishedBook::class, ReadingSession::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun progress(): ReadingProgressDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun finished(): FinishedBookDao
    abstract fun sessions(): ReadingSessionDao
}
