const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validJournalPayload(uid) {
  return {
    userId: uid,
    symbol: "XAU/USD",
    direction: "LONG",
    session: "NEW_YORK",
    timeframe: "15m",
    setupTag: "OB + Liquidity Sweep",
    entryPrice: 2642.5,
    exitPrice: 2665.0,
    stopLoss: 2635.0,
    takeProfit1: 2653.75,
    takeProfit2: 2661.25,
    takeProfit3: 2672.5,
    lotSize: 0.45,
    riskPercent: 1.5,
    plannedRr: 4.0,
    realizedRr: 3.0,
    pnlUsd: 1012.5,
    durationMinutes: 94,
    atrMultiplier: 1.5,
    usedOrderBlock: true,
    usedLiquiditySweep: true,
    usedFvg: true,
    usedStructureBreak: true,
    alignedWithPoc: true,
    inKillzone: true,
    followedAtrTrail: true,
    notes: "Clean NY sweep into M15 bullish OB.",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validSettingsPayload(uid) {
  return {
    userId: uid,
    showOrderBlocks: true,
    showFvg: true,
    showLiquiditySweeps: true,
    showStructureBreaks: true,
    showSessionPoc: true,
    showKillzones: true,
    showAtrBands: true,
    enablePushNotifications: true,
    alertLiquiditySweeps: true,
    alertOrderBlockRetest: true,
    alertMultiConfluenceEntry: true,
    autoAnalyzeJournal: true,
    accountBalance: 25000.0,
    defaultRiskPercent: 1.5,
    defaultAtrMultiplier: 1.5,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validSignalPayload(uid) {
  return {
    userId: uid,
    symbol: "EUR/USD",
    direction: "SHORT",
    timeframe: "15m",
    session: "LONDON",
    setupSummary: "BSL Sweep + M15 Bearish OB + CHoCH",
    entryPrice: 1.0842,
    initialStopLoss: 1.0858,
    atrTrailingStop15: 1.0857,
    atrTrailingStop10: 1.0852,
    atrValue: 0.001,
    tp1: 1.0818,
    tp2: 1.0802,
    tp3: 1.0778,
    rrTp3: 4.0,
    confidence: 89,
    status: "ACTIVE",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validAiReportPayload(uid) {
  return {
    userId: uid,
    tradesAnalyzed: 5,
    smcAdherenceScore: 86,
    overallSummary: "Strong discipline on NY Killzone sweeps.",
    entryExitAnalysis: "Entries align well with OB midpoints.",
    rrEfficiencySummary: "Realized R:R averages 2.4R vs 3.5R target.",
    durationInsight: "Best win rate in 45-120 min holds.",
    recurringPatterns: ["High win rate on NY sweep + OB"],
    commonMistakes: ["Exiting early before TP2"],
    successfulStrategies: ["XAU/USD 15m Sweep + FVG"],
    recommendations: ["Hold 50% runner to 1.5x ATR trail"],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write journal entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("journal_entries").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j1").set(validJournalPayload(ALICE_UID))
  );
});

test("Owner can create, read, and update journal entry, settings, signal, and AI report", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const journalRef = aliceDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j1");
  await assertSucceeds(journalRef.set(validJournalPayload(ALICE_UID)));
  await assertSucceeds(journalRef.get());
  await assertSucceeds(
    journalRef.update({
      notes: "Held runner with 1.5 ATR trail",
      updatedAt: serverTimestamp(),
    })
  );

  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  await assertSucceeds(settingsRef.set(validSettingsPayload(ALICE_UID)));

  const signalRef = aliceDb.collection("users").doc(ALICE_UID).collection("signal_history").doc("s1");
  await assertSucceeds(signalRef.set(validSignalPayload(ALICE_UID)));
  await assertSucceeds(signalRef.update({ status: "LOGGED", updatedAt: serverTimestamp() }));

  const aiRef = aliceDb.collection("users").doc(ALICE_UID).collection("ai_reports").doc("latest");
  await assertSucceeds(aiRef.set(validAiReportPayload(ALICE_UID)));
});

test("Cross-user isolation blocks Bob from reading or writing Alice's subcollections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j1").set(validJournalPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("journal_entries").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j2").set(validJournalPayload(BOB_UID))
  );
});

test("Schema validation rejects ghost fields and invalid enum/bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badPayload = { ...validJournalPayload(ALICE_UID), isAdmin: true };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j_ghost").set(badPayload)
  );

  const badDirection = { ...validJournalPayload(ALICE_UID), direction: "SIDEWAYS" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("journal_entries").doc("j_dir").set(badDirection)
  );
});
