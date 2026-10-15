package com.bibliotecago.library

import android.content.Context
import androidx.room.Room
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Datos de lectura guardados en el dispositivo (Room, `library.db`). */
class LibraryStore(context: Context, clock: () -> Long = System::currentTimeMillis) {

    private val database = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db")
        .build()

    val progress = ReadingProgressStore(database.progress(), clock)
    val bookmarks = BookmarkStore(database.bookmarks(), clock)
}

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
