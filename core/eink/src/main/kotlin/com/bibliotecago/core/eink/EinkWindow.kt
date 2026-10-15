package com.bibliotecago.core.eink

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Lectura a pantalla completa: sin barra de estado ni de navegación. Se
 * asoman deslizando desde el borde, por encima del texto y sin recolocarlo,
 * y vuelven a ocultarse solas.
 *
 * Hay que llamarla también al recuperar el foco: un diálogo o el teclado
 * pueden volver a mostrarlas.
 */
fun Activity.hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
