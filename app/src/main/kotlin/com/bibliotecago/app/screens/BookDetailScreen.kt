package com.bibliotecago.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.catalog.CatalogRepository
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkChip
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTone
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkBorder
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
    val book = vm.book

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Ficha del libro", onBack = onBack)
        when {
            book != null -> Column(
                Modifier
                    .weight(1f)
                    .einkVerticalScroll(rememberScrollState())
                    .padding(horizontal = EinkTheme.dimens.gutter)
                    .padding(bottom = 20.dp),
            ) {
                Header(book, vm, onOpenFeed)
                Spacer(Modifier.height(20.dp))
                // Sin conexión solo se conocen los formatos descargados.
                val formats = book.files.ifEmpty { vm.downloads.values.map { it.book.files }.flatten() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    formats.forEach { file ->
                        FormatCard(
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
                    }
                }
                Details(book, onOpenFeed = onOpenFeed)
            }
            // Mientras llega la ficha, la pantalla en blanco bajo la barra: sin mensajes intermedios.
            vm.loading -> Unit
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EinkMessage(
                    "Ficha no disponible",
                    message = "Este libro no está descargado y no se ha cargado del servidor. Ábrelo desde la biblioteca.",
                    icon = EinkIcons.Warning,
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
            icon = EinkIcons.Trash,
            confirmLabel = "Quitar",
            onConfirm = {
                vm.remove(format)
                confirmRemove = null
            },
        )
    }
}

/** Está a medias: hay progreso guardado, sin terminar y sin marcar como leído. */
private fun BookDetailViewModel.isReading(): Boolean {
    val fraction = progress?.progression ?: return false
    return !finished && fraction > 0.0 && fraction < 1.0
}

/**
 * Si está a medias, por cuál va: «Página 42 de 310 · 13 %» con una barra fina.
 * Sin páginas conocidas muestra solo el porcentaje.
 */
@Composable
private fun ReadingInfo(vm: BookDetailViewModel) {
    val total = vm.pageCount
    val progress = vm.progress
    val fraction = progress?.progression?.toFloat()?.coerceIn(0f, 1f)
    val reading = vm.isReading()
    val text = when {
        reading && total != null && progress?.page != null ->
            "Página ${progress.page} de $total · ${percent(fraction!!)}"
        reading -> "Leído ${percent(fraction!!)}"
        else -> return
    }
    Spacer(Modifier.height(10.dp))
    EinkText(text, style = EinkTheme.typography.caption, maxLines = 1)
    if (reading) {
        Spacer(Modifier.height(6.dp))
        EinkProgressBar(fraction!!, Modifier.width(140.dp), height = 2.dp)
    }
}

private fun percent(fraction: Float) = "${(fraction * 100).toInt()} %"

/**
 * Cabecera: portada, título, autor (enlace a sus libros), serie y valoración.
 * «Más de…» busca por nombre: las entradas OPDS no enlazan al autor ni a la
 * serie, y la búsqueda de Calibre-Web incluye ambos.
 */
@Composable
private fun Header(book: Book, vm: BookDetailViewModel, onOpenFeed: (String, String) -> Unit) {
    val author = book.authors.firstOrNull()
    val series = book.series?.name
    Row {
        BookCover(
            book, vm.coverBase,
            Modifier.size(width = 140.dp, height = 210.dp),
            corner = 12.dp,
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            EinkText(book.title, style = EinkTheme.typography.title, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            val authors = book.authors.joinToString().ifEmpty { "Autor desconocido" }
            EinkText(
                authors,
                modifier = if (author != null) {
                    Modifier.clickable { onOpenFeed(CatalogRepository.searchHref(author), author) }
                } else Modifier,
                style = EinkTheme.typography.body.copy(
                    textDecoration = if (author != null) TextDecoration.Underline else null,
                ),
                maxLines = 2,
            )
            seriesLabel(book)?.let {
                Spacer(Modifier.height(10.dp))
                EinkText(it, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 2)
            }
            book.rating?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(8.dp))
                EinkText(ratingStars(it), style = EinkTheme.typography.section)
            }
            val downloaded = vm.downloads.values.any { it.state is DownloadState.Done }
            if (downloaded || vm.finished) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.finished) ReadTag()
                    if (downloaded) DownloadedTag()
                }
            }
            ReadingInfo(vm)
            // Idioma y páginas al lado de la portada, donde sobra sitio.
            book.language?.let {
                Spacer(Modifier.height(10.dp))
                EinkText("Idioma: ${languageName(it)}", style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
            }
            // OPDS no da las páginas: solo se saben con el archivo descargado. A medias
            // ya salen en «Página 4 de 81», así que el total solo va sin empezar o leído.
            val pages = vm.pageCount
            if (pages != null && !vm.isReading()) {
                Spacer(Modifier.height(if (book.language != null) 4.dp else 10.dp))
                EinkText("Páginas: $pages", style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (author != null) {
            EinkChip("Más del autor", { onOpenFeed(CatalogRepository.searchHref(author), author) }, icon = EinkIcons.User)
        }
        if (series != null) {
            EinkChip("Ver la serie", { onOpenFeed(CatalogRepository.searchHref(series), series) }, icon = EinkIcons.Series)
        }
        // Solo el ojo, con el borde de las píldoras; relleno en negro si está leído.
        // Se envía a Calibre-Web igual que desde el lector.
        EinkIconButton(
            EinkIcons.Eye,
            contentDescription = if (vm.finished) "Quitar de leídos" else "Marcar como leído",
            onClick = vm::toggleFinished,
            modifier = Modifier.einkBorder(1.5.dp, EinkTheme.colors.ink, 20.dp),
            selected = vm.finished,
            tonal = false,
            size = 40.dp,
        )
    }
}

/** Un formato con su estado local y las acciones que tocan (RF-04). */
@Composable
private fun FormatCard(
    file: BookFile,
    download: Download?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRead: (DownloadState.Done) -> Unit,
) {
    val state = download?.state
    EinkCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EinkSurface(corner = 8.dp, tone = EinkTone.Ink) {
                EinkText(
                    file.format.name,
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = EinkTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = EinkTheme.colors.paper,
                )
            }
            Spacer(Modifier.width(12.dp))
            val meta = when (state) {
                null -> file.sizeBytes?.let { formatBytes(it) } ?: "No descargado"
                else -> describe(state)
            }
            EinkText(meta, Modifier.weight(1f), style = EinkTheme.typography.caption, maxLines = 1)
        }
        if (state is DownloadState.Running) {
            Spacer(Modifier.height(12.dp))
            EinkProgressBar(state.fraction ?: 0f, Modifier.fillMaxWidth(), height = 6.dp)
        }
        if (state is DownloadState.Failed) {
            Spacer(Modifier.height(8.dp))
            EinkText(state.problem.hint, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val wide = Modifier.weight(1f)
            val h = 48.dp
            when (state) {
                null -> EinkButton("Descargar", onDownload, wide, primary = true, icon = EinkIcons.Download, height = h)
                is DownloadState.Queued, is DownloadState.Running ->
                    EinkButton("Cancelar", onCancel, wide, icon = EinkIcons.Close, height = h)
                is DownloadState.Failed -> {
                    EinkButton("Reintentar", onDownload, wide, primary = true, icon = EinkIcons.Refresh, height = h)
                    EinkButton("Descartar", onCancel, wide, height = h)
                }
                is DownloadState.Done -> {
                    EinkButton("Leer", { onRead(state) }, wide, primary = true, icon = EinkIcons.Book, height = h)
                    EinkButton("Quitar", onRemove, wide, icon = EinkIcons.Trash, height = h)
                }
            }
        }
    }
}

@Composable
private fun Details(book: Book, onOpenFeed: (String, String) -> Unit) {
    book.summary?.takeIf { it.isNotBlank() }?.let { summary ->
        Spacer(Modifier.height(24.dp))
        EinkText("Sinopsis", style = EinkTheme.typography.section)
        Spacer(Modifier.height(8.dp))
        EinkText(summary, style = EinkTheme.typography.serif.copy(lineHeight = EinkTheme.typography.serif.fontSize * 1.5f))
    }
    // Abajo y no junto a la portada: puede haber muchas.
    if (book.tags.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        EinkText("Etiquetas", style = EinkTheme.typography.section)
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            book.tags.forEach { tag ->
                EinkChip(tag, { onOpenFeed(CatalogRepository.searchHref(tag), tag) }, icon = EinkIcons.Tag)
            }
        }
    }
}

