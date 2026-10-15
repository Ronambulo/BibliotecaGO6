package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.demo.DemoBooks
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMenu
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.Book

private val sortOptions = listOf("Título", "Autor", "Fecha de incorporación")

@Composable
fun LibraryScreen() {
    var asList by rememberSaveable { mutableStateOf(false) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    val gridPage = rememberEinkPageState()
    val listPage = rememberEinkPageState()

    val books = when (sortIndex) {
        1 -> DemoBooks.all.sortedBy { it.authors.firstOrNull() }
        else -> DemoBooks.all.sortedBy { it.title }
    }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Biblioteca") {
            EinkIconButton(EinkIcons.Search, contentDescription = "Ordenar", onClick = { showSort = true })
            EinkIconButton(
                icon = if (asList) EinkIcons.Grid else EinkIcons.List,
                contentDescription = if (asList) "Ver cuadrícula" else "Ver lista",
                onClick = { asList = !asList },
            )
        }
        // El tamaño de la cuadrícula (3×3 o 3×4) se decidirá probando en el BOOX.
        if (asList) {
            EinkPagedGrid(books, columns = 1, rows = 7, state = listPage, modifier = Modifier.weight(1f), key = { it.id.value }) {
                BookRow(it)
            }
        } else {
            EinkPagedGrid(books, columns = 3, rows = 3, state = gridPage, modifier = Modifier.weight(1f), key = { it.id.value }) {
                BookCover(it)
            }
        }
    }

    if (showSort) {
        EinkMenu(
            onDismissRequest = { showSort = false },
            title = "Ordenar por",
            options = sortOptions,
            selectedIndex = sortIndex,
            onSelect = { sortIndex = it; showSort = false },
        )
    }
}

/** Portada provisional: Coil llegará en la Fase 2. */
@Composable
private fun BookCover(book: Book) {
    EinkSurface(Modifier.fillMaxSize(), bordered = true) {
        Column(
            Modifier.align(Alignment.Center).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EinkText(book.title, style = EinkTheme.typography.caption, maxLines = 4, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            EinkText(book.formats.joinToString { it.name }, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
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
