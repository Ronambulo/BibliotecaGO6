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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.server.ConnectionProblem

@Composable
fun LibraryScreen(onOpenSettings: () -> Unit, onOpenBook: (BookId) -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { LibraryViewModel(container) }
    var asList by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val gridPage = rememberEinkPageState()
    val listPage = rememberEinkPageState()

    Column(Modifier.fillMaxSize()) {
        val resetPages = {
            gridPage.page = 0
            listPage.page = 0
        }
        EinkTopBar(title = vm.query?.let { "Resultados" } ?: "Biblioteca") {
            EinkIconButton(
                EinkIcons.Search, contentDescription = "Buscar", selected = searching || vm.query != null,
                onClick = { searching = true },
            )
            EinkIconButton(EinkIcons.Sort, contentDescription = "Ordenar", onClick = { showSort = true })
            EinkIconButton(
                icon = if (asList) EinkIcons.Grid else EinkIcons.List,
                contentDescription = if (asList) "Ver cuadrícula" else "Ver lista",
                onClick = { asList = !asList },
            )
        }

        if (searching || vm.query != null) {
            SearchBar(
                text = searchText,
                onTextChange = { searchText = it },
                onSearch = {
                    resetPages()
                    vm.search(searchText)
                },
                onClose = {
                    searching = false
                    searchText = ""
                    resetPages()
                    vm.clearSearch()
                },
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
                    val query = vm.query
                    if (query != null) {
                        EinkMessage(
                            "Sin resultados",
                            Modifier.align(Alignment.Center),
                            message = "Ningún título ni autor coincide con «$query». Prueba con menos palabras.",
                        )
                    } else {
                        EinkMessage(
                            "No hay libros",
                            Modifier.align(Alignment.Center),
                            message = "El servidor no tiene libros en este listado.",
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        val base = vm.coverBase
                        // El tamaño de la cuadrícula (3×3 o 3×4) se decidirá probando en el BOOX.
                        if (asList) {
                            EinkPagedGrid(
                                state.books, columns = 1, rows = 7, state = listPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                            ) { BookRow(it, it.id.value in vm.downloaded) { onOpenBook(it.id) } }
                        } else {
                            EinkPagedGrid(
                                state.books, columns = 3, rows = 3, state = gridPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                            ) { book ->
                                BookCover(
                                    book, base,
                                    Modifier.fillMaxSize().clickable(role = Role.Button) { onOpenBook(book.id) },
                                    downloaded = book.id.value in vm.downloaded,
                                )
                            }
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
                searching = false
                searchText = ""
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

@Composable
private fun BookRow(book: Book, downloaded: Boolean, onClick: () -> Unit) {
    EinkCard(Modifier.fillMaxSize(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EinkText(book.title, style = EinkTheme.typography.label, maxLines = 1)
                EinkText(book.authors.joinToString(), style = EinkTheme.typography.caption, maxLines = 1)
            }
            if (downloaded) DownloadedBadge()
        }
    }
}

/** Buscar por título o autor (RF-03). Se busca al pulsar el botón o «Intro», no a cada letra. */
@Composable
private fun SearchBar(text: String, onTextChange: (String) -> Unit, onSearch: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        EinkTextField(
            value = text,
            onValueChange = onTextChange,
            label = "Buscar por título o autor",
            modifier = Modifier.weight(1f),
            onDone = onSearch,
        )
        Spacer(Modifier.width(8.dp))
        EinkIconButton(EinkIcons.Search, contentDescription = "Buscar", onClick = onSearch, enabled = text.isNotBlank())
        EinkIconButton(EinkIcons.Close, contentDescription = "Cerrar búsqueda", onClick = onClose)
    }
}
