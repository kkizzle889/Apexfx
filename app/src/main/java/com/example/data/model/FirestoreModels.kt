package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "EUR/USD",
    val direction: String = "LONG",
    val session: String = "NEW_YORK",
    val timeframe: String = "15m",
    val setupTag: String = "OB + Liquidity Sweep",
    val entryPrice: Double = 1.0,
    val exitPrice: Double = 1.0,
    val stopLoss: Double = 1.0,
    val takeProfit1: Double = 1.0,
    val takeProfit2: Double = 1.0,
    val takeProfit3: Double = 1.0,
    val lotSize: Double = 0.1,
    val riskPercent: Double = 1.5,
    val plannedRr: Double = 3.0,
    val realizedRr: Double = 0.0,
    val pnlUsd: Double = 0.0,
    val durationMinutes: Int = 45,
    val atrMultiplier: Double = 1.5,
    val usedOrderBlock: Boolean = true,
    val usedLiquiditySweep: Boolean = true,
    val usedFvg: Boolean = false,
    val usedStructureBreak: Boolean = true,
    val alignedWithPoc: Boolean = true,
    val inKillzone: Boolean = true,
    val followedAtrTrail: Boolean = true,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isWin: Boolean get() = pnlUsd > 0.0
    val smcScorePercent: Int
        get() {
            val checks = listOf(
                usedOrderBlock,
                usedLiquiditySweep,
                usedFvg,
                usedStructureBreak,
                alignedWithPoc,
                inKillzone,
                followedAtrTrail
            )
            return (checks.count { it } * 100) / checks.size
        }
}

@IgnoreExtraProperties
data class SmcSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val showOrderBlocks: Boolean = true,
    val showFvg: Boolean = true,
    val showLiquiditySweeps: Boolean = true,
    val showStructureBreaks: Boolean = true,
    val showSessionPoc: Boolean = true,
    val showKillzones: Boolean = true,
    val showAtrBands: Boolean = true,
    val enablePushNotifications: Boolean = true,
    val alertLiquiditySweeps: Boolean = true,
    val alertOrderBlockRetest: Boolean = true,
    val alertMultiConfluenceEntry: Boolean = true,
    val autoAnalyzeJournal: Boolean = true,
    val accountBalance: Double = 25_000.0,
    val defaultRiskPercent: Double = 1.5,
    val defaultAtrMultiplier: Double = 1.5,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedSignal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "XAU/USD",
    val direction: String = "LONG",
    val timeframe: String = "15m",
    val session: String = "NEW_YORK",
    val setupSummary: String = "SSL Sweep + Bullish OB + BOS",
    val entryPrice: Double = 2642.5,
    val initialStopLoss: Double = 2635.0,
    val atrTrailingStop15: Double = 2635.3,
    val atrTrailingStop10: Double = 2637.7,
    val atrValue: Double = 4.8,
    val tp1: Double = 2653.75,
    val tp2: Double = 2661.25,
    val tp3: Double = 2672.5,
    val rrTp3: Double = 4.0,
    val confidence: Int = 88,
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiPerformanceReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tradesAnalyzed: Int = 0,
    val smcAdherenceScore: Int = 0,
    val overallSummary: String = "",
    val entryExitAnalysis: String = "",
    val rrEfficiencySummary: String = "",
    val durationInsight: String = "",
    val recurringPatterns: List<String> = emptyList(),
    val commonMistakes: List<String> = emptyList(),
    val successfulStrategies: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
