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
 * Paleta monocroma. Ningún estado depende del color (apartado 4): el gris
 * solo se usa para texto secundario, nunca como única señal de un estado.
 */
@Immutable
data class EinkColors(
    val ink: Color = Color.Black,
    val paper: Color = Color.White,
    val muted: Color = Color(0xFF4D4D4D),
)

@Immutable
data class EinkTypography(
    val title: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    val heading: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    val body: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 24.sp),
    val label: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 18.sp),
)

/** Medidas pensadas para el dedo en una pantalla de 6". */
@Immutable
data class EinkDimens(
    val touchTarget: Dp = 56.dp,
    val border: Dp = 2.dp,
    val corner: Dp = 6.dp,
    val gutter: Dp = 16.dp,
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
 * - sin ripple: la respuesta táctil es una inversión instantánea ([InvertIndication]);
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
        LocalIndication provides InvertIndication,
        LocalOverscrollFactory provides null,
        LocalTextSelectionColors provides selection,
    ) {
        EinkOverlayHost(content)
    }
}
