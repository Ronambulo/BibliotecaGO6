package com.bibliotecago.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliotecago.app.appContainer
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkButtonStyle
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkSegmented
import com.bibliotecago.core.eink.EinkSwitch
import com.bibliotecago.reader.epub.ReaderFont
import com.bibliotecago.reader.epub.ParagraphStyle
import com.bibliotecago.reader.epub.ReaderSettings
import com.bibliotecago.reader.epub.ReaderSettingsStore
import com.bibliotecago.reader.pdf.PdfFit
import com.bibliotecago.reader.pdf.PdfSettings
import com.bibliotecago.reader.pdf.PdfSettingsStore
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll
import com.bibliotecago.server.EndpointReport

/** Ajustes: grupos en tarjetas; el servidor tiene su propia página. */
@Composable
fun SettingsScreen(onOpenServer: () -> Unit, onOpenReading: () -> Unit, onOpenLicenses: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { SettingsViewModel(container) }
    val prefs = rememberLibraryPrefs()
    val context = LocalContext.current
    // Se relee al volver de la página de lectura.
    val reader = remember { ReaderSettingsStore(context) }.load()
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }
    // Al volver a Ajustes desde otra pestaña puede haber nuevas descargas.
    LaunchedEffect(Unit) { vm.refreshStorage() }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Ajustes")
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(horizontal = EinkTheme.dimens.gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Group("Conexión") {
                NavRow(
                    icon = EinkIcons.Server,
                    title = "Servidor",
                    detail = if (vm.hasSavedConfiguration) {
                        vm.tunnelUrl.removePrefix("https://").removePrefix("http://").ifEmpty { "Configurado" }
                    } else {
                        "Sin configurar"
                    },
                    onClick = onOpenServer,
                )
            }
            Group("Lectura") {
                NavRow(
                    icon = EinkIcons.Text,
                    title = "Lectura",
                    detail = "Letra al ${reader.fontPercent} % · tema ${if (reader.dark) "oscuro" else "claro"}",
                    onClick = onOpenReading,
                )
            }
            Group("Biblioteca") {
                EinkSwitch(
                    prefs.asList, prefs::updateAsList, "Mostrar en lista",
                    description = "Una columna con título y autor en vez de la cuadrícula de portadas.",
                )
            }
            Group("Almacenamiento") {
                InfoRow("Libros descargados", vm.downloadsBytes?.let(::formatBytes) ?: "…")
                EinkDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth().height(EinkTheme.dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        EinkText("Caché de portadas", style = EinkTheme.typography.label)
                        EinkText(
                            vm.coverCacheBytes?.let(::formatBytes) ?: "…",
                            style = EinkTheme.typography.caption, color = EinkTheme.colors.muted,
                        )
                    }
                    EinkButton(
                        "Vaciar",
                        onClick = { confirmClearCache = true },
                        enabled = (vm.coverCacheBytes ?: 0L) > 0L,
                        height = 40.dp,
                    )
                }
            }
            Group("Acerca de") {
                InfoRow("BibliotecaGO", "Versión 0.1.0")
                EinkDivider(Modifier.padding(vertical = 4.dp))
                NavRow(
                    icon = EinkIcons.Info,
                    title = "Licencias de terceros",
                    detail = "Bibliotecas de código abierto",
                    onClick = onOpenLicenses,
                )
            }
        }
    }

    if (confirmClearCache) {
        EinkDialog(
            onDismissRequest = { confirmClearCache = false },
            title = "¿Vaciar caché de portadas?",
            text = "Las portadas se volverán a descargar al ver la biblioteca. Sin conexión no se verán hasta entonces.",
            icon = EinkIcons.Trash,
            confirmLabel = "Vaciar",
            onConfirm = {
                vm.clearCoverCache()
                confirmClearCache = false
            },
        )
    }
}

/**
 * Ajustes de lectura para todos los libros, los mismos que la hoja del lector.
 * Se aplican al abrir el siguiente libro.
 */
@Composable
fun ReadingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val epubStore = remember { ReaderSettingsStore(context) }
    val pdfStore = remember { PdfSettingsStore(context) }
    var epub by remember { mutableStateOf(epubStore.load()) }
    var pdf by remember { mutableStateOf(pdfStore.load()) }
    val updateEpub = { new: ReaderSettings -> epub = new; epubStore.save(new) }
    val updatePdf = { new: PdfSettings -> pdf = new; pdfStore.save(new) }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Lectura", subtitle = "Para todos los libros", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(horizontal = EinkTheme.dimens.gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Group("EPUB") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        EinkText("Tamaño de letra", style = EinkTheme.typography.label)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EinkIconButton(
                                EinkIcons.Minus, "Letra más pequeña", { updateEpub(epub.smallerFont()) },
                                enabled = epub.fontScale > ReaderSettings.MIN_FONT,
                            )
                            EinkText(
                                "${epub.fontPercent} %",
                                Modifier.weight(1f),
                                style = EinkTheme.typography.heading,
                                textAlign = TextAlign.Center,
                            )
                            EinkIconButton(
                                EinkIcons.Plus, "Letra más grande", { updateEpub(epub.largerFont()) },
                                enabled = epub.fontScale < ReaderSettings.MAX_FONT,
                            )
                        }
                    }
                    Choice("Letra", ReaderFont.entries.map { it.label to it }, epub.font) { updateEpub(epub.copy(font = it)) }
                    Choice("Interlineado", ReaderSettings.LINE_HEIGHTS, epub.lineHeight) { updateEpub(epub.copy(lineHeight = it)) }
                    Choice("Márgenes", ReaderSettings.MARGINS, epub.margins) { updateEpub(epub.copy(margins = it)) }
                    Choice("Párrafos", ParagraphStyle.entries.map { it.label to it }, epub.paragraphs) {
                        updateEpub(epub.copy(paragraphs = it))
                    }
                    Choice("Tema", listOf("Claro" to false, "Oscuro" to true), epub.dark) { updateEpub(epub.copy(dark = it)) }
                }
            }
            Group("PDF") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Choice("Ajuste", PdfFit.entries.map { it.label to it }, pdf.fit) { updatePdf(pdf.copy(fit = it)) }
                    Choice("Márgenes en blanco", listOf("Recortar" to true, "Mantener" to false), pdf.crop) {
                        updatePdf(pdf.copy(crop = it))
                    }
                }
            }
            EinkText(
                "También se cambian desde el lector; el cambio vale para todos los libros.",
                Modifier.padding(start = 4.dp, top = 4.dp),
                style = EinkTheme.typography.caption,
                color = EinkTheme.colors.muted,
            )
        }
    }
}

/** Título y control segmentado con las opciones; marca la que coincide con [selected]. */
@Composable
private fun <T> Choice(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        EinkText(title, style = EinkTheme.typography.label)
        Spacer(Modifier.height(6.dp))
        EinkSegmented(
            options.map { it.first },
            selectedIndex = options.indexOfFirst { it.second == selected },
            onSelect = { onSelect(options[it].second) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(8.dp))
    EinkEyebrow(title, Modifier.padding(start = 4.dp, bottom = 4.dp))
    EinkCard(Modifier.fillMaxWidth(), padding = 16.dp, content = content)
}

@Composable
private fun NavRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkIcon(icon, null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            EinkText(title, style = EinkTheme.typography.label)
            EinkText(detail, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
        }
        EinkIcon(EinkIcons.ChevronRight, null)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        EinkText(label, Modifier.weight(1f), style = EinkTheme.typography.label)
        EinkText(value, style = EinkTheme.typography.body, color = EinkTheme.colors.muted)
    }
}

/** Página del servidor: URL, credenciales, prueba y guardado. */
@Composable
fun ServerSettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { SettingsViewModel(container) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Servidor", subtitle = "Calibre-Web-Automated", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(horizontal = EinkTheme.dimens.gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EinkTextField(
                vm.tunnelUrl, { vm.tunnelUrl = it }, "URL del túnel",
                placeholder = "https://libros.midominio.com",
                keyboardType = KeyboardType.Uri,
                leadingIcon = EinkIcons.Server,
                supportingText = vm.tunnelError?.let { "Error: $it" },
            )
            EinkTextField(
                vm.localUrl, { vm.localUrl = it }, "URL local (opcional)",
                placeholder = "http://192.168.1.10:8083",
                keyboardType = KeyboardType.Uri,
                supportingText = vm.localError?.let { "Error: $it" }
                    ?: "Se usa cuando responde; si no, el túnel. HTTP solo hacia direcciones de red privada.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EinkTextField(
                    vm.username, { vm.username = it }, "Usuario",
                    Modifier.weight(1f),
                    supportingText = vm.credentialsError?.let { "Error: $it" },
                )
                EinkTextField(
                    vm.password, { vm.password = it }, "Contraseña",
                    Modifier.weight(1f),
                    password = true,
                )
            }
            EinkText(
                "La contraseña se guarda cifrada en el dispositivo.",
                style = EinkTheme.typography.caption, color = EinkTheme.colors.muted,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EinkButton(
                    if (vm.testing) "Probando…" else "Probar conexión",
                    onClick = vm::test,
                    modifier = Modifier.weight(1f),
                    enabled = !vm.testing,
                    icon = EinkIcons.Refresh,
                )
                EinkButton("Guardar", onClick = vm::save, modifier = Modifier.weight(1f), primary = true, icon = EinkIcons.Check)
            }
            if (vm.hasSavedConfiguration) {
                EinkText(
                    "Eliminar configuración",
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { confirmDelete = true }
                        .padding(12.dp),
                    style = EinkTheme.typography.label.copy(textDecoration = TextDecoration.Underline),
                )
            }
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
            icon = if (ok) EinkIcons.Check else EinkIcons.Warning,
            confirmLabel = if (ok) "Guardar" else "Entendido",
            onConfirm = { if (ok) vm.save(); vm.dismissReports() },
            dismissLabel = if (ok) "Cerrar" else null,
        )
    }

    if (confirmDelete) {
        EinkDialog(
            onDismissRequest = { confirmDelete = false },
            title = "¿Eliminar configuración?",
            text = "Se borran del dispositivo las URL, el usuario y la contraseña. " +
                "Los libros descargados se quedan y se pueden seguir leyendo. No cambia nada en el servidor.",
            icon = EinkIcons.Trash,
            confirmLabel = "Eliminar",
            onConfirm = {
                vm.deleteConfiguration()
                confirmDelete = false
            },
        )
    }

    if (vm.savedNotice) {
        EinkDialog(
            onDismissRequest = vm::dismissSavedNotice,
            title = "Ajustes guardados",
            text = "La biblioteca usará este servidor.",
            icon = EinkIcons.Check,
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
