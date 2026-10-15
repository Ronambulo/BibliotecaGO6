package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bibliotecago.app.demo.DemoBooks
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar

@Composable
fun HomeScreen(onOpenLibrary: () -> Unit) {
    val current = DemoBooks.all.first()
    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "BibliotecaGO") {
            EinkIconButton(EinkIcons.Search, contentDescription = "Buscar", onClick = onOpenLibrary)
        }
        Column(Modifier.padding(EinkTheme.dimens.gutter)) {
            EinkText("Continuar leyendo", style = EinkTheme.typography.label)
            Spacer(Modifier.height(8.dp))
            EinkCard(Modifier.fillMaxWidth(), onClick = {}) {
                EinkText(current.title, style = EinkTheme.typography.heading, maxLines = 2)
                EinkText(current.authors.joinToString(), color = EinkTheme.colors.muted)
                Spacer(Modifier.height(8.dp))
                EinkText("42 %", style = EinkTheme.typography.caption)
            }
            Spacer(Modifier.height(24.dp))
            EinkText("Servidor: sin configurar", style = EinkTheme.typography.caption)
            Spacer(Modifier.height(24.dp))
            EinkButton("Ir a la biblioteca", onClick = onOpenLibrary, primary = true, icon = EinkIcons.Library)
        }
    }
}
