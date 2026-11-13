package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AiPerformanceReport
import com.example.data.model.JournalEntry
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.example.domain.MarketCatalog
import com.example.domain.SmcAnalysisEngine
import com.example.domain.formatPrice
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
import com.example.ui.theme.VioletAiDim
import com.example.viewmodel.UiState
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalAndAiCoachScreen(
    journalState: UiState<List<JournalEntry>>,
    latestAiReport: AiPerformanceReport?,
    aiAnalysisStatus: UiState<AiPerformanceReport>,
    settings: SmcSettings,
    prefillSignal: SavedSignal?,
    onClearPrefill: () -> Unit,
    onLogTrade: (JournalEntry) -> Unit,
    onDeleteTrade: (String) -> Unit,
    onRunAiAnalysis: () -> Unit
) {
    var showLogSheet by remember { mutableStateOf(false) }
    var sessionFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(prefillSignal) {
        if (prefillSignal != null) {
            showLogSheet = true
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogSheet = true },
                containerColor = EmeraldBull,
                contentColor = ObsidianBg,
                modifier = Modifier.testTag("log_trade_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Log trade")
            }
        }
    ) { innerPadding ->
        val entries = (journalState as? UiState.Success)?.data.orEmpty()
        val filtered = if (sessionFilter == "ALL") entries else entries.filter { it.session == sessionFilter }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Performance Metrics & Equity Curve
            item {
                JournalMetricsSummaryCard(entries)
            }

            // 2. AI-Powered Performance Analysis Card
            item {
                AiPerformanceCoachCard(
                    hasEntries = entries.isNotEmpty(),
                    latestReport = latestAiReport,
                    analysisStatus = aiAnalysisStatus,
                    onRunAnalysis = onRunAiAnalysis
                )
            }

            // 3. Session Filter Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "NEW_YORK", "LONDON", "ASIA", "LONDON_NY_OVERLAP").forEach { sess ->
                        FilterChip(
                            selected = sessionFilter == sess,
                            onClick = { sessionFilter = sess },
                            label = { Text(sess.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            when (journalState) {
                UiState.Idle, UiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EmeraldBull)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CrimsonBearDim),
                            border = BorderStroke(1.dp, CrimsonBear)
                        ) {
                            Text(
                                text = journalState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonBear,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                                border = BorderStroke(1.dp, HairlineBorder),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        tint = VioletAi,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text("No Trades Logged Yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = "Tap '+' to log your first trade with SMC confluence tags, duration, and R:R—then let Gemini AI analyze your execution patterns.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { entry ->
                            JournalEntryCard(entry = entry, onDelete = { onDeleteTrade(entry.id) })
                        }
                    }
                }
            }
        }

        if (showLogSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showLogSheet = false
                    onClearPrefill()
                },
                containerColor = TerminalSurface
            ) {
                LogTradeSheetContent(
                    prefill = prefillSignal,
                    settings = settings,
                    onSubmit = { entry ->
                        onLogTrade(entry)
                        onClearPrefill()
                        showLogSheet = false
                    },
                    onCancel = {
                        onClearPrefill()
                        showLogSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun JournalMetricsSummaryCard(entries: List<JournalEntry>) {
    val total = entries.size
    val wins = entries.count { it.pnlUsd > 0.0 }
    val winRate = if (total == 0) 0.0 else (wins * 100.0) / total
    val netPnl = entries.sumOf { it.pnlUsd }
    val grossWin = entries.filter { it.pnlUsd > 0.0 }.sumOf { it.pnlUsd }
    val grossLoss = abs(entries.filter { it.pnlUsd < 0.0 }.sumOf { it.pnlUsd })
    val profitFactor = when {
        grossLoss > 0.0 -> grossWin / grossLoss
        grossWin > 0.0 -> 9.99
        else -> 0.0
    }
    val avgRr = if (total == 0) 0.0 else entries.map { it.realizedRr }.average()
    val avgDur = if (total == 0) 0.0 else entries.map { it.durationMinutes }.average()

    val equityPoints = remember(entries) {
        val pts = mutableListOf(0.0)
        var running = 0.0
        entries.reversed().forEach {
            running += it.pnlUsd
            pts.add(running)
        }
        pts
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TRADE JOURNAL TELEMETRY ($total TRADES)", style = MaterialTheme.typography.labelMedium, color = EmeraldBull)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryStat(
                    "Net PnL",
                    String.format(Locale.US, "%+$%.0f", netPnl),
                    if (netPnl >= 0) EmeraldBull else CrimsonBear
                )
                TelemetryStat("Win Rate", "${String.format(Locale.US, "%.1f", winRate)}%", CyanPulse)
                TelemetryStat("Profit Factor", String.format(Locale.US, "%.2f", profitFactor), EmeraldBull)
                TelemetryStat("Avg R:R", "${String.format(Locale.US, "%+.2f", avgRr)}R", AmberPoc)
                TelemetryStat("Avg Hold", "${avgDur.toInt()}m", TextSecondary)
            }

            if (equityPoints.size >= 2) {
                EquityCurveCanvas(
                    points = equityPoints,
                    lineColor = if (netPnl >= 0) EmeraldBull else CrimsonBear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                )
            }
        }
    }
}

@Composable
private fun AiPerformanceCoachCard(
    hasEntries: Boolean,
    latestReport: AiPerformanceReport?,
    analysisStatus: UiState<AiPerformanceReport>,
    onRunAnalysis: () -> Unit
) {
    val activeReport = (analysisStatus as? UiState.Success)?.data ?: latestReport
    val isLoading = analysisStatus is UiState.Loading

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurfaceElevated),
        border = BorderStroke(1.dp, VioletAi.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = VioletAiDim, shape = RoundedCornerShape(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI Performance Analysis",
                            tint = VioletAi,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text("AI Performance Coach", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = "Analyzes entry/exit, R:R, duration & SMC adherence",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRunAnalysis,
                    enabled = hasEntries && !isLoading,
                    border = BorderStroke(1.dp, VioletAi),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("analyze_ai_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = VioletAi, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analyze", style = MaterialTheme.typography.labelMedium, color = VioletAi)
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VioletAi, strokeWidth = 2.dp)
                    Text(
                        text = "Gemini is auditing your trade entries, R:R efficiency, hold durations, and SMC discipline...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else if (analysisStatus is UiState.Error) {
                Text(
                    text = analysisStatus.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonBear
                )
            }

            if (activeReport != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SMC Adherence Score (${activeReport.tradesAnalyzed} trades)",
                        style = MaterialTheme.typography.labelMedium,
                        color = VioletAi
                    )
                    Text(
                        text = "${activeReport.smcAdherenceScore}/100",
                        style = TabularNumericStyle.copy(fontWeight = FontWeight.Bold, color = EmeraldBull)
                    )
                }
                LinearProgressIndicator(
                    progress = { (activeReport.smcAdherenceScore / 100f).coerceIn(0f, 1f) },
                    color = VioletAi,
                    trackColor = HairlineBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                )

                Text(activeReport.overallSummary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)

                AiMetricBlock("Entry & Exit Precision", activeReport.entryExitAnalysis, CyanPulse)
                AiMetricBlock("R:R Efficiency", activeReport.rrEfficiencySummary, EmeraldBull)
                AiMetricBlock("Trade Duration Insight", activeReport.durationInsight, AmberPoc)

                AiBulletList("Recurring Patterns", activeReport.recurringPatterns, CyanPulse)
                AiBulletList("Common Mistakes", activeReport.commonMistakes, CrimsonBear)
                AiBulletList("Successful Strategies", activeReport.successfulStrategies, EmeraldBull)
                AiBulletList("Personalized Recommendations", activeReport.recommendations, VioletAi)
            } else if (!isLoading) {
                Text(
                    text = "Log trades to unlock automated Gemini AI analysis of your recurring patterns, common mistakes, winning strategies, and SMC execution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun AiMetricBlock(title: String, body: String, accent: Color) {
    if (body.isBlank()) return
    Surface(
        color = TerminalSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun AiBulletList(title: String, items: List<String>, accent: Color) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
        items.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onDelete: () -> Unit
) {
    val spec = MarketCatalog.bySymbol(entry.symbol)
    val d = spec.decimals
    val winColor = if (entry.pnlUsd >= 0) EmeraldBull else CrimsonBear

    Card(
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        color = if (entry.direction == "LONG") EmeraldBullDim else CrimsonBearDim,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.direction,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (entry.direction == "LONG") EmeraldBull else CrimsonBear,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text("${entry.symbol} • ${entry.timeframe}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(Locale.US, "%+$%.2f (%+.2fR)", entry.pnlUsd, entry.realizedRr),
                        style = TabularNumericStyle.copy(fontWeight = FontWeight.Bold, color = winColor)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete trade", tint = TextMuted)
                    }
                }
            }

            Text(
                text = "${entry.setupTag} • ${entry.session.replace("_", " ")} • ${entry.durationMinutes}m hold • SMC ${entry.smcScorePercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryStat("Entry", formatPrice(entry.entryPrice, d), TextPrimary)
                TelemetryStat("Exit", formatPrice(entry.exitPrice, d), winColor)
                TelemetryStat("SL", formatPrice(entry.stopLoss, d), CrimsonBear)
                TelemetryStat("Lot", String.format(Locale.US, "%.2f", entry.lotSize), CyanPulse)
            }

            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogTradeSheetContent(
    prefill: SavedSignal?,
    settings: SmcSettings,
    onSubmit: (JournalEntry) -> Unit,
    onCancel: () -> Unit
) {
    val initialSymbol = prefill?.symbol ?: "XAU/USD"
    val spec = MarketCatalog.bySymbol(initialSymbol)

    var symbol by remember { mutableStateOf(initialSymbol) }
    var direction by remember { mutableStateOf(prefill?.direction ?: "LONG") }
    var session by remember { mutableStateOf("NEW_YORK") }
    var timeframe by remember { mutableStateOf(prefill?.timeframe ?: "15m") }
    var setupTag by remember { mutableStateOf(prefill?.setupSummary ?: "OB + Liquidity Sweep") }
    var entryText by remember { mutableStateOf(formatPrice(prefill?.entryPrice ?: spec.basePrice, spec.decimals)) }
    var exitText by remember { mutableStateOf(formatPrice(prefill?.tp2 ?: (spec.basePrice + spec.volatilityFactor * 3.0), spec.decimals)) }
    var slText by remember { mutableStateOf(formatPrice(prefill?.atrTrailingStop15 ?: (spec.basePrice - spec.volatilityFactor * 1.5), spec.decimals)) }
    var lotText by remember { mutableStateOf("0.40") }
    var durationText by remember { mutableStateOf("65") }
    var notesText by remember { mutableStateOf("") }

    var usedOb by remember { mutableStateOf(true) }
    var usedSweep by remember { mutableStateOf(true) }
    var usedFvg by remember { mutableStateOf(true) }
    var usedBos by remember { mutableStateOf(true) }
    var alignedPoc by remember { mutableStateOf(true) }
    var inKillzone by remember { mutableStateOf(true) }
    var followedAtr by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Log Trade Execution", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MarketCatalog.instruments.forEach { inst ->
                FilterChip(
                    selected = symbol == inst.symbol,
                    onClick = { symbol = inst.symbol },
                    label = { Text(inst.symbol, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LONG", "SHORT").forEach { dir ->
                FilterChip(
                    selected = direction == dir,
                    onClick = { direction = dir },
                    label = { Text(dir) }
                )
            }
            listOf("NEW_YORK", "LONDON", "ASIA").forEach { sess ->
                FilterChip(
                    selected = session == sess,
                    onClick = { session = sess },
                    label = { Text(sess.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OutlinedTextField(
            value = setupTag,
            onValueChange = { setupTag = it },
            label = { Text("Strategy / Setup Tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entryText,
                onValueChange = { entryText = it },
                label = { Text("Entry Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = exitText,
                onValueChange = { exitText = it },
                label = { Text("Exit Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = slText,
                onValueChange = { slText = it },
                label = { Text("Stop Loss") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lotText,
                onValueChange = { lotText = it },
                label = { Text("Lots") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(0.7f)
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Min") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.6f)
            )
        }

        Text("SMART MONEY CONCEPTS CHECKLIST", style = MaterialTheme.typography.labelSmall, color = VioletAi)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = usedOb, onClick = { usedOb = !usedOb }, label = { Text("Order Block") })
            FilterChip(selected = usedSweep, onClick = { usedSweep = !usedSweep }, label = { Text("Liq Sweep") })
            FilterChip(selected = usedFvg, onClick = { usedFvg = !usedFvg }, label = { Text("FVG") })
            FilterChip(selected = usedBos, onClick = { usedBos = !usedBos }, label = { Text("BOS/CHoCH") })
            FilterChip(selected = alignedPoc, onClick = { alignedPoc = !alignedPoc }, label = { Text("POC Align") })
            FilterChip(selected = inKillzone, onClick = { inKillzone = !inKillzone }, label = { Text("Killzone") })
            FilterChip(selected = followedAtr, onClick = { followedAtr = !followedAtr }, label = { Text("1.5x ATR Trail") })
        }

        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Execution Notes & Psychology") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val activeSpec = MarketCatalog.bySymbol(symbol)
                    val entry = (entryText.toDoubleOrNull() ?: activeSpec.basePrice).coerceAtLeast(0.0001)
                    val exit = (exitText.toDoubleOrNull() ?: entry).coerceAtLeast(0.0001)
                    val sl = (slText.toDoubleOrNull() ?: (entry - activeSpec.volatilityFactor)).coerceAtLeast(0.0001)
                    val lots = (lotText.toDoubleOrNull() ?: 0.25).coerceIn(0.01, 500.0)
                    val duration = (durationText.toIntOrNull() ?: 45).coerceIn(1, 43200)
                    val slDist = abs(entry - sl).coerceAtLeast(activeSpec.pipSize * 2)
                    val move = if (direction == "LONG") (exit - entry) else (entry - exit)
                    val realizedRr = SmcAnalysisEngine.roundToDecimals(move / slDist, 2).coerceIn(-50.0, 100.0)
                    val pipsMoved = move / activeSpec.pipSize
                    val pnl = SmcAnalysisEngine.roundToDecimals(pipsMoved * activeSpec.pipValuePerStandardLot * lots, 2)

                    val sign = if (direction == "LONG") 1.0 else -1.0
                    val tp1 = (prefill?.tp1 ?: SmcAnalysisEngine.roundToDecimals(entry + sign * slDist * 1.5, activeSpec.decimals)).coerceAtLeast(0.0001)
                    val tp2 = (prefill?.tp2 ?: SmcAnalysisEngine.roundToDecimals(entry + sign * slDist * 2.5, activeSpec.decimals)).coerceAtLeast(0.0001)
                    val tp3 = (prefill?.tp3 ?: SmcAnalysisEngine.roundToDecimals(entry + sign * slDist * 4.0, activeSpec.decimals)).coerceAtLeast(0.0001)

                    onSubmit(
                        JournalEntry(
                            symbol = symbol,
                            direction = direction,
                            session = session,
                            timeframe = timeframe,
                            setupTag = setupTag.ifBlank { "SMC Setup" },
                            entryPrice = entry,
                            exitPrice = exit,
                            stopLoss = sl,
                            takeProfit1 = tp1,
                            takeProfit2 = tp2,
                            takeProfit3 = tp3,
                            lotSize = lots,
                            riskPercent = settings.defaultRiskPercent,
                            plannedRr = 4.0,
                            realizedRr = realizedRr,
                            pnlUsd = pnl,
                            durationMinutes = duration,
                            atrMultiplier = settings.defaultAtrMultiplier,
                            usedOrderBlock = usedOb,
                            usedLiquiditySweep = usedSweep,
                            usedFvg = usedFvg,
                            usedStructureBreak = usedBos,
                            alignedWithPoc = alignedPoc,
                            inKillzone = inKillzone,
                            followedAtrTrail = followedAtr,
                            notes = notesText
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianBg),
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_journal_trade_button")
            ) {
                Text("Save & Analyze", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
