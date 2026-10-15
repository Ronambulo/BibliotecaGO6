package com.bibliotecago.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.bibliotecago.core.eink.EinkIcon
import com.bibliotecago.core.eink.EinkIcons
import com.bibliotecago.core.eink.EinkSurface
import com.bibliotecago.core.eink.EinkText
import com.bibliotecago.core.eink.EinkTheme
import com.bibliotecago.core.model.Book
import com.bibliotecago.server.ServerUrls
import okhttp3.HttpUrl

/**
 * Portada del servidor. El marco toma la proporción real de la imagen y se
 * centra en el hueco disponible, así el borde siempre ciñe la portada; hasta
 * que carga (o si no hay portada) usa 2:3 y muestra el título. La clave de
 * caché es la ruta, no la URL: la misma portada sirve por la red local, por
 * el túnel y sin conexión.
 */
@Composable
fun BookCover(book: Book, base: HttpUrl?, modifier: Modifier = Modifier, downloaded: Boolean = false) {
    var ratio by remember(book.coverPath) { mutableStateOf<Float?>(null) }
    Box(modifier, contentAlignment = Alignment.Center) {
        EinkSurface(Modifier.aspectRatio(ratio ?: BOOK_RATIO), bordered = true) {
            if (ratio == null) {
                Column(
                    Modifier.align(Alignment.Center).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EinkText(book.title, style = EinkTheme.typography.caption, maxLines = 4, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    EinkText(book.formats.joinToString { it.name }, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted)
                }
            }
            val path = book.coverPath
            if (path != null && base != null) {
                val context = LocalContext.current
                val request = remember(path, base) {
                    ImageRequest.Builder(context)
                        .data(ServerUrls.resolve(base, path).toString())
                        .memoryCacheKey(path)
                        .diskCacheKey(path)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = "Portada de ${book.title}",
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val image = state.result.image
                        ratio = if (image.width > 0 && image.height > 0) {
                            // Acotada: una imagen rarísima no debe dejar una tira finísima.
                            (image.width.toFloat() / image.height).coerceIn(0.4f, 1.5f)
                        } else {
                            BOOK_RATIO
                        }
                    },
                )
            }
            if (downloaded) DownloadedBadge(Modifier.align(Alignment.BottomEnd))
        }
    }
}

/** Proporción típica de un libro, para el marco mientras no hay portada. */
private const val BOOK_RATIO = 2f / 3f

/**
 * Marca de «en el dispositivo»: bloque negro con icono y texto. No depende
 * del color ni de un gris sutil, se distingue igual en E Ink (RF-09).
 */
@Composable
fun DownloadedBadge(modifier: Modifier = Modifier, compact: Boolean = true) {
    EinkSurface(modifier.padding(4.dp), inverted = true) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            EinkIcon(EinkIcons.Check, contentDescription = if (compact) "En el dispositivo" else null, size = 16.dp)
            if (!compact) {
                Box(Modifier.width(4.dp))
                EinkText("En el dispositivo", style = EinkTheme.typography.caption)
            }
        }
    }
}
