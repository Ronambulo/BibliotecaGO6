package com.bibliotecago.core.eink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Básicos
// ---------------------------------------------------------------------------

@Composable
fun EinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = EinkTheme.typography.body,
    color: Color = LocalEinkContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Igual que el anterior, con partes en otro estilo (por ejemplo, en negrita). */
@Composable
fun EinkText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = EinkTheme.typography.body,
    color: Color = LocalEinkContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Antetítulo en mayúsculas y gris («CONTINUAR LEYENDO», «12 RESULTADOS»). */
@Composable
fun EinkEyebrow(text: String, modifier: Modifier = Modifier) {
    EinkText(text.uppercase(), modifier, style = EinkTheme.typography.eyebrow, color = EinkTheme.colors.muted, maxLines = 1)
}

/**
 * Borde nítido. Discontinuo = desactivado: así el estado no depende del gris.
 */
fun Modifier.einkBorder(width: Dp, color: Color, corner: Dp, dashed: Boolean = false): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((corner.toPx() - stroke / 2).coerceAtLeast(0f)),
            style = Stroke(
                width = stroke,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
            ),
        )
    }

/** Tono de una superficie: papel, gris claro o negro con contenido blanco. */
enum class EinkTone { Paper, Surface, Ink }

/** Superficie con fondo propio. [inverted] equivale a [EinkTone.Ink]. */
@Composable
fun EinkSurface(
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    bordered: Boolean = false,
    corner: Dp = EinkTheme.dimens.corner,
    tone: EinkTone = if (inverted) EinkTone.Ink else EinkTone.Paper,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = EinkTheme.colors
    val background = when (tone) {
        EinkTone.Paper -> colors.paper
        EinkTone.Surface -> colors.surface
        EinkTone.Ink -> colors.ink
    }
    val foreground = if (tone == EinkTone.Ink) colors.paper else colors.ink
    val shape = RoundedCornerShape(corner)
    CompositionLocalProvider(LocalEinkContentColor provides foreground) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(background, shape)
                .then(if (bordered) Modifier.einkBorder(EinkTheme.dimens.border, colors.ink, corner) else Modifier),
            content = content,
        )
    }
}

/** Línea fina gris. */
@Composable
fun EinkDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = EinkTheme.colors.line) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

/** Barra de progreso redondeada: negro sobre una pista gris. */
@Composable
fun EinkProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = EinkTheme.colors.surfaceStrong,
) {
    val colors = EinkTheme.colors
    val shape = CircleShape
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor, shape)
    ) {
        val value = fraction.coerceIn(0f, 1f)
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(colors.ink, shape)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Botones
// ---------------------------------------------------------------------------

/**
 * Variantes de botón, todas en forma de píldora:
 * - [Primary]: relleno negro, la acción principal de la pantalla.
 * - [Secondary]: borde negro de 2 dp.
 * - [Tonal]: relleno gris claro, acciones de apoyo dentro de tarjetas.
 * - [Text]: solo texto subrayado.
 */
enum class EinkButtonStyle { Primary, Secondary, Tonal, Text }

/**
 * Botón en forma de píldora. Desactivado: borde discontinuo y texto
 * atenuado, para que el estado no dependa solo del gris.
 */
@Composable
fun EinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    style: EinkButtonStyle = if (primary) EinkButtonStyle.Primary else EinkButtonStyle.Secondary,
    height: Dp = EinkTheme.dimens.touchTarget,
) {
    val colors = EinkTheme.colors
    val shape = CircleShape
    val filled = style == EinkButtonStyle.Primary && enabled
    val background = when {
        filled -> colors.ink
        style == EinkButtonStyle.Tonal && enabled -> colors.surface
        else -> Color.Transparent
    }
    val border = when {
        !enabled && style != EinkButtonStyle.Text -> Modifier.einkBorder(EinkTheme.dimens.border, colors.muted, height / 2, dashed = true)
        style == EinkButtonStyle.Secondary -> Modifier.einkBorder(EinkTheme.dimens.border, colors.ink, height / 2)
        else -> Modifier
    }
    CompositionLocalProvider(
        LocalEinkContentColor provides when {
            filled -> colors.paper
            enabled -> colors.ink
            else -> colors.muted
        }
    ) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = height, minWidth = height)
                .clip(shape)
                .background(background, shape)
                .then(border)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = if (style == EinkButtonStyle.Text) 12.dp else 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                EinkIcon(icon, contentDescription = null, size = 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            EinkText(
                text,
                style = EinkTheme.typography.label.merge(
                    textDecoration = if (style == EinkButtonStyle.Text) TextDecoration.Underline else null,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Botón redondo de icono. Por defecto va sobre gris claro; [selected] lo
 * pinta en negro y [tonal] = false lo deja sin fondo.
 */
@Composable
fun EinkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tonal: Boolean = true,
    size: Dp = EinkTheme.dimens.iconButton,
) {
    val colors = EinkTheme.colors
    val shape = CircleShape
    val background = when {
        selected -> colors.ink
        tonal && enabled -> colors.surface
        else -> Color.Transparent
    }
    CompositionLocalProvider(
        LocalEinkContentColor provides when {
            selected -> colors.paper
            enabled -> colors.ink
            else -> colors.muted
        }
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(background, shape)
                .then(if (!enabled && tonal) Modifier.einkBorder(1.5.dp, colors.line, size / 2, dashed = true) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            EinkIcon(icon, contentDescription, size = if (size >= 52.dp) 26.dp else 22.dp)
        }
    }
}

/** Ficha en forma de píldora. La seleccionada va en negro. */
@Composable
fun EinkChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = EinkTheme.colors
    val shape = CircleShape
    CompositionLocalProvider(LocalEinkContentColor provides if (selected) colors.paper else colors.ink) {
        Row(
            modifier
                .height(40.dp)
                .clip(shape)
                .background(if (selected) colors.ink else colors.paper, shape)
                .einkBorder(1.5.dp, colors.ink, 20.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                EinkIcon(icon, null, size = 18.dp)
                Spacer(Modifier.width(6.dp))
            }
            EinkText(text, style = EinkTheme.typography.label.merge(fontSize = EinkTheme.typography.caption.fontSize), maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(4.dp))
                EinkIcon(trailingIcon, null, size = 18.dp)
            }
        }
    }
}

/**
 * Control segmentado: pista gris en píldora y el segmento elegido en negro.
 * [segment] dibuja el contenido de cada opción (texto o icono).
 */
@Composable
fun EinkSegmented(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    contentDescription: (Int) -> String? = { null },
    segment: @Composable (index: Int) -> Unit,
) {
    val colors = EinkTheme.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(colors.surface, CircleShape)
            .padding(4.dp),
    ) {
        for (index in 0 until count) {
            val selected = index == selectedIndex
            CompositionLocalProvider(LocalEinkContentColor provides if (selected) colors.paper else colors.ink) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (selected) colors.ink else Color.Transparent, CircleShape)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    segment(index)
                }
            }
        }
    }
}

/** Control segmentado de textos. */
@Composable
fun EinkSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    EinkSegmented(options.size, selectedIndex, onSelect, modifier, height) { index ->
        EinkText(
            options[index],
            style = if (index == selectedIndex) EinkTheme.typography.label.merge(fontSize = EinkTheme.typography.caption.fontSize)
            else EinkTheme.typography.caption,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Tarjetas y barras
// ---------------------------------------------------------------------------

/** Tarjeta sobre gris claro, sin borde. */
@Composable
fun EinkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    corner: Dp = EinkTheme.dimens.cardCorner,
    /** Si se pasa, la tarjeta no dibuja el marco de pulsado: lo dibuja quien lo reciba (p. ej. la portada). */
    pressSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = when {
        onClick == null -> Modifier
        pressSource != null -> Modifier.clickable(pressSource, indication = null, role = Role.Button, onClick = onClick)
        else -> Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    EinkSurface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(corner)).then(click) else Modifier),
        tone = EinkTone.Surface,
        corner = corner,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/**
 * Cabecera de 64 dp sin línea inferior. Sin [onBack], título grande a la
 * izquierda; con [onBack], botón redondo de volver (o de cerrar, con
 * [closeIcon]) y título mediano con [subtitle] opcional.
 */
@Composable
fun EinkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    closeIcon: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = if (onBack != null) 12.dp else EinkTheme.dimens.gutter, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            EinkIconButton(
                if (closeIcon) EinkIcons.Close else EinkIcons.Back,
                contentDescription = if (closeIcon) "Cerrar" else "Atrás",
                onClick = onBack,
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                EinkText(title, style = EinkTheme.typography.heading, maxLines = 1)
                if (subtitle != null) {
                    EinkText(subtitle, style = EinkTheme.typography.caption, color = EinkTheme.colors.muted, maxLines = 1)
                }
            }
        } else {
            EinkText(title, Modifier.weight(1f), style = EinkTheme.typography.title, maxLines = 1)
        }
        actions()
    }
}

/** Título de sección con una acción de texto opcional a la derecha. */
@Composable
fun EinkSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkText(title, Modifier.weight(1f), style = EinkTheme.typography.section, maxLines = 1)
        if (actionLabel != null && onAction != null) {
            EinkButton(actionLabel, onAction, style = EinkButtonStyle.Text, height = 40.dp)
        }
    }
}

data class EinkBottomBarItem(val label: String, val icon: ImageVector)

/**
 * Barra inferior de 72 dp. El destino activo lleva una barra negra arriba,
 * pegada a la línea separadora, y la etiqueta en negrita; los demás van en
 * gris. Dos señales que no dependen solo del gris.
 */
@Composable
fun EinkBottomBar(
    items: List<EinkBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EinkTheme.colors
    Column(modifier.fillMaxWidth().background(colors.paper)) {
        EinkDivider()
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .toggleable(value = selected, role = Role.Tab, indication = null, interactionSource = null) { onSelect(index) },
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .size(width = 56.dp, height = 4.dp)
                                .background(colors.ink, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                        )
                    }
                    CompositionLocalProvider(LocalEinkContentColor provides if (selected) colors.ink else colors.muted) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EinkIcon(item.icon, contentDescription = null, size = 24.dp)
                            Spacer(Modifier.height(6.dp))
                            EinkText(
                                item.label,
                                style = EinkTheme.typography.eyebrow.merge(
                                    letterSpacing = EinkTheme.typography.caption.letterSpacing,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                                ),
                                color = if (selected) colors.ink else colors.muted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formularios
// ---------------------------------------------------------------------------

/**
 * Campo de texto relleno de gris con la etiqueta encima. Con el foco gana un
 * borde negro. Pendiente de validar en el BOOX: el cursor de BasicTextField
 * parpadea y puede provocar refrescos.
 */
@Composable
fun EinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    /** Si no es nulo, la tecla de acción del teclado es «Buscar» y lo llama. */
    onDone: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    pill: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = EinkTheme.colors
    val dimens = EinkTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corner = if (pill) dimens.touchTarget / 2 else dimens.corner
    val shape = RoundedCornerShape(corner)
    Column(modifier) {
        if (label != null) {
            EinkText(label, style = EinkTheme.typography.label.merge(fontSize = EinkTheme.typography.caption.fontSize))
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = EinkTheme.typography.body.merge(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                autoCorrectEnabled = false,
                imeAction = if (onDone != null) ImeAction.Search else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(dimens.touchTarget)
                        .clip(shape)
                        .background(colors.surface, shape)
                        .then(if (focused) Modifier.einkBorder(dimens.border, colors.ink, corner) else Modifier)
                        .padding(start = if (leadingIcon != null) 14.dp else 16.dp, end = if (trailing != null) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        EinkIcon(leadingIcon, null, size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            EinkText(placeholder, color = colors.muted, maxLines = 1)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            EinkText(supportingText, style = EinkTheme.typography.caption)
        }
    }
}

/** Interruptor en píldora de 52×32: negro con la bola a la derecha si está activo. */
@Composable
fun EinkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    val colors = EinkTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = EinkTheme.dimens.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EinkText(label, style = EinkTheme.typography.label)
            if (description != null) {
                EinkText(description, style = EinkTheme.typography.caption, color = colors.muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(CircleShape)
                .background(if (checked) colors.ink else colors.surfaceStrong, CircleShape)
                .then(if (!enabled) Modifier.einkBorder(1.5.dp, colors.muted, 16.dp, dashed = true) else Modifier)
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.paper, CircleShape)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogos, hojas, menús y estados
// ---------------------------------------------------------------------------

/**
 * Diálogo centrado con esquinas de 28 dp. [icon] se muestra arriba en un
 * círculo gris. Los dos botones se reparten el ancho.
 */
@Composable
fun EinkDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancelar",
    text: String? = null,
    icon: ImageVector? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    EinkOverlay(onDismissRequest) {
        EinkDialogFrame {
            if (icon != null) {
                EinkSurface(Modifier.size(56.dp), tone = EinkTone.Surface, corner = 28.dp) {
                    EinkIcon(icon, null, Modifier.align(Alignment.Center), size = 26.dp)
                }
                Spacer(Modifier.height(16.dp))
            }
            EinkText(title, style = EinkTheme.typography.heading)
            if (text != null) {
                Spacer(Modifier.height(10.dp))
                EinkText(text, color = EinkTheme.colors.muted)
            }
            if (content != null) {
                Spacer(Modifier.height(14.dp))
                content()
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dismissLabel != null) EinkButton(dismissLabel, onClick = onDismissRequest, modifier = Modifier.weight(1f))
                EinkButton(confirmLabel, onClick = onConfirm, primary = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EinkDialogFrame(content: @Composable ColumnScope.() -> Unit) {
    val corner = EinkTheme.dimens.sheetCorner
    EinkSurface(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .consumeTaps(),
        bordered = true,
        corner = corner,
    ) {
        Column(Modifier.padding(24.dp), content = content)
    }
}

/**
 * Hoja inferior: esquinas superiores de 28 dp, línea negra arriba, asa,
 * título y botón redondo de cerrar.
 */
@Composable
fun EinkSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EinkTheme.colors
    val corner = EinkTheme.dimens.sheetCorner
    EinkOverlay(onDismissRequest, contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(topStart = corner, topEnd = corner)
        CompositionLocalProvider(LocalEinkContentColor provides colors.ink) {
            Column(
                modifier
                    .fillMaxWidth()
                    .consumeTaps()
                    .clip(shape)
                    .background(colors.paper, shape)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = colors.ink,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height + corner.toPx()),
                            cornerRadius = CornerRadius(corner.toPx() - stroke / 2),
                            style = Stroke(stroke),
                        )
                    }
                    .padding(bottom = 20.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceStrong)
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = EinkTheme.dimens.gutter, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EinkText(title, Modifier.weight(1f), style = EinkTheme.typography.heading, maxLines = 1)
                    EinkIconButton(EinkIcons.Close, "Cerrar", onDismissRequest)
                }
                content()
            }
        }
    }
}

/**
 * Fila de opción dentro de una hoja: la elegida va sobre gris con un
 * círculo negro con marca.
 */
@Composable
fun EinkOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = EinkTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .background(if (selected) colors.surface else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EinkText(text, style = if (selected) EinkTheme.typography.label else EinkTheme.typography.body, maxLines = 1)
            if (description != null) {
                EinkText(description, style = EinkTheme.typography.caption, color = colors.muted, maxLines = 2)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (selected) {
            EinkSurface(Modifier.size(26.dp), tone = EinkTone.Ink, corner = 13.dp) {
                EinkIcon(EinkIcons.Check, "Seleccionado", Modifier.align(Alignment.Center), size = 16.dp)
            }
        } else {
            Box(Modifier.size(26.dp).einkBorder(2.dp, colors.line, 13.dp))
        }
    }
}

/** Menú de opciones como hoja inferior con filas de radio. */
@Composable
fun EinkMenu(
    onDismissRequest: () -> Unit,
    options: List<String>,
    onSelect: (Int) -> Unit,
    title: String? = null,
    selectedIndex: Int? = null,
) {
    EinkSheet(onDismissRequest, title ?: "Elegir") {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, option ->
                EinkOptionRow(option, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

/**
 * Estado vacío o de error: icono en un círculo gris, título, explicación y
 * hasta dos acciones apiladas a lo ancho.
 */
@Composable
fun EinkMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            EinkSurface(Modifier.size(88.dp), tone = EinkTone.Surface, corner = 44.dp) {
                EinkIcon(icon, null, Modifier.align(Alignment.Center), size = 40.dp)
            }
            Spacer(Modifier.height(20.dp))
        }
        EinkText(title, style = EinkTheme.typography.title, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            EinkText(message, color = EinkTheme.colors.muted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            EinkButton(actionLabel, onClick = onAction, primary = true, modifier = Modifier.fillMaxWidth())
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(10.dp))
            EinkButton(secondaryLabel, onClick = onSecondary, modifier = Modifier.fillMaxWidth())
        }
    }
}
