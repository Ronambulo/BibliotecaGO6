package com.bibliotecago.reader.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.model.BookId
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lector PDF. Cada página se pinta como imagen y se muestra entera o, al
 * ancho, por trozos. Toques en los bordes y deslizamientos pasan pantalla sin
 * animación; un toque en el centro muestra el menú de arriba.
 */
class PdfReaderActivity : ComponentActivity() {

    private val viewModel: PdfReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EinkTheme { Reader() } }
        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.panel != PdfPanel.None -> viewModel.panel = PdfPanel.None
                viewModel.controlsVisible -> viewModel.controlsVisible = false
                else -> finish()
            }
        }
    }

    // Teclado o mando: flechas y avance/retroceso de página.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (viewModel.panel == PdfPanel.GoTo) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> viewModel.next()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> viewModel.previous()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    @Composable
    private fun Reader() {
        val state by viewModel.state.collectAsState()
        Box(Modifier.fillMaxSize().background(EinkTheme.colors.paper)) {
            when (val s = state) {
                PdfState.Opening -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EinkMessage("Abriendo libro…")
                }
                is PdfState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EinkMessage("No se puede leer", message = s.message, actionLabel = "Volver", onAction = ::finish)
                }
                is PdfState.Ready -> {
                    Page(Modifier.fillMaxSize())
                    if (viewModel.controlsVisible) Controls(s.pageCount, Modifier.align(Alignment.TopCenter))
                    if (viewModel.panel == PdfPanel.GoTo) GoToPage(s.pageCount)
                }
            }
        }
    }

    @Composable
    private fun Page(modifier: Modifier) {
        Canvas(
            modifier
                .onSizeChanged(viewModel::onViewport)
                .pointerInput(Unit) { detectPageGestures() },
        ) {
            val shown = viewModel.shown ?: return@Canvas
            val image = shown.image
            val visible = min(image.height - shown.offsetPx, size.height.toInt())
            // Centrada si sobra sitio; arriba del todo si la página es más alta que la pantalla.
            val x = ((size.width - image.width) / 2).roundToInt().coerceAtLeast(0)
            val y = ((size.height - visible) / 2).roundToInt().coerceAtLeast(0)
            drawImage(
                image,
                srcOffset = IntOffset(0, shown.offsetPx),
                srcSize = IntSize(image.width, visible),
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(image.width, visible),
                filterQuality = FilterQuality.None,
            )
        }
    }

    /**
     * Toques y deslizamientos, sin que la página siga al dedo: se mira el gesto
     * entero y al soltar se decide. Bordes (30 %) y deslizamientos pasan
     * pantalla; el centro abre o cierra el menú.
     */
    private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPageGestures() {
        val minSwipe = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            var multiTouch = false
            var end = down.position
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) multiTouch = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                end = change.position
                if (!change.pressed) break
            }
            if (multiTouch) return@awaitEachGesture
            val dx = end.x - down.position.x
            val dy = end.y - down.position.y
            val slop = viewConfiguration.touchSlop
            when {
                abs(dx) < slop && abs(dy) < slop -> {
                    val fraction = down.position.x / size.width
                    when {
                        fraction < EDGE -> viewModel.previous()
                        fraction > 1 - EDGE -> viewModel.next()
                        else -> {
                            viewModel.controlsVisible = !viewModel.controlsVisible
                            viewModel.panel = PdfPanel.None
                        }
                    }
                }
                abs(dx) >= abs(dy) && abs(dx) >= minSwipe -> if (dx < 0) viewModel.next() else viewModel.previous()
                abs(dy) > abs(dx) && abs(dy) >= minSwipe -> if (dy < 0) viewModel.next() else viewModel.previous()
            }
        }
    }

    /** Menú arriba: botones, página y, si están abiertos, los ajustes de vista. */
    @Composable
    private fun Controls(pageCount: Int, modifier: Modifier) {
        Column(modifier.fillMaxWidth().background(EinkTheme.colors.paper).blockTouches()) {
            Row(
                Modifier.fillMaxWidth().height(EinkTheme.dimens.touchTarget + 8.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkIconButton(EinkIcons.Back, contentDescription = "Cerrar libro", onClick = ::finish)
                Spacer(Modifier.width(4.dp))
                EinkText(viewModel.title, Modifier.weight(1f), style = EinkTheme.typography.heading, maxLines = 1)
                EinkIconButton(EinkIcons.List, contentDescription = "Ir a página", onClick = { viewModel.panel = PdfPanel.GoTo })
                EinkIconButton(
                    EinkIcons.Settings,
                    contentDescription = "Vista",
                    onClick = { viewModel.panel = if (viewModel.panel == PdfPanel.View) PdfPanel.None else PdfPanel.View },
                    selected = viewModel.panel == PdfPanel.View,
                )
            }
            EinkDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 12.dp)) {
                EinkText(
                    "Página ${viewModel.position.page + 1} de $pageCount",
                    Modifier.weight(1f),
                    style = EinkTheme.typography.caption,
                )
                EinkText("${(viewModel.progression * 100).roundToInt()} %", style = EinkTheme.typography.caption)
            }
            if (viewModel.panel == PdfPanel.View) {
                EinkDivider()
                ViewSettings()
            }
            EinkDivider(thickness = EinkTheme.dimens.border)
        }
    }

    @Composable
    private fun ViewSettings() {
        val settings = viewModel.settings
        Column(
            Modifier.padding(EinkTheme.dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(EinkTheme.dimens.spacing),
        ) {
            Choice("Ajuste", PdfFit.entries.map { it.label to it }, settings.fit) {
                viewModel.updateSettings(settings.copy(fit = it))
            }
            Choice("Márgenes en blanco", listOf("Recortar" to true, "Mantener" to false), settings.crop) {
                viewModel.updateSettings(settings.copy(crop = it))
            }
        }
    }

    /** Fila de opciones excluyentes; la elegida, rellena en negro. */
    @Composable
    private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
        Column {
            EinkText(label, style = EinkTheme.typography.caption)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (text, value) ->
                    EinkButton(text, { onSelect(value) }, Modifier.weight(1f), primary = value == selected)
                }
            }
        }
    }

    /** A pantalla completa y arriba, para que el teclado no tape el campo. */
    @Composable
    private fun GoToPage(pageCount: Int) {
        var text by rememberSaveable { mutableStateOf("") }
        val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
        val go = {
            page?.let {
                viewModel.goToPage(it)
                viewModel.panel = PdfPanel.None
                viewModel.controlsVisible = false
            }
        }
        Column(Modifier.fillMaxSize().background(EinkTheme.colors.paper).blockTouches()) {
            EinkTopBar(title = "Ir a página", onBack = { viewModel.panel = PdfPanel.None })
            Column(
                Modifier.padding(EinkTheme.dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(EinkTheme.dimens.spacing),
            ) {
                EinkTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                    label = "Página (1–$pageCount)",
                    placeholder = "${viewModel.position.page + 1}",
                    keyboardType = KeyboardType.Number,
                    onDone = { go() },
                )
                EinkButton("Ir", { go() }, Modifier.fillMaxWidth(), primary = true, enabled = page != null)
            }
        }
    }

    /** Lo que se toque aquí sin pulsar un botón no llega a la página de debajo. */
    private fun Modifier.blockTouches() = pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
    }

    companion object {
        /** Ancho de cada borde que pasa página, como fracción de la pantalla. */
        private const val EDGE = 0.3f

        fun intent(context: Context, file: File, bookId: BookId, title: String): Intent =
            Intent(context, PdfReaderActivity::class.java)
                .putExtra(PdfReaderViewModel.EXTRA_PATH, file.absolutePath)
                .putExtra(PdfReaderViewModel.EXTRA_BOOK_ID, bookId.value)
                .putExtra(PdfReaderViewModel.EXTRA_TITLE, title)
    }
}
