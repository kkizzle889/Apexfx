package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SmcSettings
import com.example.domain.SmcChartSnapshot
import com.example.domain.TradingSession
import com.example.domain.formatPrice
import com.example.ui.theme.AmberPoc
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SmcChartCanvas(
    snapshot: SmcChartSnapshot,
    settings: SmcSettings,
    visibleCandleCount: Int = 46,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var scrollOffsetBars by remember(snapshot.instrument.symbol, snapshot.timeframe) { mutableFloatStateOf(0f) }
    var selectedCandleIdx by remember(snapshot.instrument.symbol, snapshot.timeframe) { mutableStateOf<Int?>(null) }

    val candles = snapshot.candles
    val maxOffset = (candles.size - visibleCandleCount).coerceAtLeast(0).toFloat()
    val clampedOffset = scrollOffsetBars.coerceIn(0f, maxOffset)
    val endIdx = (candles.size - clampedOffset.roundToInt()).coerceIn(min(visibleCandleCount, candles.size), candles.size)
    val startIdx = (endIdx - visibleCandleCount).coerceAtLeast(0)
    val visibleCandles = candles.subList(startIdx, endIdx)

    Box(
        modifier = modifier
            .background(ObsidianBg)
            .pointerInput(candles.size) {
                detectHorizontalDragGestures { _, dragAmount ->
                    scrollOffsetBars = (scrollOffsetBars + dragAmount / 18f).coerceIn(0f, maxOffset)
                }
            }
            .pointerInput(visibleCandles.size, startIdx) {
                detectTapGestures { tapOffset ->
                    if (visibleCandles.isEmpty()) return@detectTapGestures
                    val chartWidth = size.width * 0.78f
                    val step = chartWidth / visibleCandles.size
                    val localIdx = (tapOffset.x / step).toInt().coerceIn(0, visibleCandles.lastIndex)
                    val globalIdx = startIdx + localIdx
                    selectedCandleIdx = if (selectedCandleIdx == globalIdx) null else globalIdx
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (visibleCandles.isEmpty()) return@Canvas

            val chartWidth = size.width * 0.76f
            val vpWidth = size.width * 0.13f
            val vpLeft = chartWidth + 2f
            val priceAxisLeft = vpLeft + vpWidth + 4f
            val topPad = 18.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(40f)

            var rawMin = visibleCandles.minOf { it.low }
            var rawMax = visibleCandles.maxOf { it.high }
            snapshot.latestSignal?.let { sig ->
                if (sig.symbol == snapshot.instrument.symbol) {
                    rawMin = min(rawMin, min(sig.atrTrailingStop15, sig.tp3))
                    rawMax = max(rawMax, max(sig.atrTrailingStop15, sig.tp3))
                }
            }
            val padRange = (rawMax - rawMin).coerceAtLeast(snapshot.instrument.pipSize * 10) * 0.10
            val minPrice = rawMin - padRange
            val maxPrice = rawMax + padRange
            val priceSpan = (maxPrice - minPrice).coerceAtLeast(1e-6)

            fun priceToY(p: Double): Float {
                val norm = ((p - minPrice) / priceSpan).toFloat()
                return topPad + chartHeight * (1f - norm)
            }

            fun idxToX(globalIdx: Int): Float {
                val local = (globalIdx - startIdx).toFloat()
                val slotWidth = chartWidth / visibleCandles.size.toFloat()
                return local * slotWidth + slotWidth / 2f
            }

            // 1. Subtle horizontal price grid
            val gridSteps = 5
            for (g in 0..gridSteps) {
                val y = topPad + (chartHeight / gridSteps) * g
                drawLine(
                    color = HairlineBorder.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(priceAxisLeft - 4f, y),
                    strokeWidth = 1f
                )
                val levelPrice = maxPrice - (priceSpan / gridSteps) * g
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatPrice(levelPrice, snapshot.instrument.decimals),
                    topLeft = Offset(priceAxisLeft, y - 7.dp.toPx()),
                    style = TextStyle(
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = JetBrainsMonoFamily
                    )
                )
            }

            val slotWidth = chartWidth / visibleCandles.size.toFloat()

            // 2. Killzone Boxes
            if (settings.showKillzones) {
                for (kz in snapshot.killzones) {
                    val sIdx = max(kz.startIndex, startIdx)
                    val eIdx = min(kz.endIndex, endIdx - 1)
                    if (sIdx <= eIdx) {
                        val left = (sIdx - startIdx) * slotWidth
                        val right = (eIdx - startIdx + 1) * slotWidth
                        val top = priceToY(kz.highPrice)
                        val bottom = priceToY(kz.lowPrice)
                        val boxColor = if (kz.session == TradingSession.LONDON) CyanPulse else VioletAi
                        drawRect(
                            color = boxColor.copy(alpha = 0.08f),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(4f), (bottom - top).coerceAtLeast(4f))
                        )
                        drawRect(
                            color = boxColor.copy(alpha = 0.25f),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(4f), (bottom - top).coerceAtLeast(4f)),
                            style = Stroke(width = 1f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = if (kz.session == TradingSession.LONDON) "LDN KZ" else "NY KZ",
                            topLeft = Offset(left + 4.dp.toPx(), top + 2.dp.toPx()),
                            style = TextStyle(color = boxColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 3. Fair Value Gaps (FVG)
            if (settings.showFvg) {
                for (fvg in snapshot.fairValueGaps) {
                    val sIdx = max(fvg.startIndex, startIdx)
                    val eIdx = min(fvg.endIndex, endIdx - 1)
                    if (sIdx <= eIdx) {
                        val left = (sIdx - startIdx) * slotWidth
                        val right = (eIdx - startIdx + 1) * slotWidth
                        val top = priceToY(fvg.topPrice)
                        val bottom = priceToY(fvg.bottomPrice)
                        val c = if (fvg.isBullish) CyanPulse else AmberPoc
                        drawRect(
                            color = c.copy(alpha = 0.14f),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(6f), (bottom - top).coerceAtLeast(3f))
                        )
                    }
                }
            }

            // 4. Institutional Order Blocks (OB)
            if (settings.showOrderBlocks) {
                for (ob in snapshot.orderBlocks) {
                    val sIdx = max(ob.startIndex, startIdx)
                    val eIdx = min(ob.endIndex, endIdx - 1)
                    if (sIdx <= eIdx) {
                        val left = (sIdx - startIdx) * slotWidth
                        val right = chartWidth
                        val top = priceToY(ob.topPrice)
                        val bottom = priceToY(ob.bottomPrice)
                        val baseColor = if (ob.isBullish) EmeraldBull else CrimsonBear
                        val fillAlpha = if (ob.mitigated) 0.08f else 0.19f
                        drawRect(
                            color = baseColor.copy(alpha = fillAlpha),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(4f))
                        )
                        drawRect(
                            color = baseColor.copy(alpha = if (ob.mitigated) 0.35f else 0.70f),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(10f), (bottom - top).coerceAtLeast(4f)),
                            style = Stroke(width = 1.2f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = if (ob.isBullish) "Bull OB" else "Bear OB",
                            topLeft = Offset(left + 4.dp.toPx(), top + 2.dp.toPx()),
                            style = TextStyle(color = baseColor, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // 5. Market Structure Breaks (BOS / CHoCH)
            if (settings.showStructureBreaks) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                for (sb in snapshot.structureBreaks) {
                    if (sb.endIndex in startIdx until endIdx) {
                        val x1 = idxToX(max(sb.startIndex, startIdx))
                        val x2 = idxToX(sb.endIndex)
                        val y = priceToY(sb.levelPrice)
                        val c = if (sb.isBullish) EmeraldBull else CrimsonBear
                        drawLine(
                            color = c.copy(alpha = 0.8f),
                            start = Offset(x1, y),
                            end = Offset(x2, y),
                            strokeWidth = 1.5f,
                            pathEffect = dash
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = sb.type,
                            topLeft = Offset((x1 + x2) / 2f - 12.dp.toPx(), y - 11.dp.toPx()),
                            style = TextStyle(color = c, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 6. EMA 20 / VWAP Overlay when ATR/Technicals enabled
            if (settings.showAtrBands && visibleCandles.size > 4) {
                val emaPath = Path()
                var ema = visibleCandles.first().close
                val k = 2.0 / 13.0
                visibleCandles.forEachIndexed { idx, c ->
                    ema = c.close * k + ema * (1.0 - k)
                    val x = idx * slotWidth + slotWidth / 2f
                    val y = priceToY(ema)
                    if (idx == 0) emaPath.moveTo(x, y) else emaPath.lineTo(x, y)
                }
                drawPath(
                    path = emaPath,
                    color = CyanPulse.copy(alpha = 0.7f),
                    style = Stroke(width = 1.6f)
                )
            }

            // 7. Candlesticks
            val candleWidth = (slotWidth * 0.62f).coerceIn(3f, 16f)
            visibleCandles.forEachIndexed { localIdx, c ->
                val centerX = localIdx * slotWidth + slotWidth / 2f
                val color = if (c.isBullish) EmeraldBull else CrimsonBear
                val highY = priceToY(c.high)
                val lowY = priceToY(c.low)
                val openY = priceToY(c.open)
                val closeY = priceToY(c.close)

                drawLine(
                    color = color,
                    start = Offset(centerX, highY),
                    end = Offset(centerX, lowY),
                    strokeWidth = 1.5f
                )

                val bodyTop = min(openY, closeY)
                val bodyHeight = max(abs(closeY - openY), 2f)
                drawRect(
                    color = color,
                    topLeft = Offset(centerX - candleWidth / 2f, bodyTop),
                    size = Size(candleWidth, bodyHeight)
                )
            }

            // 8. Liquidity Sweep Markers
            if (settings.showLiquiditySweeps) {
                for (sweep in snapshot.liquiditySweeps) {
                    if (sweep.candleIndex in startIdx until endIdx) {
                        val x = idxToX(sweep.candleIndex)
                        val y = priceToY(sweep.sweptPrice)
                        val markerColor = if (sweep.isBuySideLiquidity) CrimsonBear else EmeraldBull
                        val offsetY = if (sweep.isBuySideLiquidity) -12.dp.toPx() else 4.dp.toPx()
                        drawCircle(
                            color = markerColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = if (sweep.isBuySideLiquidity) "BSL×" else "SSL×",
                            topLeft = Offset(x - 12.dp.toPx(), y + offsetY),
                            style = TextStyle(color = markerColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 9. Session Volume Profile + POC Line
            if (settings.showSessionPoc && snapshot.volumeProfile.bins.isNotEmpty()) {
                val maxBinVol = snapshot.volumeProfile.bins.maxOf { it.totalVolume }.coerceAtLeast(1.0)
                for (bin in snapshot.volumeProfile.bins) {
                    val yHigh = priceToY(bin.priceHigh)
                    val yLow = priceToY(bin.priceLow)
                    val barHeight = (yLow - yHigh).coerceAtLeast(2f)
                    val barLen = ((bin.totalVolume / maxBinVol).toFloat() * vpWidth).coerceAtLeast(2f)
                    val barColor = when {
                        bin.isPoc -> AmberPoc
                        bin.inValueArea -> CyanPulse.copy(alpha = 0.50f)
                        else -> TextMuted.copy(alpha = 0.25f)
                    }
                    drawRect(
                        color = barColor,
                        topLeft = Offset(vpLeft + vpWidth - barLen, yHigh),
                        size = Size(barLen, barHeight * 0.82f)
                    )
                }

                val pocY = priceToY(snapshot.volumeProfile.pocPrice)
                drawLine(
                    color = AmberPoc,
                    start = Offset(0f, pocY),
                    end = Offset(vpLeft + vpWidth, pocY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "POC",
                    topLeft = Offset(4.dp.toPx(), pocY - 12.dp.toPx()),
                    style = TextStyle(color = AmberPoc, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }

            // 10. Signal TP1-TP3 and 1.5x ATR Trailing Stop lines
            snapshot.latestSignal?.let { sig ->
                if (sig.symbol == snapshot.instrument.symbol) {
                    val levels = listOf(
                        Triple(sig.atrTrailingStop15, CrimsonBear, "1.5 ATR SL"),
                        Triple(sig.entryPrice, TextPrimary, "ENTRY"),
                        Triple(sig.tp1, EmeraldBull.copy(alpha = 0.75f), "TP1"),
                        Triple(sig.tp2, EmeraldBull.copy(alpha = 0.88f), "TP2"),
                        Triple(sig.tp3, EmeraldBull, "TP3")
                    )
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    for ((lvlPrice, color, tag) in levels) {
                        val y = priceToY(lvlPrice)
                        if (y in topPad..(topPad + chartHeight)) {
                            drawLine(
                                color = color.copy(alpha = 0.7f),
                                start = Offset(chartWidth * 0.48f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1.2f,
                                pathEffect = dash
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = tag,
                                topLeft = Offset(chartWidth * 0.48f, y - 10.dp.toPx()),
                                style = TextStyle(color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // 11. Selected Crosshair Inspection
            selectedCandleIdx?.let { selIdx ->
                if (selIdx in startIdx until endIdx) {
                    val c = candles[selIdx]
                    val x = idxToX(selIdx)
                    val y = priceToY(c.close)
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.6f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + chartHeight),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.6f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }
            }
        }

        // Crosshair floating OHLCV HUD
        selectedCandleIdx?.let { idx ->
            candles.getOrNull(idx)?.let { c ->
                val d = snapshot.instrument.decimals
                Surface(
                    color = TerminalSurfaceElevated.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "O:${formatPrice(c.open, d)} H:${formatPrice(c.high, d)} L:${formatPrice(c.low, d)} C:${formatPrice(c.close, d)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
