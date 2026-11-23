package ir.homeacs.emascanner

internal object Ema {
    fun calculate(values: List<Double>, period: Int): Double? {
        if (values.size < period || period <= 0) return null
        val alpha = 2.0 / (period + 1.0)
        var result = values.first()
        for (i in 1 until values.size) {
            result = alpha * values[i] + (1.0 - alpha) * result
        }
        return result
    }
}
