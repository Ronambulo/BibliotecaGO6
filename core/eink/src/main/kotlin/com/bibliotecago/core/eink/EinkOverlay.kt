package com.bibliotecago.core.eink

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/*
 * Diálogos y menús se dibujan dentro de la propia ventana de la actividad en
 * lugar de abrir una ventana nueva (Dialog/Popup), porque esas ventanas traen
 * animaciones de entrada y salida del sistema. Un velo gris fijo (sin fundido)
 * separa el diálogo o la hoja del contenido de detrás.
 */

private class OverlayEntry(val content: @Composable () -> Unit)

private class OverlayState {
    val entries = mutableStateListOf<OverlayEntry>()
}

private val LocalOverlayState = staticCompositionLocalOf<OverlayState> {
    error("EinkOverlay necesita estar dentro de EinkTheme")
}

@Composable
internal fun EinkOverlayHost(content: @Composable () -> Unit) {
    val state = remember { OverlayState() }
    CompositionLocalProvider(LocalOverlayState provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.entries.forEach { entry -> key(entry) { entry.content() } }
        }
    }
}

/**
 * Capa a pantalla completa por encima del contenido. Tocar fuera de [content]
 * o pulsar Atrás llama a [onDismissRequest].
 */
@Composable
fun EinkOverlay(
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment = Alignment.Center,
    scrim: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalOverlayState.current
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentContent by rememberUpdatedState(content)
    val entry = remember {
        OverlayEntry {
            BackHandler { currentDismiss() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (scrim) Modifier.background(Scrim) else Modifier)
                    .clickable(interactionSource = null, indication = null) { currentDismiss() },
                contentAlignment = contentAlignment,
            ) {
                currentContent()
            }
        }
    }
    DisposableEffect(state, entry) {
        state.entries += entry
        onDispose { state.entries -= entry }
    }
}

private val Scrim = Color(0x73000000)

/** Evita que un toque dentro de la tarjeta del diálogo lo cierre. */
internal fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }
