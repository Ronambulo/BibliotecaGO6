package com.bibliotecago.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bibliotecago.core.eink.EinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EinkTheme {
                BibliotecaApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Al volver a la app, se trae lo marcado como leído en Calibre-Web mientras tanto.
        appContainer.readSync.retry()
    }
}
