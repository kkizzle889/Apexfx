package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.example.domain.MarketCatalog
import com.example.domain.SmcChartSnapshot
import com.example.domain.TickQuote
import com.example.domain.Timeframe
import com.example.domain.formatPrice
import com.example.ui.components.SmcChartCanvas
import com.example.ui.theme.AmberPoc
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CrimsonBearDim
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
import java.util.Locale

@Composable
fun TerminalScreen(
    selectedSymbol: String,
    selectedTimeframe: Timeframe,
    quotes: Map<String, TickQuote>,
    isLiveRestConnected: Boolean,
    chartSnapshot: SmcChartSnapshot,
    settings: SmcSettings,
    onSelectSymbol: (String) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onUpdateSettings: ((SmcSettings) -> SmcSettings) -> Unit,
    onSendTestPush: () -> Unit,
    onUseSignalInCalculator: (SavedSignal) -> Unit,
    onLogSignalToJournal: (SavedSignal) -> Unit
) {
    val activeQuote = quotes[selectedSymbol]
    val spec = chartSnapshot.instrument
    val d = spec.decimals

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Live Watchlist Strip (Majors + Gold + Oil + NAS100/SPX500/US30)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MarketCatalog.instruments, key = { it.symbol }) { item ->
                    val quote = quotes[item.symbol]
                    val isSelected = item.symbol == selectedSymbol
                    val up = quote?.isTickUp ?: true
                    val change = quote?.changePercent ?: 0.0
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TerminalSurfaceElevated else TerminalSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) EmeraldBull else HairlineBorder
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { onSelectSymbol(item.symbol) }
                            .testTag("watchlist_item_${item.symbol}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.symbol,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.2f%%", change),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (change >= 0) EmeraldBull else CrimsonBear
                                )
                            }
                            Text(
                                text = formatPrice(quote?.bid ?: item.basePrice, item.decimals),
                                style = TabularNumericStyle.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (up) EmeraldBull else CrimsonBear
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Live Instrument Bid / Ask / Spread / Feed Status Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = spec.symbol,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Surface(
                                    color = if (isLiveRestConnected) EmeraldBullDim else CyanPulse.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (isLiveRestConnected) EmeraldBull else CyanPulse,
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = if (isLiveRestConnected) "LIVE FX TICK" else "HFT STREAM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isLiveRestConnected) EmeraldBull else CyanPulse
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${spec.displayName} • ${spec.assetClass.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val bid = activeQuote?.bid ?: spec.basePrice
                            val ask = activeQuote?.ask ?: spec.basePrice
                            Text(
                                text = formatPrice(bid, d),
                                style = MaterialTheme.typography.headlineMedium.merge(TabularNumericStyle),
                                color = if (activeQuote?.isTickUp != false) EmeraldBull else CrimsonBear
                            )
                            Text(
                                text = "ASK ${formatPrice(ask, d)}  •  SPD ${String.format(Locale.US, "%.1f", activeQuote?.spreadPips ?: spec.typicalSpreadPips)}p",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Timeframe Bar + Instant Push Signal Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Timeframe.entries.forEach { tf ->
                                val active = tf == selectedTimeframe
                                Surface(
                                    color = if (active) EmeraldBullDim else TerminalSurfaceElevated,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (active) EmeraldBull else HairlineBorder),
                                    modifier = Modifier
                                        .clickable { onSelectSymbolAndTf(selectedSymbol, tf, onSelectTimeframe) }
                                        .testTag("tf_chip_${tf.label}")
                                ) {
                                    Text(
                                        text = tf.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) EmeraldBull else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onSendTestPush,
                            border = BorderStroke(1.dp, CyanPulse),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("push_signal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Push signal alert",
                                tint = CyanPulse,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Push Alert",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }
                    }
                }
            }
        }

        // 3. SMC Toggle HUD Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmcToggleChip("Order Blocks", settings.showOrderBlocks, EmeraldBull) {
                    onUpdateSettings { it.copy(showOrderBlocks = !it.showOrderBlocks) }
                }
                SmcToggleChip("FVGs", settings.showFvg, CyanPulse) {
                    onUpdateSettings { it.copy(showFvg = !it.showFvg) }
                }
                SmcToggleChip("Liq Sweeps", settings.showLiquiditySweeps, CrimsonBear) {
                    onUpdateSettings { it.copy(showLiquiditySweeps = !it.showLiquiditySweeps) }
                }
                SmcToggleChip("BOS / CHoCH", settings.showStructureBreaks, EmeraldBull) {
                    onUpdateSettings { it.copy(showStructureBreaks = !it.showStructureBreaks) }
                }
                SmcToggleChip("Session POC", settings.showSessionPoc, AmberPoc) {
                    onUpdateSettings { it.copy(showSessionPoc = !it.showSessionPoc) }
                }
                SmcToggleChip("Killzones", settings.showKillzones, VioletAi) {
                    onUpdateSettings { it.copy(showKillzones = !it.showKillzones) }
                }
                SmcToggleChip("EMA / ATR", settings.showAtrBands, CyanPulse) {
                    onUpdateSettings { it.copy(showAtrBands = !it.showAtrBands) }
                }
            }
        }

        // 4. Interactive Candlestick + SMC + Volume Profile Canvas
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianBg),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(320.dp)
            ) {
                SmcChartCanvas(
                    snapshot = chartSnapshot,
                    settings = settings,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 5. Session Volume Profile & POC Telemetry Strip
        item {
            val vp = chartSnapshot.volumeProfile
            val vol = chartSnapshot.volatility
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SESSION VOLUME PROFILE & POC",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberPoc
                        )
                        Text(
                            text = "ATR(14): ${String.format(Locale.US, "%.1f", vol.atrPips)} pips (${vol.regime.label})",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("POC Level", formatPrice(vp.pocPrice, d), AmberPoc)
                        TelemetryStat("VAH / VAL", "${formatPrice(vp.vahPrice, d)} / ${formatPrice(vp.valPrice, d)}", CyanPulse)
                        TelemetryStat(
                            "Net Delta",
                            String.format(Locale.US, "%+.1f%%", vp.sessionDeltaPercent),
                            if (vp.sessionDeltaPercent >= 0) EmeraldBull else CrimsonBear
                        )
                    }
                }
            }
        }

        // 6. Active Entry Signal Card (Entry + 1.5x ATR Trailing Stop + TP1/TP2/TP3)
        item {
            val signal = chartSnapshot.latestSignal
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(
                    1.dp,
                    when (signal?.direction) {
                        "LONG" -> EmeraldBull.copy(alpha = 0.6f)
                        "SHORT" -> CrimsonBear.copy(alpha = 0.6f)
                        else -> HairlineBorder
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (signal == null) {
                        Text(
                            text = "LIVE SMC SCANNER • MONITORING ${spec.symbol}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "Waiting for Liquidity Sweep + Order Block confluence on ${selectedTimeframe.label}. Tap Push Alert above to dispatch a manual snapshot alert anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    } else {
                        val dirColor = if (signal.direction == "LONG") EmeraldBull else CrimsonBear
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (signal.direction == "LONG") EmeraldBullDim else CrimsonBearDim,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${signal.direction} SIGNAL",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = dirColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "${signal.symbol} • ${signal.timeframe}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "${signal.confidence}% CONF",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                        }

                        Text(
                            text = "${signal.setupSummary} (${signal.session})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryStat("Entry", formatPrice(signal.entryPrice, d), TextPrimary)
                            TelemetryStat("1.5x ATR SL", formatPrice(signal.atrTrailingStop15, d), CrimsonBear)
                            TelemetryStat("TP1 (1.5R)", formatPrice(signal.tp1, d), EmeraldBull)
                            TelemetryStat("TP2 (2.5R)", formatPrice(signal.tp2, d), EmeraldBull)
                            TelemetryStat("TP3 (4R)", formatPrice(signal.tp3, d), EmeraldBull)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onUseSignalInCalculator(signal) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("signal_to_calculator_button")
                            ) {
                                Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lot Size", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { onLogSignalToJournal(signal) },
                                border = BorderStroke(1.dp, EmeraldBull),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("signal_to_journal_button")
                            ) {
                                Icon(Icons.Filled.EditNote, contentDescription = null, tint = EmeraldBull, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log Trade", style = MaterialTheme.typography.labelLarge, color = EmeraldBull)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun onSelectSymbolAndTf(
    symbol: String,
    tf: Timeframe,
    onSelectTimeframe: (Timeframe) -> Unit
) {
    onSelectTimeframe(tf)
}

@Composable
private fun SmcToggleChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent,
            containerColor = TerminalSurface,
            labelColor = TextMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = accent.copy(alpha = 0.65f),
            borderColor = HairlineBorder
        )
    )
}

@Composable
fun TelemetryStat(label: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = TabularNumericStyle.copy(
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        )
    }
}
