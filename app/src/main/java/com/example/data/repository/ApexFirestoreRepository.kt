package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiPerformanceReport
import com.example.data.model.JournalEntry
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ApexFirestoreRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    fun observeJournalEntries(userId: String): Flow<List<JournalEntry>> {
        val path = "users/$userId/journal_entries"
        return userDoc(userId).collection("journal_entries")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    JournalEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addJournalEntry(userId: String, entry: JournalEntry): Result<String> {
        val docId = entry.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(userId).collection("journal_entries").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "symbol" to entry.symbol.ifBlank { "XAU/USD" }.take(24),
            "direction" to if (entry.direction == "SHORT") "SHORT" else "LONG",
            "session" to entry.session,
            "timeframe" to entry.timeframe,
            "setupTag" to entry.setupTag.ifBlank { "SMC Setup" }.take(60),
            "entryPrice" to entry.entryPrice.coerceAtLeast(0.00001),
            "exitPrice" to entry.exitPrice.coerceAtLeast(0.00001),
            "stopLoss" to entry.stopLoss.coerceAtLeast(0.00001),
            "takeProfit1" to entry.takeProfit1.coerceAtLeast(0.00001),
            "takeProfit2" to entry.takeProfit2.coerceAtLeast(0.00001),
            "takeProfit3" to entry.takeProfit3.coerceAtLeast(0.00001),
            "lotSize" to entry.lotSize.coerceIn(0.01, 1000.0),
            "riskPercent" to entry.riskPercent.coerceIn(0.01, 50.0),
            "plannedRr" to entry.plannedRr.coerceIn(0.0, 100.0),
            "realizedRr" to entry.realizedRr.coerceIn(-50.0, 100.0),
            "pnlUsd" to entry.pnlUsd,
            "durationMinutes" to entry.durationMinutes.coerceIn(0, 43200),
            "atrMultiplier" to entry.atrMultiplier.coerceIn(0.5, 5.0),
            "usedOrderBlock" to entry.usedOrderBlock,
            "usedLiquiditySweep" to entry.usedLiquiditySweep,
            "usedFvg" to entry.usedFvg,
            "usedStructureBreak" to entry.usedStructureBreak,
            "alignedWithPoc" to entry.alignedWithPoc,
            "inKillzone" to entry.inKillzone,
            "followedAtrTrail" to entry.followedAtrTrail,
            "notes" to entry.notes.take(1000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateJournalEntry(userId: String, entry: JournalEntry): Result<Unit> {
        require(entry.id.isNotBlank()) { "JournalEntry ID must not be blank for update" }
        val docRef = userDoc(userId).collection("journal_entries").document(entry.id)
        return try {
            val updates = mapOf(
                "symbol" to entry.symbol,
                "direction" to entry.direction,
                "session" to entry.session,
                "timeframe" to entry.timeframe,
                "setupTag" to entry.setupTag.ifBlank { "SMC Setup" },
                "entryPrice" to entry.entryPrice,
                "exitPrice" to entry.exitPrice,
                "stopLoss" to entry.stopLoss,
                "takeProfit1" to entry.takeProfit1,
                "takeProfit2" to entry.takeProfit2,
                "takeProfit3" to entry.takeProfit3,
                "lotSize" to entry.lotSize,
                "riskPercent" to entry.riskPercent,
                "plannedRr" to entry.plannedRr,
                "realizedRr" to entry.realizedRr,
                "pnlUsd" to entry.pnlUsd,
                "durationMinutes" to entry.durationMinutes,
                "atrMultiplier" to entry.atrMultiplier,
                "usedOrderBlock" to entry.usedOrderBlock,
                "usedLiquiditySweep" to entry.usedLiquiditySweep,
                "usedFvg" to entry.usedFvg,
                "usedStructureBreak" to entry.usedStructureBreak,
                "alignedWithPoc" to entry.alignedWithPoc,
                "inKillzone" to entry.inKillzone,
                "followedAtrTrail" to entry.followedAtrTrail,
                "notes" to entry.notes.take(1000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteJournalEntry(userId: String, entryId: String): Result<Unit> {
        val docRef = userDoc(userId).collection("journal_entries").document(entryId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSettings(userId: String): Flow<SmcSettings> {
        val docRef = userDoc(userId).collection("settings").document("preferences")
        return docRef.snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    SmcSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: SmcSettings(id = "preferences", userId = userId)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun saveSettings(userId: String, settings: SmcSettings): Result<Unit> {
        val docRef = userDoc(userId).collection("settings").document("preferences")
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = mapOf(
                    "showOrderBlocks" to settings.showOrderBlocks,
                    "showFvg" to settings.showFvg,
                    "showLiquiditySweeps" to settings.showLiquiditySweeps,
                    "showStructureBreaks" to settings.showStructureBreaks,
                    "showSessionPoc" to settings.showSessionPoc,
                    "showKillzones" to settings.showKillzones,
                    "showAtrBands" to settings.showAtrBands,
                    "enablePushNotifications" to settings.enablePushNotifications,
                    "alertLiquiditySweeps" to settings.alertLiquiditySweeps,
                    "alertOrderBlockRetest" to settings.alertOrderBlockRetest,
                    "alertMultiConfluenceEntry" to settings.alertMultiConfluenceEntry,
                    "autoAnalyzeJournal" to settings.autoAnalyzeJournal,
                    "accountBalance" to settings.accountBalance.coerceAtLeast(100.0),
                    "defaultRiskPercent" to settings.defaultRiskPercent.coerceIn(0.1, 10.0),
                    "defaultAtrMultiplier" to if (settings.defaultAtrMultiplier == 1.0) 1.0 else 1.5,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "showOrderBlocks" to settings.showOrderBlocks,
                    "showFvg" to settings.showFvg,
                    "showLiquiditySweeps" to settings.showLiquiditySweeps,
                    "showStructureBreaks" to settings.showStructureBreaks,
                    "showSessionPoc" to settings.showSessionPoc,
                    "showKillzones" to settings.showKillzones,
                    "showAtrBands" to settings.showAtrBands,
                    "enablePushNotifications" to settings.enablePushNotifications,
                    "alertLiquiditySweeps" to settings.alertLiquiditySweeps,
                    "alertOrderBlockRetest" to settings.alertOrderBlockRetest,
                    "alertMultiConfluenceEntry" to settings.alertMultiConfluenceEntry,
                    "autoAnalyzeJournal" to settings.autoAnalyzeJournal,
                    "accountBalance" to settings.accountBalance.coerceAtLeast(100.0),
                    "defaultRiskPercent" to settings.defaultRiskPercent.coerceIn(0.1, 10.0),
                    "defaultAtrMultiplier" to if (settings.defaultAtrMultiplier == 1.0) 1.0 else 1.5,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSignals(userId: String): Flow<List<SavedSignal>> {
        val path = "users/$userId/signal_history"
        return userDoc(userId).collection("signal_history")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SavedSignal::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun saveSignal(userId: String, signal: SavedSignal): Result<String> {
        val docId = signal.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val docRef = userDoc(userId).collection("signal_history").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "symbol" to signal.symbol,
            "direction" to signal.direction,
            "timeframe" to signal.timeframe,
            "session" to signal.session,
            "setupSummary" to signal.setupSummary.ifBlank { "SMC Confluence Setup" }.take(160),
            "entryPrice" to signal.entryPrice,
            "initialStopLoss" to signal.initialStopLoss,
            "atrTrailingStop15" to signal.atrTrailingStop15,
            "atrTrailingStop10" to signal.atrTrailingStop10,
            "atrValue" to signal.atrValue,
            "tp1" to signal.tp1,
            "tp2" to signal.tp2,
            "tp3" to signal.tp3,
            "rrTp3" to signal.rrTp3,
            "confidence" to signal.confidence.coerceIn(0, 100),
            "status" to signal.status,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSignalStatus(userId: String, signalId: String, status: String): Result<Unit> {
        val docRef = userDoc(userId).collection("signal_history").document(signalId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeLatestAiReport(userId: String): Flow<AiPerformanceReport?> {
        val docRef = userDoc(userId).collection("ai_reports").document("latest")
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    AiPerformanceReport::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun saveAiReport(userId: String, report: AiPerformanceReport): Result<Unit> {
        val docRef = userDoc(userId).collection("ai_reports").document("latest")
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updates = mapOf(
                    "tradesAnalyzed" to report.tradesAnalyzed,
                    "smcAdherenceScore" to report.smcAdherenceScore.coerceIn(0, 100),
                    "overallSummary" to report.overallSummary.take(2000),
                    "entryExitAnalysis" to report.entryExitAnalysis.take(2000),
                    "rrEfficiencySummary" to report.rrEfficiencySummary.take(2000),
                    "durationInsight" to report.durationInsight.take(2000),
                    "recurringPatterns" to report.recurringPatterns.take(10),
                    "commonMistakes" to report.commonMistakes.take(10),
                    "successfulStrategies" to report.successfulStrategies.take(10),
                    "recommendations" to report.recommendations.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to userId,
                    "tradesAnalyzed" to report.tradesAnalyzed,
                    "smcAdherenceScore" to report.smcAdherenceScore.coerceIn(0, 100),
                    "overallSummary" to report.overallSummary.take(2000),
                    "entryExitAnalysis" to report.entryExitAnalysis.take(2000),
                    "rrEfficiencySummary" to report.rrEfficiencySummary.take(2000),
                    "durationInsight" to report.durationInsight.take(2000),
                    "recurringPatterns" to report.recurringPatterns.take(10),
                    "commonMistakes" to report.commonMistakes.take(10),
                    "successfulStrategies" to report.successfulStrategies.take(10),
                    "recommendations" to report.recommendations.take(10),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
