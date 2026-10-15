package com.bibliotecago.reader.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Páginas de un PDF descargado, para la ficha del libro; `null` si no se puede abrir. */
suspend fun pdfPageCount(file: File): Int? = withContext(Dispatchers.IO) {
    runCatching { PdfDocument.open(file).use { it.pageCount } }.getOrNull()
}
