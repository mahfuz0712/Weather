package com.mux.android.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlacesScreen(onGps: () -> Unit, onPicked: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun search() {
        val q = query.trim()
        if (q.length < 2) return
        status = "SEARCHING..."
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Api.search(q) }
                results = list
                status = if (list.isEmpty()) "NOTHING FOUND" else ""
            } catch (e: Exception) {
                results = emptyList()
                status = "SEARCH FAILED. CHECK THE INTERNET."
            }
        }
    }

    fun pick(p: Place) {
        Prefs.add(p)
        WxState.load(ctx, false)
        results = emptyList()
        query = ""
        status = ""
        onPicked()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "ADD A PLACE") {
            HudField(query, "⌕  CITY NAME", Modifier.fillMaxWidth().height(44.dp), onDone = { search() }) { query = it }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                HudButton("SEARCH", Modifier.weight(1f).height(42.dp), KeyKind.ACCENT, enabled = query.trim().length >= 2, fontSize = 12.sp) { search() }
                HudButton("MY LOCATION", Modifier.weight(1f).height(42.dp), KeyKind.OP, fontSize = 12.sp) { onGps() }
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HudText(status, color = c.dim, size = 11.sp)
            }
            for (p in results) {
                HudItem(Modifier.fillMaxWidth(), onClick = { pick(p) }) {
                    HudText(p.name, color = c.text, size = 13.sp, bold = true, maxLines = 1)
                    HudText(p.region, color = c.dim, size = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "SAVED PLACES") {
            Prefs.places.forEachIndexed { i, p ->
                HudItem(Modifier.fillMaxWidth(), selected = i == Prefs.selected, onClick = {
                    Prefs.select(i)
                    WxState.load(ctx, false)
                    onPicked()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            HudText(p.name, color = if (i == Prefs.selected) c.primary else c.text, size = 13.sp, bold = true, maxLines = 1)
                            HudText(p.region, color = c.dim, size = 10.sp, maxLines = 1)
                        }
                        if (Prefs.places.size > 1) {
                            HudButton("✕", Modifier.size(width = 40.dp, height = 34.dp), KeyKind.DANGER, fontSize = 12.sp) {
                                Prefs.remove(i)
                                WxState.load(ctx, false)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "OPTIONS") {
            SettingRow("TEMPERATURE", "degrees", if (Prefs.celsius) "°C" else "°F") { Prefs.celsius = !Prefs.celsius; Prefs.commit() }
            SettingRow("WIND / RAIN", "metric or imperial", if (Prefs.kmh) "KM/H · MM" else "MPH · IN") { Prefs.kmh = !Prefs.kmh; Prefs.commit() }
            SettingRow("PALETTE", "cyan or amber HUD colors", if (Prefs.amber) "AMBER" else "CYAN") { Prefs.amber = !Prefs.amber; Prefs.commit() }
        }
        Spacer(Modifier.height(8.dp))
    }
}
