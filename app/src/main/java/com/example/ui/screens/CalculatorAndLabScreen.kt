package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BacktestConfig
import com.example.domain.BacktestResult
import com.example.domain.MarketCatalog
import com.example.domain.PositionSizingResult
import com.example.domain.SmcChartSnapshot
import com.example.domain.Timeframe
import com.example.domain.VolatilityRegime
import com.example.domain.formatPrice
import com.example.ui.theme.AmberPoc
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.EmeraldBullDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAi
import com.example.viewmodel.CalculatorDraft
import com.example.viewmodel.UiState
import java.util.Locale

@Composable
fun CalculatorAndLabScreen(
    draft: CalculatorDraft,
    sizingResult: PositionSizingResult,
    chartSnapshot: SmcChartSnapshot,
    backtestConfig: BacktestConfig,
    backtestResultState: UiState<BacktestResult>,
    onUpdateDraft: ((CalculatorDraft) -> CalculatorDraft) -> Unit,
    onSelectSymbolForCalc: (String) -> Unit,
    onUpdateBacktestConfig: ((BacktestConfig) -> BacktestConfig) -> Unit,
    onRunBacktest: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Lot & R:R", "Volatility", "Backtester")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = TerminalSurface,
            contentColor = EmeraldBull
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == index) EmeraldBull else TextSecondary
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> LotCalculatorPane(draft, sizingResult, chartSnapshot, onUpdateDraft, onSelectSymbolForCalc)
            1 -> VolatilityLabPane(chartSnapshot)
            2 -> BacktesterPane(backtestConfig, backtestResultState, onUpdateBacktestConfig, onRunBacktest)
        }
    }
}

@Composable
private fun LotCalculatorPane(
    draft: CalculatorDraft,
    sizingResult: PositionSizingResult,
    chartSnapshot: SmcChartSnapshot,
    onUpdateDraft: ((CalculatorDraft) -> CalculatorDraft) -> Unit,
    onSelectSymbolForCalc: (String) -> Unit
) {
    val spec = MarketCatalog.bySymbol(draft.symbol)
    val d = spec.decimals
    val atrPips = chartSnapshot.volatility.atrPips.coerceAtLeast(6.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Instrument Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MarketCatalog.instruments.forEach { inst ->
                    val selected = inst.symbol == draft.symbol
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSymbolForCalc(inst.symbol) },
                        label = { Text(inst.symbol, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldBullDim,
                            selectedLabelColor = EmeraldBull,
                            containerColor = TerminalSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        // Result Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
                border = BorderStroke(1.dp, EmeraldBull.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("RECOMMENDED LOT SIZE", style = MaterialTheme.typography.labelMedium, color = EmeraldBull)
                            Text(
                                text = "${String.format(Locale.US, "%.2f", sizingResult.standardLots)} Lots",
                                style = MaterialTheme.typography.displaySmall.merge(TabularNumericStyle),
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("RISK EXPOSURE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", sizingResult.riskUsd)}",
                                style = MaterialTheme.typography.titleLarge.merge(TabularNumericStyle),
                                color = CrimsonBear
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", sizingResult.miniLots)} mini • ${String.format(Locale.US, "%.0f", sizingResult.microLots)} micro",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("TP1 (1:1.5)", "+$${String.format(Locale.US, "%.0f", sizingResult.tp1ProfitUsd)}", EmeraldBull)
                        TelemetryStat("TP2 (1:2.5)", "+$${String.format(Locale.US, "%.0f", sizingResult.tp2ProfitUsd)}", EmeraldBull)
                        TelemetryStat("TP3 (1:4.0)", "+$${String.format(Locale.US, "%.0f", sizingResult.tp3ProfitUsd)}", EmeraldBull)
                    }
                }
            }
        }

        // Inputs Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ACCOUNT & RISK PARAMETERS", style = MaterialTheme.typography.labelMedium, color = CyanPulse)

                    OutlinedTextField(
                        value = draft.accountBalance.toInt().toString(),
                        onValueChange = { raw ->
                            val v = raw.toDoubleOrNull() ?: return@OutlinedTextField
                            onUpdateDraft { it.copy(accountBalance = v.coerceIn(100.0, 50_000_000.0)) }
                        },
                        label = { Text("Account Equity (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldBull,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_balance_input")
                    )

                    Text(
                        text = "Risk Per Trade: ${String.format(Locale.US, "%.2f", draft.riskPercent)}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0, 1.5, 2.0).forEach { pct ->
                            val active = kotlin.math.abs(draft.riskPercent - pct) < 0.05
                            FilterChip(
                                selected = active,
                                onClick = { onUpdateDraft { it.copy(riskPercent = pct) } },
                                label = { Text("${pct}%") }
                            )
                        }
                    }
                    Slider(
                        value = draft.riskPercent.toFloat(),
                        onValueChange = { valPct ->
                            onUpdateDraft { it.copy(riskPercent = (valPct * 10f).toInt() / 10.0) }
                        },
                        valueRange = 0.25f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                    )

                    Text(
                        text = "ATR Trailing Stop Multiplier & Distance (${String.format(Locale.US, "%.1f", draft.stopDistancePips)} pips)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.0, 1.5).forEach { mult ->
                            val active = draft.atrMultiplier == mult
                            FilterChip(
                                selected = active,
                                onClick = {
                                    val newPips = (atrPips * mult).coerceAtLeast(4.0)
                                    val distPrice = newPips * spec.pipSize
                                    val nextSl = if (draft.direction == "LONG") draft.entryPrice - distPrice else draft.entryPrice + distPrice
                                    onUpdateDraft {
                                        it.copy(
                                            atrMultiplier = mult,
                                            stopDistancePips = newPips,
                                            stopLossPrice = nextSl
                                        )
                                    }
                                },
                                label = { Text("${mult}x ATR (${String.format(Locale.US, "%.1f", atrPips * mult)}p)") }
                            )
                        }
                    }
                    Slider(
                        value = draft.stopDistancePips.toFloat().coerceIn(4f, 250f),
                        onValueChange = { pips ->
                            val distPrice = pips * spec.pipSize
                            val nextSl = if (draft.direction == "LONG") draft.entryPrice - distPrice else draft.entryPrice + distPrice
                            onUpdateDraft { it.copy(stopDistancePips = pips.toDouble(), stopLossPrice = nextSl) }
                        },
                        valueRange = 4f..250f,
                        colors = SliderDefaults.colors(thumbColor = CyanPulse, activeTrackColor = CyanPulse)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("Entry", formatPrice(draft.entryPrice, d), TextPrimary)
                        TelemetryStat("Trail SL", formatPrice(draft.stopLossPrice, d), CrimsonBear)
                        TelemetryStat("TP1", formatPrice(draft.tp1, d), EmeraldBull)
                        TelemetryStat("TP3", formatPrice(draft.tp3, d), EmeraldBull)
                    }
                }
            }
        }
    }
}

@Composable
private fun VolatilityLabPane(snapshot: SmcChartSnapshot) {
    val vol = snapshot.volatility
    val spec = snapshot.instrument
    val d = spec.decimals

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val regimeColor = when (vol.regime) {
                VolatilityRegime.COMPRESSED -> CyanPulse
                VolatilityRegime.NORMAL -> EmeraldBull
                VolatilityRegime.EXPANSION -> AmberPoc
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, regimeColor.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${spec.symbol} VOLATILITY REGIME", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Surface(color = regimeColor.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = vol.regime.label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = regimeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("ATR(14) Pips", String.format(Locale.US, "%.1f", vol.atrPips), TextPrimary)
                        TelemetryStat("1.5x ATR Trail", formatPrice(vol.trailingStop15Distance, d + 1), CrimsonBear)
                        TelemetryStat("1.0x ATR Trail", formatPrice(vol.trailingStop10Distance, d + 1), CyanPulse)
                        TelemetryStat("RVI", String.format(Locale.US, "%.0f", vol.relativeVolatilityIndex), regimeColor)
                    }

                    LinearProgressIndicator(
                        progress = { (vol.relativeVolatilityIndex / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = regimeColor,
                        trackColor = HairlineBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("INTRADAY SESSION VOLATILITY (AVG BAR PIPS)", style = MaterialTheme.typography.labelMedium, color = CyanPulse)
                    val maxSessionPips = vol.sessionVolatilityMap.values.maxOrNull()?.coerceAtLeast(1.0) ?: 10.0
                    vol.sessionVolatilityMap.forEach { (sessionName, pips) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(sessionName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", pips)} pips",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldBull
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (pips / maxSessionPips).toFloat().coerceIn(0f, 1f) },
                                color = EmeraldBull,
                                trackColor = HairlineBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TREND & INSTITUTIONAL REFERENCE LEVELS", style = MaterialTheme.typography.labelMedium, color = VioletAi)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TelemetryStat("EMA 20", formatPrice(vol.ema20, d), CyanPulse)
                        TelemetryStat("EMA 50", formatPrice(vol.ema50, d), VioletAi)
                        TelemetryStat("Session VWAP", formatPrice(vol.vwap, d), AmberPoc)
                    }
                }
            }
        }
    }
}

@Composable
private fun BacktesterPane(
    config: BacktestConfig,
    resultState: UiState<BacktestResult>,
    onUpdateConfig: ((BacktestConfig) -> BacktestConfig) -> Unit,
    onRunBacktest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("HISTORICAL SMC BACKTESTER", style = MaterialTheme.typography.labelMedium, color = CyanPulse)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MarketCatalog.instruments.take(8).forEach { inst ->
                            FilterChip(
                                selected = config.symbol == inst.symbol,
                                onClick = { onUpdateConfig { it.copy(symbol = inst.symbol) } },
                                label = { Text(inst.symbol, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Timeframe.entries.forEach { tf ->
                            FilterChip(
                                selected = config.timeframe == tf,
                                onClick = { onUpdateConfig { it.copy(timeframe = tf) } },
                                label = { Text(tf.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = config.requireOrderBlock,
                            onClick = { onUpdateConfig { it.copy(requireOrderBlock = !it.requireOrderBlock) } },
                            label = { Text("Require OB") }
                        )
                        FilterChip(
                            selected = config.requireLiquiditySweep,
                            onClick = { onUpdateConfig { it.copy(requireLiquiditySweep = !it.requireLiquiditySweep) } },
                            label = { Text("Liq Sweep") }
                        )
                        FilterChip(
                            selected = config.killzoneOnly,
                            onClick = { onUpdateConfig { it.copy(killzoneOnly = !it.killzoneOnly) } },
                            label = { Text("Killzones Only") }
                        )
                        FilterChip(
                            selected = config.atrTrailMultiplier == 1.5,
                            onClick = {
                                onUpdateConfig {
                                    it.copy(atrTrailMultiplier = if (it.atrTrailMultiplier == 1.5) 1.0 else 1.5)
                                }
                            },
                            label = { Text("${config.atrTrailMultiplier}x ATR Trail") }
                        )
                    }

                    Button(
                        onClick = onRunBacktest,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_backtest_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run ${config.lookbackBars}-Bar Historical Simulation", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        when (resultState) {
            UiState.Idle, UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EmeraldBull)
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(resultState.message, color = CrimsonBear, style = MaterialTheme.typography.bodyMedium)
                }
            }

            is UiState.Success -> {
                val res = resultState.data
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryStat(
                                    "Net PnL",
                                    String.format(Locale.US, "%+$%.0f", res.netProfitUsd),
                                    if (res.netProfitUsd >= 0) EmeraldBull else CrimsonBear
                                )
                                TelemetryStat("Win Rate", "${String.format(Locale.US, "%.1f", res.winRatePercent)}%", CyanPulse)
                                TelemetryStat("Profit Factor", String.format(Locale.US, "%.2f", res.profitFactor), EmeraldBull)
                                TelemetryStat("Max DD", "${String.format(Locale.US, "%.1f", res.maxDrawdownPercent)}%", CrimsonBear)
                            }

                            Text(
                                text = "SIMULATED EQUITY CURVE (${res.totalTrades} TRADES • EXP ${String.format(Locale.US, "%+.2f", res.expectancyR)}R)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )

                            EquityCurveCanvas(
                                points = res.equityCurve,
                                lineColor = if (res.netProfitUsd >= 0) EmeraldBull else CrimsonBear,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }

                items(res.trades.takeLast(12).reversed()) { trade ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "#${trade.index} ${trade.direction} • ${trade.exitReason}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Duration: ${trade.durationBars} bars",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%+$%.2f", trade.pnlUsd),
                                    style = TabularNumericStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (trade.pnlUsd >= 0) EmeraldBull else CrimsonBear
                                    )
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.2fR", trade.rMultiple),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquityCurveCanvas(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(ObsidianBg, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        if (points.size < 2) return@Canvas
        val minVal = points.min()
        val maxVal = points.max()
        val span = (maxVal - minVal).coerceAtLeast(1.0)
        val stepX = size.width / (points.size - 1).toFloat()

        val path = Path()
        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val norm = ((value - minVal) / span).toFloat()
            val y = size.height * (1f - norm)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
        drawLine(
            color = HairlineBorder,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f
        )
    }
}
