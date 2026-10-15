package com.bibliotecago.reader.epub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/**
 * Páginas de un EPUB descargado, para la ficha del libro: las mismas que
 * cuenta el lector (posiciones de Readium). `null` si no se puede abrir.
 */
suspend fun epubPageCount(context: Context, file: File): Int? = withContext(Dispatchers.IO) {
    val http = DefaultHttpClient()
    val retriever = AssetRetriever(context.contentResolver, http)
    val opener = PublicationOpener(DefaultPublicationParser(context, http, retriever, pdfFactory = null))
    val asset = retriever.retrieve(file).getOrNull() ?: return@withContext null
    val publication = opener.open(asset, allowUserInteraction = false).getOrNull()
    if (publication == null) {
        asset.close()
        return@withContext null
    }
    try {
        publication.positions().size.takeIf { it > 0 }
    } finally {
        publication.close()
    }
}
