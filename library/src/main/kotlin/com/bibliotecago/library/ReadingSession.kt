package com.bibliotecago.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Un rato de lectura seguido de un libro: desde que se abre o se vuelve a él
 * hasta que se cierra o se deja de pasar página un buen rato.
 */
@Entity(tableName = "reading_sessions", indices = [Index("startedAt"), Index("bookId")])
data class ReadingSession(
    @PrimaryKey val id: String,
    val bookId: String,
    val format: BookFormat,
    val startedAt: Long,
    val endedAt: Long,
    /** De 0 a 1 en todo el libro al empezar y al acabar, si el lector lo sabe. */
    val startProgression: Double?,
    val endProgression: Double?,
    /** Páginas avanzadas; volver atrás no cuenta. */
    val pages: Int,
) {
    val durationMillis: Long get() = endedAt - startedAt
}

@Dao
internal interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY startedAt")
    fun observeAll(): Flow<List<ReadingSession>>

    @Upsert
    suspend fun upsert(session: ReadingSession)

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

/** Historial de sesiones de lectura: de aquí salen las estadísticas de tiempo. */
class ReadingSessionStore internal constructor(private val dao: ReadingSessionDao, private val clock: () -> Long) {

    /** Todas, de la más antigua a la más reciente. */
    fun observeAll(): Flow<List<ReadingSession>> = dao.observeAll()

    /** Un registrador para el libro que se abre en un lector. */
    fun recorder(bookId: BookId, format: BookFormat) = ReadingSessionRecorder(dao, bookId, format, clock)
}

/**
 * Va apuntando la sesión en curso según se pasa página. Si pasa más de
 * [IDLE_MILLIS] sin tocar el libro, la sesión se cierra (con [LAST_PAGE_MILLIS]
 * para la última página) y la siguiente vuelta de página empieza otra: así un
 * libro abierto y olvidado no cuenta como lectura.
 */
class ReadingSessionRecorder internal constructor(
    private val dao: ReadingSessionDao,
    private val bookId: BookId,
    private val format: BookFormat,
    private val clock: () -> Long,
) {
    private val lock = Mutex()
    private var current: ReadingSession? = null
    private var lastActivity = 0L
    private var lastPage: Int? = null

    /**
     * El lector muestra otra posición (o la misma al volver a él).
     * [page] es la página desde 1, si el lector la sabe.
     */
    suspend fun onPosition(progression: Double?, page: Int?) = lock.withLock {
        val now = clock()
        val session = current
        current = if (session == null || now - lastActivity > IDLE_MILLIS) {
            session?.let { close(it) }
            ReadingSession(UUID.randomUUID().toString(), bookId.value, format, now, now, progression, progression, 0)
        } else {
            session.copy(endedAt = now, endProgression = progression ?: session.endProgression, pages = session.pages + advanced(session, progression, page))
        }
        lastActivity = now
        lastPage = page
        dao.upsert(current!!)
    }

    /** El lector deja de verse: se cierra la sesión en curso, contando lo leído de la última página. */
    suspend fun pause() = lock.withLock {
        current?.let { close(it) }
        current = null
    }

    private suspend fun close(session: ReadingSession) {
        val idle = clock() - lastActivity
        val end = lastActivity + if (idle > IDLE_MILLIS) LAST_PAGE_MILLIS else idle
        val closed = session.copy(endedAt = end)
        // Abrir y cerrar sin leer no es una sesión.
        if (closed.durationMillis < MIN_SESSION_MILLIS && closed.pages == 0) dao.delete(closed.id) else dao.upsert(closed)
    }

    /** Páginas que se ha avanzado: los saltos largos (índice, buscar) cuentan como una. */
    private fun advanced(session: ReadingSession, progression: Double?, page: Int?): Int {
        val previous = lastPage
        if (page != null && previous != null) {
            val step = page - previous
            return if (step > MAX_STEP) 1 else step.coerceAtLeast(0)
        }
        val before = session.endProgression ?: return 0
        return if (progression != null && progression > before) 1 else 0
    }

    companion object {
        const val IDLE_MILLIS = 10 * 60_000L
        const val LAST_PAGE_MILLIS = 60_000L
        private const val MIN_SESSION_MILLIS = 20_000L
        private const val MAX_STEP = 3
    }
}
