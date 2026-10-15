package com.bibliotecago.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.catalog.OpdsNavigation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.BookId

/**
 * Recorre un feed OPDS siguiendo sus enlaces: letras → autores (o series, o
 * etiquetas) → libros. Cada nivel es una pantalla, así «Atrás» sube un nivel
 * y conserva la página en la que se estaba.
 */
@Composable
fun ExploreScreen(
    href: String,
    title: String,
    onBack: () -> Unit,
    onOpenFeed: (href: String, title: String) -> Unit,
    onOpenBook: (BookId) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = href) { ExploreViewModel(container, href) }
    val page = rememberEinkPageState()

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = title, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = vm.state) {
                // Mientras llega el feed, la pantalla sin contenido: nada salta al cargar.
                ExploreState.Loading -> EinkPagedGrid(
                    emptyList<Any>(), columns = 3, rows = 2, state = page, modifier = Modifier.fillMaxSize(),
                ) {}

                is ExploreState.Failed -> EinkMessage(
                    title = state.problem.title,
                    message = state.problem.hint,
                    icon = EinkIcons.Warning,
                    actionLabel = "Reintentar",
                    onAction = vm::reload,
                    modifier = Modifier.align(Alignment.Center),
                )

                is ExploreState.Content -> Column(Modifier.fillMaxSize()) {
                    when {
                        state.books.isNotEmpty() -> {
                            val base = vm.coverBase
                            EinkPagedGrid(
                                state.books, columns = 3, rows = 2, state = page, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                                spacing = 16.dp,
                            ) { book ->
                                BookTile(book, base, book.id.value in vm.downloaded, onClick = { onOpenBook(book.id) })
                            }
                        }
                        state.navigation.isNotEmpty() -> {
                            // Un índice de letras cabe en una cuadrícula; nombres largos, en lista.
                            val letters = state.navigation.all { it.title.length <= 3 }
                            EinkPagedGrid(
                                state.navigation,
                                columns = if (letters) 4 else 1,
                                rows = if (letters) 7 else 8,
                                state = page,
                                modifier = Modifier.weight(1f),
                                spacing = if (letters) 10.dp else 0.dp,
                                key = { it.href }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                            ) { entry -> NavigationCell(entry, centered = letters) { onOpenFeed(entry.href, entry.title) } }
                        }
                        else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                            EinkMessage(
                                "Sin resultados",
                                Modifier.align(Alignment.Center),
                                message = "No hay libros en este apartado.",
                                icon = EinkIcons.Library,
                            )
                        }
                    }
                    state.moreProblem?.let { MoreFailed(it, onRetry = vm::loadMore) }
                }
            }
        }
    }
}

@Composable
private fun NavigationCell(entry: OpdsNavigation, centered: Boolean, onClick: () -> Unit) {
    if (centered) {
        // Letra: baldosa gris redondeada.
        EinkCard(Modifier.fillMaxSize(), onClick = onClick, padding = 0.dp, corner = 16.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EinkText(entry.title, style = EinkTheme.typography.heading, textAlign = TextAlign.Center)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EinkText(entry.title, style = EinkTheme.typography.label.merge(fontSize = 17.sp), maxLines = 1)
                    // Calibre-Web pone aquí el número de libros («3 libros»).
                    entry.summary?.let { EinkText(it, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1) }
                }
                EinkIcon(EinkIcons.ChevronRight, contentDescription = null, size = 22.dp)
            }
            EinkDivider()
        }
    }
}
