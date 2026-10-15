package com.bibliotecago.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.bibliotecago.core.model.BookFormat
import kotlinx.coroutines.flow.Flow

/**
 * Marcador en un formato de un libro. El id es un UUID, no un autonumérico,
 * para que siga siendo el mismo si algún día se sincroniza con otro aparato.
 */
@Entity(tableName = "bookmarks", indices = [Index("bookId", "format")])
data class Bookmark(
    @PrimaryKey val id: String,
    val bookId: String,
    val format: BookFormat,
    /** JSON del `Locator` de Readium, como en [ReadingProgress]. */
    val locator: String,
    /** Capítulo, si se sabe. */
    val title: String?,
    /** Principio del texto marcado, para reconocerlo en la lista. */
    val text: String?,
    val progression: Double?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
internal interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND format = :format ORDER BY progression, createdAt")
    fun observe(bookId: String, format: BookFormat): Flow<List<Bookmark>>

    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)
}
