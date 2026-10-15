package com.bibliotecago.app.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** Preferencias de la interfaz de la biblioteca: de momento, lista o cuadrícula. */
class LibraryPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var asList by mutableStateOf(prefs.getBoolean(KEY_AS_LIST, false))
        private set

    fun updateAsList(value: Boolean) {
        asList = value
        prefs.edit { putBoolean(KEY_AS_LIST, value) }
    }

    private companion object {
        const val KEY_AS_LIST = "library_as_list"
    }
}

@Composable
fun rememberLibraryPrefs(): LibraryPrefs {
    val context = LocalContext.current.applicationContext
    return remember(context) { LibraryPrefs(context) }
}
