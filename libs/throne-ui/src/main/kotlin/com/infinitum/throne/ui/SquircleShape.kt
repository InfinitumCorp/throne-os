package com.infinitum.throne.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Continuous-corner icon shape: a superellipse |x|^n + |y|^n = 1 filling the box.
 * n = 5 gives the smooth, curvature-continuous corners phone home screens use,
 * instead of a circle arc meeting a straight edge.
 */
class SquircleShape(private val exponent: Float = 5f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        val power = 2f / exponent
        val steps = 160
        val path = Path()
        for (i in 0..steps) {
            val t = (i.toDouble() / steps) * 2.0 * Math.PI
            val c = cos(t)
            val s = sin(t)
            val x = halfW + halfW * (sign(c) * abs(c).pow(power.toDouble())).toFloat()
            val y = halfH + halfH * (sign(s) * abs(s).pow(power.toDouble())).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val IconSquircle: Shape = SquircleShape()
