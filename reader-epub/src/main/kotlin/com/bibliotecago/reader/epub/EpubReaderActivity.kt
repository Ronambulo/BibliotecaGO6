package com.bibliotecago.reader.epub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bibliotecago.core.eink.EinkEyebrow
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkProgressBar
import com.bibliotecago.core.eink.EinkSegmented
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkTone
import com.bibliotecago.core.eink.einkBorder
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bibliotecago.core.eink.EinkButton
import com.bibliotecago.core.eink.EinkDialog
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTextField
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.disableTransitions
import com.bibliotecago.core.eink.finishWithoutAnimation
import com.bibliotecago.core.eink.hideSystemBars
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.BookId
import com.bibliotecago.library.Bookmark
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import java.io.File
import kotlin.math.roundToInt

/**
 * Lector EPUB. El navegador de Readium es un Fragment; encima va un menú en
 * Compose, abajo, que aparece y desaparece con un toque en el centro. Los
 * toques en los bordes y los deslizamientos pasan página sin animación.
 */
@OptIn(ExperimentalReadiumApi::class)
class EpubReaderActivity : FragmentActivity() {

    private val viewModel: EpubReaderViewModel by viewModels()

    /**
     * Si Android mata el proceso, al volver recrea el navegador antes de que el
     * libro esté abierto. Readium da para eso un fragment de relleno que se
     * cambia por el de verdad en cuanto hay publicación.
     */
    private var placeholderNavigator = false

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = NavigatorFragmentFactory()
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is EpubNavigatorFragment && !placeholderNavigator) attach(fragment)
        }
        super.onCreate(savedInstanceState)
        disableTransitions()

        val root = FrameLayout(this)
        val pager = SwipeTurnLayout(this) { forward ->
            navigator()?.let { if (forward) it.goForward(animated = false) else it.goBackward(animated = false) }
        }
        // Readium apenas deja margen arriba y abajo en pantallas pequeñas: el
        // hueco lo pone el contenedor, y el de abajo aloja el folio.
        val pageMargin = (PAGE_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        pager.setPadding(0, pageMargin, 0, pageMargin)
        pager.addView(FragmentContainerView(this).apply { id = R.id.epub_navigator_container }, MATCH_PARENT, MATCH_PARENT)
        root.addView(pager, MATCH_PARENT, MATCH_PARENT)
        val folio = composeView { Folio() }
        // A todo el ancho y el texto alineado dentro: medida al ancho del
        // contenido se quedaba a la izquierda. Sin nada pulsable, los toques
        // en la franja siguen llegando al libro.
        root.addView(folio, FrameLayout.LayoutParams(MATCH_PARENT, pageMargin, Gravity.BOTTOM))
        // El menú ocupa toda la pantalla solo mientras se ve: arriba volver y
        // título, abajo las herramientas, y tocar en medio lo cierra. Oculto
        // queda en GONE y los toques van directos al navegador.
        val chrome = composeView { Chrome() }
        chrome.visibility = View.GONE
        root.addView(chrome, MATCH_PARENT, MATCH_PARENT)
        // A pantalla completa: apertura, error o índice.
        val overlay = composeView { Overlay() }
        root.addView(overlay, MATCH_PARENT, MATCH_PARENT)
        setContentView(root)
        hideSystemBars()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.state, snapshotFlow { viewModel.panel }, ::Pair).collect { (state, panel) ->
                        val ready = state is ReaderState.Ready
                        val fullScreen = panel == ReaderPanel.Contents || panel == ReaderPanel.Bookmarks || panel == ReaderPanel.Search
                        overlay.visibility = if (!ready || fullScreen) View.VISIBLE else View.GONE
                        if (ready) showNavigator()
                    }
                }
                launch {
                    snapshotFlow { viewModel.controlsVisible }.collect {
                        chrome.visibility = if (it) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    snapshotFlow { viewModel.settings }.collect {
                        navigator()?.submitPreferences(it.toPreferences())
                        // Los márgenes, del mismo color que la página de Readium.
                        pager.setBackgroundColor(if (it.dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.panel != ReaderPanel.None -> viewModel.panel = ReaderPanel.None
                viewModel.controlsVisible -> viewModel.controlsVisible = false
                else -> finishWithoutAnimation()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun composeView(content: @Composable () -> Unit) = ComposeView(this).apply {
        setContent { EinkTheme(content = content) }
    }

    /** El navegador de verdad, si ya está puesto. */
    private fun navigator(): EpubNavigatorFragment? =
        (supportFragmentManager.findFragmentById(R.id.epub_navigator_container) as? EpubNavigatorFragment)
            ?.takeUnless { placeholderNavigator }

    private fun showNavigator() {
        val current = supportFragmentManager.findFragmentById(R.id.epub_navigator_container)
        if (current != null && !placeholderNavigator) return
        placeholderNavigator = false
        supportFragmentManager.commitNow {
            replace(R.id.epub_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }
    }

    private fun attach(navigator: EpubNavigatorFragment) {
        // Bordes izquierdo y derecho (30 % del ancho) y teclas: página anterior y siguiente.
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = false))
        // Lo que no es borde es el centro: muestra u oculta el menú.
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                viewModel.controlsVisible = !viewModel.controlsVisible
                if (!viewModel.controlsVisible) viewModel.panel = ReaderPanel.None
                return true
            }
        })
        navigator.lifecycleScope.launch {
            navigator.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { navigator.currentLocator.collect(viewModel::onLocatorChanged) }
                // El resultado de búsqueda elegido, subrayado en negro.
                launch {
                    snapshotFlow { viewModel.searchSelected }.collect { locator ->
                        val decorations = listOfNotNull(
                            locator?.let { Decoration(SEARCH_DECORATION, it, Decoration.Style.Underline(android.graphics.Color.BLACK)) },
                        )
                        navigator.applyDecorations(decorations, SEARCH_DECORATION)
                    }
                }
            }
        }
    }

    private fun openLink(link: Link) {
        navigator()?.go(link, animated = false)
        closePanels()
    }

    private fun openLocator(locator: Locator) {
        navigator()?.go(locator, animated = false)
        closePanels()
    }

    private fun openSearchResult(locator: Locator) {
        viewModel.searchSelected = locator
        openLocator(locator)
    }

    private fun closePanels() {
        viewModel.panel = ReaderPanel.None
        viewModel.controlsVisible = false
    }

    private inner class NavigatorFragmentFactory : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            if (className != EpubNavigatorFragment::class.java.name) return super.instantiate(classLoader, className)
            val ready = viewModel.state.value as? ReaderState.Ready
            placeholderNavigator = ready == null
            val factory = if (ready == null) {
                EpubNavigatorFragment.createDummyFactory()
            } else {
                EpubNavigatorFactory(ready.publication).createFragmentFactory(
                    initialLocator = viewModel.locator ?: ready.savedLocator,
                    initialPreferences = viewModel.settings.toPreferences(),
                    listener = navigatorListener,
                )
            }
            return factory.instantiate(classLoader, className)
        }
    }

    private val navigatorListener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toString().toUri()))
            } catch (_: ActivityNotFoundException) {
                // Sin navegador instalado no hay dónde abrirlo.
            }
        }
    }

    /** Folio en el margen de abajo a la derecha: página de cuántas y porcentaje leído. */
    @Composable
    private fun Folio() {
        val state by viewModel.state.collectAsState()
        val total = (state as? ReaderState.Ready)?.positionCount ?: 0
        val locations = viewModel.locator?.locations ?: return
        val position = locations.position
        val text = listOfNotNull(
            if (position != null && total > 0) "$position/$total" else null,
            locations.totalProgression?.let { "${(it * 100).roundToInt()} %" },
        ).joinToString(" - ")
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
            EinkText(
                text,
                style = EinkTheme.typography.caption.copy(fontSize = 11.sp),
                color = if (viewModel.settings.dark) Color(0xFFBDBDBD) else EinkTheme.colors.muted,
                maxLines = 1,
            )
        }
    }

    /** Menú del lector: franja de arriba, hueco que cierra al tocarlo y herramientas abajo. */
    @Composable
    private fun Chrome() {
        if (!viewModel.controlsVisible) return
        Column(Modifier.fillMaxSize()) {
            TopStrip()
            Spacer(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {
                        viewModel.panel = ReaderPanel.None
                        viewModel.controlsVisible = false
                    },
            )
            Controls()
        }
    }

    /** Franja de arriba: volver, título y autor, leído y marcador de la página actual. */
    @Composable
    private fun TopStrip() {
        if (!viewModel.controlsVisible) return
        val state by viewModel.state.collectAsState()
        val bookmarks by viewModel.bookmarks.collectAsState()
        val finished by viewModel.finished.collectAsState()
        val author = (state as? ReaderState.Ready)?.publication?.metadata?.authors
            ?.joinToString { it.name }?.ifBlank { null }
        Column(Modifier.fillMaxWidth().background(EinkTheme.colors.paper)) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkIconButton(EinkIcons.Back, contentDescription = "Cerrar libro", onClick = ::finish)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    EinkText(viewModel.title, style = EinkTheme.typography.label, maxLines = 1)
                    author?.let {
                        EinkText(it, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(12.dp))
                EinkIconButton(
                    EinkIcons.Eye,
                    contentDescription = if (finished) "Quitar de leídos" else "Marcar como leído",
                    onClick = viewModel::toggleFinished,
                    selected = finished,
                )
                Spacer(Modifier.width(8.dp))
                val marked = viewModel.bookmarkHere(bookmarks) != null
                EinkIconButton(
                    if (marked) EinkIcons.BookmarkFilled else EinkIcons.Bookmark,
                    contentDescription = if (marked) "Quitar marcador" else "Añadir marcador",
                    onClick = viewModel::toggleBookmark,
                    enabled = viewModel.locator != null,
                    selected = marked,
                )
            }
            EinkDivider()
        }
    }

    /** Menú abajo, al alcance del pulgar: ajustes de texto si están abiertos, progreso y herramientas. */
    @Composable
    private fun Controls() {
        if (!viewModel.controlsVisible) return
        val state by viewModel.state.collectAsState()
        val ready = state as? ReaderState.Ready
        val total = ready?.positionCount ?: 0
        Column(Modifier.fillMaxWidth()) {
            if (viewModel.panel == ReaderPanel.Text) {
                TextSettings()
            } else {
                EinkDivider()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(EinkTheme.colors.paper)
                    .padding(horizontal = EinkTheme.dimens.gutter)
                    .padding(top = 14.dp, bottom = 12.dp),
            ) {
                val locator = viewModel.locator
                EinkText(locator?.title ?: viewModel.title, style = EinkTheme.typography.label, maxLines = 1)
                val position = locator?.locations?.position
                val progression = locator?.locations?.totalProgression
                EinkText(
                    listOfNotNull(
                        if (position != null && total > 0) "Pág. $position de $total" else null,
                        progression?.let { "${(it * 100).roundToInt()} %" },
                    ).joinToString(" · "),
                    style = EinkTheme.typography.caption,
                    color = EinkTheme.colors.muted,
                )
                Spacer(Modifier.height(10.dp))
                val chapter = ready?.let { currentChapter(it) } ?: -1
                val chapters = ready?.publication?.readingOrder.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EinkIconButton(
                        EinkIcons.ChevronLeft, "Capítulo anterior",
                        onClick = { chapters.getOrNull(chapter - 1)?.let { navigator()?.go(it, animated = false) } },
                        enabled = chapter > 0,
                        size = 40.dp,
                    )
                    EinkProgressBar(
                        (progression ?: 0.0).toFloat(),
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        height = 6.dp,
                    )
                    EinkIconButton(
                        EinkIcons.ChevronRight, "Capítulo siguiente",
                        onClick = { chapters.getOrNull(chapter + 1)?.let { navigator()?.go(it, animated = false) } },
                        enabled = chapter >= 0 && chapter < chapters.size - 1,
                        size = 40.dp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tool(EinkIcons.Toc, "Índice") { viewModel.panel = ReaderPanel.Contents }
                    Tool(EinkIcons.Search, "Buscar") { viewModel.panel = ReaderPanel.Search }
                    Tool(EinkIcons.Text, "Texto", selected = viewModel.panel == ReaderPanel.Text) {
                        viewModel.panel = if (viewModel.panel == ReaderPanel.Text) ReaderPanel.None else ReaderPanel.Text
                    }
                    Tool(EinkIcons.Bookmark, "Marcadores") { viewModel.panel = ReaderPanel.Bookmarks }
                }
            }
        }
    }

    /** Posición del archivo actual en el orden de lectura. */
    private fun currentChapter(ready: ReaderState.Ready): Int {
        val href = viewModel.locator?.href?.toString()?.substringBefore('#') ?: return -1
        return ready.publication.readingOrder.indexOfFirst { it.url().toString().substringBefore('#') == href }
    }

    /** Herramienta del menú: icono sobre etiqueta, en una baldosa redondeada. */
    @Composable
    private fun RowScope.Tool(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
        EinkSurface(
            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            corner = 16.dp,
            tone = if (selected) EinkTone.Ink else EinkTone.Surface,
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EinkIcon(icon, null, size = 22.dp)
                Spacer(Modifier.height(4.dp))
                EinkText(label, style = EinkTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }
    }

    /** Hoja de ajustes de texto, sobre el menú. */
    @Composable
    private fun TextSettings() {
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
                EinkText("Texto", Modifier.weight(1f), style = EinkTheme.typography.heading)
                EinkIconButton(EinkIcons.Close, "Cerrar ajustes de texto", { viewModel.panel = ReaderPanel.None })
            }
            TextPreview(settings)
            Column {
                EinkEyebrow("Tamaño de letra")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EinkIconButton(
                        EinkIcons.Minus, "Letra más pequeña", { viewModel.updateSettings(settings.smallerFont()) },
                        enabled = settings.fontScale > ReaderSettings.MIN_FONT,
                    )
                    EinkText(
                        "${settings.fontPercent} %",
                        Modifier.weight(1f),
                        style = EinkTheme.typography.heading,
                        textAlign = TextAlign.Center,
                    )
                    EinkIconButton(
                        EinkIcons.Plus, "Letra más grande", { viewModel.updateSettings(settings.largerFont()) },
                        enabled = settings.fontScale < ReaderSettings.MAX_FONT,
                    )
                }
            }
            Choice("Letra", ReaderFont.entries.map { it.label to it }, settings.font) {
                viewModel.updateSettings(settings.copy(font = it))
            }
            Choice("Interlineado", ReaderSettings.LINE_HEIGHTS, settings.lineHeight) {
                viewModel.updateSettings(settings.copy(lineHeight = it))
            }
            Choice("Márgenes", ReaderSettings.MARGINS, settings.margins) {
                viewModel.updateSettings(settings.copy(margins = it))
            }
            Choice("Tema", listOf("Claro" to false, "Oscuro" to true), settings.dark) {
                viewModel.updateSettings(settings.copy(dark = it))
            }
        }
    }

    /**
     * Muestra de cómo quedará el texto con los ajustes elegidos, porque la hoja
     * tapa casi toda la página. Alto fijo para que la hoja no salte al cambiar.
     */
    @Composable
    private fun TextPreview(settings: ReaderSettings) {
        val base = EinkTheme.typography.serif
        val size = PREVIEW_FONT * settings.fontScale.toFloat()
        val ink = if (settings.dark) EinkTheme.colors.paper else EinkTheme.colors.ink
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .einkBorder(1.dp, EinkTheme.colors.line, 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (settings.dark) EinkTheme.colors.ink else EinkTheme.colors.paper)
                .padding(horizontal = 24.dp * settings.margins.toFloat(), vertical = 12.dp),
        ) {
            EinkText(
                PREVIEW_TEXT,
                style = base.copy(
                    fontFamily = when (settings.font) {
                        ReaderFont.Original -> base.fontFamily
                        ReaderFont.Serif -> FontFamily.Serif
                        ReaderFont.Sans -> FontFamily.SansSerif
                    },
                    fontSize = size,
                    lineHeight = size * (settings.lineHeight ?: 1.4).toFloat(),
                ),
                color = ink,
            )
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

    @Composable
    private fun Overlay() {
        val state by viewModel.state.collectAsState()
        Box(Modifier.fillMaxSize().background(EinkTheme.colors.paper), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is ReaderState.Failed -> EinkMessage(
                    "No se puede leer",
                    message = s.message,
                    icon = EinkIcons.Warning,
                    actionLabel = "Volver",
                    onAction = ::finish,
                )
                is ReaderState.Ready -> when (viewModel.panel) {
                    ReaderPanel.Contents, ReaderPanel.Bookmarks -> Navigation(s)
                    ReaderPanel.Search -> SearchPanel()
                    else -> Unit
                }
                // Página en blanco hasta que se abre el libro: sin mensajes intermedios.
                ReaderState.Opening -> Unit
            }
        }
    }

    /** Índice y marcadores, a pantalla completa, con pestañas para pasar de uno a otro. */
    @Composable
    private fun Navigation(ready: ReaderState.Ready) {
        Column(Modifier.fillMaxSize()) {
            EinkTopBar(title = viewModel.title, onBack = { viewModel.panel = ReaderPanel.None }, closeIcon = true)
            val tabs = listOf(ReaderPanel.Contents, ReaderPanel.Bookmarks)
            EinkSegmented(
                listOf("Índice", "Marcadores"),
                selectedIndex = tabs.indexOf(viewModel.panel),
                onSelect = { viewModel.panel = tabs[it] },
                modifier = Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 8.dp),
            )
            if (viewModel.panel == ReaderPanel.Bookmarks) Bookmarks() else Contents(ready)
        }
    }

    /** Índice paginado, abierto en la página del capítulo actual. */
    @Composable
    private fun ColumnScope.Contents(ready: ReaderState.Ready) {
        val entries = remember(ready) { tocEntries(ready.publication.tableOfContents) }
        val current = remember(entries) { viewModel.locator?.let { currentEntry(entries, it) } ?: -1 }
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EinkMessage("Sin índice", message = "Este libro no trae tabla de contenidos.", icon = EinkIcons.Toc)
            }
        } else {
            val page = rememberEinkPageState(initialPage = maxOf(current, 0) / TOC_ROWS)
            EinkPagedGrid(
                items = entries.withIndex().toList(),
                columns = 1,
                rows = TOC_ROWS,
                state = page,
                modifier = Modifier.weight(1f),
            ) { (index, entry) ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (index == current) EinkTheme.colors.surface else EinkTheme.colors.paper)
                        .clickable { openLink(entry.link) }
                        .padding(start = 12.dp + 20.dp * entry.depth, end = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val title = entry.link.title?.ifBlank { null } ?: "Sin título"
                    EinkText(
                        title,
                        style = if (index == current) EinkTheme.typography.label else EinkTheme.typography.body,
                        maxLines = 2,
                    )
                }
            }
        }
    }

    /** Marcadores en orden de lectura: tocar uno lleva a su página; la equis lo borra, tras confirmar. */
    @Composable
    private fun ColumnScope.Bookmarks() {
        val bookmarks by viewModel.bookmarks.collectAsState()
        var deleting by remember { mutableStateOf<Bookmark?>(null) }
        if (bookmarks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EinkMessage(
                    "Sin marcadores",
                    message = "Toca el marcador de arriba para guardar la página actual.",
                    icon = EinkIcons.Bookmark,
                )
            }
        } else {
            val here = viewModel.bookmarkHere(bookmarks)
            val start = bookmarks.indexOf(here).coerceAtLeast(0)
            EinkPagedGrid(
                items = bookmarks,
                columns = 1,
                rows = BOOKMARK_ROWS,
                state = rememberEinkPageState(initialPage = start / BOOKMARK_ROWS),
                modifier = Modifier.weight(1f),
                key = { it.id },
            ) { bookmark ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { bookmark.toLocator()?.let(::openLocator) },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val title = bookmark.title?.ifBlank { null } ?: "Sin capítulo"
                        EinkText(
                            if (bookmark == here) "▸ $title" else title,
                            style = EinkTheme.typography.label,
                            maxLines = 1,
                        )
                        bookmark.text?.ifBlank { null }?.let {
                            EinkText("«$it»", style = EinkTheme.typography.caption, maxLines = 2)
                        }
                        EinkText(
                            place(bookmark.toLocator()?.locations?.position, bookmark.progression),
                            style = EinkTheme.typography.caption,
                            color = EinkTheme.colors.muted,
                        )
                    }
                    EinkIconButton(EinkIcons.Trash, contentDescription = "Borrar marcador", onClick = { deleting = bookmark })
                }
            }
        }
        deleting?.let { bookmark ->
            EinkDialog(
                onDismissRequest = { deleting = null },
                title = "¿Borrar el marcador?",
                confirmLabel = "Borrar",
                onConfirm = {
                    viewModel.deleteBookmark(bookmark)
                    deleting = null
                },
                text = bookmark.title,
                icon = EinkIcons.Trash,
            )
        }
    }

    /** «Página 12 · 34 %», con lo que se sepa. */
    private fun place(position: Int?, progression: Double?): String {
        val percent = progression?.let { "${(it * 100).roundToInt()} %" }
        return listOfNotNull(position?.let { "Página $it" }, percent).joinToString(" · ")
    }

    /** Buscador a pantalla completa: campo arriba, resultados paginados debajo. */
    @Composable
    private fun SearchPanel() {
        val keyboard = LocalSoftwareKeyboardController.current
        val search = viewModel.search
        val start = {
            keyboard?.hide()
            viewModel.search()
        }
        Column(Modifier.fillMaxSize()) {
            EinkTopBar(title = "Buscar en el libro", onBack = { viewModel.panel = ReaderPanel.None }, closeIcon = true)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EinkTheme.dimens.gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkTextField(
                    value = viewModel.searchInput,
                    onValueChange = { viewModel.searchInput = it },
                    label = null,
                    placeholder = "Palabra o frase",
                    modifier = Modifier.weight(1f),
                    leadingIcon = EinkIcons.Search,
                    pill = true,
                    onDone = start,
                )
                EinkButton("Buscar", start, primary = true, enabled = viewModel.searchInput.isNotBlank())
            }
            val status = if (search == null) {
                "Entre comillas busca el texto tal cual: con mayúsculas, acentos y palabras enteras."
            } else {
                val count = search.results.size
                val found = when {
                    search.loading -> "Buscando… $count encontrados"
                    search.done -> if (count == 1) "1 resultado" else "$count resultados"
                    else -> "$count resultados o más"
                }
                if (search.query.literal) "$found · literal" else found
            }
            EinkText(
                status,
                Modifier.padding(horizontal = EinkTheme.dimens.gutter, vertical = 4.dp),
                style = EinkTheme.typography.caption,
                color = EinkTheme.colors.muted,
            )
            when {
                search == null -> Unit
                search.results.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Mientras busca, la lista vacía; el estado ya va en la línea de arriba.
                    if (!search.loading) {
                        EinkMessage("Sin resultados", message = search.error ?: "«${search.query.text}» no aparece en este libro.")
                    }
                }
                else -> key(search.query) {
                    EinkPagedGrid(
                        items = search.results,
                        columns = 1,
                        rows = SEARCH_ROWS,
                        state = rememberEinkPageState(initialPage = 0),
                        modifier = Modifier.weight(1f),
                        // Se pide más al acabar cada tanda: así la petición no se pierde mientras carga.
                        hasMore = !search.done && !search.loading,
                        onLoadMore = viewModel::loadMoreResults,
                    ) { locator ->
                        Column(
                            Modifier.fillMaxSize().clickable { openSearchResult(locator) },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EinkText(snippet(locator), style = EinkTheme.typography.body, maxLines = 3)
                            EinkText(
                                listOfNotNull(
                                    locator.title?.ifBlank { null },
                                    place(locator.locations.position, locator.locations.totalProgression).ifEmpty { null },
                                ).joinToString(" · "),
                                style = EinkTheme.typography.caption,
                                color = EinkTheme.colors.muted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Lo que rodea al resultado, con la coincidencia en negrita. */
    private fun snippet(locator: Locator): AnnotatedString {
        fun clean(text: String?) = text.orEmpty().replace(WHITESPACE, " ")
        val before = clean(locator.text.before).takeLast(SNIPPET_CONTEXT).trimStart()
        val after = clean(locator.text.after).take(SNIPPET_CONTEXT * 2).trimEnd()
        return buildAnnotatedString {
            append("…")
            append(before)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(clean(locator.text.highlight)) }
            append(after)
            append("…")
        }
    }

    private class TocEntry(val link: Link, val depth: Int)

    private fun tocEntries(links: List<Link>, depth: Int = 0): List<TocEntry> =
        links.flatMap { listOf(TocEntry(it, depth)) + tocEntries(it.children, depth + 1) }

    /** Última entrada del índice que apunta al mismo archivo que la posición actual. */
    private fun currentEntry(entries: List<TocEntry>, locator: Locator): Int {
        val href = locator.href.toString().substringBefore('#')
        return entries.indexOfLast { it.link.url().toString().substringBefore('#') == href }
    }

    companion object {
        private const val NAVIGATOR_TAG = "navigator"
        /** Margen de arriba y de abajo de la página; abajo también va el folio. */
        private const val PAGE_MARGIN_DP = 32
        private const val TOC_ROWS = 10
        private const val BOOKMARK_ROWS = 6
        private const val SEARCH_ROWS = 6
        private const val SNIPPET_CONTEXT = 50
        private const val SEARCH_DECORATION = "search"
        private val PREVIEW_FONT = 18.sp
        private const val PREVIEW_TEXT = "Era una tarde tranquila de otoño. Abrió el libro por la primera página y, sin darse cuenta, las horas pasaron mientras la luz se apagaba en la ventana."
        private val WHITESPACE = Regex("\\s+")

        fun intent(context: Context, file: File, bookId: BookId, title: String): Intent =
            Intent(context, EpubReaderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EpubReaderViewModel.EXTRA_PATH, file.absolutePath)
                .putExtra(EpubReaderViewModel.EXTRA_BOOK_ID, bookId.value)
                .putExtra(EpubReaderViewModel.EXTRA_TITLE, title)
    }
}
