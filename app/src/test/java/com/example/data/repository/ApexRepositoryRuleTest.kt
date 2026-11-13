package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AiPerformanceReport
import com.example.data.model.JournalEntry
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ApexRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun journalEntry_ownerCreateObserveUpdateDelete_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = ApexFirestoreRepository(firestore)
        val entryId = "j_${UUID.randomUUID().toString().take(8)}"

        val entry = JournalEntry(
            id = entryId,
            symbol = "XAU/USD",
            direction = "LONG",
            session = "NEW_YORK",
            timeframe = "15m",
            setupTag = "SSL Sweep + OB",
            entryPrice = 2642.5,
            exitPrice = 2665.0,
            stopLoss = 2635.0,
            takeProfit1 = 2653.75,
            takeProfit2 = 2661.25,
            takeProfit3 = 2672.5,
            lotSize = 0.45,
            riskPercent = 1.5,
            plannedRr = 4.0,
            realizedRr = 3.0,
            pnlUsd = 1012.5,
            durationMinutes = 92,
            atrMultiplier = 1.5,
            notes = "Clean sweep into M15 bullish OB."
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.addJournalEntry(aliceUid, entry) }
        assertTrue(createResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeJournalEntries(aliceUid).first { list -> list.any { it.id == entryId } }
        }
        val saved = observed.first { it.id == entryId }
        assertEquals("XAU/USD", saved.symbol)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateJournalEntry(aliceUid, saved.copy(notes = "Trailed 1.5x ATR"))
        }
        assertTrue(updateResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteJournalEntry(aliceUid, entryId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun settingsAndSignalAndAiReport_ownerWriteAndObserve_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = ApexFirestoreRepository(firestore)

        val settingsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveSettings(aliceUid, SmcSettings(accountBalance = 50_000.0, defaultRiskPercent = 1.5))
        }
        assertTrue(settingsResult.isSuccess)

        val signalId = "s_${UUID.randomUUID().toString().take(8)}"
        val signalResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveSignal(aliceUid, SavedSignal(id = signalId, symbol = "NAS100", direction = "SHORT"))
        }
        assertTrue(signalResult.isSuccess)

        val statusResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateSignalStatus(aliceUid, signalId, "LOGGED")
        }
        assertTrue(statusResult.isSuccess)

        val aiResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveAiReport(
                aliceUid,
                AiPerformanceReport(
                    tradesAnalyzed = 4,
                    smcAdherenceScore = 88,
                    overallSummary = "Strong adherence to NY Killzone sweeps.",
                    entryExitAnalysis = "Midpoint OB fills improved risk efficiency.",
                    rrEfficiencySummary = "Average realized R:R of 2.6R.",
                    durationInsight = "Optimal holds between 45 and 110 minutes.",
                    recurringPatterns = listOf("NY Killzone Sweep + BOS"),
                    commonMistakes = listOf("Scaling out before TP1 on volatile pairs"),
                    successfulStrategies = listOf("XAU/USD M15 Bullish OB"),
                    recommendations = listOf("Keep 1.5x ATR trailing stop active past TP1")
                )
            )
        }
        assertTrue(aiResult.isSuccess)
    }

    @Test
    fun journalEntries_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ApexFirestoreRepository(firestore)
        val entryId = "j_${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addJournalEntry(aliceUid, JournalEntry(id = entryId)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ApexFirestoreRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeJournalEntries(aliceUid).first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val firestoreException = checkNotNull(captured?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user read, got $captured"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun journalEntries_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ApexFirestoreRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeJournalEntries("some_uid_123").first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val firestoreException = checkNotNull(captured?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException when unauthenticated, got $captured"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_trader@test.com"
        const val BOB_EMAIL = "bob_trader@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
