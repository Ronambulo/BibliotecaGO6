package com.bibliotecago.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibliotecago.app.appContainer
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkSectionHeader
import com.bibliotecago.core.eink.EinkSegmented
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.DownloadState
import com.bibliotecago.library.FinishedBook
import com.bibliotecago.library.ReadingProgress
import com.bibliotecago.library.ReadingSession
import kotlinx.coroutines.flow.combine
import okhttp3.HttpUrl
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private enum class Period(val label: String) { Week("Semana"), Month("Mes"), Year("Año"), All("Todo") }

/** Lo leído de la base de datos, sin agrupar. */
private class StatsData(
    val sessions: List<ReadingSession>,
    val progress: List<ReadingProgress>,
    val finished: List<FinishedBook>,
    /** Libros descargados, por id: de aquí salen título, autores y categorías. */
    val books: Map<String, Book>,
    val bytes: Long,
)

/** Una barra de una gráfica; [label] nulo deja el hueco sin rótulo. */
private class Bar(val label: String?, val value: Float, val valueLabel: String?, val highlight: Boolean = false)

private class Current(
    val book: Book,
    val progression: Double,
    val spentMillis: Long,
    val leftMillis: Long?,
    val lastRead: Long,
    val firstRead: Long?,
)

private class Finished(val book: Book?, val finishedAt: Long, val tookDays: Long?, val spentMillis: Long)

private class Share(val label: String, val millis: Long, val books: Int = 0)

private class Stats(
    val hasSessions: Boolean,
    val totalMillis: Long,
    /** Diferencia con el periodo anterior hasta el mismo punto; nula en «Todo». */
    val deltaMillis: Long?,
    val perDayMillis: Long,
    val bars: List<Bar>,
    val streak: Int,
    val bestStreak: Int,
    val pages: Int,
    val pagesPerHour: Int?,
    val current: List<Current>,
    val year: Int,
    val finishedByMonth: List<Bar>,
    val finishedThisYear: Int,
    val recentFinished: List<Finished>,
    val undatedFinished: Int,
    val dayparts: List<Share>,
    val averageSessionMillis: Long?,
    val longest: ReadingSession?,
    val tags: List<Share>,
    val authors: List<Share>,
    val downloaded: Int,
    val readDownloaded: Int,
    val bytes: Long,
    val stale: Int,
)

/**
 * Estadísticas de lectura: tiempo leído (de las sesiones que apuntan los
 * lectores), ritmo, libros a medias con lo que les queda, terminados por mes,
 * a qué horas se lee y qué se lee más.
 */
@Composable
fun StatsScreen(onBack: () -> Unit, onOpenBook: (BookId) -> Unit) {
    val container = LocalContext.current.appContainer
    val data by remember(container) {
        combine(
            container.library.sessions.observeAll(),
            container.library.progress.observeAll(),
            container.library.finished.observeFinished(),
            container.downloads.downloads,
        ) { sessions, progress, finished, downloads ->
            val done = downloads.filter { it.state is DownloadState.Done }
            StatsData(
                sessions = sessions.filter { it.durationMillis > 0 },
                progress = progress,
                finished = finished,
                books = done.map { it.book }.associateBy { it.id.value },
                bytes = done.sumOf { (it.state as DownloadState.Done).sizeBytes },
            )
        }
    }.collectAsState(initial = null)
    var period by rememberSaveable { mutableStateOf(Period.Week) }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Estadísticas", onBack = onBack)
        val d = data ?: return@Column
        val s = remember(d, period) { buildStats(d, period, System.currentTimeMillis(), ZoneId.systemDefault()) }
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(horizontal = EinkTheme.dimens.gutter)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            EinkSegmented(Period.entries.map { it.label }, period.ordinal, { period = Period.entries[it] }, Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
            TimeRead(s, period)

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(if (s.streak == 1) "1 día" else "${s.streak} días", "Racha", if (s.bestStreak > s.streak) "Récord: ${s.bestStreak}" else "Tu récord")
                Tile(s.pages.toString(), "Páginas", PERIOD_SCOPE.getValue(period))
                Tile(s.pagesPerHour?.toString() ?: "—", "Pág. / hora", "tu ritmo")
            }

            if (s.current.isNotEmpty()) {
                Section("Leyendo ahora")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.current.forEach { CurrentBook(it, container.coverBase) { onOpenBook(it.book.id) } }
                }
            }

            Section("Terminados en ${s.year}", detail = s.finishedThisYear.toString())
            Bars(s.finishedByMonth, height = 88.dp)
            if (s.recentFinished.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                s.recentFinished.forEach { FinishedRow(it) }
            }
            if (s.undatedFinished > 0) {
                Spacer(Modifier.height(10.dp))
                Caption(
                    if (s.undatedFinished == 1) "Y 1 leído sin fecha: marcado antes de esta versión o en Calibre-Web."
                    else "Y ${s.undatedFinished} leídos sin fecha: marcados antes de esta versión o en Calibre-Web.",
                )
            }

            Section("Cuándo lees")
            run {
                if (s.totalMillis == 0L) {
                    Caption("Aún no hay sesiones ${PERIOD_SCOPE.getValue(period)}: aquí verás a qué horas lees.")
                    Spacer(Modifier.height(6.dp))
                }
                val top = s.dayparts.maxOf { it.millis }.coerceAtLeast(1)
                s.dayparts.forEach { part ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        EinkText(part.label, Modifier.width(104.dp), style = EinkTheme.typography.caption.copy(fontSize = 15.sp), maxLines = 1)
                        EinkProgressBar(part.millis.toFloat() / top, Modifier.weight(1f), height = 12.dp)
                        EinkText(
                            "${(part.millis * 100f / s.totalMillis.coerceAtLeast(1)).roundToInt()} %",
                            Modifier.width(52.dp),
                            style = EinkTheme.typography.caption,
                            color = EinkTheme.colors.muted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
                val average = s.averageSessionMillis
                val longest = s.longest
                if (average != null && longest != null) {
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "Tus sesiones duran ${formatDuration(average)} de media. " +
                            "La más larga: ${formatDuration(longest.durationMillis)}, ${dayName(longest.startedAt)}.",
                    )
                }
            }

            Section("Lo que más lees")
            run {
                Caption(
                    if (s.tags.isEmpty() && s.authors.isEmpty()) {
                        "Aún nada ${PERIOD_SCOPE.getValue(period)}: lee un rato un libro descargado y aquí saldrán sus etiquetas y autores."
                    } else {
                        "Por tiempo de lectura, ${PERIOD_SCOPE.getValue(period)}"
                    },
                )
                val top = s.tags.firstOrNull()?.millis?.coerceAtLeast(1)
                s.tags.forEach { tag ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            EinkText(tag.label, Modifier.weight(1f), style = EinkTheme.typography.label, maxLines = 1)
                            Spacer(Modifier.width(12.dp))
                            EinkText(formatHours(tag.millis), style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        EinkProgressBar(tag.millis.toFloat() / (top ?: 1), height = 6.dp)
                    }
                }
                if (s.authors.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    EinkEyebrow("Autores")
                    Spacer(Modifier.height(6.dp))
                    s.authors.forEach { author ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            EinkText(author.label, Modifier.weight(1f), style = EinkTheme.typography.body, maxLines = 1)
                            Spacer(Modifier.width(12.dp))
                            EinkText(
                                (if (author.books == 1) "1 libro" else "${author.books} libros") + " · " + formatHours(author.millis),
                                style = EinkTheme.typography.caption,
                                color = EinkTheme.colors.muted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            EinkDivider(thickness = 1.5.dp, color = EinkTheme.colors.ink)
            Section("Biblioteca", top = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure(s.downloaded.toString(), "descargados")
                Figure(s.readDownloaded.toString(), "leídos de ellos")
                Figure(formatBytes(s.bytes), "en el dispositivo")
            }
            if (s.stale > 0) {
                Spacer(Modifier.height(10.dp))
                Caption(
                    if (s.stale == 1) "1 empezado y sin abrir desde hace más de un mes"
                    else "${s.stale} empezados y sin abrir desde hace más de un mes",
                )
            }
        }
    }
}

@Composable
private fun TimeRead(s: Stats, period: Period) {
    EinkEyebrow("Tiempo leído ${PERIOD_SCOPE.getValue(period)}")
    Spacer(Modifier.height(2.dp))
    EinkText(
        formatDuration(s.totalMillis),
        style = EinkTheme.typography.serif.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        maxLines = 1,
    )
    val delta = s.deltaMillis
    val parts = buildList {
        if (delta != null && (delta != 0L || s.totalMillis > 0)) {
            val previous = PREVIOUS.getValue(period)
            add(
                when {
                    delta > 0 -> "${formatDuration(delta)} más que $previous"
                    delta < 0 -> "${formatDuration(-delta)} menos que $previous"
                    else -> "Igual que $previous"
                },
            )
        }
        if (s.totalMillis > 0) add("media de ${formatDuration(s.perDayMillis)} al día")
    }
    if (parts.isNotEmpty()) Caption(parts.joinToString(" · ").replaceFirstChar { it.uppercase() })
    if (!s.hasSessions) {
        Spacer(Modifier.height(10.dp))
        EinkCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            EinkText(
                "El tiempo de lectura se apunta desde esta versión: abre un libro y lo que leas irá apareciendo aquí.",
                style = EinkTheme.typography.caption,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Bars(s.bars, height = 124.dp)
}

/**
 * Gráfica de barras con el valor encima y el rótulo debajo. La barra
 * [Bar.highlight] (hoy, este mes) va en hueco: se distingue sin depender del gris.
 */
@Composable
private fun Bars(bars: List<Bar>, height: Dp) {
    val colors = EinkTheme.colors
    val top = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val gap = if (bars.size > 12) 2.dp else 6.dp
    val area = height - 18.dp
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
    val small = EinkTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp)
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        bars.forEach { bar ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (bar.valueLabel != null && bar.value > 0f) {
                    EinkText(bar.valueLabel, style = small, color = colors.muted, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                }
                if (bar.value > 0f) {
                    val barHeight = (area * (bar.value / top)).coerceAtLeast(3.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .then(
                                if (bar.highlight) Modifier.border(1.5.dp, colors.ink, shape)
                                else Modifier.background(colors.ink, shape),
                            ),
                    )
                }
            }
        }
    }
    EinkDivider(thickness = 1.5.dp, color = colors.ink)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        bars.forEach { bar ->
            EinkText(
                bar.label.orEmpty(),
                Modifier.weight(1f),
                style = if (bar.highlight) small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else small,
                color = if (bar.highlight) colors.ink else colors.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,

            )
        }
    }
}

@Composable
private fun CurrentBook(current: Current, base: HttpUrl?, onClick: () -> Unit) {
    EinkCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCover(current.book, base, Modifier.height(86.dp).width(58.dp), corner = 4.dp, compact = true)
            Column(Modifier.weight(1f)) {
                EinkText(current.book.title, style = EinkTheme.typography.label, maxLines = 1)
                current.book.authors.firstOrNull()?.let {
                    EinkText(it, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                EinkProgressBar(current.progression.toFloat())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EinkText("${(current.progression * 100).toInt()} %", style = EinkTheme.typography.label.copy(fontSize = 14.sp))
                    Spacer(Modifier.width(12.dp))
                    EinkText(
                        current.leftMillis?.let { "Te quedan ~${formatDuration(it)}" } ?: "Lee un rato más para calcular lo que queda",
                        Modifier.weight(1f),
                        style = EinkTheme.typography.caption,
                        color = EinkTheme.colors.muted,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                val spent = if (current.spentMillis > 0) "${formatDuration(current.spentMillis)} leídos" else null
                val since = current.firstRead?.let { first ->
                    val days = ChronoUnit.DAYS.between(localDate(first), LocalDate.now())
                    if (days >= 1) "empezado hace ${if (days == 1L) "1 día" else "$days días"}" else null
                } ?: "última lectura: ${dayName(current.lastRead)}"
                Caption(listOfNotNull(spent, since).joinToString(" · ").replaceFirstChar { it.uppercase() }, size = 13)
            }
        }
    }
}

@Composable
private fun FinishedRow(finished: Finished) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            EinkText(finished.book?.title ?: "Libro sin descargar", style = EinkTheme.typography.body, maxLines = 1)
            Caption("Terminado ${dayName(finished.finishedAt)}", size = 13)
        }
        val took = listOfNotNull(
            finished.tookDays?.let { if (it <= 1) "1 día" else "$it días" },
            finished.spentMillis.takeIf { it > 0 }?.let(::formatHours),
        )
        if (took.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            EinkText(took.joinToString(" · "), style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
        }
    }
    EinkDivider()
}

@Composable
private fun Section(title: String, detail: String? = null, top: Dp = 28.dp) {
    Spacer(Modifier.height(top))
    EinkSectionHeader(title, detail = detail)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Caption(text: String, size: Int = 14) {
    EinkText(text, style = EinkTheme.typography.caption.copy(fontSize = size.sp), color = EinkTheme.colors.muted)
}

@Composable
private fun RowScope.Tile(value: String, label: String, detail: String) {
    EinkCard(Modifier.weight(1f), padding = 12.dp) {
        EinkText(value, style = EinkTheme.typography.heading.copy(fontSize = 24.sp, lineHeight = 28.sp), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        EinkText(label, style = EinkTheme.typography.label.copy(fontSize = 14.sp), maxLines = 1)
        EinkText(detail, style = EinkTheme.typography.caption.copy(fontSize = 12.sp), color = EinkTheme.colors.muted, maxLines = 1)
    }
}

@Composable
private fun RowScope.Figure(value: String, label: String) {
    Column(Modifier.weight(1f)) {
        EinkText(value, style = EinkTheme.typography.heading, maxLines = 1)
        EinkText(label, style = EinkTheme.typography.caption.copy(fontSize = 12.sp), color = EinkTheme.colors.muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Cálculo
// ---------------------------------------------------------------------------

private fun buildStats(d: StatsData, period: Period, now: Long, zone: ZoneId): Stats {
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    fun dateOf(millis: Long) = LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun millisOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun sumBetween(from: LocalDate, to: LocalDate): Long {
        val a = millisOf(from)
        val b = millisOf(to)
        return d.sessions.filter { it.startedAt in a until b }.sumOf { it.durationMillis }
    }

    val firstDay = d.sessions.firstOrNull()?.let { dateOf(it.startedAt) } ?: today
    val start = when (period) {
        Period.Week -> today.with(DayOfWeek.MONDAY)
        Period.Month -> today.withDayOfMonth(1)
        Period.Year -> today.withDayOfYear(1)
        Period.All -> firstDay
    }
    val tomorrow = today.plusDays(1)
    val inPeriod = d.sessions.filter { it.startedAt >= millisOf(start) }
    val total = inPeriod.sumOf { it.durationMillis }
    val previousStart = when (period) {
        Period.Week -> start.minusWeeks(1)
        Period.Month -> start.minusMonths(1)
        Period.Year -> start.minusYears(1)
        Period.All -> null
    }
    // Hasta el mismo punto del periodo anterior: el lunes no se compara con una semana entera.
    val delta = previousStart?.let { total - sumBetween(it, it.plusDays(ChronoUnit.DAYS.between(start, tomorrow))) }
    val days = ChronoUnit.DAYS.between(start, tomorrow).coerceAtLeast(1)

    val bars = when (period) {
        Period.Week -> (0 until 7).map { i ->
            val day = start.plusDays(i.toLong())
            val millis = sumBetween(day, day.plusDays(1))
            Bar(WEEKDAYS[i], millis.toFloat(), shortDuration(millis), highlight = day == today)
        }
        Period.Month -> (1..start.lengthOfMonth()).map { n ->
            val day = start.withDayOfMonth(n)
            Bar(if (n == 1 || n % 5 == 0) n.toString() else null, sumBetween(day, day.plusDays(1)).toFloat(), null, highlight = day == today)
        }
        Period.Year -> (0 until 12).map { i ->
            val month = start.plusMonths(i.toLong())
            val millis = sumBetween(month, month.plusMonths(1))
            Bar(MONTHS[i], millis.toFloat(), shortHours(millis), highlight = month.month == today.month)
        }
        Period.All -> (11 downTo 0).map { back ->
            val month = today.withDayOfMonth(1).minusMonths(back.toLong())
            val millis = sumBetween(month, month.plusMonths(1))
            Bar(MONTHS[month.monthValue - 1], millis.toFloat(), shortHours(millis), highlight = back == 0)
        }
    }

    // Rachas: días con al menos un minuto leído. Si hoy aún no, la racha sigue viva desde ayer.
    val readDays = d.sessions.groupBy { dateOf(it.startedAt) }
        .filterValues { list -> list.sumOf { it.durationMillis } >= 60_000 }.keys
    var streak = 0
    var cursor = if (today in readDays) today else today.minusDays(1)
    while (cursor in readDays) {
        streak++
        cursor = cursor.minusDays(1)
    }
    var best = 0
    var run = 0
    var previousDay: LocalDate? = null
    for (day in readDays.sorted()) {
        run = if (previousDay != null && day == previousDay.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previousDay = day
    }

    val pages = inPeriod.sumOf { it.pages }
    fun speed(list: List<ReadingSession>): Double? {
        val millis = list.sumOf { it.durationMillis }
        val turned = list.sumOf { it.pages }
        return if (millis >= 15 * 60_000 && turned > 0) turned / (millis / 3_600_000.0) else null
    }
    val periodSpeed = speed(inPeriod)
    val overallSpeed = speed(d.sessions)

    val finishedIds = d.finished.mapTo(HashSet()) { it.bookId }
    val byBook = d.sessions.groupBy { it.bookId }

    val current = d.progress
        .groupBy { it.bookId }
        .mapNotNull { (id, rows) ->
            val latest = rows.maxBy { it.updatedAt }
            val book = d.books[id] ?: return@mapNotNull null
            val p = latest.progression ?: return@mapNotNull null
            if (id in finishedIds || p <= 0.0 || p >= 0.995) return@mapNotNull null
            val sessions = byBook[id].orEmpty()
            Current(
                book = book,
                progression = p,
                spentMillis = sessions.sumOf { it.durationMillis },
                leftMillis = timeLeft(p, sessions, latest.pageCount, overallSpeed),
                lastRead = latest.updatedAt,
                firstRead = sessions.minOfOrNull { it.startedAt },
            )
        }
        .sortedByDescending { it.lastRead }
        .take(CURRENT_BOOKS)

    val year = today.year
    val dated = d.finished.filter { it.finishedAt != null }
    val thisYear = dated.filter { dateOf(it.finishedAt!!).year == year }
    val finishedByMonth = (1..12).map { m ->
        val count = thisYear.count { dateOf(it.finishedAt!!).monthValue == m }
        Bar(MONTHS[m - 1], count.toFloat(), count.toString(), highlight = m == today.monthValue)
    }
    val recentFinished = dated.sortedByDescending { it.finishedAt }.take(RECENT_FINISHED).map { row ->
        val sessions = byBook[row.bookId].orEmpty()
        val first = sessions.minOfOrNull { it.startedAt }
        Finished(
            book = d.books[row.bookId],
            finishedAt = row.finishedAt!!,
            tookDays = first?.let { ChronoUnit.DAYS.between(dateOf(it), dateOf(row.finishedAt!!)) + 1 },
            spentMillis = sessions.sumOf { it.durationMillis },
        )
    }

    val dayparts = DAYPARTS.map { (label, hours) ->
        Share(label, inPeriod.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour in hours }.sumOf { it.durationMillis })
    }

    val tagTime = HashMap<String, Long>()
    val authorTime = HashMap<String, Long>()
    val authorBooks = HashMap<String, MutableSet<String>>()
    for (session in inPeriod) {
        val book = d.books[session.bookId] ?: continue
        book.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
            tagTime[it] = (tagTime[it] ?: 0) + session.durationMillis
        }
        book.authors.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
            authorTime[it] = (authorTime[it] ?: 0) + session.durationMillis
            authorBooks.getOrPut(it) { HashSet() }.add(session.bookId)
        }
    }
    val minShare = 60_000L
    val tags = tagTime.filterValues { it >= minShare }.entries.sortedByDescending { it.value }.take(TOP_TAGS).map { Share(it.key, it.value) }
    val authors = authorTime.filterValues { it >= minShare }.entries.sortedByDescending { it.value }.take(TOP_AUTHORS)
        .map { Share(it.key, it.value, authorBooks[it.key]?.size ?: 0) }

    val monthAgo = now - 30L * 24 * 3_600_000
    val stale = d.progress.groupBy { it.bookId }.count { (id, rows) ->
        val latest = rows.maxBy { it.updatedAt }
        id !in finishedIds && (latest.progression ?: 0.0) in 0.0..0.995 && latest.updatedAt < monthAgo
    }

    return Stats(
        hasSessions = d.sessions.isNotEmpty(),
        totalMillis = total,
        deltaMillis = delta,
        perDayMillis = total / days,
        bars = bars,
        streak = streak,
        bestStreak = best,
        pages = pages,
        pagesPerHour = (periodSpeed ?: overallSpeed)?.roundToInt(),
        current = current,
        year = year,
        finishedByMonth = finishedByMonth,
        finishedThisYear = thisYear.size,
        recentFinished = recentFinished,
        undatedFinished = d.finished.size - dated.size,
        dayparts = dayparts,
        averageSessionMillis = inPeriod.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.durationMillis } / list.size },
        longest = inPeriod.maxByOrNull { it.durationMillis },
        tags = tags,
        authors = authors,
        downloaded = d.books.size,
        readDownloaded = d.books.keys.count { it in finishedIds },
        bytes = d.bytes,
        stale = stale,
    )
}

/**
 * Lo que queda a [progression] al ritmo con que se ha leído este libro; si aún
 * hay poco leído de él, a las páginas por hora de siempre.
 */
private fun timeLeft(progression: Double, sessions: List<ReadingSession>, pageCount: Int?, pagesPerHour: Double?): Long? {
    val millis = sessions.sumOf { it.durationMillis }
    val gained = sessions.sumOf { s ->
        val a = s.startProgression
        val b = s.endProgression
        if (a != null && b != null && b > a) b - a else 0.0
    }
    if (millis >= 10 * 60_000 && gained > 0.005) return ((1 - progression) * millis / gained).toLong()
    if (pageCount != null && pagesPerHour != null) return ((1 - progression) * pageCount / pagesPerHour * 3_600_000).toLong()
    return null
}

// ---------------------------------------------------------------------------
// Formato
// ---------------------------------------------------------------------------

private val spanish = Locale.forLanguageTag("es-ES")

/** «0 min», «36 min», «4 h 12 min», «2 h». */
private fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** «25 min», «14 h»: para totales largos sin minutos sueltos. */
private fun formatHours(millis: Long): String =
    if (millis < 3_600_000) formatDuration(millis) else "${(millis / 3_600_000.0).roundToInt()} h"

/** Encima de una barra diaria: «42'», «1,5 h». */
private fun shortDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes'" else String.format(spanish, "%.1f h", minutes / 60.0)
}

/** Encima de una barra mensual: «40'», «12 h». */
private fun shortHours(millis: Long): String =
    if (millis < 3_600_000) "${millis / 60_000}'" else "${(millis / 3_600_000.0).roundToInt()} h"

private fun localDate(millis: Long) = LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

/** «hoy», «ayer», «el martes» o «el 3 oct.». */
private fun dayName(millis: Long): String {
    val date = localDate(millis)
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days == 0L -> "hoy"
        days == 1L -> "ayer"
        days in 2..6 -> "el " + date.dayOfWeek.getDisplayName(TextStyle.FULL, spanish)
        date.year == LocalDate.now().year -> "el " + date.format(DateTimeFormatter.ofPattern("d MMM", spanish))
        else -> "el " + date.format(DateTimeFormatter.ofPattern("d MMM yyyy", spanish))
    }
}

private val PERIOD_SCOPE = mapOf(
    Period.Week to "esta semana",
    Period.Month to "este mes",
    Period.Year to "este año",
    Period.All to "en total",
)

private val PREVIOUS = mapOf(
    Period.Week to "a estas alturas de la semana pasada",
    Period.Month to "a estas alturas del mes pasado",
    Period.Year to "a estas alturas del año pasado",
)

private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")
private val MONTHS = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Franjas por la hora a la que empieza la sesión. */
private val DAYPARTS = listOf(
    "Mañana" to (6..13),
    "Tarde" to (14..20),
    "Noche" to ((21..23) + (0..1)),
    "Madrugada" to (2..5),
)

private const val CURRENT_BOOKS = 3
private const val RECENT_FINISHED = 4
private const val TOP_TAGS = 5
private const val TOP_AUTHORS = 3
