package com.bibliotecago.core.eink

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
 */
@Composable
fun <T> EinkPagedGrid(
    items: List<T>,
    columns: Int,
    rows: Int,
    state: EinkPageState,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val perPage = columns * rows
    val pageCount = maxOf(1, (items.size + perPage - 1) / perPage)
    // Si el número de elementos baja (p. ej. al filtrar), se muestra la última página válida.
    val page = state.page.coerceIn(0, pageCount - 1)
    val pageItems = items.drop(page * perPage).take(perPage)

    val currentPageCount by rememberUpdatedState(pageCount)
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .einkSwipePaging(
                    onNext = { state.page = (state.page + 1).coerceAtMost(currentPageCount - 1) },
                    onPrevious = { state.page = (state.page - 1).coerceAtLeast(0) },
                )
                .padding(horizontal = EinkTheme.dimens.gutter, vertical = EinkTheme.dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(EinkTheme.dimens.spacing),
        ) {
            for (r in 0 until rows) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EinkTheme.dimens.spacing),
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
            onPrevious = { state.page = page - 1 },
            onNext = { state.page = page + 1 },
        )
    }
}

/** Indicador «3 / 41» con botones grandes de anterior y siguiente. */
@Composable
fun EinkPageIndicator(
    page: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        EinkDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkIconButton(EinkIcons.ChevronLeft, "Página anterior", onPrevious, enabled = page > 0)
            EinkText(
                "${page + 1} / $pageCount",
                Modifier.weight(1f),
                style = EinkTheme.typography.label,
                textAlign = TextAlign.Center,
            )
            EinkIconButton(EinkIcons.ChevronRight, "Página siguiente", onNext, enabled = page < pageCount - 1)
        }
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
