package com.bibliotecago.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bibliotecago.core.eink.EinkTone
import com.bibliotecago.core.eink.einkBorder
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
 * Portada del servidor con esquinas redondeadas. El marco toma la proporción
 * real de la imagen y se coloca en el hueco según [alignment], así el borde
 * siempre ciñe la portada; hasta que carga (o si no hay portada) usa 2:3 y
 * muestra autor y título en serif sobre gris. La clave de caché es la ruta,
 * no la URL: la misma portada sirve por la red local, por el túnel y sin
 * conexión.
 */
@Composable
fun BookCover(
    book: Book,
    base: HttpUrl?,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    alignment: Alignment = Alignment.Center,
    corner: Dp = 10.dp,
    compact: Boolean = false,
) {
    var ratio by remember(book.coverPath) { mutableStateOf<Float?>(null) }
    val colors = EinkTheme.colors
    Box(modifier, contentAlignment = alignment) {
        Box(
            Modifier
                .aspectRatio(ratio ?: BOOK_RATIO)
                .clip(RoundedCornerShape(corner))
                .background(if (ratio == null) colors.surfaceStrong else colors.paper)
        ) {
            if (ratio == null && !compact) {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    EinkText(
                        book.authors.firstOrNull().orEmpty().uppercase(),
                        style = EinkTheme.typography.eyebrow.merge(fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.6.sp),
                        maxLines = 2,
                    )
                    EinkText(
                        book.title,
                        style = EinkTheme.typography.serif.merge(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
                        maxLines = 5,
                    )
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
            // Línea fina para que una portada blanca no se funda con el papel.
            Box(Modifier.matchParentSize().einkBorder(1.dp, colors.line, corner))
            if (downloaded) DownloadedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp), small = compact)
        }
    }
}

/** Proporción típica de un libro, para el marco mientras no hay portada. */
private const val BOOK_RATIO = 2f / 3f

/**
 * Marca de «en el dispositivo»: círculo negro con una marca blanca y borde
 * blanco, que se distingue igual sobre una portada clara u oscura (RF-09).
 */
@Composable
fun DownloadedBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    val size = if (small) 20.dp else 28.dp
    EinkSurface(
        modifier
            .size(size)
            .border(2.dp, EinkTheme.colors.paper, CircleShape),
        tone = EinkTone.Ink,
        corner = size / 2,
    ) {
        EinkIcon(EinkIcons.Check, contentDescription = "En el dispositivo", Modifier.align(Alignment.Center), size = size * 0.55f)
    }
}

/** Píldora negra con «En el dispositivo», para filas y la ficha. */
@Composable
fun DownloadedTag(modifier: Modifier = Modifier) {
    EinkSurface(modifier, tone = EinkTone.Ink, corner = 14.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            EinkIcon(EinkIcons.Check, contentDescription = null, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            EinkText("En el dispositivo", style = EinkTheme.typography.eyebrow.merge(letterSpacing = 0.sp), maxLines = 1)
        }
    }
}

/**
 * Celda de cuadrícula: portada apoyada abajo, y debajo título y autor en una
 * línea cada uno.
 */
@Composable
fun BookTile(book: Book, base: HttpUrl?, downloaded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        BookCover(
            book, base,
            Modifier.weight(1f).fillMaxWidth(),
            downloaded = downloaded,
            alignment = Alignment.BottomCenter,
        )
        Spacer(Modifier.height(8.dp))
        EinkText(book.title, style = EinkTheme.typography.label.merge(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 1)
        EinkText(
            book.authors.firstOrNull() ?: "Autor desconocido",
            style = EinkTheme.typography.caption.merge(fontSize = 13.sp, lineHeight = 17.sp),
            color = EinkTheme.colors.muted,
            maxLines = 1,
        )
    }
}
