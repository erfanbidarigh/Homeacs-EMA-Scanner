package ir.homeacs.emascanner

import org.json.JSONObject
import java.net.URLEncoder

internal class BitunixClient {
    private val base = "https://fapi.bitunix.com"

    data class Ticker(val symbol: String, val last: Double, val quoteVol: Double)

    private fun pacedGet(url: String): String {
        Thread.sleep(150) // 150 ms spacing stays below the documented 10 req/sec limit.
        return HttpJson.get(url)
    }

    fun tickers(): List<Ticker> {
        val json = pacedGet("$base/api/v1/futures/market/tickers")
        val array = HttpJson.dataArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val symbol = item.optString("symbol")
                val last = HttpJson.number(item, "lastPrice", "last", "markPrice")
                val quoteVol = HttpJson.number(item, "quoteVol")
                if (symbol.endsWith("USDT") && last > 0.0 && quoteVol > 0.0) {
                    add(Ticker(symbol, last, quoteVol))
                }
            }
        }
    }

    fun closes(symbol: String, interval: String, limit: Int = 200): List<Double> {
        val encoded = URLEncoder.encode(symbol, "UTF-8")
        val json = pacedGet(
            "$base/api/v1/futures/market/kline?symbol=$encoded&interval=$interval&limit=$limit"
        )
        val array = HttpJson.dataArray(json)
        val candles = ArrayList<Pair<Long, Double>>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val close = HttpJson.number(item, "close")
            val time = HttpJson.number(item, "time").toLong()
            if (close > 0.0) candles.add(time to close)
        }
        return candles.sortedBy { it.first }.map { it.second }
    }
}
