package com.mux.android.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CELL = 52.dp

@Composable
fun ForecastScreen() {
    val c = Hud.colors
    val r = WxState.report
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (r == null) {
            EmptyNote(if (WxState.loading) "LOADING WEATHER..." else "NO DATA YET. OPEN THE NOW TAB.")
            return@Column
        }
        HudPanel(Modifier.fillMaxWidth(), "NEXT 24 HOURS · " + Prefs.current.name.uppercase()) {
            val hs = r.hours
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                if (hs.size >= 2) {
                    val lo = hs.minOf { it.temp }
                    val hi = hs.maxOf { it.temp }
                    val line = c.primary
                    val dot = c.accent
                    Canvas(Modifier.width(CELL * hs.size).height(60.dp)) {
                        val cell = size.width / hs.size
                        val span = (hi - lo).coerceAtLeast(1.0).toFloat()
                        val pad = 10f
                        fun pt(i: Int): Offset {
                            val y = pad + (size.height - 2 * pad) * (1f - ((hs[i].temp - lo).toFloat() / span))
                            return Offset(cell * (i + 0.5f), y)
                        }
                        val p = Path()
                        for (i in hs.indices) {
                            val o = pt(i)
                            if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                        }
                        drawPath(p, line, style = Stroke(3f, cap = StrokeCap.Round))
                        for (i in hs.indices step 1) drawCircle(dot, 4f, pt(i))
                    }
                }
                Row {
                    for ((i, h) in hs.withIndex()) {
                        Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally) {
                            HudText(Units.temp(h.temp), color = c.text, size = 13.sp, bold = true, align = TextAlign.Center)
                            WxIcon(Codes.kind(h.code), h.day, 32.dp)
                            HudText(if (i == 0) "NOW" else hourLabel(h.time) + "h", color = c.dim, size = 10.sp, align = TextAlign.Center)
                            HudText(if (h.pop > 0) h.pop.toString() + "%" else "", color = c.primary, size = 10.sp, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val gmin = r.days.minOfOrNull { it.min } ?: 0.0
        val gmax = r.days.maxOfOrNull { it.max } ?: 1.0
        val span = (gmax - gmin).coerceAtLeast(1.0)
        HudPanel(Modifier.fillMaxWidth(), "7 DAYS") {
            for ((i, d) in r.days.withIndex()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudText(dayName(d.date, i), color = if (i == 0) c.accent else c.text, size = 12.sp, bold = true, modifier = Modifier.width(52.dp))
                    WxIcon(Codes.kind(d.code), true, 30.dp)
                    HudText(if (d.pop > 0) d.pop.toString() + "%" else "", color = c.primary, size = 10.sp, modifier = Modifier.width(34.dp), align = TextAlign.End)
                    HudText(Units.temp(d.min), color = c.dim, size = 12.sp, modifier = Modifier.width(36.dp), align = TextAlign.End)
                    val a = ((d.min - gmin) / span).toFloat()
                    val b = ((d.max - gmin) / span).toFloat()
                    val track = c.dim.copy(alpha = 0.3f)
                    val fill = c.primary
                    Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 6.dp)) {
                        drawRoundRect(track, Offset.Zero, Size(size.width, size.height), CornerRadius(4f, 4f))
                        drawRoundRect(
                            fill, Offset(size.width * a, 0f),
                            Size((size.width * (b - a)).coerceAtLeast(8f), size.height), CornerRadius(4f, 4f)
                        )
                    }
                    HudText(Units.temp(d.max), color = c.text, size = 12.sp, bold = true, modifier = Modifier.width(36.dp))
                }
                HudText(
                    Codes.text(d.code) + if (d.rain > 0.0) "  ·  " + Units.rain(d.rain) else "",
                    color = c.dim, size = 9.sp, modifier = Modifier.padding(start = 52.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
