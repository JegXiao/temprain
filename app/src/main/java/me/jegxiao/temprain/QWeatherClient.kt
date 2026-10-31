package me.jegxiao.temprain

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 和风天气 (QWeather) 拉取与解析，复刻 cpp 的 parseQWeatherNow / parseQWeather24h 逻辑。
 * Key 为空时抛 MissingKeyException，由调用方提示。
 */
object QWeatherClient {

    class MissingKeyException : Exception("API Key 为空，请在 APP 内填入")

    /** 请求 devapi.qweather.com/v7/{path}，返回 JSON body（失败抛异常） */
    fun get(path: String, cityIdx: Int, key: String): String {
        if (key.isBlank()) throw MissingKeyException()
        val city = CITIES[cityIdx]
        val loc = String.format(java.util.Locale.US, "%.2f,%.2f", city.lon, city.lat)
        val url = "https://devapi.qweather.com/v7/$path?location=$loc&key=$key"
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val stream = if (conn.contentEncoding?.contains("gzip", true) == true)
                GZIPInputStream(conn.inputStream) else conn.inputStream
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解析 now 实时天气：temp / text / precip，写入 state（复刻 parseQWeatherNow）
     */
    fun parseNow(payload: String, s: WeatherState) {
        val json = JSONObject(payload)
        val now = json.optJSONObject("now") ?: return
        now.optString("temp").toIntOrNull()?.let { s.currentTemp = it }
        now.optString("text").takeIf { it.isNotEmpty() }?.let { s.nowText = it }
        now.optString("precip").toFloatOrNull()?.let { s.currentRainNow = it }
    }

    /**
     * 解析 24h 预报：按每个条目 fxTime 的"实际小时(0-23)"索引存储（复刻 parseQWeather24h）
     * fxTime 形如 "2026-10-23T14:00+08:00"，HH 位于下标 11..12
     */
    fun parse24h(payload: String, s: WeatherState) {
        for (i in 0 until 24) { s.precip[i] = 0f; s.snow[i] = 0f }
        val arr = JSONObject(payload).optJSONArray("hourly") ?: return
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val fxTime = item.optString("fxTime")
            val text = item.optString("text")
            if (fxTime.length < 13) continue
            val h = fxTime.substring(11, 13).toIntOrNull() ?: continue
            if (h in 0..23) {
                s.precip[h] = if (text.contains("雨")) 1f else 0f
                s.snow[h] = if (text.contains("雪")) 1f else 0f
            }
        }
    }
}
