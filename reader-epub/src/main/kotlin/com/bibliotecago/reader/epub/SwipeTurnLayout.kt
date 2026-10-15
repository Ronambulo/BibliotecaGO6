package com.bibliotecago.reader.epub

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Envuelve el navegador de Readium y le quita los deslizamientos horizontales:
 * el visor de Readium hace seguir la página al dedo, y en E Ink eso es una
 * animación. Aquí, en cuanto el gesto es claramente horizontal se cancela para
 * el navegador y, al soltar, se llama a [onSwipe] para pasar página de golpe.
 *
 * Los toques, los toques largos (selección de texto) y los gestos verticales
 * siguen llegando al navegador sin cambios.
 */
@SuppressLint("ViewConstructor")
internal class SwipeTurnLayout(
    context: Context,
    private val onSwipe: (forward: Boolean) -> Unit,
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private val minDistance = 48 * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    /** Toque largo o varios dedos: el gesto es del navegador hasta el final. */
    private var passThrough = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiping = false
                passThrough = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!swiping) passThrough = true
            MotionEvent.ACTION_MOVE -> if (!swiping && !passThrough) {
                val dx = event.x - downX
                val dy = event.y - downY
                when {
                    abs(dx) > touchSlop && abs(dx) > abs(dy) -> {
                        swiping = true
                        // El navegador recibe un CANCEL y olvida el gesto.
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                    abs(dy) > touchSlop -> passThrough = true
                    event.eventTime - event.downTime > longPressTimeout -> passThrough = true
                }
            }
            MotionEvent.ACTION_UP -> if (swiping) {
                swiping = false
                val dx = event.x - downX
                if (abs(dx) >= minDistance) onSwipe(dx < 0)
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (swiping) {
                swiping = false
                return true
            }
        }
        return if (swiping) true else super.dispatchTouchEvent(event)
    }
}
