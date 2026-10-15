package com.bibliotecago.library

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Las migraciones no pueden perder lo que ya se ha leído. Usa los esquemas de `library/schemas`. */
@RunWith(AndroidJUnit4::class)
class LibraryMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)

    @Test
    fun de1a2ConservaElProgresoYAnadeMarcadores() {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO reading_progress VALUES ('7', 'EPUB', 'loc', 0.25, 1000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(NAME, 2, true)

        db.query("SELECT bookId, format, locator, progression, updatedAt FROM reading_progress").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("7", it.getString(0))
            assertEquals("EPUB", it.getString(1))
            assertEquals("loc", it.getString(2))
            assertEquals(0.25, it.getDouble(3), 0.0)
            assertEquals(1000L, it.getLong(4))
        }
        db.query("SELECT COUNT(*) FROM bookmarks").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val NAME = "migracion-test.db"
    }
}
