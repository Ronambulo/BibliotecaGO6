package com.bibliotecago.app.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.AppContainer
import com.bibliotecago.app.appContainer
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.Download
import com.bibliotecago.downloads.DownloadState
import com.bibliotecago.reader.epub.EpubReaderActivity
import com.bibliotecago.reader.pdf.PdfReaderActivity
import kotlinx.coroutines.launch

/**
 * Libros guardados en el dispositivo (RF-09). Sale todo de Room y del
 * almacenamiento privado: funciona igual con el servidor caído.
 */
@Composable
fun DownloadsScreen(onOpenLibrary: () -> Unit, onOpenBook: (BookId) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val downloads by container.downloads.downloads.collectAsState(initial = null)
    val prefs = rememberLibraryPrefs()
    val gridPage = rememberEinkPageState()
    val listPage = rememberEinkPageState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        val list = downloads
        EinkTopBar(title = "Descargas", actions = { if (!list.isNullOrEmpty()) StorageSummary(list) })
        when {
            list == null -> Unit
            list.isEmpty() -> EinkMessage(
                title = "Aún no hay descargas",
                message = "Los libros que descargues desde la biblioteca aparecerán aquí y podrás leerlos sin conexión.",
                icon = EinkIcons.Download,
                actionLabel = "Ir a la biblioteca",
                onAction = onOpenLibrary,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // Cuadrícula de portadas, como la biblioteca: tocar abre la ficha.
            !prefs.asList -> EinkPagedGrid(
                list, columns = 3, rows = 2, state = gridPage, modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { "${it.book.id.value}:${it.format}" },
                spacing = 16.dp,
            ) { download -> DownloadTile(download, container) { onOpenBook(download.book.id) } }
            else -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Tantas filas como quepan enteras.
                    val rows = ((maxHeight - 56.dp - 8.dp) / ROW_HEIGHT).toInt().coerceAtLeast(1)
                    EinkPagedGrid(
                        list, columns = 1, rows = rows, state = listPage, modifier = Modifier.fillMaxSize(),
                        key = { "${it.book.id.value}:${it.format}" },
                        spacing = 0.dp,
                    ) { download ->
                        DownloadRow(
                            download,
                            container,
                            onClick = { onOpenBook(download.book.id) },
                            onRead = { done -> context.openReader(download, done) },
                            onCancel = { scope.launch { container.downloads.cancel(download.book.id, download.format) } },
                            onRetry = { scope.launch { container.downloads.enqueue(download.book, download.format) } },
                        )
                    }
                }
            }
        }
    }
}

private fun Context.openReader(download: Download, done: DownloadState.Done) {
    val book = download.book
    startActivity(
        when (download.format) {
            BookFormat.EPUB -> EpubReaderActivity.intent(this, done.file, book.id, book.title)
            BookFormat.PDF -> PdfReaderActivity.intent(this, done.file, book.id, book.title)
        },
    )
}

/** Cuánto ocupa lo descargado, a la derecha del título. */
@Composable
private fun StorageSummary(list: List<Download>) {
    val done = list.filter { it.state is DownloadState.Done }
    val used = done.sumOf { (it.state as DownloadState.Done).sizeBytes }
    val books = done.distinctBy { it.book.id }.size
    val pending = list.size - done.size
    Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End) {
        EinkText(
            "${if (books == 1) "1 libro" else "$books libros"} · ${formatBytes(used)}",
            style = EinkTheme.typography.label,
            maxLines = 1,
        )
        if (pending > 0) {
            EinkText(
                if (pending == 1) "1 pendiente" else "$pending pendientes",
                style = EinkTheme.typography.caption,
                color = EinkTheme.colors.muted,
                maxLines = 1,
            )
        }
    }
}

/** Portada en la cuadrícula; si aún no está en el dispositivo, su estado debajo. */
@Composable
private fun DownloadTile(download: Download, container: AppContainer, onClick: () -> Unit) {
    val state = download.state
    if (state is DownloadState.Done) {
        BookTile(download.book, container.coverBase, downloaded = true, onClick = onClick)
        return
    }
    Column(Modifier.fillMaxSize()) {
        BookTile(download.book, container.coverBase, downloaded = false, onClick = onClick, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(4.dp))
        EinkText(describe(state), style = EinkTheme.typography.caption, maxLines = 1)
        if (state is DownloadState.Running) {
            EinkProgressBar(state.fraction ?: 0f, Modifier.padding(top = 4.dp).fillMaxWidth(), height = 4.dp)
        }
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    container: AppContainer,
    onClick: () -> Unit,
    onRead: (DownloadState.Done) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = download.state
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.weight(1f).fillMaxWidth().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(download.book, container.coverBase, Modifier.size(width = 52.dp, height = 78.dp), corner = 6.dp, compact = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                EinkText(download.book.title, style = EinkTheme.typography.label, maxLines = 2)
                EinkText(
                    download.book.authors.joinToString().ifEmpty { "Autor desconocido" },
                    style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1,
                )
                EinkText("${download.format.name} · ${describe(state)}", style = EinkTheme.typography.caption, maxLines = 1)
                if (state is DownloadState.Running) {
                    EinkProgressBar(state.fraction ?: 0f, Modifier.padding(top = 4.dp).fillMaxWidth(), height = 6.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            when (state) {
                is DownloadState.Done -> EinkIconButton(EinkIcons.Book, "Leer", { onRead(state) }, tonal = false, selected = true)
                is DownloadState.Queued, is DownloadState.Running -> EinkIconButton(EinkIcons.Close, "Cancelar", onCancel)
                is DownloadState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EinkIconButton(EinkIcons.Refresh, "Reintentar", onRetry)
                    EinkIconButton(EinkIcons.Trash, "Descartar", onCancel)
                }
            }
        }
        EinkDivider()
    }
}

private val ROW_HEIGHT = 104.dp
