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
    val sessions = ReadingSessionStore(database.sessions(), clock)
}

internal const val DATABASE_NAME = "library.db"

/** Por dónde va cada libro. */
class ReadingProgressStore internal constructor(private val dao: ReadingProgressDao, private val clock: () -> Long) {

    suspend fun get(bookId: BookId, format: BookFormat): ReadingProgress? = dao.get(bookId.value, format)

    suspend fun save(
        bookId: BookId,
        format: BookFormat,
        locator: String,
        progression: Double?,
        page: Int? = null,
        pageCount: Int? = null,
    ) = dao.upsert(ReadingProgress(bookId.value, format, locator, progression, clock(), page, pageCount))

    suspend fun delete(bookId: BookId, format: BookFormat) = dao.delete(bookId.value, format)

    /** Marca el libro como recién abierto (orden «Último abierto») sin cambiar dónde va. */
    suspend fun touch(bookId: BookId, format: BookFormat) = dao.touch(bookId.value, format, clock())

    /** Lo último leído primero: para «Seguir leyendo». */
    fun observeAll(): Flow<List<ReadingProgress>> = dao.observeAll()

    /** El progreso de cada formato de un libro, lo último leído primero. */
    fun observe(bookId: BookId): Flow<List<ReadingProgress>> = dao.observe(bookId.value)
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

    /** Los leídos, con la fecha en que se terminaron si se sabe. */
    fun observeFinished(): Flow<List<FinishedBook>> = dao.observeFinished()

    suspend fun set(bookId: BookId, finished: Boolean) {
        val now = clock()
        // Volver a marcar uno ya leído no cambia cuándo se terminó.
        val finishedAt = if (finished) dao.get(bookId.value)?.takeIf { it.finished }?.finishedAt ?: now else null
        dao.upsert(FinishedBook(bookId.value, finished, now, synced = false, finishedAt = finishedAt))
    }

    /** Cambios aún sin enviar a Calibre-Web, del más antiguo al más nuevo. */
    fun observePending(): Flow<List<FinishedBook>> = dao.observePending()

    /** Enviado: deja de estar pendiente, salvo que haya cambiado entretanto. */
    suspend fun markSynced(book: FinishedBook) = dao.markSynced(book.bookId, book.updatedAt)

    /** Lo que queda por enviar ahora mismo. */
    suspend fun pending(): List<FinishedBook> = dao.pending()

    /**
     * Copia lo que dice Calibre-Web: leídos los de [readIds] y no leídos los
     * demás. Lo que aún está pendiente de enviar no se toca: manda el cambio
     * hecho en el dispositivo.
     */
    suspend fun applyRemote(readIds: Set<String>) = dao.applyRemote(readIds, clock())
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

val Context.readingSessions: ReadingSessionStore get() = library.sessions
