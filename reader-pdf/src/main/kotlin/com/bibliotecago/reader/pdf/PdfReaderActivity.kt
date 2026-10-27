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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkSegmented
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkTone
import com.bibliotecago.core.eink.einkBorder
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.disableTransitions
import com.bibliotecago.core.eink.finishWithoutAnimation
import com.bibliotecago.core.eink.hideSystemBars
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.model.BookId
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lector PDF. Cada página se pinta como imagen y se muestra entera o, al
 * ancho, por trozos. Toques en los bordes y deslizamientos pasan pantalla sin
 * animación; un toque en el centro muestra el menú. Pellizcar o tocar dos
 * veces en el centro amplía la página.
 */
class PdfReaderActivity : ComponentActivity() {

    private val viewModel: PdfReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()
        setContent { EinkTheme { Reader() } }
        hideSystemBars()
        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.panel != PdfPanel.None -> viewModel.panel = PdfPanel.None
                viewModel.controlsVisible -> viewModel.controlsVisible = false
                else -> finishWithoutAnimation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeReading()
    }

    override fun onStop() {
        viewModel.pauseReading()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
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
                // Página en blanco hasta que se pinta la primera: sin mensajes intermedios.
                PdfState.Opening -> Unit
                is PdfState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EinkMessage("No se puede leer", message = s.message, icon = EinkIcons.Warning, actionLabel = "Volver", onAction = ::finish)
                }
                is PdfState.Ready -> {
                    Page(Modifier.fillMaxSize())
                    if (viewModel.controlsVisible) {
                        TopStrip(Modifier.align(Alignment.TopCenter))
                        Controls(s.pageCount, Modifier.align(Alignment.BottomCenter))
                    }
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
     * pantalla; el centro abre o cierra el menú, y dos toques seguidos en el
     * centro amplían o vuelven a la página entera. Pellizcar cambia el zoom al
     * soltar; con zoom, arrastrar mueve la página en vez de pasarla.
     */
    private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPageGestures() {
        val minSwipe = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            var end = down.position
            var pinchStart = 0f
            var pinchEnd = 0f
            var focus = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val span = (pressed[0].position - pressed[1].position).getDistance()
                    if (pinchStart == 0f) {
                        pinchStart = span
                        focus = (pressed[0].position + pressed[1].position) / 2f
                    }
                    pinchEnd = span
                }
                event.changes.firstOrNull { it.id == down.id }?.let { end = it.position }
                if (pressed.isEmpty()) break
            }
            if (pinchStart > 0f) {
                viewModel.pinch(pinchEnd / pinchStart, focus)
                return@awaitEachGesture
            }
            val dx = end.x - down.position.x
            val dy = end.y - down.position.y
            val slop = viewConfiguration.touchSlop
            val zoomed = viewModel.zoom > 1f
            when {
                abs(dx) < slop && abs(dy) < slop -> {
                    val fraction = down.position.x / size.width
                    when {
                        fraction < EDGE -> viewModel.previous()
                        fraction > 1 - EDGE -> viewModel.next()
                        // Con el menú abierto no se espera al segundo toque: se cierra ya.
                        viewModel.controlsVisible -> {
                            viewModel.controlsVisible = false
                            viewModel.panel = PdfPanel.None
                        }
                        else -> {
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                            if (second != null && (second.position - down.position).getDistance() < DOUBLE_TAP_SLOP.dp.toPx()) {
                                viewModel.toggleZoom(down.position)
                            } else {
                                viewModel.controlsVisible = true
                            }
                        }
                    }
                }
                zoomed -> viewModel.pan(dx, dy)
                abs(dx) >= abs(dy) && abs(dx) >= minSwipe -> if (dx < 0) viewModel.next() else viewModel.previous()
                abs(dy) > abs(dx) && abs(dy) >= minSwipe -> if (dy < 0) viewModel.next() else viewModel.previous()
            }
        }
    }

    /** Franja de arriba: volver, título y leído. */
    @Composable
    private fun TopStrip(modifier: Modifier) {
        val finished by viewModel.finished.collectAsState()
        Column(modifier.fillMaxWidth().background(EinkTheme.colors.paper).blockTouches()) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkIconButton(EinkIcons.Back, contentDescription = "Cerrar libro", onClick = ::finish)
                Spacer(Modifier.width(12.dp))
                EinkText(viewModel.title, Modifier.weight(1f), style = EinkTheme.typography.label, maxLines = 2)
                Spacer(Modifier.width(12.dp))
                EinkIconButton(
                    EinkIcons.Eye,
                    contentDescription = if (finished) "Quitar de leídos" else "Marcar como leído",
                    onClick = viewModel::toggleFinished,
                    selected = finished,
                )
            }
            EinkDivider()
        }
    }

    /** Menú abajo: ajustes de vista si están abiertos, página, progreso y herramientas. */
    @Composable
    private fun Controls(pageCount: Int, modifier: Modifier) {
        Column(modifier.fillMaxWidth().blockTouches()) {
            if (viewModel.panel == PdfPanel.View) ViewSettings() else EinkDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(EinkTheme.colors.paper)
                    .padding(horizontal = EinkTheme.dimens.gutter)
                    .padding(top = 14.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EinkText(
                        buildAnnotatedString {
                            append("Página ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${viewModel.position.page + 1}") }
                            append(" de $pageCount")
                        },
                        Modifier.weight(1f),
                        style = EinkTheme.typography.label,
                    )
                    val zoom = viewModel.zoom
                    EinkText(
                        if (zoom > 1f) "Zoom ${formatZoom(zoom)} · ${(viewModel.progression * 100).roundToInt()} %"
                        else "${(viewModel.progression * 100).roundToInt()} %",
                        style = EinkTheme.typography.caption,
                        color = EinkTheme.colors.muted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                EinkProgressBar(viewModel.progression.toFloat(), Modifier.fillMaxWidth(), height = 6.dp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tool(EinkIcons.Page, "Ir a página") { viewModel.panel = PdfPanel.GoTo }
                    Tool(EinkIcons.View, "Vista", selected = viewModel.panel == PdfPanel.View) {
                        viewModel.panel = if (viewModel.panel == PdfPanel.View) PdfPanel.None else PdfPanel.View
                    }
                }
            }
        }
    }

    /** Herramienta del menú: icono y etiqueta en una baldosa redondeada. */
    @Composable
    private fun RowScope.Tool(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
        EinkSurface(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            corner = 16.dp,
            tone = if (selected) EinkTone.Ink else EinkTone.Surface,
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkIcon(icon, null, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                EinkText(label, style = EinkTheme.typography.label, maxLines = 1)
            }
        }
    }

    /** Hoja de ajustes de vista, sobre el menú. */
    @Composable
    private fun ViewSettings() {
        val settings = viewModel.settings
        Column(
            Modifier
                .fillMaxWidth()
                .einkBorder(1.dp, EinkTheme.colors.line, 28.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(EinkTheme.colors.paper)
                .padding(horizontal = EinkTheme.dimens.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EinkText("Vista", Modifier.weight(1f), style = EinkTheme.typography.heading)
                EinkIconButton(EinkIcons.Close, "Cerrar ajustes de vista", { viewModel.panel = PdfPanel.None })
            }
            Choice("Ajuste", PdfFit.entries.map { it.label to it }, settings.fit) {
                viewModel.updateSettings(settings.copy(fit = it))
            }
            Choice("Márgenes en blanco", listOf("Recortar" to true, "Mantener" to false), settings.crop) {
                viewModel.updateSettings(settings.copy(crop = it))
            }
        }
    }

    /** Opciones excluyentes en un control segmentado. */
    @Composable
    private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
        Column {
            EinkEyebrow(label)
            Spacer(Modifier.height(6.dp))
            EinkSegmented(
                options.map { it.first },
                selectedIndex = options.indexOfFirst { it.second == selected },
                onSelect = { onSelect(options[it].second) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    /** A pantalla completa, con teclado numérico propio: sin el del sistema. */
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
            EinkTopBar(title = "Ir a página", onBack = { viewModel.panel = PdfPanel.None }, closeIcon = true)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = EinkTheme.dimens.gutter)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                EinkText(
                    text.ifEmpty { "${viewModel.position.page + 1}" },
                    style = EinkTheme.typography.title.copy(fontSize = 56.sp),
                    color = if (text.isEmpty()) EinkTheme.colors.muted else EinkTheme.colors.ink,
                )
                EinkText(
                    if (text.isNotEmpty() && page == null) "Elige una página entre 1 y $pageCount" else "de $pageCount páginas",
                    style = EinkTheme.typography.body,
                    color = EinkTheme.colors.muted,
                )
                Spacer(Modifier.weight(1f))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", BACKSPACE, "0", GO)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                val modifier = Modifier.weight(1f)
                                when (key) {
                                    BACKSPACE -> Key(modifier, "Borrar", enabled = text.isNotEmpty(), onClick = { text = text.dropLast(1) }) {
                                        EinkIcon(EinkIcons.Backspace, null, size = 26.dp)
                                    }
                                    GO -> Key(modifier, "Ir", enabled = page != null, inverted = true, onClick = { go() }) {
                                        EinkText("Ir", style = EinkTheme.typography.heading, color = EinkTheme.colors.paper)
                                    }
                                    else -> Key(modifier, key, onClick = { if (text.length < 6) text = (text + key).trimStart('0') }) {
                                        EinkText(key, style = EinkTheme.typography.heading)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Key(
        modifier: Modifier,
        description: String,
        enabled: Boolean = true,
        inverted: Boolean = false,
        onClick: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        EinkSurface(
            modifier
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
                .alpha(if (enabled) 1f else 0.35f),
            corner = 18.dp,
            tone = if (inverted) EinkTone.Ink else EinkTone.Surface,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }

    /** «2×» o «1,5×». */
    private fun formatZoom(zoom: Float): String =
        if (zoom % 1f == 0f) "${zoom.toInt()}×" else "${zoom.toString().replace('.', ',')}×"

    /** Lo que se toque aquí sin pulsar un botón no llega a la página de debajo. */
    private fun Modifier.blockTouches() = pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
    }

    companion object {
        /** Ancho de cada borde que pasa página, como fracción de la pantalla. */
        private const val EDGE = 0.3f
        /** Distancia máxima, en dp, entre los dos toques de un doble toque. */
        private const val DOUBLE_TAP_SLOP = 48
        private const val BACKSPACE = "⌫"
        private const val GO = "ir"

        fun intent(context: Context, file: File, bookId: BookId, title: String): Intent =
            Intent(context, PdfReaderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(PdfReaderViewModel.EXTRA_PATH, file.absolutePath)
                .putExtra(PdfReaderViewModel.EXTRA_BOOK_ID, bookId.value)
                .putExtra(PdfReaderViewModel.EXTRA_TITLE, title)
    }
}
