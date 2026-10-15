package com.bibliotecago.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bibliotecago.core.eink.EinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EinkTheme {
                BibliotecaApp()
            }
        }
        // Calibre-Web no avisa de cambios: con la app en pantalla se le pregunta cada
        // pocos minutos por leídos y libros nuevos. Al salir se para; al volver se
        // consulta en el acto. Sin WorkManager: en segundo plano solo gastaría batería.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    appContainer.poll()
                    delay(POLL_INTERVAL)
                }
            }
        }
    }

    private companion object {
        val POLL_INTERVAL = 5.minutes
    }
}
