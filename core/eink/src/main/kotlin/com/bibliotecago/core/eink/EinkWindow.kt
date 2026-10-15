package com.bibliotecago.core.eink

import android.app.Activity
import android.os.Build
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
/**
 * Abre y cierra la actividad de golpe. En Android 14+ el tema
 * (`windowAnimationStyle` a `@null`) ya no basta: hay que pedirlo en cada
 * actividad. En versiones anteriores, [finishWithoutAnimation] al cerrar.
 */
fun Activity.disableTransitions() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/** [Activity.finish] sin animación de cierre. */
fun Activity.finishWithoutAnimation() {
    finish()
    if (Build.VERSION.SDK_INT < 34) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

fun Activity.hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
