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

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

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
    size: Dp = 26.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(LocalEinkContentColor.current),
    )
}
