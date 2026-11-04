package com.mux.android.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

enum class Kind { CLEAR, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

/** WMO weather code (as used by Open-Meteo) to icon kind and words. */
object Codes {
    fun kind(code: Int): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2 -> Kind.PARTLY
        3 -> Kind.CLOUD
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.CLOUD
    }

    fun text(code: Int): String = when (code) {
        0 -> "CLEAR SKY"
        1 -> "MOSTLY CLEAR"
        2 -> "PARTLY CLOUDY"
        3 -> "OVERCAST"
        45, 48 -> "FOG"
        51, 53, 55 -> "DRIZZLE"
        56, 57 -> "FREEZING DRIZZLE"
        61 -> "LIGHT RAIN"
        63 -> "RAIN"
        65 -> "HEAVY RAIN"
        66, 67 -> "FREEZING RAIN"
        71 -> "LIGHT SNOW"
        73 -> "SNOW"
        75 -> "HEAVY SNOW"
        77 -> "SNOW GRAINS"
        80 -> "LIGHT SHOWERS"
        81 -> "SHOWERS"
        82 -> "HEAVY SHOWERS"
        85, 86 -> "SNOW SHOWERS"
        95 -> "THUNDERSTORM"
        96, 99 -> "THUNDERSTORM + HAIL"
        else -> "UNKNOWN"
    }

    fun aqiText(v: Int): String = when {
        v <= 50 -> "GOOD"
        v <= 100 -> "MODERATE"
        v <= 150 -> "UNHEALTHY FOR SENSITIVE"
        v <= 200 -> "UNHEALTHY"
        v <= 300 -> "VERY UNHEALTHY"
        else -> "HAZARDOUS"
    }

    fun uvText(v: Double): String = when {
        v < 3 -> "LOW"
        v < 6 -> "MODERATE"
        v < 8 -> "HIGH"
        v < 11 -> "VERY HIGH"
        else -> "EXTREME"
    }

    fun compass(deg: Double): String {
        val d = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return d[(((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
    }
}

class Place(val name: String, val region: String, val lat: Double, val lon: Double) {
    val key: String get() = "%.2f,%.2f".format(Locale.US, lat, lon)
}

class Hour(val time: String, val temp: Double, val pop: Int, val code: Int, val day: Boolean)

class Day(
    val date: String,
    val code: Int,
    val max: Double,
    val min: Double,
    val pop: Int,
    val rain: Double,
    val uv: Double,
    val sunrise: String,
    val sunset: String
)

class Report(
    val place: Place,
    val fetched: Long,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val code: Int,
    val day: Boolean,
    val wind: Double,
    val windDir: Double,
    val gust: Double,
    val pressure: Double,
    val cloud: Int,
    val precip: Double,
    val aqi: Int,
    val pm25: Double,
    val pm10: Double,
    val hours: List<Hour>,
    val days: List<Day>
)

object Api {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 12000
        c.setRequestProperty("Accept", "application/json")
        if (c.responseCode != 200) throw RuntimeException("HTTP " + c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun forecastUrl(p: Place): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
            "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max," +
            "precipitation_probability_max,precipitation_sum" +
            "&timezone=auto&forecast_days=7"

    private fun airUrl(p: Place): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${p.lat}&longitude=${p.lon}" +
            "&current=us_aqi,pm2_5,pm10&timezone=auto"

    /** Downloads and returns the raw text that [parse] understands (also what we cache). */
    fun download(p: Place): String {
        val f = get(forecastUrl(p))
        val a = try { get(airUrl(p)) } catch (e: Exception) { "{}" }
        return JSONObject().put("f", JSONObject(f)).put("a", JSONObject(a))
            .put("t", System.currentTimeMillis()).toString()
    }

    fun parse(p: Place, raw: String): Report {
        val root = JSONObject(raw)
        val f = root.getJSONObject("f")
        val a = root.optJSONObject("a") ?: JSONObject()
        val cur = f.getJSONObject("current")
        val h = f.getJSONObject("hourly")
        val d = f.getJSONObject("daily")

        val times = h.getJSONArray("time")
        val ht = h.getJSONArray("temperature_2m")
        val hp = h.optJSONArray("precipitation_probability")
        val hc = h.getJSONArray("weather_code")
        val hd = h.optJSONArray("is_day")
        val nowHour = cur.getString("time").take(13) + ":00"
        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i) >= nowHour) { start = i; break }
        }
        val hours = ArrayList<Hour>()
        var i = start
        while (i < times.length() && hours.size < 24) {
            hours.add(
                Hour(
                    times.getString(i), ht.optDouble(i, 0.0),
                    if (hp != null && !hp.isNull(i)) hp.optInt(i, 0) else 0,
                    hc.optInt(i, 0), hd == null || hd.optInt(i, 1) == 1
                )
            )
            i++
        }

        val dt = d.getJSONArray("time")
        val dc = d.getJSONArray("weather_code")
        val dmax = d.getJSONArray("temperature_2m_max")
        val dmin = d.getJSONArray("temperature_2m_min")
        val dsr = d.getJSONArray("sunrise")
        val dss = d.getJSONArray("sunset")
        val duv = d.optJSONArray("uv_index_max")
        val dpp = d.optJSONArray("precipitation_probability_max")
        val dps = d.optJSONArray("precipitation_sum")
        val days = ArrayList<Day>()
        for (k in 0 until dt.length()) {
            days.add(
                Day(
                    dt.getString(k), dc.optInt(k, 0), dmax.optDouble(k, 0.0), dmin.optDouble(k, 0.0),
                    if (dpp != null && !dpp.isNull(k)) dpp.optInt(k, 0) else 0,
                    if (dps != null && !dps.isNull(k)) dps.optDouble(k, 0.0) else 0.0,
                    if (duv != null && !duv.isNull(k)) duv.optDouble(k, 0.0) else 0.0,
                    dsr.optString(k, ""), dss.optString(k, "")
                )
            )
        }

        val ac = a.optJSONObject("current")
        return Report(
            place = p,
            fetched = root.optLong("t", System.currentTimeMillis()),
            temp = cur.optDouble("temperature_2m", 0.0),
            feels = cur.optDouble("apparent_temperature", 0.0),
            humidity = cur.optInt("relative_humidity_2m", 0),
            code = cur.optInt("weather_code", 0),
            day = cur.optInt("is_day", 1) == 1,
            wind = cur.optDouble("wind_speed_10m", 0.0),
            windDir = cur.optDouble("wind_direction_10m", 0.0),
            gust = cur.optDouble("wind_gusts_10m", 0.0),
            pressure = cur.optDouble("pressure_msl", 0.0),
            cloud = cur.optInt("cloud_cover", 0),
            precip = cur.optDouble("precipitation", 0.0),
            aqi = if (ac != null && !ac.isNull("us_aqi")) ac.optInt("us_aqi", -1) else -1,
            pm25 = ac?.optDouble("pm2_5", 0.0) ?: 0.0,
            pm10 = ac?.optDouble("pm10", 0.0) ?: 0.0,
            hours = hours,
            days = days
        )
    }

    /** City search. Returns places with a readable region line. */
    fun search(q: String): List<Place> {
        val raw = get(
            "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(q, "UTF-8") +
                "&count=8&language=en&format=json"
        )
        val arr = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val region = listOf(o.optString("admin1", ""), o.optString("country", "")).filter { it.isNotEmpty() }.joinToString(", ")
            out.add(Place(o.optString("name", "?"), region, o.optDouble("latitude"), o.optDouble("longitude")))
        }
        return out
    }
}

/** "2026-11-04T15:00" -> "15"; "...T06:10" -> "06:10". */
fun hourLabel(iso: String): String = if (iso.length >= 13) iso.substring(11, 13) else iso
fun clock(iso: String): String = if (iso.length >= 16) iso.substring(11, 16) else "--"

fun dayName(date: String, index: Int): String {
    if (index == 0) return "TODAY"
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        SimpleDateFormat("EEE", Locale.getDefault()).format(d!!).uppercase()
    } catch (e: Exception) {
        date
    }
}
