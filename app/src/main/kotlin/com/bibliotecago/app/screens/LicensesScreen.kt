package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bibliotecago.core.eink.EinkCard
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.einkVerticalScroll

/** Una biblioteca (o familia de bibliotecas) incluida en la app y su licencia. */
private class ThirdParty(val name: String, val author: String, val license: License, val detail: String? = null)

private enum class License(val label: String, val url: String) {
    Apache2("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    Bsd3("BSD 3-Clause", "https://opensource.org/license/bsd-3-clause"),
    Mit("MIT", "https://opensource.org/license/mit"),
    Gpl2Classpath("GPL v2 con Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html"),
}

/**
 * Lo que acaba dentro del APK, sacado de `releaseRuntimeClasspath` y del
 * desazucarado. Al añadir una dependencia nueva, hay que añadirla aquí.
 */
private val THIRD_PARTY = listOf(
    ThirdParty(
        "Readium Kotlin Toolkit", "Readium Foundation", License.Bsd3,
        "Apertura y lectura de EPUB.",
    ),
    ThirdParty(
        "Android Jetpack (AndroidX)", "The Android Open Source Project", License.Apache2,
        "Compose, Navigation, Room, WorkManager, Lifecycle, Fragment, Media3, WebKit y el resto de androidx.*",
    ),
    ThirdParty(
        "Kotlin y kotlinx", "JetBrains s.r.o.", License.Apache2,
        "Biblioteca estándar, corrutinas y piezas multiplataforma de Compose.",
    ),
    ThirdParty("OkHttp y Okio", "Square, Inc.", License.Apache2, "Conexión con el servidor y descargas."),
    ThirdParty("Coil", "Coil Contributors", License.Apache2, "Carga de portadas."),
    ThirdParty("jsoup", "Jonathan Hedley", License.Mit, "Lectura de HTML, a través de Readium."),
    ThirdParty("Timber", "Jake Wharton", License.Apache2, "A través de Readium."),
    ThirdParty("Guava", "Google LLC", License.Apache2, "A través de Readium."),
    ThirdParty("Accompanist", "Google LLC", License.Apache2, "A través de Readium."),
    ThirdParty("Koi", "Xiaoke Zhang", License.Apache2, "A través de Readium."),
    ThirdParty("JSpecify", "The JSpecify Authors", License.Apache2),
    ThirdParty(
        "desugar_jdk_libs", "Oracle y Google LLC", License.Gpl2Classpath,
        "APIs de java.time y java.util en Android 11.",
    ),
)

/** Licencias de terceros: qué bibliotecas de código abierto usa la app. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EinkTopBar(title = "Licencias de terceros", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .einkVerticalScroll(rememberScrollState())
                .padding(horizontal = EinkTheme.dimens.gutter)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EinkText(
                "BibliotecaGO se apoya en estas bibliotecas de código abierto. Gracias a quienes las hacen.",
                style = EinkTheme.typography.body,
                color = EinkTheme.colors.muted,
            )
            Spacer(Modifier.height(4.dp))
            THIRD_PARTY.forEach { LibraryCard(it) }
            Spacer(Modifier.height(8.dp))
            EinkEyebrow("Textos de las licencias", Modifier.padding(start = 4.dp, bottom = 4.dp))
            EinkCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                License.entries.forEachIndexed { i, license ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    EinkText(license.label, style = EinkTheme.typography.label)
                    EinkText(license.url, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(library: ThirdParty) {
    EinkCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        EinkText(library.name, style = EinkTheme.typography.label)
        EinkText("© ${library.author} · ${library.license.label}", style = EinkTheme.typography.caption)
        library.detail?.let {
            Spacer(Modifier.height(4.dp))
            EinkText(it, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
        }
    }
}
