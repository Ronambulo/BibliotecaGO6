package com.bibliotecago.core.eink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Iconos de trazo simple dibujados a mano (24×24). Se evita la librería de
 * iconos de Material para no arrastrar dependencias de Material.
 */
object EinkIcons {
    val Home = icon("Home") {
        moveTo(3f, 11f); lineTo(12f, 3.5f); lineTo(21f, 11f)
        moveTo(5.5f, 9.5f); lineTo(5.5f, 20.5f); lineTo(18.5f, 20.5f); lineTo(18.5f, 9.5f)
        moveTo(10f, 20.5f); lineTo(10f, 14.5f); lineTo(14f, 14.5f); lineTo(14f, 20.5f)
    }
    val Library = icon("Library") {
        rect(3.5f, 4f, 7.5f, 20f)
        rect(9.5f, 4f, 13.5f, 20f)
        moveTo(15.5f, 5.5f); lineTo(19f, 4.5f); lineTo(21.5f, 19f); lineTo(18f, 20f); close()
    }
    val Download = icon("Download") {
        moveTo(12f, 3.5f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 20f); lineTo(20f, 20f)
    }
    val Settings = icon("Settings") {
        moveTo(3.5f, 6f); lineTo(20.5f, 6f)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(3.5f, 18f); lineTo(20.5f, 18f)
        rect(7f, 4f, 10f, 8f)
        rect(14f, 10f, 17f, 14f)
        rect(9f, 16f, 12f, 20f)
    }
    val Search = icon("Search") {
        moveTo(16.5f, 10f)
        arcTo(6.5f, 6.5f, 0f, true, true, 3.5f, 10f)
        arcTo(6.5f, 6.5f, 0f, true, true, 16.5f, 10f)
        moveTo(14.8f, 14.8f); lineTo(20.5f, 20.5f)
    }
    /** Carpeta: explorar por autor, serie o etiqueta. */
    val Folder = icon("Folder") {
        moveTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8f); lineTo(20.5f, 8f)
        lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
    }
    /** Tres líneas de largo decreciente: ordenar. */
    val Sort = icon("Sort") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(15f, 12f)
        moveTo(4f, 18f); lineTo(10f, 18f)
    }
    val Back = icon("Back") {
        moveTo(20f, 12f); lineTo(4.5f, 12f)
        moveTo(10.5f, 6f); lineTo(4.5f, 12f); lineTo(10.5f, 18f)
    }
    val ChevronLeft = icon("ChevronLeft") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }
    val ChevronRight = icon("ChevronRight") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    }
    val Close = icon("Close") {
        moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f)
        moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f)
    }
    val Check = icon("Check") {
        moveTo(4f, 12.5f); lineTo(9.5f, 18f); lineTo(20f, 6.5f)
    }
    val Grid = icon("Grid") {
        rect(4f, 4f, 10.5f, 10.5f)
        rect(13.5f, 4f, 20f, 10.5f)
        rect(4f, 13.5f, 10.5f, 20f)
        rect(13.5f, 13.5f, 20f, 20f)
    }
    val List = icon("List") {
        moveTo(8.5f, 6f); lineTo(20.5f, 6f)
        moveTo(8.5f, 12f); lineTo(20.5f, 12f)
        moveTo(8.5f, 18f); lineTo(20.5f, 18f)
        rect(3.5f, 5f, 5.5f, 7f)
        rect(3.5f, 11f, 5.5f, 13f)
        rect(3.5f, 17f, 5.5f, 19f)
    }
    /** Cinta de marcapáginas. */
    val Bookmark = icon("Bookmark") { bookmark() }
    /** Marcador relleno: la página ya está marcada. */
    val BookmarkFilled = icon("BookmarkFilled", filled = true) { bookmark() }

    val ChevronDown = icon("ChevronDown") {
        moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
    }
    val Plus = icon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val Minus = icon("Minus") {
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val Trash = icon("Trash") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
        moveTo(10.5f, 11f); lineTo(10.5f, 16f)
        moveTo(13.5f, 11f); lineTo(13.5f, 16f)
    }
    /** Libro abierto: leer. */
    val Book = icon("Book") {
        moveTo(2.5f, 5f)
        curveTo(5.5f, 3.5f, 9.5f, 3.5f, 12f, 6f)
        curveTo(14.5f, 3.5f, 18.5f, 3.5f, 21.5f, 5f)
        lineTo(21.5f, 19f)
        curveTo(18.5f, 17.5f, 14.5f, 17.5f, 12f, 20f)
        curveTo(9.5f, 17.5f, 5.5f, 17.5f, 2.5f, 19f)
        close()
        moveTo(12f, 6f); lineTo(12f, 20f)
    }
    /** Índice: líneas con sangría. */
    val Toc = icon("Toc") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(8f, 10f); lineTo(20f, 10f)
        moveTo(8f, 14f); lineTo(20f, 14f)
        moveTo(4f, 18f); lineTo(20f, 18f)
    }
    /** «Aa»: ajustes de texto. */
    val Text = icon("Text") {
        moveTo(2.5f, 19f); lineTo(8f, 5f); lineTo(13.5f, 19f)
        moveTo(4.6f, 14f); lineTo(11.4f, 14f)
        moveTo(21.5f, 12f); lineTo(21.5f, 19f)
        moveTo(21.5f, 15.5f)
        arcTo(3f, 3.5f, 0f, true, true, 15.5f, 15.5f)
        arcTo(3f, 3.5f, 0f, true, true, 21.5f, 15.5f)
    }
    /** Hoja con número: ir a página. */
    val Page = icon("Page") {
        moveTo(6f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(6f, 21f); close()
        moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        moveTo(9f, 13f); lineTo(16f, 13f)
        moveTo(9f, 17f); lineTo(14f, 17f)
    }
    /** Marco con esquinas: ajuste de la vista. */
    val View = icon("View") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }
    val Backspace = icon("Backspace") {
        moveTo(9f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(9f, 19f); lineTo(2.5f, 12f); close()
        moveTo(12f, 9f); lineTo(17f, 15f)
        moveTo(17f, 9f); lineTo(12f, 15f)
    }
    val Server = icon("Server") {
        rect(3.5f, 4f, 20.5f, 10.5f)
        rect(3.5f, 13.5f, 20.5f, 20f)
        moveTo(7f, 7.25f); lineTo(8f, 7.25f)
        moveTo(7f, 16.75f); lineTo(8f, 16.75f)
    }
    val Info = icon("Info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.5f); lineTo(12f, 7.6f)
    }
    val User = icon("User") {
        circle(12f, 8f, 4f)
        moveTo(4f, 21f)
        curveTo(4f, 16.5f, 7.5f, 14f, 12f, 14f)
        curveTo(16.5f, 14f, 20f, 16.5f, 20f, 21f)
    }
    val Tag = icon("Tag") {
        moveTo(3.5f, 3.5f); lineTo(11.5f, 3.5f); lineTo(20.5f, 12.5f); lineTo(12.5f, 20.5f); lineTo(3.5f, 11.5f); close()
        moveTo(8f, 8f); lineTo(8f, 8.1f)
    }
    /** Libros apilados: serie. */
    val Series = icon("Series") {
        rect(4f, 15f, 20f, 20f)
        rect(5.5f, 10f, 18.5f, 15f)
        rect(7f, 5f, 17f, 10f)
    }
    val Refresh = icon("Refresh") {
        moveTo(19.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, true, true, 17f, 6.4f)
        moveTo(17.5f, 2.5f); lineTo(17.5f, 6.8f); lineTo(13.2f, 6.8f)
    }
    val Warning = icon("Warning") {
        moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
        moveTo(12f, 10f); lineTo(12f, 14f)
        moveTo(12f, 17f); lineTo(12f, 17.1f)
    }

    private fun PathBuilder.bookmark() {
        moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f); lineTo(17.5f, 20.5f); lineTo(12f, 16f); lineTo(6.5f, 20.5f); close()
    }

    private fun icon(name: String, filled: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
    }

    private fun PathBuilder.rect(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top); lineTo(right, top); lineTo(right, bottom); lineTo(left, bottom); close()
    }
}

/** Icono teñido con el color de contenido actual (negro sobre papel o papel sobre negro). */
@Composable
fun EinkIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(LocalEinkContentColor.current),
    )
}
