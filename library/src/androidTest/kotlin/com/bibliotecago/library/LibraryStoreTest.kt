package com.bibliotecago.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = 1_000L
    private lateinit var database: LibraryDatabase
    private lateinit var store: LibraryStore

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        store = LibraryStore(database) { now }
    }

    @After
    fun close() = database.close()

    @Test
    fun guardarYLeerElProgreso() = runBlocking {
        store.progress.save(DUNE, BookFormat.EPUB, "loc-1", 0.1)
        now = 2_000L
        store.progress.save(DUNE, BookFormat.EPUB, "loc-2", 0.2)

        assertEquals(ReadingProgress(DUNE.value, BookFormat.EPUB, "loc-2", 0.2, 2_000L), store.progress.get(DUNE, BookFormat.EPUB))
        assertEquals(1, store.progress.observeAll().first().size)
    }

    @Test
    fun cadaFormatoLlevaSuProgreso() = runBlocking {
        store.progress.save(DUNE, BookFormat.EPUB, "epub", 0.5)
        store.progress.save(DUNE, BookFormat.PDF, "pdf", null)

        assertEquals("epub", store.progress.get(DUNE, BookFormat.EPUB)?.locator)
        assertEquals("pdf", store.progress.get(DUNE, BookFormat.PDF)?.locator)

        store.progress.delete(DUNE, BookFormat.PDF)
        assertNull(store.progress.get(DUNE, BookFormat.PDF))
        assertEquals("epub", store.progress.get(DUNE, BookFormat.EPUB)?.locator)
    }

    @Test
    fun loUltimoLeidoVaPrimero() = runBlocking {
        store.progress.save(DUNE, BookFormat.EPUB, "a", 0.1)
        now = 2_000L
        store.progress.save(FUNDACION, BookFormat.PDF, "b", 0.2)
        now = 3_000L
        store.progress.save(DUNE, BookFormat.EPUB, "c", 0.3)

        assertEquals(listOf(DUNE.value, FUNDACION.value), store.progress.observeAll().first().map { it.bookId })
    }

    @Test
    fun marcadoresEnOrdenDeLectura() = runBlocking {
        val late = store.bookmarks.add(DUNE, BookFormat.EPUB, "l2", "Cap. 9", "tarde", 0.9)
        now = 2_000L
        val early = store.bookmarks.add(DUNE, BookFormat.EPUB, "l1", "Cap. 1", "pronto", 0.1)
        store.bookmarks.add(DUNE, BookFormat.PDF, "otro formato", null, null, 0.5)
        store.bookmarks.add(FUNDACION, BookFormat.EPUB, "otro libro", null, null, 0.5)

        assertEquals(listOf(early, late), store.bookmarks.observe(DUNE, BookFormat.EPUB).first())
        assertEquals(1_000L, late.createdAt)
        assertEquals(late.createdAt, late.updatedAt)
    }

    @Test
    fun borrarUnMarcador() = runBlocking {
        val keep = store.bookmarks.add(DUNE, BookFormat.EPUB, "l1", null, null, 0.1)
        val gone = store.bookmarks.add(DUNE, BookFormat.EPUB, "l2", null, null, 0.2)

        store.bookmarks.delete(gone)

        assertEquals(listOf(keep), store.bookmarks.observe(DUNE, BookFormat.EPUB).first())
    }

    @Test
    fun marcarYDesmarcarComoLeido() = runBlocking {
        assertFalse(store.finished.observe(DUNE).first())

        store.finished.set(DUNE, true)
        store.finished.set(DUNE, true)

        assertTrue(store.finished.observe(DUNE).first())
        assertFalse(store.finished.observe(FUNDACION).first())
        assertEquals(setOf(DUNE.value), store.finished.observeAll().first())

        store.finished.set(DUNE, false)
        assertFalse(store.finished.observe(DUNE).first())
        assertEquals(emptySet<String>(), store.finished.observeAll().first())
    }

    @Test
    fun losCambiosDeLeidoQuedanPendientesHastaEnviarlos() = runBlocking {
        store.finished.set(DUNE, true)
        val pending = store.finished.observePending().first().single()
        assertTrue(pending.finished)

        // Cambia otra vez mientras se enviaba: el envío viejo no lo da por sincronizado.
        now = 2_000L
        store.finished.set(DUNE, false)
        store.finished.markSynced(pending)
        val again = store.finished.observePending().first().single()
        assertFalse(again.finished)

        store.finished.markSynced(again)
        assertTrue(store.finished.observePending().first().isEmpty())
    }

    @Test
    fun elProgresoSigueAlVolverAAbrir() = runBlocking {
        // Como al cerrar la app o reiniciar: base de datos en disco, cerrada y abierta de nuevo.
        val name = "persistencia-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
            LibraryStore(first) { now }.apply {
                progress.save(DUNE, BookFormat.EPUB, "loc", 0.42)
                bookmarks.add(DUNE, BookFormat.EPUB, "marca", "Cap. 3", "texto", 0.4)
            }
            first.close()

            val second = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
            val reopened = LibraryStore(second) { now }
            assertEquals(0.42, reopened.progress.get(DUNE, BookFormat.EPUB)?.progression)
            assertEquals(listOf("marca"), reopened.bookmarks.observe(DUNE, BookFormat.EPUB).first().map { it.locator })
            second.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    private companion object {
        val DUNE = BookId("1")
        val FUNDACION = BookId("2")
    }
}
