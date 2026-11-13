package com.example.data.remote

import android.util.Log
import com.example.data.model.AiPerformanceReport
import com.example.data.model.JournalEntry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

class AiCoachService {

    private val reportSchema = Schema.obj(
        properties = mapOf(
            "smcAdherenceScore" to Schema.integer("Overall Smart Money Concepts methodology adherence score from 0 to 100"),
            "overallSummary" to Schema.string("Concise 2-sentence executive summary of trading performance"),
            "entryExitAnalysis" to Schema.string("Detailed assessment of entry timing and exit execution relative to OB/FVG and TP1-TP3 levels"),
            "rrEfficiencySummary" to Schema.string("Assessment of planned vs realized Risk-Reward ratios and position sizing discipline"),
            "durationInsight" to Schema.string("Analysis of trade duration in minutes and session hold-time efficiency"),
            "recurringPatterns" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 recurring behavioral or market patterns detected across logged trades"
            ),
            "commonMistakes" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 recurring mistakes or risk leaks to eliminate"
            ),
            "successfulStrategies" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 highest-performing SMC setups, instruments, or sessions"
            ),
            "recommendations" to Schema.array(
                items = Schema.string(),
                description = "3 to 4 concrete, personalized, actionable next-session rules"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = reportSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun analyzeJournal(entries: List<JournalEntry>): Result<AiPerformanceReport> {
        if (entries.isEmpty()) {
            return Result.failure(IllegalArgumentException("Log at least one trade before running AI performance analysis."))
        }

        val recent = entries.take(30)
        val winRate = (recent.count { it.pnlUsd > 0 } * 100.0) / recent.size
        val avgPlannedRr = recent.map { it.plannedRr }.average()
        val avgRealizedRr = recent.map { it.realizedRr }.average()
        val avgDuration = recent.map { it.durationMinutes }.average()
        val netPnl = recent.sumOf { it.pnlUsd }
        val smcAvg = recent.map { it.smcScorePercent }.average()

        val tradeLines = recent.joinToString("\n") { t ->
            String.format(
                Locale.US,
                "- %s %s [%s/%s] Setup='%s' Entry=%.5f Exit=%.5f SL=%.5f TP1=%.5f TP2=%.5f TP3=%.5f Lot=%.2f Risk=%.2f%% PlanRR=%.2f RealRR=%.2f PnL=%.2f Duration=%dmin ATRx=%.1f SMC[OB=%b,Sweep=%b,FVG=%b,BOS=%b,POC=%b,KZ=%b,Trail=%b] Notes='%s'",
                t.symbol, t.direction, t.session, t.timeframe, t.setupTag,
                t.entryPrice, t.exitPrice, t.stopLoss, t.takeProfit1, t.takeProfit2, t.takeProfit3,
                t.lotSize, t.riskPercent, t.plannedRr, t.realizedRr, t.pnlUsd, t.durationMinutes,
                t.atrMultiplier, t.usedOrderBlock, t.usedLiquiditySweep, t.usedFvg,
                t.usedStructureBreak, t.alignedWithPoc, t.inKillzone, t.followedAtrTrail,
                t.notes.take(120)
            )
        }

        val prompt = """
            You are an institutional Forex & CFD quantitative performance coach specializing in Smart Money Concepts (SMC).
            Analyze the trader's logged journal below and produce an objective, specific, data-backed audit.
            Consider:
            1. Entry and exit points (precision near Order Blocks/FVGs, holding vs premature exits before TP1/TP2/TP3).
            2. Planned vs realized Risk-Reward (R:R) ratios and risk% discipline.
            3. Trade duration (in minutes) across Asia, London, and New York sessions.
            4. Adherence to Smart Money Concepts (Order Blocks, Liquidity Sweeps, FVG, BOS/CHoCH, Session POC, Killzones, and 1.0x/1.5x ATR trailing stops).

            Aggregate Stats (Last ${recent.size} trades):
            - Win Rate: ${String.format(Locale.US, "%.1f", winRate)}%
            - Net PnL: $${String.format(Locale.US, "%.2f", netPnl)}
            - Avg Planned R:R: ${String.format(Locale.US, "%.2f", avgPlannedRr)}R
            - Avg Realized R:R: ${String.format(Locale.US, "%.2f", avgRealizedRr)}R
            - Avg Trade Duration: ${String.format(Locale.US, "%.0f", avgDuration)} min
            - Avg SMC Checklist Score: ${String.format(Locale.US, "%.0f", smcAvg)}%

            Trades:
            $tradeLines
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("AI Coach returned an empty response.")
            val obj = JSONObject(jsonText)

            fun readArray(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { idx ->
                    arr.optString(idx).takeIf { it.isNotBlank() }
                }
            }

            Result.success(
                AiPerformanceReport(
                    id = "latest",
                    tradesAnalyzed = recent.size,
                    smcAdherenceScore = obj.optInt("smcAdherenceScore", smcAvg.toInt()).coerceIn(0, 100),
                    overallSummary = obj.optString("overallSummary").ifBlank { "Performance audit completed across ${recent.size} logged trades." }.take(2000),
                    entryExitAnalysis = obj.optString("entryExitAnalysis").ifBlank { "Review entry precision around institutional Order Blocks and partial profit scaling at TP1-TP3." }.take(2000),
                    rrEfficiencySummary = obj.optString("rrEfficiencySummary").ifBlank { "Average planned vs realized R:R tracked against 1.5x ATR trailing stop parameters." }.take(2000),
                    durationInsight = obj.optString("durationInsight").ifBlank { "Average trade duration ${avgDuration.toInt()} minutes across active sessions." }.take(2000),
                    recurringPatterns = readArray("recurringPatterns").take(10),
                    commonMistakes = readArray("commonMistakes").take(10),
                    successfulStrategies = readArray("successfulStrategies").take(10),
                    recommendations = readArray("recommendations").take(10)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI journal analysis timed out", e)
            Result.failure(IllegalStateException("AI analysis timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI journal analysis failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiCoachService"
    }
}
