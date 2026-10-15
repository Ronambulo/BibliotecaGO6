package com.bibliotecago.app.demo

import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.core.model.Series

/** Datos ficticios para el prototipo visual (Fase 1). Desaparecen al conectar OPDS. */
object DemoBooks {
    private val seeds = listOf(
        "El nombre del viento" to "Patrick Rothfuss",
        "Clean Architecture" to "Robert C. Martin",
        "Fundación" to "Isaac Asimov",
        "Designing Data-Intensive Applications" to "Martin Kleppmann",
        "La sombra del viento" to "Carlos Ruiz Zafón",
        "Kotlin in Action" to "Dmitry Jemerov, Svetlana Isakova",
        "Dune" to "Frank Herbert",
        "The Pragmatic Programmer" to "David Thomas, Andrew Hunt",
        "Cien años de soledad" to "Gabriel García Márquez",
        "Operating Systems: Three Easy Pieces" to "Remzi Arpaci-Dusseau",
        "El problema de los tres cuerpos" to "Liu Cixin",
        "Refactoring" to "Martin Fowler",
    )

    val all: List<Book> = (0 until 40).map { i ->
        val (title, author) = seeds[i % seeds.size]
        val volume = i / seeds.size + 1
        Book(
            id = BookId("demo-$i"),
            title = if (volume == 1) title else "$title ($volume)",
            authors = author.split(", "),
            series = if (i % 5 == 0) Series("Serie de ejemplo", (i / 5 + 1).toDouble()) else null,
            summary = "Sinopsis de ejemplo para comprobar la legibilidad del texto en tinta electrónica.",
            files = listOf(BookFile(if (i % 3 == 0) BookFormat.PDF else BookFormat.EPUB, "/demo/$i", null)),
        )
    }
}
