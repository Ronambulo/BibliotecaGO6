package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkSwitch
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll
import com.bibliotecago.server.EndpointReport

@Composable
fun SettingsScreen() {
    val container = LocalContext.current.appContainer
    val vm = viewModel { SettingsViewModel(container) }
    var listByDefault by rememberSaveable { mutableStateOf(false) }

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
            EinkTextField(
                vm.tunnelUrl, { vm.tunnelUrl = it }, "URL del túnel",
                placeholder = "https://libros.midominio.com",
                keyboardType = KeyboardType.Uri,
                supportingText = vm.tunnelError?.let { "Error: $it" },
            )
            Spacer(Modifier.height(12.dp))
            EinkTextField(
                vm.localUrl, { vm.localUrl = it }, "URL local (opcional)",
                placeholder = "http://192.168.1.10:8083",
                keyboardType = KeyboardType.Uri,
                supportingText = vm.localError?.let { "Error: $it" }
                    ?: "Se usa cuando responde; si no, el túnel. HTTP solo hacia direcciones de red privada.",
            )
            Spacer(Modifier.height(12.dp))
            EinkTextField(
                vm.username, { vm.username = it }, "Usuario",
                supportingText = vm.credentialsError?.let { "Error: $it" },
            )
            Spacer(Modifier.height(12.dp))
            EinkTextField(
                vm.password, { vm.password = it }, "Contraseña", password = true,
                supportingText = "Se guarda cifrada en el dispositivo.",
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EinkButton(
                    if (vm.testing) "Probando…" else "Probar conexión",
                    onClick = vm::test,
                    modifier = Modifier.weight(1f),
                    enabled = !vm.testing,
                )
                EinkButton("Guardar", onClick = vm::save, modifier = Modifier.weight(1f), primary = true)
            }

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

    vm.reports?.let { reports ->
        // Fuera de casa la URL local no responde: basta con que una de las dos funcione.
        val ok = reports.any { it.problem == null }
        EinkDialog(
            onDismissRequest = vm::dismissReports,
            title = when {
                reports.all { it.problem == null } -> "Conexión correcta"
                ok -> "Conexión parcial"
                else -> "No se pudo conectar"
            },
            text = reports.joinToString("\n\n", transform = ::describe),
            confirmLabel = if (ok) "Guardar" else "Entendido",
            onConfirm = { if (ok) vm.save(); vm.dismissReports() },
            dismissLabel = if (ok) "Cerrar" else null,
        )
    }

    if (vm.savedNotice) {
        EinkDialog(
            onDismissRequest = vm::dismissSavedNotice,
            title = "Ajustes guardados",
            text = "La biblioteca usará este servidor.",
            confirmLabel = "Entendido",
            onConfirm = vm::dismissSavedNotice,
            dismissLabel = null,
        )
    }
}

private fun describe(report: EndpointReport): String {
    val which = if (report.isLocal) "URL local" else "Túnel"
    val problem = report.problem ?: return "$which: catálogo accesible."
    return "$which: ${problem.title}. ${problem.hint}"
}
