package com.mux.android.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun RowScope.Stat(label: String, value: String, color: Color = Hud.colors.text) {
    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
        HudText(label, color = Hud.colors.dim, size = 10.sp)
        HudText(value, color = color, size = 15.sp, bold = true, maxLines = 1)
    }
}

@Composable
fun NowScreen() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val r = WxState.report
    val place = Prefs.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HudText(place.name.uppercase(), color = c.primary, size = 18.sp, bold = true, maxLines = 1)
                HudText(place.region, color = c.dim, size = 10.sp, maxLines = 1)
            }
            HudButton(
                if (WxState.loading) "..." else "REFRESH", Modifier.width(96.dp).height(38.dp), KeyKind.ACCENT,
                enabled = !WxState.loading, fontSize = 11.sp
            ) { WxState.load(ctx, true) }
        }
        Spacer(Modifier.height(6.dp))

        if (r == null) {
            if (WxState.loading) {
                EmptyNote("LOADING WEATHER...")
            } else {
                HudPanel(Modifier.fillMaxWidth(), "NO DATA") {
                    HudText(WxState.error.ifEmpty { "NO WEATHER LOADED YET" }, color = c.danger, size = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    HudButton("TRY AGAIN", Modifier.fillMaxWidth().height(44.dp), KeyKind.ACCENT, fontSize = 13.sp) { WxState.load(ctx, true) }
                }
            }
            return@Column
        }

        val today = r.days.firstOrNull()
        HudPanel(Modifier.fillMaxWidth(), "NOW") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WxIcon(Codes.kind(r.code), r.day, 104.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    HudText(Units.temp(r.temp), color = c.primary, size = 64.sp, bold = true)
                    HudText(Codes.text(r.code), color = c.accent, size = 13.sp, bold = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            val hl = if (today != null) "  ·  H " + Units.temp(today.max) + "  L " + Units.temp(today.min) else ""
            HudText("FEELS LIKE " + Units.temp(r.feels) + hl, color = c.text, size = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        HudPanel(Modifier.fillMaxWidth(), "CONDITIONS") {
            Row(Modifier.fillMaxWidth()) {
                Stat("HUMIDITY", r.humidity.toString() + "%")
                Stat("WIND", Units.wind(r.wind) + " " + Codes.compass(r.windDir))
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("GUSTS", Units.wind(r.gust))
                Stat("PRESSURE", Math.round(r.pressure).toInt().toString() + " HPA")
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("CLOUD COVER", r.cloud.toString() + "%")
                Stat("RAIN NOW", Units.rain(r.precip))
            }
            if (today != null) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("RAIN CHANCE", today.pop.toString() + "%")
                    Stat("UV INDEX", String.format(Locale.US, "%.0f", today.uv) + " " + Codes.uvText(today.uv))
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (r.aqi >= 0) {
            val col = if (r.aqi <= 50) c.good else if (r.aqi <= 100) c.accent else c.danger
            HudPanel(Modifier.fillMaxWidth(), "AIR QUALITY (US AQI)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudText(r.aqi.toString(), color = col, size = 40.sp, bold = true)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        HudText(Codes.aqiText(r.aqi), color = col, size = 12.sp, bold = true)
                        HudText(
                            "PM2.5 " + String.format(Locale.US, "%.0f", r.pm25) + "  PM10 " + String.format(Locale.US, "%.0f", r.pm10) + " µG/M³",
                            color = c.dim, size = 10.sp
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(c.dim.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth((r.aqi / 300f).coerceIn(0.03f, 1f)).fillMaxHeight().background(col))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (today != null) {
            HudPanel(Modifier.fillMaxWidth(), "SUN") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("SUNRISE", clock(today.sunrise), c.accent)
                    Stat("SUNSET", clock(today.sunset), c.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val upd = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.fetched))
        HudText(
            "UPDATED " + upd + " · DATA: OPEN-METEO" + if (WxState.offline) " · OFFLINE COPY" else "",
            color = if (WxState.offline) c.accent else c.dim, size = 10.sp
        )
        if (WxState.error.isNotEmpty()) HudText(WxState.error, color = c.danger, size = 10.sp)
        Spacer(Modifier.height(8.dp))
    }
}
