package ir.homeacs.emascanner

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.abs

internal class ScannerEngine(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("scanner_state", Context.MODE_PRIVATE)
    private val bitunix = BitunixClient()

    data class Result(val summary: ScanSummary, val status: String)

    fun scan(volumeThresholdBtc: Double, touchTolerancePct: Double): Result {
        val messages = mutableListOf<String>()
        val signals = mutableListOf<MarketSignal>()
        var checked = 0
        var eligible = 0

        // Bitunix: quoteVol is quote-currency turnover (USDT); convert to BTC notional.
        try {
            val tickers = bitunix.tickers()
            val btcPrice = tickers.firstOrNull { it.symbol == "BTCUSDT" }?.last
                ?: error("قیمت BTCUSDT از Bitunix دریافت نشد؛ تبدیل حجم به BTC ممکن نیست.")
            val candidates = tickers.mapNotNull { ticker ->
                val volumeBtc = ticker.quoteVol / btcPrice
                if (volumeBtc > volumeThresholdBtc) Triple(ticker, volumeBtc, ticker.last) else null
            }
            eligible += candidates.size
            messages += "Bitunix: ${tickers.size} نماد؛ ${candidates.size} نماد با حجم بالاتر از ${fmt(volumeThresholdBtc)} BTC"

            for ((ticker, volumeBtc, lastPrice) in candidates) {
                try {
                    val oneHour = bitunix.closes(ticker.symbol, "1h", 200)
                    val fifteen = bitunix.closes(ticker.symbol, "15m", 200)
                    checked++
                    val h1Fast = Ema.calculate(oneHour, 50) ?: continue
                    val h1Slow = Ema.calculate(oneHour, 200) ?: continue
                    val m15Fast = Ema.calculate(fifteen, 50) ?: continue
                    val m15Slow = Ema.calculate(fifteen, 200) ?: continue

                    val bullish = h1Fast > h1Slow && m15Fast > m15Slow
                    val bearish = h1Fast < h1Slow && m15Fast < m15Slow
                    val tolerance = touchTolerancePct / 100.0
                    val distance = abs(lastPrice - m15Slow) / m15Slow

                    val keyLong = "bitunix:${ticker.symbol}:LONG"
                    val keyShort = "bitunix:${ticker.symbol}:SHORT"
                    // Rearm independently of the current trend filter after price has moved away.
                    if (lastPrice > m15Slow * 1.005) prefs.edit().putBoolean(keyLong, false).apply()
                    if (lastPrice < m15Slow * 0.995) prefs.edit().putBoolean(keyShort, false).apply()

                    if (bullish && distance <= tolerance && !prefs.getBoolean(keyLong, false)) {
                        signals += MarketSignal("Bitunix", ticker.symbol, "LONG", lastPrice, m15Slow, volumeBtc)
                        prefs.edit().putBoolean(keyLong, true).apply()
                    }
                    if (bearish && distance <= tolerance && !prefs.getBoolean(keyShort, false)) {
                        signals += MarketSignal("Bitunix", ticker.symbol, "SHORT", lastPrice, m15Slow, volumeBtc)
                        prefs.edit().putBoolean(keyShort, true).apply()
                    }
                    // Respect the documented Bitunix rate limit (10 requests/sec/IP).
                    Thread.sleep(180)
                } catch (e: Exception) {
                    messages += "Bitunix ${ticker.symbol}: ${e.message ?: "خطای دریافت دیتا"}"
                }
            }
        } catch (e: Exception) {
            messages += "خطای Bitunix: ${e.message ?: "اتصال ناموفق"}"
        }


        val summary = ScanSummary(checked, eligible, signals, messages)
        return Result(summary, messages.joinToString("\n"))
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.0f", value)
}
