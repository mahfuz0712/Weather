package com.mux.android.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TABS = listOf("NOW", "FORECAST", "PLACES", "SYS")

@Composable
fun WeatherRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (Prefs.amber) AmberPalette else CyanPalette) {
        val c = Hud.colors
        val scope = rememberCoroutineScope()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var update by remember { mutableStateOf<UpdateInfo?>(null) }

        fun useGps() {
            val pos = Gps.lastKnown(ctx)
            if (pos == null) {
                Util.toast(ctx, "NO LOCATION YET. OPEN MAPS ONCE, OR SEARCH A CITY")
                return
            }
            scope.launch {
                val p = withContext(Dispatchers.IO) { Gps.place(ctx, pos.first, pos.second) }
                Prefs.add(p)
                WxState.load(ctx, true)
                tab = 0
            }
        }
        val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res.values.any { it }) useGps() else Util.toast(ctx, "LOCATION PERMISSION DENIED")
        }
        val onGps: () -> Unit = {
            if (hasLocation(ctx)) useGps()
            else gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }

        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }
        LaunchedEffect(resumeTick.intValue) { WxState.load(ctx, false) }

        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { Util.open(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }

        HudBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                Column(Modifier.fillMaxWidth().height(40.dp)) {
                    HudText("◈ WEATHER", color = c.primary, size = 16.sp, bold = true)
                    HudText(TABS[tab], color = c.dim, size = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> NowScreen()
                        1 -> ForecastScreen()
                        2 -> PlacesScreen(onGps) { tab = 0 }
                        else -> SystemScreen(false) { update = it }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(46.dp)) {
                    TABS.forEachIndexed { i, t ->
                        HudButton(
                            label = t,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            kind = KeyKind.OP,
                            selected = i == tab,
                            fontSize = 10.sp
                        ) { tab = i }
                    }
                }
            }
        }
    }
}

private fun hasLocation(ctx: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
