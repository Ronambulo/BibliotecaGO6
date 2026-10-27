package com.bibliotecago.app.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.ServerStatus
import com.bibliotecago.app.appContainer
import androidx.compose.foundation.rememberScrollState
import com.bibliotecago.core.eink.einkVerticalScroll
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.model.BookId
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
 * Inicio: «Continuar leyendo» (RF-08), solo libros empezados, descargados y
 * no marcados como leídos, lo último leído primero; sale de Room, así que
 * funciona sin conexión. Debajo, lo último añadido al servidor y, arriba, el
 * estado de la conexión, ambos de la consulta periódica.
 */
@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenBook: (BookId) -> Unit,
    onOpenServer: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    // Calibre no avisa al importar: con Inicio en pantalla se le pregunta cada 20 s
    // (y al llegar, si la última consulta tiene más de 10 s). Solo se redibuja si
    // llegan libros nuevos; con la app en segundo plano se para.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.requestPollIfOlderThan(HOME_FRESH)
            while (true) {
                delay(HOME_POLL)
                container.requestPollIfOlderThan(HOME_FRESH)
            }
        }
    }
    val reading by remember(container) {
        combine(
            container.library.progress.observeAll(),
            container.library.finished.observeAll(),
            container.downloads.downloads,
        ) { progress, finished, downloads ->
            val files = downloads.associateBy { it.book.id.value to it.format }
            progress.filter { it.bookId !in finished }.mapNotNull { p ->
                val download = files[p.bookId to p.format] ?: return@mapNotNull null
                val done = download.state as? DownloadState.Done ?: return@mapNotNull null
                Reading(download.book, p.format, done.file, p.progression)
            }
        }
    }.collectAsState(initial = null)
    val status by container.serverStatus.collectAsState()
    val recent by container.recentBooks.collectAsState()
    val newCount by container.newRecentCount.collectAsState(initial = 0)
    val downloaded by remember(container) { container.downloadedBookIds() }.collectAsState(initial = emptySet())
    val finished by remember(container) { container.finishedBookIds() }.collectAsState(initial = emptySet())

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Inicio") {
            ServerBadge(status, onOpenServer)
            EinkIconButton(EinkIcons.Search, contentDescription = "Buscar en la biblioteca", onClick = onOpenLibrary)
        }
        val list = reading
        val recentBooks = recent.orEmpty().take(RECENT_MAX)
        val recentSection: @Composable () -> Unit = {
            if (recentBooks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                EinkSectionHeader(
                    "Recientes",
                    detail = if (newCount > 0) "$newCount ${if (newCount == 1) "nuevo" else "nuevos"}" else null,
                    actionLabel = "Ver todos",
                    onAction = {
                        container.markRecentSeen()
                        onOpenRecent()
                    },
                )
                Row(Modifier.fillMaxWidth().height(RECENT_HEIGHT), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentBooks.forEach { book ->
                        RecentCover(
                            book, container.coverBase,
                            downloaded = book.id.value in downloaded,
                            read = book.id.value in finished,
                            modifier = Modifier.weight(1f),
                        ) { onOpenBook(book.id) }
                    }
                    // Con menos libros que huecos, las portadas no se estiran.
                    repeat(RECENT_MAX - recentBooks.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Unit
                list.isEmpty() -> Column(Modifier.fillMaxSize().padding(horizontal = EinkTheme.dimens.gutter)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EinkMessage(
                    title = "Nada a medias",
                    message = "Cuando empieces un libro descargado aparecerá aquí, listo para seguir por la misma página.",
                    icon = EinkIcons.Book,
                    actionLabel = "Ir a la biblioteca",
                    onAction = onOpenLibrary,
                    secondaryLabel = "Ver descargas",
                    onSecondary = onOpenDownloads,
                    modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    recentSection()
                    StatsButton(onOpenStats)
                    Spacer(Modifier.height(16.dp))
                }
                // Por si en alguna pantalla no cabe todo: se desplaza a saltos, sin animación.
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .einkVerticalScroll(rememberScrollState())
                        .padding(horizontal = EinkTheme.dimens.gutter)
                        .padding(bottom = 16.dp),
                ) {
                    val current = list.first()
                    Spacer(Modifier.height(4.dp))
                    Hero(current, container.coverBase) { open(context, current) }
                    val others = list.drop(1)
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        EinkSectionHeader("También a medias", actionLabel = "Ver descargas", onAction = onOpenDownloads)
                        // Con «Recientes» debajo caben menos.
                        others.take(if (recentBooks.isEmpty()) OTHER_MAX else OTHER_WITH_RECENT).forEach {
                            ReadingRow(it, container.coverBase) { open(context, it) }
                        }
                    }
                    recentSection()
                    StatsButton(onOpenStats)
                }
            }
        }
    }
}

/** Abajo del todo en Inicio: lleva a las estadísticas de lectura. */
@Composable
private fun StatsButton(onClick: () -> Unit) {
    Spacer(Modifier.height(18.dp))
    EinkButton("Estadísticas de lectura", onClick = onClick, modifier = Modifier.fillMaxWidth(), icon = EinkIcons.List)
}

/** El último libro leído, en grande: portada, progreso y «Seguir leyendo». */
@Composable
private fun Hero(reading: Reading, base: HttpUrl?, onOpen: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    EinkCard(Modifier.fillMaxWidth().height(HERO_HEIGHT), onClick = onOpen, padding = 20.dp, corner = 24.dp, pressSource = press) {
        Row(Modifier.fillMaxSize()) {
            BookCover(reading.book, base, Modifier.fillMaxHeight().width(124.dp), alignment = Alignment.TopStart, pressSource = press)
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
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(OTHER_HEIGHT)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(reading.book, base, Modifier.height(68.dp).width(46.dp), corner = 6.dp, compact = true, pressSource = press)
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

/** Portada de «Recientes»: solo la imagen, con sus insignias; abre la ficha. */
@Composable
private fun RecentCover(book: Book, base: HttpUrl?, downloaded: Boolean, read: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BookCover(
        book, base,
        modifier.fillMaxHeight().clickable(press, indication = null, role = Role.Button, onClick = onClick),
        downloaded = downloaded,
        read = read,
        alignment = Alignment.BottomCenter,
        corner = 6.dp,
        compact = true,
        pressSource = press,
    )
}

/**
 * Estado del servidor en la barra superior: «Red local», «Túnel», «Sin
 * conexión»… Al pulsarlo se abre la configuración del servidor.
 */
@Composable
private fun ServerBadge(status: ServerStatus, onClick: () -> Unit) {
    val (text, icon) = when (status) {
        ServerStatus.NotConfigured -> "Sin configurar" to EinkIcons.Warning
        ServerStatus.Checking -> "Conectando…" to EinkIcons.Server
        is ServerStatus.Online -> (if (status.local) "Red local" else "Túnel") to EinkIcons.Server
        is ServerStatus.Offline -> "Sin conexión" to EinkIcons.Warning
    }
    Row(
        Modifier
            .height(EinkTheme.dimens.touchTarget)
            .clickable(role = Role.Button, onClickLabel = "Configurar el servidor", onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkIcon(icon, contentDescription = null, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        EinkText(text, style = EinkTheme.typography.caption, maxLines = 1)
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
private const val OTHER_WITH_RECENT = 2
/** Portadas de «Recientes»: una fila, sin paginar. */
private const val RECENT_MAX = 5
private val RECENT_HEIGHT = 128.dp
private val OTHER_HEIGHT = 84.dp

/** Cada cuánto se pregunta por libros nuevos con Inicio en pantalla. */
private val HOME_POLL = 20.seconds
/** Una consulta más reciente que esto ya vale al volver a Inicio. */
private val HOME_FRESH = 10.seconds
