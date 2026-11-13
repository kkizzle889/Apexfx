package com.example.domain

import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SmcAnalysisEngine {

    fun analyze(
        instrument: InstrumentSpec,
        timeframe: Timeframe,
        candles: List<OhlcvCandle>,
        settings: SmcSettings
    ): SmcChartSnapshot {
        if (candles.size < 20) {
            return SmcChartSnapshot(
                instrument = instrument,
                timeframe = timeframe,
                candles = candles,
                orderBlocks = emptyList(),
                fairValueGaps = emptyList(),
                liquiditySweeps = emptyList(),
                structureBreaks = emptyList(),
                killzones = emptyList(),
                volumeProfile = VolumeProfileSummary(emptyList(), instrument.basePrice, instrument.basePrice, instrument.basePrice, 0.0, 0.0),
                volatility = VolatilityMetrics(0.0, 0.0, 0.0, 0.0, 50.0, VolatilityRegime.NORMAL, emptyMap(), instrument.basePrice, instrument.basePrice, instrument.basePrice),
                latestSignal = null
            )
        }

        val atrSeries = computeAtrSeries(candles, 14)
        val atr14 = atrSeries.lastOrNull() ?: (instrument.volatilityFactor * 1.4)
        val ema20 = computeEma(candles.map { it.close }, 20).lastOrNull() ?: candles.last().close
        val ema50 = computeEma(candles.map { it.close }, 50).lastOrNull() ?: candles.last().close
        val vwap = computeSessionVwap(candles)

        val orderBlocks = detectOrderBlocks(candles, atr14)
        val fvgs = detectFairValueGaps(candles, instrument.pipSize)
        val sweeps = detectLiquiditySweeps(candles)
        val structureBreaks = detectStructureBreaks(candles)
        val killzones = detectKillzones(candles)
        val volumeProfile = computeSessionVolumeProfile(candles, binsCount = 22)
        val volatility = computeVolatilityMetrics(instrument, candles, atrSeries, atr14, ema20, ema50, vwap)

        val signal = evaluateEntrySignal(
            instrument = instrument,
            timeframe = timeframe,
            candles = candles,
            orderBlocks = orderBlocks,
            fvgs = fvgs,
            sweeps = sweeps,
            structureBreaks = structureBreaks,
            volumeProfile = volumeProfile,
            atr14 = atr14,
            ema20 = ema20,
            ema50 = ema50,
            settings = settings
        )

        return SmcChartSnapshot(
            instrument = instrument,
            timeframe = timeframe,
            candles = candles,
            orderBlocks = orderBlocks,
            fairValueGaps = fvgs,
            liquiditySweeps = sweeps,
            structureBreaks = structureBreaks,
            killzones = killzones,
            volumeProfile = volumeProfile,
            volatility = volatility,
            latestSignal = signal
        )
    }

    fun computeAtrSeries(candles: List<OhlcvCandle>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val trs = DoubleArray(candles.size)
        for (i in candles.indices) {
            val c = candles[i]
            trs[i] = if (i == 0) {
                c.high - c.low
            } else {
                val prevClose = candles[i - 1].close
                max(c.high - c.low, max(abs(c.high - prevClose), abs(c.low - prevClose)))
            }
        }
        val atrs = MutableList(candles.size) { trs[0] }
        var rolling = trs.take(min(period, trs.size)).average()
        for (i in candles.indices) {
            if (i < period) {
                rolling = trs.take(i + 1).average()
            } else {
                rolling = ((rolling * (period - 1)) + trs[i]) / period
            }
            atrs[i] = rolling
        }
        return atrs
    }

    fun computeEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val out = MutableList(values.size) { values[0] }
        var ema = values[0]
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            out[i] = ema
        }
        return out
    }

    private fun computeSessionVwap(candles: List<OhlcvCandle>): Double {
        val slice = candles.takeLast(min(48, candles.size))
        var pvSum = 0.0
        var vSum = 0.0
        for (c in slice) {
            val typical = (c.high + c.low + c.close) / 3.0
            pvSum += typical * c.volume
            vSum += c.volume
        }
        return if (vSum > 0.0) pvSum / vSum else candles.last().close
    }

    private fun detectOrderBlocks(candles: List<OhlcvCandle>, atr14: Double): List<OrderBlock> {
        val blocks = mutableListOf<OrderBlock>()
        val lastIdx = candles.lastIndex
        for (i in 2 until lastIdx - 2) {
            val c = candles[i]
            val next1 = candles[i + 1]
            val next2 = candles[i + 2]
            val impulseUp = (next2.close - c.high) > atr14 * 0.9 && next1.isBullish && next2.isBullish
            val impulseDown = (c.low - next2.close) > atr14 * 0.9 && !next1.isBullish && !next2.isBullish

            if (!c.isBullish && impulseUp) {
                val top = max(c.open, c.high)
                val bottom = min(c.close, c.low)
                val mitigated = (i + 3..lastIdx).any { candles[it].low <= top && candles[it].close >= bottom }
                blocks.add(OrderBlock(i, lastIdx, top, bottom, isBullish = true, mitigated = mitigated))
            } else if (c.isBullish && impulseDown) {
                val top = max(c.close, c.high)
                val bottom = min(c.open, c.low)
                val mitigated = (i + 3..lastIdx).any { candles[it].high >= bottom && candles[it].close <= top }
                blocks.add(OrderBlock(i, lastIdx, top, bottom, isBullish = false, mitigated = mitigated))
            }
        }
        return blocks.takeLast(6)
    }

    private fun detectFairValueGaps(candles: List<OhlcvCandle>, pipSize: Double): List<FairValueGap> {
        val gaps = mutableListOf<FairValueGap>()
        val minGap = pipSize * 2.0
        for (i in 2 until candles.size) {
            val c1 = candles[i - 2]
            val c3 = candles[i]
            if (c3.low > c1.high + minGap) {
                gaps.add(FairValueGap(i - 2, candles.lastIndex, c3.low, c1.high, isBullish = true))
            } else if (c3.high + minGap < c1.low) {
                gaps.add(FairValueGap(i - 2, candles.lastIndex, c1.low, c3.high, isBullish = false))
            }
        }
        return gaps.takeLast(5)
    }

    private fun detectLiquiditySweeps(candles: List<OhlcvCandle>): List<LiquiditySweep> {
        val sweeps = mutableListOf<LiquiditySweep>()
        val lookback = 8
        for (i in lookback until candles.size) {
            val window = candles.subList(i - lookback, i)
            val prevHigh = window.maxOf { it.high }
            val prevLow = window.minOf { it.low }
            val c = candles[i]
            if (c.high > prevHigh && c.close < prevHigh) {
                sweeps.add(LiquiditySweep(i, c.high, isBuySideLiquidity = true, label = "BSL Sweep"))
            } else if (c.low < prevLow && c.close > prevLow) {
                sweeps.add(LiquiditySweep(i, c.low, isBuySideLiquidity = false, label = "SSL Sweep"))
            }
        }
        return sweeps.takeLast(5)
    }

    private fun detectStructureBreaks(candles: List<OhlcvCandle>): List<StructureBreak> {
        val breaks = mutableListOf<StructureBreak>()
        var swingHigh = candles.take(10).maxOf { it.high }
        var swingLow = candles.take(10).minOf { it.low }
        var highIdx = 5
        var lowIdx = 5
        var bullBias = true

        for (i in 10 until candles.size) {
            val c = candles[i]
            if (c.close > swingHigh) {
                val label = if (bullBias) "BOS" else "CHoCH"
                breaks.add(StructureBreak(highIdx, i, swingHigh, isBullish = true, type = label))
                bullBias = true
                swingHigh = c.high
                highIdx = i
            } else if (c.close < swingLow) {
                val label = if (!bullBias) "BOS" else "CHoCH"
                breaks.add(StructureBreak(lowIdx, i, swingLow, isBullish = false, type = label))
                bullBias = false
                swingLow = c.low
                lowIdx = i
            } else {
                if (c.high > swingHigh) {
                    swingHigh = c.high
                    highIdx = i
                }
                if (c.low < swingLow) {
                    swingLow = c.low
                    lowIdx = i
                }
            }
        }
        return breaks.takeLast(6)
    }

    private fun detectKillzones(candles: List<OhlcvCandle>): List<KillzoneBox> {
        val boxes = mutableListOf<KillzoneBox>()
        var start = 0
        while (start < candles.size) {
            val currentSession = candles[start].session
            var end = start
            while (end + 1 < candles.size && candles[end + 1].session == currentSession) {
                end++
            }
            if (end - start >= 3 && (currentSession == TradingSession.LONDON || currentSession == TradingSession.NEW_YORK)) {
                val slice = candles.subList(start, end + 1)
                boxes.add(
                    KillzoneBox(
                        startIndex = start,
                        endIndex = end,
                        highPrice = slice.maxOf { it.high },
                        lowPrice = slice.minOf { it.low },
                        session = currentSession
                    )
                )
            }
            start = end + 1
        }
        return boxes.takeLast(4)
    }

    fun computeSessionVolumeProfile(candles: List<OhlcvCandle>, binsCount: Int = 22): VolumeProfileSummary {
        val sessionSlice = candles.takeLast(min(48, candles.size))
        val minLow = sessionSlice.minOf { it.low }
        val maxHigh = sessionSlice.maxOf { it.high }
        val range = max(maxHigh - minLow, 1e-6)
        val step = range / binsCount

        val buyVols = DoubleArray(binsCount)
        val sellVols = DoubleArray(binsCount)

        for (c in sessionSlice) {
            val mid = (c.high + c.low + c.close) / 3.0
            val binIdx = (((mid - minLow) / step).toInt()).coerceIn(0, binsCount - 1)
            if (c.isBullish) {
                buyVols[binIdx] += c.volume * 0.68
                sellVols[binIdx] += c.volume * 0.32
            } else {
                sellVols[binIdx] += c.volume * 0.68
                buyVols[binIdx] += c.volume * 0.32
            }
        }

        val totals = DoubleArray(binsCount) { buyVols[it] + sellVols[it] }
        val pocIdx = totals.indices.maxByOrNull { totals[it] } ?: 0
        val totalSessionVol = totals.sum().coerceAtLeast(1.0)
        val targetVaVol = totalSessionVol * 0.70

        var vaVol = totals[pocIdx]
        var lowIdx = pocIdx
        var highIdx = pocIdx
        while (vaVol < targetVaVol && (lowIdx > 0 || highIdx < binsCount - 1)) {
            val downVol = if (lowIdx > 0) totals[lowIdx - 1] else -1.0
            val upVol = if (highIdx < binsCount - 1) totals[highIdx + 1] else -1.0
            if (upVol >= downVol && highIdx < binsCount - 1) {
                highIdx++
                vaVol += totals[highIdx]
            } else if (lowIdx > 0) {
                lowIdx--
                vaVol += totals[lowIdx]
            } else {
                break
            }
        }

        val bins = (0 until binsCount).map { idx ->
            val pLow = minLow + idx * step
            val pHigh = pLow + step
            VolumeProfileBin(
                priceMid = (pLow + pHigh) / 2.0,
                priceLow = pLow,
                priceHigh = pHigh,
                buyVolume = buyVols[idx],
                sellVolume = sellVols[idx],
                isPoc = idx == pocIdx,
                inValueArea = idx in lowIdx..highIdx
            )
        }

        val buyTotal = buyVols.sum()
        val sellTotal = sellVols.sum()
        val deltaPct = ((buyTotal - sellTotal) / totalSessionVol) * 100.0

        return VolumeProfileSummary(
            bins = bins,
            pocPrice = bins[pocIdx].priceMid,
            vahPrice = bins[highIdx].priceHigh,
            valPrice = bins[lowIdx].priceLow,
            sessionTotalVolume = totalSessionVol,
            sessionDeltaPercent = deltaPct
        )
    }

    private fun computeVolatilityMetrics(
        instrument: InstrumentSpec,
        candles: List<OhlcvCandle>,
        atrSeries: List<Double>,
        atr14: Double,
        ema20: Double,
        ema50: Double,
        vwap: Double
    ): VolatilityMetrics {
        val avgAtr = atrSeries.takeLast(min(50, atrSeries.size)).average().coerceAtLeast(1e-9)
        val ratio = atr14 / avgAtr
        val regime = when {
            ratio < 0.85 -> VolatilityRegime.COMPRESSED
            ratio > 1.20 -> VolatilityRegime.EXPANSION
            else -> VolatilityRegime.NORMAL
        }
        val rvi = (50.0 + (ratio - 1.0) * 40.0).coerceIn(12.0, 95.0)
        val sessionVolMap = TradingSession.entries.filter { it != TradingSession.LONDON_NY_OVERLAP }
            .associate { session ->
                val subset = candles.filter { it.session == session }
                val avgRange = if (subset.isEmpty()) atr14 else subset.map { it.high - it.low }.average()
                session.label to (avgRange / instrument.pipSize)
            }

        return VolatilityMetrics(
            atr14 = atr14,
            atrPips = atr14 / instrument.pipSize,
            trailingStop15Distance = atr14 * 1.5,
            trailingStop10Distance = atr14 * 1.0,
            relativeVolatilityIndex = rvi,
            regime = regime,
            sessionVolatilityMap = sessionVolMap,
            ema20 = ema20,
            ema50 = ema50,
            vwap = vwap
        )
    }

    private fun evaluateEntrySignal(
        instrument: InstrumentSpec,
        timeframe: Timeframe,
        candles: List<OhlcvCandle>,
        orderBlocks: List<OrderBlock>,
        fvgs: List<FairValueGap>,
        sweeps: List<LiquiditySweep>,
        structureBreaks: List<StructureBreak>,
        volumeProfile: VolumeProfileSummary,
        atr14: Double,
        ema20: Double,
        ema50: Double,
        settings: SmcSettings
    ): SavedSignal? {
        val last = candles.last()
        val lastIdx = candles.lastIndex
        val recentSweep = sweeps.lastOrNull { lastIdx - it.candleIndex <= 6 }
        val recentBreak = structureBreaks.lastOrNull { lastIdx - it.endIndex <= 8 }
        val nearBullOb = orderBlocks.lastOrNull { it.isBullish && last.low <= it.topPrice * 1.002 && last.close >= it.bottomPrice * 0.998 }
        val nearBearOb = orderBlocks.lastOrNull { !it.isBullish && last.high >= it.bottomPrice * 0.998 && last.close <= it.topPrice * 1.002 }

        var bullScore = 0
        var bearScore = 0
        val bullReasons = mutableListOf<String>()
        val bearReasons = mutableListOf<String>()

        if (recentSweep != null) {
            if (!recentSweep.isBuySideLiquidity) {
                bullScore += 30
                bullReasons.add("SSL Sweep")
            } else {
                bearScore += 30
                bearReasons.add("BSL Sweep")
            }
        }
        if (nearBullOb != null) {
            bullScore += 28
            bullReasons.add("Bullish OB")
        }
        if (nearBearOb != null) {
            bearScore += 28
            bearReasons.add("Bearish OB")
        }
        if (recentBreak != null) {
            if (recentBreak.isBullish) {
                bullScore += 20
                bullReasons.add(recentBreak.type)
            } else {
                bearScore += 20
                bearReasons.add(recentBreak.type)
            }
        }
        if (fvgs.any { it.isBullish && last.close >= it.bottomPrice }) {
            bullScore += 10
            bullReasons.add("FVG")
        }
        if (fvgs.any { !it.isBullish && last.close <= it.topPrice }) {
            bearScore += 10
            bearReasons.add("FVG")
        }
        if (ema20 >= ema50 && last.close >= volumeProfile.pocPrice) {
            bullScore += 12
            bullReasons.add("POC+EMA")
        } else if (ema20 < ema50 && last.close <= volumeProfile.pocPrice) {
            bearScore += 12
            bearReasons.add("POC+EMA")
        }

        val isLong = bullScore >= bearScore
        val rawConfidence = max(bullScore, bearScore)
        val meetsCriteria = rawConfidence >= 48 ||
            (settings.alertLiquiditySweeps && recentSweep != null) ||
            (settings.alertOrderBlockRetest && (nearBullOb != null || nearBearOb != null))

        if (!meetsCriteria) return null

        val entry = last.close
        val atr15Dist = atr14 * 1.5
        val atr10Dist = atr14 * 1.0
        val sl15 = if (isLong) entry - atr15Dist else entry + atr15Dist
        val sl10 = if (isLong) entry - atr10Dist else entry + atr10Dist
        val risk = atr15Dist.coerceAtLeast(instrument.pipSize * 4.0)

        val tp1 = if (isLong) entry + risk * 1.5 else entry - risk * 1.5
        val tp2 = if (isLong) entry + risk * 2.5 else entry - risk * 2.5
        val tp3 = if (isLong) entry + risk * 4.0 else entry - risk * 4.0
        val reasons = (if (isLong) bullReasons else bearReasons).ifEmpty { listOf("EMA Momentum", "Session POC") }

        return SavedSignal(
            symbol = instrument.symbol,
            direction = if (isLong) "LONG" else "SHORT",
            timeframe = timeframe.label,
            session = last.session.label,
            setupSummary = reasons.distinct().joinToString(" + "),
            entryPrice = roundToDecimals(entry, instrument.decimals),
            initialStopLoss = roundToDecimals(sl15, instrument.decimals),
            atrTrailingStop15 = roundToDecimals(sl15, instrument.decimals),
            atrTrailingStop10 = roundToDecimals(sl10, instrument.decimals),
            atrValue = roundToDecimals(atr14, instrument.decimals + 1),
            tp1 = roundToDecimals(tp1, instrument.decimals),
            tp2 = roundToDecimals(tp2, instrument.decimals),
            tp3 = roundToDecimals(tp3, instrument.decimals),
            rrTp3 = 4.0,
            confidence = rawConfidence.coerceIn(64, 96),
            status = "ACTIVE"
        )
    }

    fun runBacktest(
        instrument: InstrumentSpec,
        candles: List<OhlcvCandle>,
        config: BacktestConfig
    ): BacktestResult {
        if (candles.size < 40) {
            return BacktestResult(config, 0, 0.0, 0.0, 0.0, 0.0, 0.0, listOf(config.startingBalance), emptyList())
        }

        val atrSeries = computeAtrSeries(candles, 14)
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf(config.startingBalance)
        var equity = config.startingBalance
        var peakEquity = equity
        var maxDd = 0.0
        var grossProfit = 0.0
        var grossLoss = 0.0

        var i = 20
        while (i < candles.size - 12) {
            val c = candles[i]
            if (config.killzoneOnly && c.session == TradingSession.ASIA) {
                i++
                continue
            }

            val window = candles.subList(i - 12, i)
            val prevHigh = window.maxOf { it.high }
            val prevLow = window.minOf { it.low }
            val bullSweep = c.low < prevLow && c.close > prevLow
            val bearSweep = c.high > prevHigh && c.close < prevHigh
            val bullOb = candles[i - 1].let { !it.isBullish && c.close > it.high }
            val bearOb = candles[i - 1].let { it.isBullish && c.close < it.low }
            val bullFvg = candles[i].low > candles[i - 2].high
            val bearFvg = candles[i].high < candles[i - 2].low

            val triggerLong = (!config.requireLiquiditySweep || bullSweep) &&
                (!config.requireOrderBlock || bullOb) &&
                (!config.requireFvg || bullFvg) &&
                c.isBullish

            val triggerShort = (!config.requireLiquiditySweep || bearSweep) &&
                (!config.requireOrderBlock || bearOb) &&
                (!config.requireFvg || bearFvg) &&
                !c.isBullish

            if (!triggerLong && !triggerShort) {
                i++
                continue
            }

            val isLong = triggerLong
            val entry = c.close
            val atr = atrSeries[i].coerceAtLeast(instrument.pipSize * 5.0)
            val stopDist = atr * config.atrTrailMultiplier
            var trailStop = if (isLong) entry - stopDist else entry + stopDist
            val tp3 = if (isLong) entry + stopDist * 4.0 else entry - stopDist * 4.0

            var exitPrice = candles[min(i + 10, candles.lastIndex)].close
            var exitReason = "Time Exit"
            var exitBar = min(i + 10, candles.lastIndex)

            for (j in (i + 1)..min(i + 24, candles.lastIndex)) {
                val bar = candles[j]
                val barAtr = atrSeries[j] * config.atrTrailMultiplier
                if (isLong) {
                    if (bar.low <= trailStop) {
                        exitPrice = trailStop
                        exitReason = if (trailStop > entry) "ATR Trail Lock" else "Stop Loss"
                        exitBar = j
                        break
                    }
                    if (bar.high >= tp3) {
                        exitPrice = tp3
                        exitReason = "TP3 Hit"
                        exitBar = j
                        break
                    }
                    trailStop = max(trailStop, bar.high - barAtr)
                } else {
                    if (bar.high >= trailStop) {
                        exitPrice = trailStop
                        exitReason = if (trailStop < entry) "ATR Trail Lock" else "Stop Loss"
                        exitBar = j
                        break
                    }
                    if (bar.low <= tp3) {
                        exitPrice = tp3
                        exitReason = "TP3 Hit"
                        exitBar = j
                        break
                    }
                    trailStop = min(trailStop, bar.low + barAtr)
                }
                exitPrice = bar.close
                exitBar = j
            }

            val rMultiple = if (isLong) (exitPrice - entry) / stopDist else (entry - exitPrice) / stopDist
            val riskAmount = equity * (config.riskPercent / 100.0)
            val pnl = riskAmount * rMultiple
            equity += pnl
            if (pnl >= 0) grossProfit += pnl else grossLoss += abs(pnl)
            peakEquity = max(peakEquity, equity)
            val dd = if (peakEquity > 0) ((peakEquity - equity) / peakEquity) * 100.0 else 0.0
            maxDd = max(maxDd, dd)
            equityCurve.add(equity)

            trades.add(
                BacktestTrade(
                    index = trades.size + 1,
                    direction = if (isLong) "LONG" else "SHORT",
                    entryPrice = entry,
                    exitPrice = exitPrice,
                    stopLoss = if (isLong) entry - stopDist else entry + stopDist,
                    pnlUsd = pnl,
                    rMultiple = rMultiple,
                    exitReason = exitReason,
                    durationBars = (exitBar - i).coerceAtLeast(1)
                )
            )
            i = exitBar + 2
        }

        val wins = trades.count { it.pnlUsd > 0 }
        val winRate = if (trades.isEmpty()) 0.0 else (wins * 100.0) / trades.size
        val profitFactor = when {
            grossLoss > 0.0 -> grossProfit / grossLoss
            grossProfit > 0.0 -> 9.99
            else -> 0.0
        }
        val expectancy = if (trades.isEmpty()) 0.0 else trades.map { it.rMultiple }.average()

        return BacktestResult(
            config = config,
            totalTrades = trades.size,
            winRatePercent = winRate,
            profitFactor = profitFactor,
            netProfitUsd = equity - config.startingBalance,
            maxDrawdownPercent = maxDd,
            expectancyR = expectancy,
            equityCurve = equityCurve,
            trades = trades
        )
    }

    fun calculatePositionSize(
        instrument: InstrumentSpec,
        accountBalance: Double,
        riskPercent: Double,
        stopDistancePips: Double
    ): PositionSizingResult {
        val safeStopPips = stopDistancePips.coerceAtLeast(1.0)
        val riskUsd = accountBalance * (riskPercent / 100.0)
        val rawStandardLots = riskUsd / (safeStopPips * instrument.pipValuePerStandardLot)
        val standardLots = ((rawStandardLots * 100.0).roundToInt() / 100.0).coerceAtLeast(0.01)
        val miniLots = standardLots * 10.0
        val microLots = standardLots * 100.0
        return PositionSizingResult(
            riskUsd = riskUsd,
            standardLots = standardLots,
            miniLots = miniLots,
            microLots = microLots,
            tp1ProfitUsd = riskUsd * 1.5,
            tp2ProfitUsd = riskUsd * 2.5,
            tp3ProfitUsd = riskUsd * 4.0
        )
    }

    fun roundToDecimals(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals.coerceIn(0, 6)) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }
}

data class PositionSizingResult(
    val riskUsd: Double,
    val standardLots: Double,
    val miniLots: Double,
    val microLots: Double,
    val tp1ProfitUsd: Double,
    val tp2ProfitUsd: Double,
    val tp3ProfitUsd: Double
)
