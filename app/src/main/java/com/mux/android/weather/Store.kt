package com.mux.android.weather

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Settings and saved places. */
object Prefs {
    private var sp: SharedPreferences? = null
    var amber by mutableStateOf(false)
    var celsius by mutableStateOf(true)
    var kmh by mutableStateOf(true)
    var places by mutableStateOf<List<Place>>(emptyList())
    var selected by mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("mux_weather", Context.MODE_PRIVATE)
        sp = p
        amber = p.getBoolean("amber", false)
        celsius = p.getBoolean("celsius", true)
        kmh = p.getBoolean("kmh", true)
        selected = p.getInt("selected", 0)
        val list = ArrayList<Place>()
        try {
            val arr = JSONArray(p.getString("places", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Place(o.getString("n"), o.optString("r", ""), o.getDouble("la"), o.getDouble("lo")))
            }
        } catch (e: Exception) {
        }
        if (list.isEmpty()) list.add(Place("Dhaka", "Dhaka, Bangladesh", 23.8103, 90.4125))
        places = list
        if (selected !in list.indices) selected = 0
    }

    fun commit() {
        val arr = JSONArray()
        for (p in places) arr.put(JSONObject().put("n", p.name).put("r", p.region).put("la", p.lat).put("lo", p.lon))
        sp?.edit()
            ?.putBoolean("amber", amber)?.putBoolean("celsius", celsius)?.putBoolean("kmh", kmh)
            ?.putInt("selected", selected)?.putString("places", arr.toString())?.apply()
    }

    val current: Place get() = places.getOrNull(selected) ?: places.first()

    fun cacheGet(key: String): String? = sp?.getString("c_$key", null)
    fun cachePut(key: String, raw: String) { sp?.edit()?.putString("c_$key", raw)?.apply() }

    fun select(i: Int) {
        selected = i
        commit()
    }

    fun add(p: Place) {
        val i = places.indexOfFirst { it.key == p.key }
        if (i >= 0) {
            select(i)
        } else {
            places = places + p
            select(places.size - 1)
        }
    }

    fun remove(i: Int) {
        if (places.size <= 1) return
        places = places.filterIndexed { k, _ -> k != i }
        if (selected >= places.size) selected = places.size - 1
        else if (i < selected) selected -= 1
        commit()
    }
}

/** Number formatting with the chosen units. */
object Units {
    fun temp(c: Double): String {
        val v = if (Prefs.celsius) c else c * 9.0 / 5.0 + 32.0
        return Math.round(v).toInt().toString() + "°"
    }

    val tempUnit: String get() = if (Prefs.celsius) "C" else "F"

    fun wind(kmh: Double): String =
        if (Prefs.kmh) Math.round(kmh).toInt().toString() + " KM/H"
        else Math.round(kmh * 0.621371).toInt().toString() + " MPH"

    fun rain(mm: Double): String =
        if (Prefs.kmh) String.format(Locale.US, "%.1f MM", mm) else String.format(Locale.US, "%.2f IN", mm / 25.4)
}

/** The weather shown on screen, and the code that fetches it. */
object WxState {
    var report by mutableStateOf<Report?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var offline by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var token = 0

    /** Show the saved copy right away (if any), then refresh from the internet when [force] or old. */
    fun load(ctx: Context, force: Boolean) {
        val place = Prefs.current
        val cached = Prefs.cacheGet(place.key)?.let { try { Api.parse(place, it) } catch (e: Exception) { null } }
        if (cached != null) report = cached else if (report?.place?.key != place.key) report = null
        val old = cached == null || System.currentTimeMillis() - cached.fetched > 15 * 60 * 1000L
        if (!force && !old) return
        refresh(ctx.applicationContext, place)
    }

    private fun refresh(app: Context, place: Place) {
        val my = ++token
        loading = true
        error = ""
        Thread {
            var rep: Report? = null
            var err = ""
            try {
                val raw = Api.download(place)
                Prefs.cachePut(place.key, raw)
                rep = Api.parse(place, raw)
            } catch (e: Exception) {
                err = "COULD NOT LOAD: " + (e.message ?: "NETWORK ERROR")
            }
            main.post {
                if (my != token) return@post
                loading = false
                if (rep != null) {
                    report = rep
                    offline = false
                } else {
                    error = err
                    offline = report != null
                }
            }
        }.start()
    }
}

object Gps {
    /** Last known position of the phone (no live GPS fix needed). Returns null when unknown. */
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Pair<Double, Double>? {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            }
            best?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }

    /** City name for a position, or a "lat, lon" label. Call off the main thread. */
    @Suppress("DEPRECATION")
    fun place(ctx: Context, lat: Double, lon: Double): Place {
        var name = ""
        var region = ""
        try {
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            if (a != null) {
                name = a.locality ?: a.subAdminArea ?: a.adminArea ?: ""
                region = listOf(a.adminArea ?: "", a.countryName ?: "").filter { it.isNotEmpty() }.joinToString(", ")
            }
        } catch (e: Exception) {
        }
        if (name.isEmpty()) name = String.format(Locale.US, "%.2f, %.2f", lat, lon)
        return Place(name, region.ifEmpty { "MY LOCATION" }, lat, lon)
    }
}
