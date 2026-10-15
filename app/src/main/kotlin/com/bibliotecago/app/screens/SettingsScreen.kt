package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkSwitch
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll

/** Solo maqueta: la configuración real (cifrado, prueba de conexión) es la Fase 2. */
@Composable
fun SettingsScreen() {
    var tunnelUrl by rememberSaveable { mutableStateOf("") }
    var localUrl by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var listByDefault by rememberSaveable { mutableStateOf(false) }
    var showTest by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Ajustes")
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(EinkTheme.dimens.gutter)
        ) {
            EinkText("Servidor", style = EinkTheme.typography.heading)
            Spacer(Modifier.height(12.dp))
            EinkTextField(tunnelUrl, { tunnelUrl = it }, "URL del túnel", placeholder = "https://libros.midominio.com", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(12.dp))
            EinkTextField(
                localUrl, { localUrl = it }, "URL local (opcional)",
                placeholder = "http://192.168.1.10:8083",
                keyboardType = KeyboardType.Uri,
                supportingText = "Solo se admite HTTP hacia direcciones de red privada.",
            )
            Spacer(Modifier.height(12.dp))
            EinkTextField(user, { user = it }, "Usuario")
            Spacer(Modifier.height(12.dp))
            EinkTextField(password, { password = it }, "Contraseña", password = true)
            Spacer(Modifier.height(16.dp))
            EinkButton("Probar conexión", onClick = { showTest = true }, modifier = Modifier.fillMaxWidth(), primary = true)

            Spacer(Modifier.height(24.dp))
            EinkDivider()
            Spacer(Modifier.height(16.dp))
            EinkText("Biblioteca", style = EinkTheme.typography.heading)
            EinkSwitch(listByDefault, { listByDefault = it }, "Mostrar en lista por defecto")

            Spacer(Modifier.height(24.dp))
            EinkDivider()
            Spacer(Modifier.height(16.dp))
            EinkText("Versión 0.1.0 · prototipo", style = EinkTheme.typography.caption)
        }
    }

    if (showTest) {
        EinkDialog(
            onDismissRequest = { showTest = false },
            title = "Aún no disponible",
            text = "La conexión con Calibre-Web llega en la Fase 2.",
            confirmLabel = "Entendido",
            onConfirm = { showTest = false },
            dismissLabel = null,
        )
    }
}
