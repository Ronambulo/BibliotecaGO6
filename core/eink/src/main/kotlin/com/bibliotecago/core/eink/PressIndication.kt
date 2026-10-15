package com.bibliotecago.core.eink

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Sustituye al ripple: mientras el elemento está pulsado (o con el puntero
 * encima) se dibuja un marco fino (tinta por fuera, papel por dentro) sin
 * tocar el contenido. Las portadas no cambian de color y E Ink solo refresca
 * el borde. Las fichas de libro lo ponen solo en la portada, con
 * `Modifier.indication(source, PressIndication)`.
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}

private class PressNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    private val presses = mutableSetOf<PressInteraction.Press>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                }
                val now = presses.isNotEmpty() || hovers.isNotEmpty()
                if (now != pressed) {
                    pressed = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pressed) return
        val outer = 3.dp.toPx()
        val inner = 2.dp.toPx()
        // El marco de papel separa el de tinta de un fondo negro (botón primario).
        drawRect(INK, Offset(outer / 2, outer / 2), Size(size.width - outer, size.height - outer), style = Stroke(outer))
        val o = outer + inner / 2
        drawRect(PAPER, Offset(o, o), Size(size.width - 2 * o, size.height - 2 * o), style = Stroke(inner))
    }
}

private val INK = Color.Black
private val PAPER = Color.White
