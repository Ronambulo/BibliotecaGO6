package com.bibliotecago.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.appContainer
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkSectionHeader
import okhttp3.HttpUrl
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookFormat
import com.bibliotecago.downloads.DownloadState
import com.bibliotecago.reader.epub.EpubReaderActivity
import com.bibliotecago.reader.pdf.PdfReaderActivity
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine

/** Un libro empezado que sigue en el dispositivo. */
private data class Reading(val book: Book, val format: BookFormat, val file: File, val progression: Double?)

/**
 * Inicio: «Continuar leyendo» (RF-08). Solo libros empezados y descargados,
 * lo último leído primero; sale de Room, así que funciona sin conexión.
 */
@Composable
fun HomeScreen(onOpenLibrary: () -> Unit, onOpenDownloads: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val reading by remember(container) {
        combine(container.library.progress.observeAll(), container.downloads.downloads) { progress, downloads ->
            val files = downloads.associateBy { it.book.id.value to it.format }
            progress.mapNotNull { p ->
                val download = files[p.bookId to p.format] ?: return@mapNotNull null
                val done = download.state as? DownloadState.Done ?: return@mapNotNull null
                Reading(download.book, p.format, done.file, p.progression)
            }
        }
    }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Inicio") {
            EinkIconButton(EinkIcons.Search, contentDescription = "Buscar en la biblioteca", onClick = onOpenLibrary)
        }
        val list = reading
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Unit
                list.isEmpty() -> EinkMessage(
                    title = "Nada a medias",
                    message = "Cuando empieces un libro descargado aparecerá aquí, listo para seguir por la misma página.",
                    icon = EinkIcons.Book,
                    actionLabel = "Ir a la biblioteca",
                    onAction = onOpenLibrary,
                    secondaryLabel = "Ver descargas",
                    onSecondary = onOpenDownloads,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(Modifier.fillMaxSize().padding(horizontal = EinkTheme.dimens.gutter)) {
                    val current = list.first()
                    Spacer(Modifier.height(4.dp))
                    Hero(current, container.coverBase) { open(context, current) }
                    val others = list.drop(1)
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        EinkSectionHeader("También a medias", actionLabel = "Ver descargas", onAction = onOpenDownloads)
                        others.take(OTHER_MAX).forEach {
                            ReadingRow(it, container.coverBase) { open(context, it) }
                        }
                    }
                }
            }
        }
    }
}

/** El último libro leído, en grande: portada, progreso y «Seguir leyendo». */
@Composable
private fun Hero(reading: Reading, base: HttpUrl?, onOpen: () -> Unit) {
    EinkCard(Modifier.fillMaxWidth().height(HERO_HEIGHT), onClick = onOpen, padding = 20.dp, corner = 24.dp) {
        Row(Modifier.fillMaxSize()) {
            BookCover(reading.book, base, Modifier.fillMaxHeight().width(124.dp), alignment = Alignment.TopStart)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                EinkEyebrow("Continuar leyendo")
                Spacer(Modifier.height(8.dp))
                EinkText(reading.book.title, style = EinkTheme.typography.heading.merge(fontSize = 22.sp, lineHeight = 27.sp), maxLines = 3)
                Spacer(Modifier.height(4.dp))
                EinkText(authors(reading.book), style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    EinkText(reading.format.name, Modifier.weight(1f), style = EinkTheme.typography.caption, maxLines = 1)
                    percent(reading)?.let { EinkText(it, style = EinkTheme.typography.section) }
                }
                Spacer(Modifier.height(8.dp))
                EinkProgressBar((reading.progression ?: 0.0).toFloat())
                Spacer(Modifier.height(14.dp))
                EinkButton("Seguir leyendo", onClick = onOpen, primary = true, icon = EinkIcons.Book, height = 48.dp)
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: Reading, base: HttpUrl?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(OTHER_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(reading.book, base, Modifier.height(68.dp).width(46.dp), corner = 6.dp, compact = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            EinkText(reading.book.title, style = EinkTheme.typography.label.merge(fontSize = 17.sp), maxLines = 1)
            EinkText(
                "${authors(reading.book)} · ${reading.format.name}",
                style = EinkTheme.typography.caption,
                color = EinkTheme.colors.muted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
            EinkText(percent(reading) ?: "—", style = EinkTheme.typography.label.merge(fontSize = 15.sp))
            Spacer(Modifier.height(8.dp))
            EinkProgressBar((reading.progression ?: 0.0).toFloat(), height = 6.dp)
        }
    }
}

private fun authors(book: Book) = book.authors.joinToString().ifEmpty { "Autor desconocido" }

/** «42 %»; nulo si el lector aún no lo sabe. */
private fun percent(reading: Reading): String? =
    reading.progression?.let { "${(it * 100).roundToInt().coerceIn(0, 100)} %" }

private fun open(context: Context, reading: Reading) {
    val intent = when (reading.format) {
        BookFormat.EPUB -> EpubReaderActivity.intent(context, reading.file, reading.book.id, reading.book.title)
        BookFormat.PDF -> PdfReaderActivity.intent(context, reading.file, reading.book.id, reading.book.title)
    }
    context.startActivity(intent)
}

private val HERO_HEIGHT = 226.dp
/** Más no caben sin paginar; el resto sigue en Descargas. */
private const val OTHER_MAX = 3
private val OTHER_HEIGHT = 84.dp
