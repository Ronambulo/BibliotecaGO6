package com.bibliotecago.library

import android.content.Context
import androidx.room.Room
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Datos de lectura guardados en el dispositivo (Room, `library.db`). */
class LibraryStore internal constructor(database: LibraryDatabase, clock: () -> Long) {

    constructor(context: Context, clock: () -> Long = System::currentTimeMillis) : this(
        Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, DATABASE_NAME).build(),
        clock,
    )

    val progress = ReadingProgressStore(database.progress(), clock)
    val bookmarks = BookmarkStore(database.bookmarks(), clock)
    val finished = FinishedStore(database.finished(), clock)
}

internal const val DATABASE_NAME = "library.db"

/** Por dónde va cada libro. */
class ReadingProgressStore internal constructor(private val dao: ReadingProgressDao, private val clock: () -> Long) {

    suspend fun get(bookId: BookId, format: BookFormat): ReadingProgress? = dao.get(bookId.value, format)

    suspend fun save(bookId: BookId, format: BookFormat, locator: String, progression: Double?) =
        dao.upsert(ReadingProgress(bookId.value, format, locator, progression, clock()))

    suspend fun delete(bookId: BookId, format: BookFormat) = dao.delete(bookId.value, format)

    /** Lo último leído primero: para «Seguir leyendo». */
    fun observeAll(): Flow<List<ReadingProgress>> = dao.observeAll()
}

/** Marcadores de cada libro, en orden de lectura. */
class BookmarkStore internal constructor(private val dao: BookmarkDao, private val clock: () -> Long) {

    fun observe(bookId: BookId, format: BookFormat): Flow<List<Bookmark>> = dao.observe(bookId.value, format)

    suspend fun add(
        bookId: BookId,
        format: BookFormat,
        locator: String,
        title: String?,
        text: String?,
        progression: Double?,
    ): Bookmark {
        val now = clock()
        val bookmark = Bookmark(UUID.randomUUID().toString(), bookId.value, format, locator, title, text, progression, now, now)
        dao.upsert(bookmark)
        return bookmark
    }

    suspend fun delete(bookmark: Bookmark) = dao.delete(bookmark.id)
}

/** Libros marcados como leídos. */
class FinishedStore internal constructor(private val dao: FinishedBookDao, private val clock: () -> Long) {

    fun observe(bookId: BookId): Flow<Boolean> = dao.observe(bookId.value)

    /** Ids de todos los libros leídos. */
    fun observeAll(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    suspend fun set(bookId: BookId, finished: Boolean) {
        dao.upsert(FinishedBook(bookId.value, finished, clock(), synced = false))
    }

    /** Cambios aún sin enviar a Calibre-Web, del más antiguo al más nuevo. */
    fun observePending(): Flow<List<FinishedBook>> = dao.observePending()

    /** Enviado: deja de estar pendiente, salvo que haya cambiado entretanto. */
    suspend fun markSynced(book: FinishedBook) = dao.markSynced(book.bookId, book.updatedAt)
}

/**
 * Lo implementa la `Application`: así los lectores, que son actividades de
 * otros módulos, llegan al almacén sin depender de `:app`.
 */
interface LibraryHost {
    val library: LibraryStore
}

private val Context.library: LibraryStore
    get() = (applicationContext as LibraryHost).library

val Context.readingProgress: ReadingProgressStore get() = library.progress

val Context.bookmarks: BookmarkStore get() = library.bookmarks

val Context.finishedBooks: FinishedStore get() = library.finished
