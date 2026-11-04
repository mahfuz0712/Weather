package com.mux.android.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

private fun DrawScope.sun(c: Offset, r: Float, w: Float, col: Color) {
    drawCircle(col, r, c, style = Stroke(w))
    for (i in 0 until 8) {
        val a = Math.PI / 4 * i
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(col, Offset(c.x + dx * r * 1.45f, c.y + dy * r * 1.45f), Offset(c.x + dx * r * 1.9f, c.y + dy * r * 1.9f), w, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float, w: Float, col: Color) {
    val outer = Path().apply { addOval(Rect(c, r)) }
    val inner = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
    val m = Path.combine(PathOperation.Difference, outer, inner)
    drawPath(m, col, style = Stroke(w, join = StrokeJoin.Round))
}

private fun DrawScope.cloud(c: Offset, s: Float, w: Float, col: Color) {
    val a = Path().apply { addOval(Rect(Offset(c.x - 0.17f * s, c.y + 0.04f * s), 0.14f * s)) }
    val b = Path().apply { addOval(Rect(Offset(c.x + 0.01f * s, c.y - 0.07f * s), 0.19f * s)) }
    val d = Path().apply { addOval(Rect(Offset(c.x + 0.2f * s, c.y + 0.03f * s), 0.14f * s)) }
    val base = Path().apply { addRect(Rect(c.x - 0.31f * s, c.y + 0.04f * s, c.x + 0.34f * s, c.y + 0.17f * s)) }
    var u = Path.combine(PathOperation.Union, a, b)
    u = Path.combine(PathOperation.Union, u, d)
    u = Path.combine(PathOperation.Union, u, base)
    drawPath(u, col.copy(alpha = 0.14f))
    drawPath(u, col, style = Stroke(w, join = StrokeJoin.Round))
}

/** Weather icon drawn in the HUD style (thin outlines). */
@Composable
fun WxIcon(kind: Kind, day: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val hud = Hud.colors
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.045f
        val mid = Offset(s * 0.5f, s * 0.5f)
        val cl = hud.primary
        val ac = hud.accent
        when (kind) {
            Kind.CLEAR -> if (day) sun(mid, s * 0.2f, w, ac) else moon(mid, s * 0.3f, w, cl)
            Kind.PARTLY -> {
                if (day) sun(Offset(s * 0.66f, s * 0.34f), s * 0.12f, w, ac) else moon(Offset(s * 0.66f, s * 0.34f), s * 0.17f, w, cl)
                cloud(Offset(s * 0.46f, s * 0.58f), s * 0.95f, w, cl)
            }
            Kind.CLOUD -> cloud(Offset(s * 0.5f, s * 0.5f), s, w, cl)
            Kind.FOG -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f, w, cl)
                for (i in 0..2) {
                    val y = s * (0.66f + 0.1f * i)
                    drawLine(cl, Offset(s * (0.24f + 0.04f * i), y), Offset(s * (0.76f - 0.04f * i), y), w, StrokeCap.Round)
                }
            }
            Kind.RAIN -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f, w, cl)
                for (i in 0..2) {
                    val x = s * (0.33f + 0.17f * i)
                    drawLine(cl, Offset(x, s * 0.68f), Offset(x - s * 0.06f, s * 0.84f), w, StrokeCap.Round)
                }
            }
            Kind.SNOW -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f, w, cl)
                for (i in 0..2) {
                    drawCircle(cl, w * 1.2f, Offset(s * (0.33f + 0.17f * i), s * (0.74f + 0.05f * (i % 2))))
                }
            }
            Kind.STORM -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.9f, w, cl)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f)
                    lineTo(s * 0.42f, s * 0.76f)
                    lineTo(s * 0.52f, s * 0.76f)
                    lineTo(s * 0.46f, s * 0.92f)
                    lineTo(s * 0.62f, s * 0.70f)
                    lineTo(s * 0.52f, s * 0.70f)
                    close()
                }
                drawPath(bolt, ac, style = Stroke(w, join = StrokeJoin.Round))
            }
        }
    }
}
