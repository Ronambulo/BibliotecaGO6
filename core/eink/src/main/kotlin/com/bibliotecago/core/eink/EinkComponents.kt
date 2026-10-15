package com.bibliotecago.core.eink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(
                width = stroke,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
            ),
        )
    }

/** Superficie con fondo propio; [inverted] la pinta en negro con contenido blanco. */
@Composable
fun EinkSurface(
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    bordered: Boolean = false,
    corner: Dp = EinkTheme.dimens.corner,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = EinkTheme.colors
    val background = if (inverted) colors.ink else colors.paper
    val foreground = if (inverted) colors.paper else colors.ink
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

@Composable
fun EinkDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(EinkTheme.colors.ink)
    )
}

// ---------------------------------------------------------------------------
// Botones
// ---------------------------------------------------------------------------

/**
 * Botón grande. [primary] lo pinta relleno en negro; el resto va con borde.
 * Desactivado: borde discontinuo y texto atenuado.
 */
@Composable
fun EinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = EinkTheme.colors
    val dimens = EinkTheme.dimens
    val filled = primary && enabled
    val shape = RoundedCornerShape(dimens.corner)
    CompositionLocalProvider(
        LocalEinkContentColor provides when {
            filled -> colors.paper
            enabled -> colors.ink
            else -> colors.muted
        }
    ) {
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = dimens.touchTarget, minWidth = dimens.touchTarget)
                .clip(shape)
                .background(if (filled) colors.ink else colors.paper, shape)
                .einkBorder(dimens.border, if (enabled) colors.ink else colors.muted, dimens.corner, dashed = !enabled)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                EinkIcon(icon, contentDescription = null)
                Spacer(Modifier.width(10.dp))
            }
            EinkText(text, style = EinkTheme.typography.label, maxLines = 1)
        }
    }
}

/** Botón de icono con área táctil completa. [selected] lo invierte. */
@Composable
fun EinkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    EinkSurface(
        modifier = modifier
            .size(EinkTheme.dimens.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        inverted = selected,
    ) {
        CompositionLocalProvider(
            LocalEinkContentColor provides if (enabled) LocalEinkContentColor.current else EinkTheme.colors.muted
        ) {
            EinkIcon(icon, contentDescription, Modifier.align(Alignment.Center))
        }
    }
}

// ---------------------------------------------------------------------------
// Tarjetas y barras
// ---------------------------------------------------------------------------

@Composable
fun EinkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EinkSurface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
        ),
        bordered = true,
    ) {
        Column(Modifier.padding(EinkTheme.dimens.spacing), content = content)
    }
}

@Composable
fun EinkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EinkTheme.dimens.touchTarget + 8.dp)
                .padding(horizontal = if (onBack != null) 4.dp else EinkTheme.dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                EinkIconButton(EinkIcons.Back, contentDescription = "Atrás", onClick = onBack)
                Spacer(Modifier.width(4.dp))
            }
            EinkText(title, Modifier.weight(1f), style = EinkTheme.typography.heading, maxLines = 1)
            actions()
        }
        EinkDivider(thickness = EinkTheme.dimens.border)
    }
}

data class EinkBottomBarItem(val label: String, val icon: ImageVector)

/**
 * Barra inferior. El destino activo se marca invertido (fondo negro) y con el
 * texto en negrita: dos señales que no dependen del color.
 */
@Composable
fun EinkBottomBar(
    items: List<EinkBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        EinkDivider(thickness = EinkTheme.dimens.border)
        Row(Modifier.fillMaxWidth().height(68.dp)) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                EinkSurface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(68.dp)
                        .toggleable(value = selected, role = Role.Tab) { onSelect(index) },
                    inverted = selected,
                    corner = 0.dp,
                ) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EinkIcon(item.icon, contentDescription = null)
                        Spacer(Modifier.height(2.dp))
                        EinkText(
                            item.label,
                            style = if (selected) EinkTheme.typography.label else EinkTheme.typography.caption,
                            maxLines = 1,
                        )
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
 * Campo de texto con etiqueta encima. Pendiente de validar en el BOOX: el
 * cursor de BasicTextField parpadea y puede provocar refrescos.
 */
@Composable
fun EinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
) {
    val colors = EinkTheme.colors
    val dimens = EinkTheme.dimens
    Column(modifier) {
        EinkText(label, style = EinkTheme.typography.label)
        Spacer(Modifier.height(6.dp))
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
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = dimens.touchTarget)
                        .einkBorder(dimens.border, colors.ink, dimens.corner)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        EinkText(placeholder, color = colors.muted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            EinkText(supportingText, style = EinkTheme.typography.caption)
        }
    }
}

/**
 * Interruptor sin animación: la marca salta de un lado a otro y además se
 * escribe «Sí» o «No».
 */
@Composable
fun EinkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        EinkText(label, Modifier.weight(1f))
        EinkText(if (checked) "Sí" else "No", style = EinkTheme.typography.label)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .einkBorder(EinkTheme.dimens.border, colors.ink, 16.dp, dashed = !enabled)
                .padding(5.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (checked) colors.ink else colors.paper)
                    .einkBorder(EinkTheme.dimens.border, colors.ink, 11.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Diálogos, menús y estados
// ---------------------------------------------------------------------------

@Composable
fun EinkDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = "Cancelar",
    text: String? = null,
) {
    EinkOverlay(onDismissRequest) {
        EinkDialogFrame {
            EinkText(title, style = EinkTheme.typography.heading)
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                EinkText(text)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                if (dismissLabel != null) EinkButton(dismissLabel, onClick = onDismissRequest)
                EinkButton(confirmLabel, onClick = onConfirm, primary = true)
            }
        }
    }
}

/**
 * Menú de opciones. En E Ink se muestra como una lista centrada en lugar de un
 * desplegable anclado: un único nivel, sin submenús.
 */
@Composable
fun EinkMenu(
    onDismissRequest: () -> Unit,
    options: List<String>,
    onSelect: (Int) -> Unit,
    title: String? = null,
    selectedIndex: Int? = null,
) {
    EinkOverlay(onDismissRequest) {
        EinkDialogFrame(padding = 0.dp) {
            if (title != null) {
                EinkText(title, Modifier.padding(20.dp), style = EinkTheme.typography.heading)
                EinkDivider()
            }
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = EinkTheme.dimens.touchTarget)
                        .clickable(role = Role.Button) { onSelect(index) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EinkText(option, Modifier.weight(1f), style = if (selected) EinkTheme.typography.label else EinkTheme.typography.body)
                    if (selected) EinkIcon(EinkIcons.Check, contentDescription = "Seleccionado")
                }
                if (index < options.lastIndex) EinkDivider()
            }
        }
    }
}

@Composable
private fun EinkDialogFrame(padding: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    EinkSurface(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .consumeTaps(),
    ) {
        Column(
            Modifier
                .einkBorder(4.dp, EinkTheme.colors.ink, EinkTheme.dimens.corner)
                .padding(padding),
            content = content,
        )
    }
}

/** Estado vacío o de error: mensaje claro y, si procede, una acción. */
@Composable
fun EinkMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(EinkTheme.dimens.gutter * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EinkText(title, style = EinkTheme.typography.heading, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            EinkText(message, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            EinkButton(actionLabel, onClick = onAction)
        }
    }
}
