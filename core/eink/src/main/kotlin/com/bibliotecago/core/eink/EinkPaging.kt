package com.bibliotecago.core.eink

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Página actual de una vista paginada (base 0). Sobrevive a la rotación y a
 * volver atrás, así que la página se conserva igual que los filtros (RF-03).
 */
@Stable
class EinkPageState(initialPage: Int = 0) {
    var page by mutableIntStateOf(initialPage)

    companion object {
        val Saver: Saver<EinkPageState, Int> = Saver(save = { it.page }, restore = { EinkPageState(it) })
    }
}

@Composable
fun rememberEinkPageState(initialPage: Int = 0): EinkPageState =
    rememberSaveable(saver = EinkPageState.Saver) { EinkPageState(initialPage) }

/**
 * Paso de página por deslizamiento horizontal: al soltar, si el gesto supera
 * el umbral se cambia de página de golpe. Nada se mueve mientras se arrastra.
 */
fun Modifier.einkSwipePaging(onNext: () -> Unit, onPrevious: () -> Unit): Modifier =
    pointerInput(onNext, onPrevious) {
        val threshold = 48.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> onNext()
                    total >= threshold -> onPrevious()
                }
            },
            onHorizontalDrag = { change, dx ->
                change.consume()
                total += dx
            },
        )
    }

/**
 * Cuadrícula paginada (RF-02, §11.2): [columns] × [rows] elementos por página,
 * sin desplazamiento continuo. Con `columns = 1` sirve como lista paginada.
 * Las celdas vacías de la última página se reservan para que nada salte.
 *
 * Con [hasMore] la lista está incompleta (catálogo paginado en el servidor):
 * al acercarse al final se pide [onLoadMore] con una página de antelación,
 * para que el paso siguiente ya tenga datos.
 */
@Composable
fun <T> EinkPagedGrid(
    items: List<T>,
    columns: Int,
    rows: Int,
    state: EinkPageState,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    spacing: Dp = EinkTheme.dimens.spacing,
    contentPadding: PaddingValues = PaddingValues(horizontal = EinkTheme.dimens.gutter, vertical = 8.dp),
    itemContent: @Composable (T) -> Unit,
) {
    val perPage = columns * rows
    val pageCount = maxOf(1, (items.size + perPage - 1) / perPage)
    // Si el número de elementos baja (p. ej. al filtrar), se muestra la última página válida.
    val page = state.page.coerceIn(0, pageCount - 1)
    val pageItems = items.drop(page * perPage).take(perPage)

    val currentPageCount by rememberUpdatedState(pageCount)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(page, pageCount, hasMore) {
        if (hasMore && page >= pageCount - 2) currentOnLoadMore()
    }
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .einkSwipePaging(
                    onNext = { state.page = (state.page + 1).coerceAtMost(currentPageCount - 1) },
                    onPrevious = { state.page = (state.page - 1).coerceAtLeast(0) },
                )
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            for (r in 0 until rows) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    for (c in 0 until columns) {
                        val item = pageItems.getOrNull(r * columns + c)
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            if (item != null) {
                                androidx.compose.runtime.key(key?.invoke(item) ?: (r * columns + c)) {
                                    itemContent(item)
                                }
                            }
                        }
                    }
                }
            }
        }
        EinkPageIndicator(
            page = page,
            pageCount = pageCount,
            hasMore = hasMore,
            onPrevious = { state.page = page - 1 },
            onNext = { state.page = page + 1 },
        )
    }
}

/** «Página 3 de 41» (o «de 7+» si quedan por cargar) entre dos botones redondos. */
@Composable
fun EinkPageIndicator(
    page: Int,
    pageCount: Int,
    hasMore: Boolean = false,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkIconButton(EinkIcons.ChevronLeft, "Página anterior", onPrevious, enabled = page > 0)
        EinkText(
            buildAnnotatedString {
                append("Página ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${page + 1}") }
                append(" de $pageCount${if (hasMore) "+" else ""}")
            },
            Modifier.weight(1f),
            style = EinkTheme.typography.body,
            textAlign = TextAlign.Center,
        )
        EinkIconButton(EinkIcons.ChevronRight, "Página siguiente", onNext, enabled = page < pageCount - 1)
    }
}

/**
 * Sin inercia: el contenido se detiene en cuanto se levanta el dedo. Para las
 * pocas vistas que necesitan desplazamiento (formularios con teclado abierto).
 */
object EinkNoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = 0f
}

/** Desplazamiento vertical sin inercia ni rebote. */
fun Modifier.einkVerticalScroll(state: ScrollState): Modifier =
    verticalScroll(state, flingBehavior = EinkNoFling)

/**
 * Contenido largo (una sinopsis) partido en páginas de la altura disponible,
 * con el indicador de página debajo. Se pasa de página con los botones o
 * deslizando en horizontal; cada página repite un poco de la anterior para
 * no perder el hilo.
 */
@Composable
fun EinkPagedColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = EinkTheme.dimens.gutter),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val viewport = with(density) { (maxHeight - 56.dp).roundToPx() }
        val step = (viewport - with(density) { 24.dp.roundToPx() }).coerceAtLeast(1)
        val pageCount = 1 + (scroll.maxValue + step - 1) / step
        val page = if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) pageCount - 1 else scroll.value / step
        val go: (Int) -> Unit = { target ->
            val clamped = target.coerceIn(0, pageCount - 1)
            scope.launch { scroll.scrollTo((clamped * step).coerceAtMost(scroll.maxValue)) }
        }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .einkSwipePaging(onNext = { go(page + 1) }, onPrevious = { go(page - 1) })
                    .verticalScroll(scroll, enabled = false)
                    .padding(contentPadding),
                content = content,
            )
            EinkPageIndicator(
                page = page,
                pageCount = pageCount,
                onPrevious = { go(page - 1) },
                onNext = { go(page + 1) },
            )
        }
    }
}
