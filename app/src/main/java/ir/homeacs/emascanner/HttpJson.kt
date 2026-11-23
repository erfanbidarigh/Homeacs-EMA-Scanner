package ir.homeacs.emascanner

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object HttpJson {
    fun get(url: String, timeoutMs: Int = 12_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HomeacsEmaScanner/1.0")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("HTTP $code: ${body.take(180)}")
            body
        } finally {
            connection.disconnect()
        }
    }

    fun dataArray(raw: String): JSONArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val obj = JSONObject(trimmed)
        if (obj.has("code") && obj.optInt("code", 0) != 0) {
            error("API code=${obj.opt("code")}: ${obj.optString("msg")}")
        }
        val data = obj.opt("data")
        return when (data) {
            is JSONArray -> data
            is JSONObject -> JSONArray().put(data)
            else -> JSONArray()
        }
    }

    fun number(obj: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            val value = obj.opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return 0.0
    }
}
