package com.bibliotecago.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Estado «leído» de un libro, sea cual sea el formato en que se leyó. Al
 * desmarcarlo la fila se queda con [finished] a `false`: hasta que [synced]
 * esté a `true` también hay que contárselo a Calibre-Web.
 */
@Entity(tableName = "finished_books")
data class FinishedBook(
    @PrimaryKey val bookId: String,
    val finished: Boolean,
    val updatedAt: Long,
    val synced: Boolean,
)

@Dao
internal interface FinishedBookDao {
    @Query("SELECT EXISTS(SELECT 1 FROM finished_books WHERE bookId = :bookId AND finished = 1)")
    fun observe(bookId: String): Flow<Boolean>

    @Query("SELECT bookId FROM finished_books WHERE finished = 1")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM finished_books WHERE synced = 0 ORDER BY updatedAt")
    fun observePending(): Flow<List<FinishedBook>>

    @Query("SELECT * FROM finished_books WHERE synced = 0 ORDER BY updatedAt")
    suspend fun pending(): List<FinishedBook>

    @Query("SELECT * FROM finished_books")
    suspend fun all(): List<FinishedBook>

    @Upsert
    suspend fun upsert(book: FinishedBook)

    @Transaction
    suspend fun applyRemote(readIds: Set<String>, now: Long) {
        val local = all().associateBy { it.bookId }
        for (row in local.values) {
            val read = row.bookId in readIds
            if (row.synced && row.finished != read) upsert(row.copy(finished = read, updatedAt = now))
        }
        for (id in readIds) {
            if (id !in local) upsert(FinishedBook(id, finished = true, updatedAt = now, synced = true))
        }
    }

    /** Solo si no ha vuelto a cambiar mientras se enviaba. */
    @Query("UPDATE finished_books SET synced = 1 WHERE bookId = :bookId AND updatedAt = :updatedAt")
    suspend fun markSynced(bookId: String, updatedAt: Long)
}
