package com.bibliotecago.library

import android.content.Context
import androidx.room.Room
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.Flow

/** Guarda la posición de lectura en el dispositivo (Room, `library.db`). */
class ReadingProgressStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val dao = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db")
        .build()
        .progress()

    suspend fun get(bookId: BookId, format: BookFormat): ReadingProgress? = dao.get(bookId.value, format)

    suspend fun save(bookId: BookId, format: BookFormat, locator: String, progression: Double?) =
        dao.upsert(ReadingProgress(bookId.value, format, locator, progression, clock()))

    suspend fun delete(bookId: BookId, format: BookFormat) = dao.delete(bookId.value, format)

    /** Lo último leído primero: para «Seguir leyendo». */
    fun observeAll(): Flow<List<ReadingProgress>> = dao.observeAll()
}

/**
 * Lo implementa la `Application`: así los lectores, que son actividades de
 * otros módulos, llegan al almacén sin depender de `:app`.
 */
interface LibraryHost {
    val readingProgress: ReadingProgressStore
}

val Context.readingProgress: ReadingProgressStore
    get() = (applicationContext as LibraryHost).readingProgress
