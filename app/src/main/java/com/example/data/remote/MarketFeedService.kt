package com.example.data.remote

import android.util.Log
import com.example.domain.InstrumentSpec
import com.example.domain.MarketCatalog
import com.example.domain.OhlcvCandle
import com.example.domain.TickQuote
import com.example.domain.Timeframe
import com.example.domain.TradingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MarketFeedService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var feedJob: Job? = null

    private val _quotes = MutableStateFlow<Map<String, TickQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, TickQuote>> = _quotes.asStateFlow()

    private val _isLiveRestConnected = MutableStateFlow(false)
    val isLiveRestConnected: StateFlow<Boolean> = _isLiveRestConnected.asStateFlow()

    private val liveAnchors = mutableMapOf<String, Double>()
    private val sessionOpens = mutableMapOf<String, Double>()
    private val candleBuffers = mutableMapOf<Pair<String, Timeframe>, MutableList<OhlcvCandle>>()
    private val random = Random(System.currentTimeMillis())

    init {
        val now = System.currentTimeMillis()
        val initialMap = mutableMapOf<String, TickQuote>()
        for (spec in MarketCatalog.instruments) {
            liveAnchors[spec.symbol] = spec.basePrice
            sessionOpens[spec.symbol] = spec.basePrice
            val spread = spec.typicalSpreadPips * spec.pipSize
            initialMap[spec.symbol] = TickQuote(
                symbol = spec.symbol,
                bid = spec.basePrice,
                ask = spec.basePrice + spread,
                spreadPips = spec.typicalSpreadPips,
                changePercent = 0.0,
                isTickUp = true,
                isLiveRestAnchored = false,
                timestampMs = now
            )
        }
        _quotes.value = initialMap
    }

    fun startStreaming() {
        if (feedJob?.isActive == true) return
        feedJob = scope.launch {
            refreshLiveFxSpotQuotes()
            var tickCount = 0
            while (isActive) {
                delay(750L)
                tickCount++
                if (tickCount % 80 == 0) {
                    launch { refreshLiveFxSpotQuotes() }
                }
                emitSubSecondTick(tickCount)
            }
        }
    }

    suspend fun refreshLiveFxSpotQuotes() {
        try {
            val response = FxRemoteClient.api.getLatestUsdRates()
            if (response.rates.isNotEmpty()) {
                for (spec in MarketCatalog.instruments) {
                    val code = spec.fxCode ?: continue
                    val usdToCode = response.rates[code] ?: continue
                    if (usdToCode > 0.0) {
                        val spot = if (spec.invertedUsdPair) 1.0 / usdToCode else usdToCode
                        liveAnchors[spec.symbol] = spot
                        if (sessionOpens[spec.symbol] == spec.basePrice) {
                            sessionOpens[spec.symbol] = spot
                        }
                    }
                }
                _isLiveRestConnected.value = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live FX REST poll skipped (using calibrated feed): ${e.message}")
        }
    }

    private fun emitSubSecondTick(tickCount: Int) {
        val now = System.currentTimeMillis()
        val updated = _quotes.value.toMutableMap()

        for (spec in MarketCatalog.instruments) {
            val prev = updated[spec.symbol]
            val prevBid = prev?.bid ?: liveAnchors[spec.symbol] ?: spec.basePrice
            val anchor = liveAnchors[spec.symbol] ?: spec.basePrice
            val openPrice = sessionOpens[spec.symbol] ?: anchor

            // Mean-reverting micro-tick anchored around live FX rate with realistic institutional bursts
            val burst = if (tickCount % 9 == 0) 1.65 else 0.75
            val noise = (random.nextDouble() - 0.5) * spec.volatilityFactor * 0.24 * burst
            val reversion = (anchor - prevBid) * 0.04
            val nextBid = (prevBid + noise + reversion).coerceAtLeast(spec.pipSize * 10)
            val spreadPips = (spec.typicalSpreadPips * (0.85 + random.nextDouble() * 0.35))
            val spread = spreadPips * spec.pipSize
            val changePct = ((nextBid - openPrice) / openPrice) * 100.0

            updated[spec.symbol] = TickQuote(
                symbol = spec.symbol,
                bid = nextBid,
                ask = nextBid + spread,
                spreadPips = spreadPips,
                changePercent = changePct,
                isTickUp = nextBid >= prevBid,
                isLiveRestAnchored = spec.fxCode != null && _isLiveRestConnected.value,
                timestampMs = now
            )

            // Update active candles across cached timeframes
            Timeframe.entries.forEach { tf ->
                candleBuffers[spec.symbol to tf]?.let { list ->
                    if (list.isNotEmpty()) {
                        val last = list.last()
                        val updatedLast = last.copy(
                            high = max(last.high, nextBid),
                            low = min(last.low, nextBid),
                            close = nextBid,
                            volume = last.volume + abs(noise / spec.pipSize) * 12.0 + 35.0
                        )
                        list[list.lastIndex] = updatedLast
                    }
                }
            }
        }
        _quotes.value = updated
    }

    fun getCandles(symbol: String, timeframe: Timeframe, count: Int = 110): List<OhlcvCandle> {
        val key = symbol to timeframe
        val existing = candleBuffers[key]
        if (existing != null && existing.size >= count) {
            return existing.toList()
        }
        val spec = MarketCatalog.bySymbol(symbol)
        val anchor = liveAnchors[symbol] ?: _quotes.value[symbol]?.bid ?: spec.basePrice
        val generated = generateInstitutionalHistory(spec, timeframe, anchor, count)
        candleBuffers[key] = generated.toMutableList()
        return generated
    }

    fun getBacktestSeries(symbol: String, timeframe: Timeframe, count: Int = 240): List<OhlcvCandle> {
        val spec = MarketCatalog.bySymbol(symbol)
        val anchor = liveAnchors[symbol] ?: _quotes.value[symbol]?.bid ?: spec.basePrice
        return generateInstitutionalHistory(spec, timeframe, anchor, count)
    }

    private fun generateInstitutionalHistory(
        spec: InstrumentSpec,
        timeframe: Timeframe,
        targetEndPrice: Double,
        count: Int
    ): List<OhlcvCandle> {
        val seed = spec.symbol.hashCode().toLong() * 31L + timeframe.minutes.toLong()
        val rng = Random(seed)
        val tfMult = sqrt(timeframe.minutes / 15.0)
        val stepVol = spec.volatilityFactor * tfMult

        val rawBars = ArrayList<OhlcvCandle>(count)
        var price = targetEndPrice - stepVol * 2.5
        val now = System.currentTimeMillis()
        val barMillis = timeframe.minutes * 60_000L

        for (i in 0 until count) {
            val wave = sin(i * 0.24) * stepVol * 0.65 + cos(i * 0.09) * stepVol * 0.45
            val impulse = if (i % 17 == 0) (if (i % 34 == 0) 1.8 else -1.8) * stepVol else 0.0
            val delta = wave + impulse + (rng.nextDouble() - 0.5) * stepVol * 1.3
            val open = price
            val close = (open + delta).coerceAtLeast(spec.pipSize * 20)

            // Occasional liquidity sweep wicks around swing points
            val wickScale = if (i % 11 == 0) 1.9 else 0.85
            val high = max(open, close) + rng.nextDouble() * stepVol * wickScale
            val low = min(open, close) - rng.nextDouble() * stepVol * wickScale

            val session = when ((i % 36)) {
                in 0..11 -> TradingSession.ASIA
                in 12..23 -> TradingSession.LONDON
                else -> TradingSession.NEW_YORK
            }
            val sessionVolMult = when (session) {
                TradingSession.ASIA -> 0.7
                TradingSession.LONDON -> 1.35
                TradingSession.NEW_YORK, TradingSession.LONDON_NY_OVERLAP -> 1.55
            }
            val volume = (800.0 + rng.nextDouble() * 1400.0 + (abs(delta) / stepVol) * 900.0) * sessionVolMult

            rawBars.add(
                OhlcvCandle(
                    index = i,
                    timestampMs = now - (count - i) * barMillis,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    session = session
                )
            )
            price = close
        }

        // Shift final close smoothly onto live anchor price
        val offset = targetEndPrice - rawBars.last().close
        return rawBars.map { c ->
            c.copy(
                open = (c.open + offset).coerceAtLeast(spec.pipSize * 10),
                high = (c.high + offset).coerceAtLeast(spec.pipSize * 10),
                low = (c.low + offset).coerceAtLeast(spec.pipSize * 10),
                close = (c.close + offset).coerceAtLeast(spec.pipSize * 10)
            )
        }
    }

    private companion object {
        const val TAG = "MarketFeedService"
    }
}
