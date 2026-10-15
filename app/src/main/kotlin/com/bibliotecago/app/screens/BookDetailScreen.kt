package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.catalog.CatalogRepository
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFile
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.Download
import com.bibliotecago.downloads.DownloadState
import com.bibliotecago.reader.epub.EpubReaderActivity
import com.bibliotecago.reader.pdf.PdfReaderActivity

@Composable
fun BookDetailScreen(bookId: BookId, onBack: () -> Unit, onOpenFeed: (href: String, title: String) -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = bookId.value) { BookDetailViewModel(container, bookId) }
    var confirmRemove by rememberSaveable { mutableStateOf<BookFormat?>(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Ficha del libro", onBack = onBack)
        val book = vm.book
        when {
            book != null -> Column(
                Modifier
                    .weight(1f)
                    .einkVerticalScroll(rememberScrollState())
                    .padding(EinkTheme.dimens.gutter)
            ) {
                Header(book, vm)
                Related(book, onOpenFeed)
                Spacer(Modifier.height(16.dp))
                EinkText("Formatos", style = EinkTheme.typography.heading)
                Spacer(Modifier.height(8.dp))
                // Sin conexión solo se conocen los formatos descargados.
                val formats = book.files.ifEmpty { vm.downloads.values.map { it.book.files }.flatten() }
                formats.forEach { file ->
                    FormatRow(
                        file = file,
                        download = vm.downloads[file.format],
                        onDownload = { vm.download(file.format) },
                        onCancel = { vm.cancel(file.format) },
                        onRemove = { confirmRemove = file.format },
                        onRead = { done ->
                            when (file.format) {
                                BookFormat.EPUB -> context.startActivity(EpubReaderActivity.intent(context, done.file, book.id, book.title))
                                BookFormat.PDF -> context.startActivity(PdfReaderActivity.intent(context, done.file, book.id, book.title))
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Details(book)
            }
            vm.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EinkMessage("Cargando ficha…")
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EinkMessage(
                    "Ficha no disponible",
                    message = "Este libro no está descargado y no se ha cargado del servidor. Ábrelo desde la biblioteca.",
                    actionLabel = "Volver",
                    onAction = onBack,
                )
            }
        }
    }

    confirmRemove?.let { format ->
        EinkDialog(
            onDismissRequest = { confirmRemove = null },
            title = "¿Quitar del dispositivo?",
            text = "Se borra la copia en ${format.name} de «${vm.book?.title.orEmpty()}» de este dispositivo. " +
                "El libro no se borra del servidor: podrás volver a descargarlo.",
            confirmLabel = "Quitar",
            onConfirm = {
                vm.remove(format)
                confirmRemove = null
            },
        )
    }
}

@Composable
private fun Header(book: Book, vm: BookDetailViewModel) {
    Row {
        BookCover(
            book, vm.coverBase,
            Modifier.size(width = 120.dp, height = 180.dp),
            downloaded = vm.downloads.values.any { it.state is DownloadState.Done },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            EinkText(book.title, style = EinkTheme.typography.heading)
            Spacer(Modifier.height(4.dp))
            EinkText(book.authors.joinToString().ifEmpty { "Autor desconocido" })
            seriesLabel(book)?.let {
                Spacer(Modifier.height(8.dp))
                EinkText(it, style = EinkTheme.typography.caption)
            }
            book.rating?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(8.dp))
                EinkText(ratingStars(it), style = EinkTheme.typography.label)
            }
        }
    }
}

/**
 * «Más de…»: las entradas OPDS no enlazan al autor ni a la serie, así que se
 * busca su nombre (la búsqueda de Calibre-Web incluye autores y series).
 */
@Composable
private fun Related(book: Book, onOpenFeed: (String, String) -> Unit) {
    val author = book.authors.firstOrNull()
    val series = book.series?.name
    if (author == null && series == null) return
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (author != null) {
            EinkButton("Más del autor", { onOpenFeed(CatalogRepository.searchHref(author), author) }, Modifier.weight(1f))
        }
        if (series != null) {
            EinkButton("Ver la serie", { onOpenFeed(CatalogRepository.searchHref(series), series) }, Modifier.weight(1f))
        }
    }
}

/** Un formato con su estado local y las acciones que tocan (RF-04). */
@Composable
private fun FormatRow(
    file: BookFile,
    download: Download?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRead: (DownloadState.Done) -> Unit,
) {
    val state = download?.state
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EinkText(file.format.name, style = EinkTheme.typography.label)
            file.sizeBytes?.let { EinkText(" · ${formatBytes(it)}", style = EinkTheme.typography.label) }
            if (state is DownloadState.Done) DownloadedBadge(compact = false)
        }
        when (state) {
            null -> Unit
            is DownloadState.Done -> Unit
            is DownloadState.Failed -> {
                EinkText(describe(state), style = EinkTheme.typography.caption)
                EinkText(state.problem.hint, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
            }
            else -> EinkText(describe(state), style = EinkTheme.typography.caption)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val wide = Modifier.weight(1f)
            when (state) {
                null -> EinkButton("Descargar", onDownload, wide, primary = true, icon = EinkIcons.Download)
                is DownloadState.Queued, is DownloadState.Running -> EinkButton("Cancelar", onCancel, wide)
                is DownloadState.Failed -> {
                    EinkButton("Reintentar", onDownload, wide, primary = true)
                    EinkButton("Descartar", onCancel, wide)
                }
                is DownloadState.Done -> {
                    EinkButton("Leer", { onRead(state) }, wide, primary = true)
                    EinkButton("Quitar", onRemove, wide)
                }
            }
        }
    }
}

@Composable
private fun Details(book: Book) {
    book.summary?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(8.dp))
        EinkDivider()
        Spacer(Modifier.height(16.dp))
        EinkText("Sinopsis", style = EinkTheme.typography.heading)
        Spacer(Modifier.height(8.dp))
        EinkText(it)
    }
    if (book.tags.isNotEmpty() || book.language != null) {
        Spacer(Modifier.height(16.dp))
        EinkDivider()
        Spacer(Modifier.height(16.dp))
    }
    if (book.tags.isNotEmpty()) {
        EinkText("Etiquetas: ${book.tags.joinToString()}", style = EinkTheme.typography.caption)
        Spacer(Modifier.height(4.dp))
    }
    book.language?.let { EinkText("Idioma: ${languageName(it)}", style = EinkTheme.typography.caption) }
}
