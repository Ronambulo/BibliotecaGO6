package com.bibliotecago.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.catalog.CatalogBrowse
import com.bibliotecago.catalog.CatalogListing
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkButtonStyle
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkChip
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMenu
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkSegmented
import com.bibliotecago.core.eink.EinkSheet
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTone
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.server.ConnectionProblem

@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenBook: (BookId) -> Unit,
    onBrowse: (CatalogBrowse) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { LibraryViewModel(container) }
    val prefs = rememberLibraryPrefs()
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showBrowse by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val gridPage = rememberEinkPageState()
    val listPage = rememberEinkPageState()
    val resetPages = {
        gridPage.page = 0
        listPage.page = 0
    }
    val closeSearch = {
        searching = false
        searchText = ""
        resetPages()
        vm.clearSearch()
    }
    val inSearch = searching || vm.query != null
    BackHandler(enabled = inSearch, onBack = closeSearch)

    Column(Modifier.fillMaxSize()) {
        if (inSearch) {
            SearchHeader(
                text = searchText,
                onTextChange = { searchText = it },
                onSearch = {
                    resetPages()
                    vm.search(searchText)
                },
                onClose = closeSearch,
            )
        } else {
            EinkTopBar(title = "Biblioteca") {
                EinkIconButton(EinkIcons.Search, contentDescription = "Buscar", onClick = { searching = true })
            }
            Row(
                Modifier.fillMaxWidth().padding(start = EinkTheme.dimens.gutter, end = EinkTheme.dimens.gutter, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EinkChip(
                    if (vm.lastOpened) LAST_OPENED else vm.listing.label,
                    onClick = { showSort = true },
                    selected = true,
                    trailingIcon = EinkIcons.ChevronDown,
                    modifier = Modifier.weight(1f, fill = false),
                )
                EinkChip("Explorar", onClick = { showBrowse = true }, icon = EinkIcons.Folder)
                Spacer(Modifier.weight(1f))
                EinkSegmented(
                    count = 2,
                    selectedIndex = if (prefs.asList) 1 else 0,
                    onSelect = { prefs.updateAsList(it == 1) },
                    modifier = Modifier.width(96.dp),
                    height = 40.dp,
                ) { index ->
                    EinkIcon(
                        if (index == 0) EinkIcons.Grid else EinkIcons.List,
                        contentDescription = if (index == 0) "Cuadrícula" else "Lista",
                        size = 20.dp,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = vm.state) {
                // Mientras llega el catálogo, la misma pantalla sin libros: nada salta al cargar.
                LibraryState.Loading -> EinkPagedGrid(
                    emptyList<Any>(),
                    columns = if (prefs.asList) 1 else 3,
                    rows = if (prefs.asList) 6 else 2,
                    state = if (prefs.asList) listPage else gridPage,
                    modifier = Modifier.fillMaxSize(),
                ) {}

                is LibraryState.Failed -> {
                    val notConfigured = state.problem == ConnectionProblem.NotConfigured
                    EinkMessage(
                        title = state.problem.title,
                        message = state.problem.hint,
                        icon = if (notConfigured) EinkIcons.Server else EinkIcons.Warning,
                        actionLabel = if (notConfigured) "Configurar servidor" else "Reintentar",
                        onAction = if (notConfigured) onOpenSettings else vm::reload,
                        secondaryLabel = if (notConfigured) null else "Revisar el servidor",
                        onSecondary = onOpenSettings,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is LibraryState.Content -> if (state.books.isEmpty()) {
                    val query = vm.query
                    if (query != null) {
                        EinkMessage(
                            "Sin resultados",
                            Modifier.align(Alignment.Center),
                            message = "Ningún título ni autor coincide con «$query». Prueba con menos palabras.",
                            icon = EinkIcons.Search,
                        )
                    } else {
                        EinkMessage(
                            "No hay libros",
                            Modifier.align(Alignment.Center),
                            message = if (vm.lastOpened) {
                                "Aún no has abierto ningún libro en el lector."
                            } else {
                                "El servidor no tiene libros en este listado."
                            },
                            icon = EinkIcons.Library,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (vm.query != null) {
                            EinkEyebrow(
                                "${state.books.size}${if (state.hasMore) "+" else ""} resultados",
                                Modifier.padding(horizontal = EinkTheme.dimens.gutter, vertical = 4.dp),
                            )
                        }
                        val base = vm.coverBase
                        if (prefs.asList) {
                            EinkPagedGrid(
                                state.books, columns = 1, rows = 6, state = listPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                                spacing = 4.dp,
                            ) { BookRow(it, base, it.id.value in vm.downloaded, it.id.value in vm.finished) { onOpenBook(it.id) } }
                        } else {
                            EinkPagedGrid(
                                state.books, columns = 3, rows = 2, state = gridPage, modifier = Modifier.weight(1f),
                                key = { it.id.value }, hasMore = state.hasMore, onLoadMore = vm::loadMore,
                                spacing = 16.dp,
                            ) { book ->
                                BookTile(book, base, book.id.value in vm.downloaded, onClick = { onOpenBook(book.id) }, read = book.id.value in vm.finished)
                            }
                        }
                        state.moreProblem?.let { MoreFailed(it, onRetry = vm::loadMore) }
                    }
                }
            }
        }
    }

    if (showBrowse) {
        BrowseSheet(
            onDismissRequest = { showBrowse = false },
            onSelect = {
                showBrowse = false
                onBrowse(it)
            },
        )
    }

    if (showSort) {
        EinkMenu(
            onDismissRequest = { showSort = false },
            title = "Ordenar",
            options = CatalogListing.entries.map { it.label } + LAST_OPENED,
            selectedIndex = if (vm.lastOpened) CatalogListing.entries.size else vm.listing.ordinal,
            onSelect = {
                showSort = false
                resetPages()
                searching = false
                searchText = ""
                if (it == CatalogListing.entries.size) vm.selectLastOpened() else vm.select(CatalogListing.entries[it])
            },
        )
    }
}

/** Orden local, fuera de los listados OPDS del servidor. */
private const val LAST_OPENED = "Último abierto"

/** Aviso al pie cuando falla la página siguiente; lo ya cargado se sigue viendo. */
@Composable
fun MoreFailed(problem: ConnectionProblem, onRetry: () -> Unit) {
    EinkCard(
        Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 4.dp),
        padding = 12.dp,
        corner = 18.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EinkIcon(EinkIcons.Warning, contentDescription = null, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            EinkText("No se pudieron cargar más: ${problem.title}.", Modifier.weight(1f), style = EinkTheme.typography.caption, maxLines = 2)
            Spacer(Modifier.width(8.dp))
            EinkButton("Reintentar", onClick = onRetry, style = EinkButtonStyle.Secondary, height = 40.dp)
        }
    }
}

/** Fila de la vista de lista: portada pequeña, título, autor y formatos. */
@Composable
private fun BookRow(book: Book, base: okhttp3.HttpUrl?, downloaded: Boolean, read: Boolean, onClick: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxSize()
            .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, base, Modifier.fillMaxSize().weight(0.16f), corner = 6.dp, compact = true, pressSource = press)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            EinkText(book.title, style = EinkTheme.typography.label, maxLines = 1)
            EinkText(
                listOf(book.authors.joinToString().ifEmpty { "Autor desconocido" }, book.formats.joinToString(" · ") { it.name })
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = EinkTheme.typography.caption,
                color = EinkTheme.colors.muted,
                maxLines = 1,
            )
        }
        if (read) {
            Spacer(Modifier.width(8.dp))
            ReadBadge(small = true)
        }
        if (downloaded) {
            Spacer(Modifier.width(8.dp))
            DownloadedBadge(small = true)
        }
        Spacer(Modifier.width(4.dp))
        EinkIcon(EinkIcons.ChevronRight, contentDescription = null, size = 20.dp)
    }
}

/**
 * Cabecera de búsqueda (RF-03): volver, campo en píldora y borrar. Se busca
 * al pulsar «Buscar» en el teclado, no a cada letra.
 */
@Composable
private fun SearchHeader(text: String, onTextChange: (String) -> Unit, onSearch: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkIconButton(EinkIcons.Back, contentDescription = "Cerrar búsqueda", onClick = onClose)
        Spacer(Modifier.width(8.dp))
        EinkTextField(
            value = text,
            onValueChange = onTextChange,
            label = null,
            placeholder = "Título o autor",
            modifier = Modifier.weight(1f),
            onDone = onSearch,
            leadingIcon = EinkIcons.Search,
            pill = true,
            focusRequester = focus,
            trailing = if (text.isNotEmpty()) {
                { EinkIconButton(EinkIcons.Close, "Borrar", onClick = { onTextChange("") }, tonal = false) }
            } else null,
        )
    }
}

/** «Explorar por»: autor, serie o etiqueta, cada uno en una tarjeta. */
@Composable
private fun BrowseSheet(onDismissRequest: () -> Unit, onSelect: (CatalogBrowse) -> Unit) {
    EinkSheet(onDismissRequest, title = "Explorar por") {
        Column(
            Modifier.padding(horizontal = EinkTheme.dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatalogBrowse.entries.forEach { browse ->
                val (icon, description) = browseInfo(browse)
                EinkCard(Modifier.fillMaxWidth(), onClick = { onSelect(browse) }, padding = 16.dp, corner = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EinkSurface(Modifier.size(48.dp), tone = EinkTone.Paper, corner = 24.dp) {
                            EinkIcon(icon, null, Modifier.align(Alignment.Center))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            EinkText(browse.label, style = EinkTheme.typography.label.merge(fontSize = 17.sp))
                            EinkText(description, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
                        }
                        EinkIcon(EinkIcons.ChevronRight, null, size = 22.dp)
                    }
                }
            }
        }
    }
}

private fun browseInfo(browse: CatalogBrowse): Pair<ImageVector, String> = when (browse) {
    CatalogBrowse.Authors -> EinkIcons.User to "Todos los autores, de la A a la Z"
    CatalogBrowse.Series -> EinkIcons.Series to "Sagas y colecciones, en orden"
    CatalogBrowse.Tags -> EinkIcons.Tag to "Géneros y temas"
}
