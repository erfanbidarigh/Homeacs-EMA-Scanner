package ir.homeacs.emascanner

data class MarketSignal(
    val exchange: String,
    val symbol: String,
    val direction: String,
    val lastPrice: Double,
    val ema200: Double,
    val volumeBtc: Double
)

data class ScanSummary(
    val checked: Int,
    val eligible: Int,
    val signals: List<MarketSignal>,
    val messages: List<String>
)
