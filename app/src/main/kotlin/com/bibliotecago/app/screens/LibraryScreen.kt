package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.bibliotecago.app.appContainer
import com.bibliotecago.catalog.CatalogListing
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMenu
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.Book
import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.ServerUrls
import okhttp3.HttpUrl

@Composable
fun LibraryScreen(onOpenSettings: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { LibraryViewModel(container) }
    var asList by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    val gridPage = rememberEinkPageState()
    val listPage = rememberEinkPageState()

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Biblioteca") {
            EinkIconButton(EinkIcons.Search, contentDescription = "Ordenar", onClick = { showSort = true })
            EinkIconButton(
                icon = if (asList) EinkIcons.Grid else EinkIcons.List,
                contentDescription = if (asList) "Ver cuadrícula" else "Ver lista",
                onClick = { asList = !asList },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = vm.state) {
                LibraryState.Loading -> EinkMessage("Cargando catálogo…", Modifier.align(Alignment.Center))

                is LibraryState.Failed -> EinkMessage(
                    title = state.problem.title,
                    message = state.problem.hint,
                    actionLabel = if (state.problem == ConnectionProblem.NotConfigured) "Abrir ajustes" else "Reintentar",
                    onAction = if (state.problem == ConnectionProblem.NotConfigured) onOpenSettings else vm::reload,
                    modifier = Modifier.align(Alignment.Center),
                )

                is LibraryState.Content -> if (state.books.isEmpty()) {
                    EinkMessage(
                        "No hay libros",
                        Modifier.align(Alignment.Center),
                        message = "El servidor no tiene libros en este listado.",
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        val base = vm.coverBase
                        // El tamaño de la cuadrícula (3×3 o 3×4) se decidirá probando en el BOOX.
                        if (asList) {
                            EinkPagedGrid(
                                state.books, columns = 1, rows = 7, state = listPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                            ) { BookRow(it) }
                        } else {
                            EinkPagedGrid(
                                state.books, columns = 3, rows = 3, state = gridPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                            ) { BookCover(it, base) }
                        }
                        state.moreProblem?.let { MoreFailed(it, onRetry = vm::loadMore) }
                    }
                }
            }
        }
    }

    if (showSort) {
        EinkMenu(
            onDismissRequest = { showSort = false },
            title = "Ordenar por",
            options = CatalogListing.entries.map { it.label },
            selectedIndex = vm.listing.ordinal,
            onSelect = {
                showSort = false
                gridPage.page = 0
                listPage.page = 0
                vm.select(CatalogListing.entries[it])
            },
        )
    }
}

@Composable
private fun MoreFailed(problem: ConnectionProblem, onRetry: () -> Unit) {
    EinkDivider()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkText("No se pudieron cargar más libros: ${problem.title}.", Modifier.weight(1f), style = EinkTheme.typography.caption)
        Spacer(Modifier.width(12.dp))
        EinkButton("Reintentar", onClick = onRetry)
    }
}

/**
 * Portada del servidor. Debajo queda el título, que se ve mientras carga o si
 * el libro no tiene portada. La clave de caché es la ruta, no la URL: la
 * misma portada sirve por la red local y por el túnel.
 */
@Composable
private fun BookCover(book: Book, base: HttpUrl?) {
    var coverShown by remember(book.coverPath) { mutableStateOf(false) }
    EinkSurface(Modifier.fillMaxSize(), bordered = true) {
        if (!coverShown) {
            Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EinkText(book.title, style = EinkTheme.typography.caption, maxLines = 4, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                EinkText(book.formats.joinToString { it.name }, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
            }
        }
        val path = book.coverPath
        if (path != null && base != null) {
            val context = LocalContext.current
            val request = remember(path, base) {
                ImageRequest.Builder(context)
                    .data(ServerUrls.resolve(base, path).toString())
                    .memoryCacheKey(path)
                    .diskCacheKey(path)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "Portada de ${book.title}",
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentScale = ContentScale.Fit,
                onSuccess = { coverShown = true },
            )
        }
    }
}

@Composable
private fun BookRow(book: Book) {
    EinkCard(Modifier.fillMaxSize(), onClick = {}) {
        EinkText(book.title, style = EinkTheme.typography.label, maxLines = 1)
        EinkText(book.authors.joinToString(), style = EinkTheme.typography.caption, maxLines = 1)
    }
}
