package com.example.domain

import com.example.data.model.SavedSignal

enum class AssetClass(val label: String) {
    FOREX_MAJOR("FX Major"),
    METALS("Metal"),
    ENERGY("Energy"),
    INDEX("Index CFD")
}

enum class Timeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M5("5m", 5),
    M15("15m", 15),
    H1("1H", 60),
    H4("4H", 240)
}

enum class TradingSession(val label: String, val startHourUtc: Int, val endHourUtc: Int) {
    ASIA("Asia Range", 0, 7),
    LONDON("London Killzone", 7, 11),
    NEW_YORK("NY Killzone", 12, 16),
    LONDON_NY_OVERLAP("LDN/NY Overlap", 12, 16)
}

data class InstrumentSpec(
    val symbol: String,
    val displayName: String,
    val assetClass: AssetClass,
    val basePrice: Double,
    val decimals: Int,
    val pipSize: Double,
    val pipValuePerStandardLot: Double,
    val typicalSpreadPips: Double,
    val volatilityFactor: Double,
    val fxCode: String? = null,
    val invertedUsdPair: Boolean = false
)

object MarketCatalog {
    val instruments: List<InstrumentSpec> = listOf(
        InstrumentSpec("EUR/USD", "Euro / US Dollar", AssetClass.FOREX_MAJOR, 1.08420, 5, 0.0001, 10.0, 0.4, 0.00075, fxCode = "EUR", invertedUsdPair = true),
        InstrumentSpec("GBP/USD", "British Pound / US Dollar", AssetClass.FOREX_MAJOR, 1.27350, 5, 0.0001, 10.0, 0.6, 0.00095, fxCode = "GBP", invertedUsdPair = true),
        InstrumentSpec("USD/JPY", "US Dollar / Japanese Yen", AssetClass.FOREX_MAJOR, 154.280, 3, 0.01, 6.48, 0.5, 0.115, fxCode = "JPY", invertedUsdPair = false),
        InstrumentSpec("USD/CHF", "US Dollar / Swiss Franc", AssetClass.FOREX_MAJOR, 0.88140, 5, 0.0001, 11.34, 0.7, 0.00065, fxCode = "CHF", invertedUsdPair = false),
        InstrumentSpec("AUD/USD", "Australian Dollar / US Dollar", AssetClass.FOREX_MAJOR, 0.65480, 5, 0.0001, 10.0, 0.6, 0.00062, fxCode = "AUD", invertedUsdPair = true),
        InstrumentSpec("USD/CAD", "US Dollar / Canadian Dollar", AssetClass.FOREX_MAJOR, 1.39420, 5, 0.0001, 7.17, 0.7, 0.00072, fxCode = "CAD", invertedUsdPair = false),
        InstrumentSpec("NZD/USD", "New Zealand Dollar / US Dollar", AssetClass.FOREX_MAJOR, 0.59260, 5, 0.0001, 10.0, 0.9, 0.00058, fxCode = "NZD", invertedUsdPair = true),
        InstrumentSpec("XAU/USD", "Gold Spot CFD", AssetClass.METALS, 2642.50, 2, 0.10, 10.0, 1.8, 3.20),
        InstrumentSpec("WTI/USD", "WTI Crude Oil CFD", AssetClass.ENERGY, 69.85, 2, 0.01, 10.0, 2.5, 0.24),
        InstrumentSpec("NAS100", "Nasdaq 100 Index CFD", AssetClass.INDEX, 20845.5, 1, 1.0, 1.0, 1.4, 24.5),
        InstrumentSpec("SPX500", "S&P 500 Index CFD", AssetClass.INDEX, 5982.25, 2, 0.25, 1.25, 0.5, 5.8),
        InstrumentSpec("US30", "Dow Jones 30 Index CFD", AssetClass.INDEX, 43910.0, 1, 1.0, 1.0, 2.0, 42.0)
    )

    fun bySymbol(symbol: String): InstrumentSpec =
        instruments.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: instruments.first()
}

data class TickQuote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val spreadPips: Double,
    val changePercent: Double,
    val isTickUp: Boolean,
    val isLiveRestAnchored: Boolean,
    val timestampMs: Long
)

data class OhlcvCandle(
    val index: Int,
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val session: TradingSession
) {
    val isBullish: Boolean get() = close >= open
}

data class OrderBlock(
    val startIndex: Int,
    val endIndex: Int,
    val topPrice: Double,
    val bottomPrice: Double,
    val isBullish: Boolean,
    val mitigated: Boolean
)

data class FairValueGap(
    val startIndex: Int,
    val endIndex: Int,
    val topPrice: Double,
    val bottomPrice: Double,
    val isBullish: Boolean
)

data class LiquiditySweep(
    val candleIndex: Int,
    val sweptPrice: Double,
    val isBuySideLiquidity: Boolean,
    val label: String
)

data class StructureBreak(
    val startIndex: Int,
    val endIndex: Int,
    val levelPrice: Double,
    val isBullish: Boolean,
    val type: String
)

data class KillzoneBox(
    val startIndex: Int,
    val endIndex: Int,
    val highPrice: Double,
    val lowPrice: Double,
    val session: TradingSession
)

data class VolumeProfileBin(
    val priceMid: Double,
    val priceLow: Double,
    val priceHigh: Double,
    val buyVolume: Double,
    val sellVolume: Double,
    val isPoc: Boolean,
    val inValueArea: Boolean
) {
    val totalVolume: Double get() = buyVolume + sellVolume
}

data class VolumeProfileSummary(
    val bins: List<VolumeProfileBin>,
    val pocPrice: Double,
    val vahPrice: Double,
    val valPrice: Double,
    val sessionTotalVolume: Double,
    val sessionDeltaPercent: Double
)

enum class VolatilityRegime(val label: String) {
    COMPRESSED("Compressed"),
    NORMAL("Balanced"),
    EXPANSION("Expansion")
}

data class VolatilityMetrics(
    val atr14: Double,
    val atrPips: Double,
    val trailingStop15Distance: Double,
    val trailingStop10Distance: Double,
    val relativeVolatilityIndex: Double,
    val regime: VolatilityRegime,
    val sessionVolatilityMap: Map<String, Double>,
    val ema20: Double,
    val ema50: Double,
    val vwap: Double
)

data class SmcChartSnapshot(
    val instrument: InstrumentSpec,
    val timeframe: Timeframe,
    val candles: List<OhlcvCandle>,
    val orderBlocks: List<OrderBlock>,
    val fairValueGaps: List<FairValueGap>,
    val liquiditySweeps: List<LiquiditySweep>,
    val structureBreaks: List<StructureBreak>,
    val killzones: List<KillzoneBox>,
    val volumeProfile: VolumeProfileSummary,
    val volatility: VolatilityMetrics,
    val latestSignal: SavedSignal?
)

data class BacktestConfig(
    val symbol: String = "XAU/USD",
    val timeframe: Timeframe = Timeframe.M15,
    val requireOrderBlock: Boolean = true,
    val requireLiquiditySweep: Boolean = true,
    val requireFvg: Boolean = false,
    val killzoneOnly: Boolean = true,
    val atrTrailMultiplier: Double = 1.5,
    val riskPercent: Double = 1.5,
    val startingBalance: Double = 25_000.0,
    val lookbackBars: Int = 240
)

data class BacktestTrade(
    val index: Int,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double,
    val pnlUsd: Double,
    val rMultiple: Double,
    val exitReason: String,
    val durationBars: Int
)

data class BacktestResult(
    val config: BacktestConfig,
    val totalTrades: Int,
    val winRatePercent: Double,
    val profitFactor: Double,
    val netProfitUsd: Double,
    val maxDrawdownPercent: Double,
    val expectancyR: Double,
    val equityCurve: List<Double>,
    val trades: List<BacktestTrade>
)

fun formatPrice(price: Double, decimals: Int): String =
    String.format(java.util.Locale.US, "%.${decimals}f", price)
