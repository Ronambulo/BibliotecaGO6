package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.appContainer
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.Download
import com.bibliotecago.downloads.DownloadState

/**
 * Libros guardados en el dispositivo (RF-09). Sale todo de Room y del
 * almacenamiento privado: funciona igual con el servidor caído.
 */
@Composable
fun DownloadsScreen(onOpenLibrary: () -> Unit, onOpenBook: (BookId) -> Unit) {
    val container = LocalContext.current.appContainer
    val downloads by container.downloads.downloads.collectAsState(initial = null)
    val page = rememberEinkPageState()

    Column(Modifier.fillMaxSize()) {
        val list = downloads
        val used = list.orEmpty().sumOf { (it.state as? DownloadState.Done)?.sizeBytes ?: 0L }
        EinkTopBar(title = "Descargas") {
            if (used > 0) {
                EinkText(formatBytes(used), style = EinkTheme.typography.caption)
                Spacer(Modifier.width(EinkTheme.dimens.gutter))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Unit
                list.isEmpty() -> EinkMessage(
                    title = "No hay libros descargados",
                    message = "Los libros que descargues desde la biblioteca aparecerán aquí y podrás leerlos sin conexión.",
                    actionLabel = "Ir a la biblioteca",
                    onAction = onOpenLibrary,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> EinkPagedGrid(
                    list, columns = 1, rows = 6, state = page, modifier = Modifier.fillMaxSize(),
                    key = { "${it.book.id.value}:${it.format}" },
                ) { DownloadRow(it, container.coverBase) { onOpenBook(it.book.id) } }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: Download, base: okhttp3.HttpUrl?, onClick: () -> Unit) {
    EinkCard(Modifier.fillMaxSize(), onClick = onClick) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BookCover(download.book, base, Modifier.fillMaxHeight().width(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EinkText(download.book.title, style = EinkTheme.typography.label, maxLines = 2)
                EinkText(
                    download.book.authors.joinToString().ifEmpty { "Autor desconocido" },
                    style = EinkTheme.typography.caption, maxLines = 1,
                )
                EinkText(
                    "${download.format.name} · ${describe(download.state)}",
                    Modifier.padding(top = 4.dp),
                    style = EinkTheme.typography.caption,
                    maxLines = 1,
                )
            }
        }
    }
}
