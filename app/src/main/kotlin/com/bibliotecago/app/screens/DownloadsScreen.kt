package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkTopBar

@Composable
fun DownloadsScreen(onOpenLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Descargas")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EinkMessage(
                title = "No hay libros descargados",
                message = "Los libros que descargues desde la biblioteca aparecerán aquí y podrás leerlos sin conexión.",
                actionLabel = "Ir a la biblioteca",
                onAction = onOpenLibrary,
            )
        }
    }
}
