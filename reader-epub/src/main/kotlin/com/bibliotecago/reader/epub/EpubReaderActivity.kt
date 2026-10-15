package com.bibliotecago.reader.epub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.bibliotecago.core.eink.EinkDivider
import com.bibliotecago.core.eink.EinkIconButton
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkMessage
import com.bibliotecago.core.eink.EinkPagedGrid
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.eink.EinkTopBar
import com.bibliotecago.core.eink.rememberEinkPageState
import com.bibliotecago.core.model.BookId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

        val root = FrameLayout(this)
        val pager = SwipeTurnLayout(this) { forward ->
            navigator()?.let { if (forward) it.goForward(animated = false) else it.goBackward(animated = false) }
        }
        pager.addView(FragmentContainerView(this).apply { id = R.id.epub_navigator_container }, MATCH_PARENT, MATCH_PARENT)
        root.addView(pager, MATCH_PARENT, MATCH_PARENT)
        // El menú es una vista aparte, solo tan alta como su contenido: el resto
        // de la pantalla sigue llevando los toques al navegador.
        root.addView(composeView { Controls() }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        // A pantalla completa: apertura, error o índice.
        val overlay = composeView { Overlay() }
        root.addView(overlay, MATCH_PARENT, MATCH_PARENT)
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.state, snapshotFlow { viewModel.panel }, ::Pair).collect { (state, panel) ->
                        val ready = state is ReaderState.Ready
                        overlay.visibility = if (!ready || panel == ReaderPanel.Contents) View.VISIBLE else View.GONE
                        if (ready) showNavigator()
                    }
                }
                launch {
                    snapshotFlow { viewModel.settings }.collect { navigator()?.submitPreferences(it.toPreferences()) }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.panel != ReaderPanel.None -> viewModel.panel = ReaderPanel.None
                viewModel.controlsVisible -> viewModel.controlsVisible = false
                else -> finish()
            }
        }
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
                navigator.currentLocator.collect(viewModel::onLocatorChanged)
            }
        }
    }

    private fun openLink(link: Link) {
        navigator()?.go(link, animated = false)
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

    /** Menú abajo, al alcance del pulgar: ajustes de texto si están abiertos, progreso y botones. */
    @Composable
    private fun Controls() {
        if (!viewModel.controlsVisible) return
        val state by viewModel.state.collectAsState()
        val total = (state as? ReaderState.Ready)?.positionCount ?: 0
        Column(Modifier.fillMaxWidth().background(EinkTheme.colors.paper)) {
            EinkDivider(thickness = EinkTheme.dimens.border)
            if (viewModel.panel == ReaderPanel.Text) {
                TextSettings()
                EinkDivider()
            }
            viewModel.locator?.let { locator ->
                Column(Modifier.padding(horizontal = EinkTheme.dimens.gutter, vertical = 12.dp)) {
                    locator.title?.let { EinkText(it, style = EinkTheme.typography.label, maxLines = 1) }
                    Row(Modifier.fillMaxWidth()) {
                        val position = locator.locations.position
                        EinkText(
                            if (position != null && total > 0) "Página $position de $total" else "",
                            Modifier.weight(1f),
                            style = EinkTheme.typography.caption,
                        )
                        locator.locations.totalProgression?.let {
                            EinkText("${(it * 100).roundToInt()} %", style = EinkTheme.typography.caption)
                        }
                    }
                }
                EinkDivider()
            }
            Row(
                Modifier.fillMaxWidth().height(EinkTheme.dimens.touchTarget + 8.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkIconButton(EinkIcons.Back, contentDescription = "Cerrar libro", onClick = ::finish)
                Spacer(Modifier.width(4.dp))
                EinkText(viewModel.title, Modifier.weight(1f), style = EinkTheme.typography.heading, maxLines = 1)
                EinkIconButton(EinkIcons.List, contentDescription = "Índice", onClick = { viewModel.panel = ReaderPanel.Contents })
                EinkIconButton(
                    EinkIcons.Settings,
                    contentDescription = "Texto",
                    onClick = {
                        viewModel.panel = if (viewModel.panel == ReaderPanel.Text) ReaderPanel.None else ReaderPanel.Text
                    },
                    selected = viewModel.panel == ReaderPanel.Text,
                )
            }
        }
    }

    @Composable
    private fun TextSettings() {
        val settings = viewModel.settings
        Column(
            Modifier.padding(EinkTheme.dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(EinkTheme.dimens.spacing),
        ) {
            Column {
                EinkText("Tamaño de letra", style = EinkTheme.typography.caption)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EinkButton(
                        "A−", { viewModel.updateSettings(settings.smallerFont()) }, Modifier.weight(1f),
                        enabled = settings.fontScale > ReaderSettings.MIN_FONT,
                    )
                    EinkText(
                        "${settings.fontPercent} %",
                        Modifier.weight(1f),
                        style = EinkTheme.typography.label,
                        textAlign = TextAlign.Center,
                    )
                    EinkButton(
                        "A+", { viewModel.updateSettings(settings.largerFont()) }, Modifier.weight(1f),
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

    @Composable
    private fun Overlay() {
        val state by viewModel.state.collectAsState()
        Box(Modifier.fillMaxSize().background(EinkTheme.colors.paper), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is ReaderState.Failed -> EinkMessage(
                    "No se puede leer",
                    message = s.message,
                    actionLabel = "Volver",
                    onAction = ::finish,
                )
                is ReaderState.Ready -> if (viewModel.panel == ReaderPanel.Contents) Contents(s)
                ReaderState.Opening -> EinkMessage("Abriendo libro…")
            }
        }
    }

    /** Índice paginado, abierto en la página del capítulo actual. */
    @Composable
    private fun Contents(ready: ReaderState.Ready) {
        val entries = remember(ready) { tocEntries(ready.publication.tableOfContents) }
        val current = remember(entries) { viewModel.locator?.let { currentEntry(entries, it) } ?: -1 }
        Column(Modifier.fillMaxSize()) {
            EinkTopBar(title = "Índice", onBack = { viewModel.panel = ReaderPanel.None })
            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EinkMessage("Sin índice", message = "Este libro no trae tabla de contenidos.")
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
                            .clickable { openLink(entry.link) }
                            .padding(start = 20.dp * entry.depth),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val title = entry.link.title?.ifBlank { null } ?: "Sin título"
                        EinkText(
                            if (index == current) "▸ $title" else title,
                            style = if (index == current) EinkTheme.typography.label else EinkTheme.typography.body,
                            maxLines = 2,
                        )
                    }
                }
            }
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
        private const val TOC_ROWS = 10

        fun intent(context: Context, file: File, bookId: BookId, title: String): Intent =
            Intent(context, EpubReaderActivity::class.java)
                .putExtra(EpubReaderViewModel.EXTRA_PATH, file.absolutePath)
                .putExtra(EpubReaderViewModel.EXTRA_BOOK_ID, bookId.value)
                .putExtra(EpubReaderViewModel.EXTRA_TITLE, title)
    }
}
