package com.bibliotecago.core.eink

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta monocroma. Ningún estado depende del color (apartado 4): los grises
 * rellenan superficies y texto secundario, nunca son la única señal de un
 * estado. Lo seleccionado va siempre en negro.
 */
@Immutable
data class EinkColors(
    val ink: Color = Color.Black,
    val paper: Color = Color.White,
    /** Texto secundario. */
    val muted: Color = Color(0xFF555555),
    /** Relleno de tarjetas, campos y botones redondos. */
    val surface: Color = Color(0xFFEFEFEF),
    /** Pistas de barras de progreso e interruptores; portadas sin imagen. */
    val surfaceStrong: Color = Color(0xFFDCDCDC),
    /** Líneas finas de separación. */
    val line: Color = Color(0xFFC4C4C4),
)

private val Sans = FontFamily.SansSerif

@Immutable
data class EinkTypography(
    /** Título grande de pantalla y de ficha. */
    val title: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.5).sp),
    /** Título de cabecera con botón de volver, diálogos y hojas. */
    val heading: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    /** Título de sección («También a medias»). */
    val section: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp),
    val body: TextStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp),
    /** Botones, títulos de fila y valores destacados. */
    val label: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 19.sp),
    /** Antetítulo en mayúsculas («CONTINUAR LEYENDO»). El texto se pasa ya en mayúsculas. */
    val eyebrow: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    /** Texto de lectura y títulos dentro de portadas. */
    val serif: TextStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 24.sp),
)

/** Medidas pensadas para el dedo en una pantalla de 6". */
@Immutable
data class EinkDimens(
    /** Alto de botones y campos. */
    val touchTarget: Dp = 52.dp,
    /** Botones redondos de icono. */
    val iconButton: Dp = 44.dp,
    val border: Dp = 2.dp,
    /** Campos de texto y portadas. */
    val corner: Dp = 14.dp,
    /** Tarjetas. */
    val cardCorner: Dp = 22.dp,
    /** Hojas inferiores y diálogos. */
    val sheetCorner: Dp = 28.dp,
    val gutter: Dp = 20.dp,
    val spacing: Dp = 12.dp,
)

internal val LocalEinkColors = staticCompositionLocalOf { EinkColors() }
internal val LocalEinkTypography = staticCompositionLocalOf { EinkTypography() }
internal val LocalEinkDimens = staticCompositionLocalOf { EinkDimens() }

/** Color del contenido (texto e iconos) en el contexto actual: negro sobre papel o papel sobre negro. */
val LocalEinkContentColor = staticCompositionLocalOf { Color.Black }

object EinkTheme {
    val colors: EinkColors
        @Composable @ReadOnlyComposable get() = LocalEinkColors.current
    val typography: EinkTypography
        @Composable @ReadOnlyComposable get() = LocalEinkTypography.current
    val dimens: EinkDimens
        @Composable @ReadOnlyComposable get() = LocalEinkDimens.current
}

/**
 * Raíz de la interfaz. Toma las decisiones globales del apartado 5.4:
 * - sin ripple: la respuesta táctil es un marco fino instantáneo ([PressIndication]);
 * - sin efecto de rebote al llegar al final de un contenedor desplazable;
 * - una capa propia para diálogos y menús, sin las animaciones de ventana del sistema.
 */
@Composable
fun EinkTheme(
    colors: EinkColors = EinkColors(),
    typography: EinkTypography = EinkTypography(),
    dimens: EinkDimens = EinkDimens(),
    content: @Composable () -> Unit,
) {
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.ink, backgroundColor = Color(0xFFBDBDBD))
    }
    CompositionLocalProvider(
        LocalEinkColors provides colors,
        LocalEinkTypography provides typography,
        LocalEinkDimens provides dimens,
        LocalEinkContentColor provides colors.ink,
        LocalIndication provides PressIndication,
        LocalOverscrollFactory provides null,
        LocalTextSelectionColors provides selection,
    ) {
        EinkOverlayHost(content)
    }
}
