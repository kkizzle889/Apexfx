package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.data.model.AiPerformanceReport
import com.example.data.model.JournalEntry
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.example.data.remote.AiCoachService
import com.example.data.remote.MarketFeedService
import com.example.data.repository.ApexFirestoreRepository
import com.example.domain.BacktestConfig
import com.example.domain.BacktestResult
import com.example.domain.InstrumentSpec
import com.example.domain.MarketCatalog
import com.example.domain.PositionSizingResult
import com.example.domain.SmcAnalysisEngine
import com.example.domain.SmcChartSnapshot
import com.example.domain.TickQuote
import com.example.domain.Timeframe
import com.example.notifications.SignalNotificationDispatcher
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class CalculatorDraft(
    val symbol: String = "XAU/USD",
    val direction: String = "LONG",
    val accountBalance: Double = 25_000.0,
    val riskPercent: Double = 1.5,
    val entryPrice: Double = 2642.50,
    val stopLossPrice: Double = 2635.30,
    val stopDistancePips: Double = 72.0,
    val atrMultiplier: Double = 1.5,
    val tp1: Double = 2653.30,
    val tp2: Double = 2660.50,
    val tp3: Double = 2671.30
)

class ApexViewModel(
    private val appContext: Application,
    private val repository: ApexFirestoreRepository,
    private val marketFeed: MarketFeedService,
    private val aiCoachService: AiCoachService,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedSymbol = MutableStateFlow("XAU/USD")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M15)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    val quotes: StateFlow<Map<String, TickQuote>> = marketFeed.quotes
    val isLiveRestConnected: StateFlow<Boolean> = marketFeed.isLiveRestConnected

    val settingsState: StateFlow<SmcSettings> = repository.observeSettings(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing settings", unwrapFirestore(error))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SmcSettings(userId = currentUserId)
        )

    val journalState: StateFlow<UiState<List<JournalEntry>>> = repository.observeJournalEntries(currentUserId)
        .map<List<JournalEntry>, UiState<List<JournalEntry>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestore(error)
            if (root == null && error is CancellationException) throw error
            val resolved = root ?: error
            Log.w(TAG, "Error observing journal entries", resolved)
            emit(UiState.Error(resolved.message ?: "Failed to load journal entries"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val signalsState: StateFlow<UiState<List<SavedSignal>>> = repository.observeSignals(currentUserId)
        .map<List<SavedSignal>, UiState<List<SavedSignal>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestore(error)
            if (root == null && error is CancellationException) throw error
            val resolved = root ?: error
            Log.w(TAG, "Error observing signals", resolved)
            emit(UiState.Error(resolved.message ?: "Failed to observe signals"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val latestAiReportState: StateFlow<AiPerformanceReport?> = repository.observeLatestAiReport(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing AI report", unwrapFirestore(error))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _aiAnalysisStatus = MutableStateFlow<UiState<AiPerformanceReport>>(UiState.Idle)
    val aiAnalysisStatus: StateFlow<UiState<AiPerformanceReport>> = _aiAnalysisStatus.asStateFlow()

    val chartSnapshot: StateFlow<SmcChartSnapshot> = combine(
        _selectedSymbol,
        _selectedTimeframe,
        quotes,
        settingsState
    ) { symbol, timeframe, _, settings ->
        val spec = MarketCatalog.bySymbol(symbol)
        val candles = marketFeed.getCandles(symbol, timeframe, 110)
        SmcAnalysisEngine.analyze(spec, timeframe, candles, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SmcAnalysisEngine.analyze(
            MarketCatalog.bySymbol("XAU/USD"),
            Timeframe.M15,
            marketFeed.getCandles("XAU/USD", Timeframe.M15, 110),
            SmcSettings(userId = currentUserId)
        )
    )

    private val _calculatorDraft = MutableStateFlow(CalculatorDraft())
    val calculatorDraft: StateFlow<CalculatorDraft> = _calculatorDraft.asStateFlow()

    private val _backtestConfig = MutableStateFlow(BacktestConfig())
    val backtestConfig: StateFlow<BacktestConfig> = _backtestConfig.asStateFlow()

    private val _backtestResult = MutableStateFlow<UiState<BacktestResult>>(UiState.Idle)
    val backtestResult: StateFlow<UiState<BacktestResult>> = _backtestResult.asStateFlow()

    private val _journalPrefillSignal = MutableStateFlow<SavedSignal?>(null)
    val journalPrefillSignal: StateFlow<SavedSignal?> = _journalPrefillSignal.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private val lastAlertedMap = mutableMapOf<String, Long>()

    init {
        marketFeed.startStreaming()
        syncCalculatorToInstrument("XAU/USD", settingsState.value)
        runBacktest()
        monitorLiveSignals()
    }

    private fun monitorLiveSignals() {
        viewModelScope.launch {
            var scanCounter = 0
            quotes.collect { latestQuotes ->
                if (latestQuotes.isEmpty()) return@collect
                scanCounter++
                if (scanCounter % 12 != 2) return@collect

                val settings = settingsState.value
                val watchSlice = MarketCatalog.instruments
                val candidateSpec = watchSlice[(scanCounter / 12) % watchSlice.size]
                val tf = _selectedTimeframe.value
                val candles = marketFeed.getCandles(candidateSpec.symbol, tf, 110)
                val snapshot = SmcAnalysisEngine.analyze(candidateSpec, tf, candles, settings)
                val signal = snapshot.latestSignal ?: return@collect

                val key = "${signal.symbol}_${signal.direction}_${signal.timeframe}"
                val now = System.currentTimeMillis()
                val lastSent = lastAlertedMap[key] ?: 0L
                if (now - lastSent < 90_000L) return@collect

                lastAlertedMap[key] = now
                repository.saveSignal(currentUserId, signal)
                if (settings.enablePushNotifications) {
                    SignalNotificationDispatcher.dispatchSignalNotification(appContext, signal)
                }
            }
        }
    }

    fun selectSymbol(symbol: String) {
        _selectedSymbol.value = symbol
        syncCalculatorToInstrument(symbol, settingsState.value)
    }

    fun selectTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun updateSettings(transform: (SmcSettings) -> SmcSettings) {
        val next = transform(settingsState.value)
        viewModelScope.launch {
            val res = repository.saveSettings(currentUserId, next)
            if (res.isFailure) {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings")
            }
        }
    }

    fun sendTestSignalPush() {
        viewModelScope.launch {
            val snapshot = chartSnapshot.value
            val spec = snapshot.instrument
            val liveBid = quotes.value[spec.symbol]?.bid ?: spec.basePrice
            val atr = snapshot.volatility.atr14.coerceAtLeast(spec.pipSize * 12.0)
            val isLong = snapshot.volatility.ema20 >= snapshot.volatility.ema50
            val trail15Dist = atr * 1.5
            val trail10Dist = atr * 1.0
            val sl15 = if (isLong) liveBid - trail15Dist else liveBid + trail15Dist
            val sl10 = if (isLong) liveBid - trail10Dist else liveBid + trail10Dist

            val signal = snapshot.latestSignal ?: SavedSignal(
                symbol = spec.symbol,
                direction = if (isLong) "LONG" else "SHORT",
                timeframe = snapshot.timeframe.label,
                session = snapshot.candles.lastOrNull()?.session?.label ?: "NY Killzone",
                setupSummary = "Liquidity Sweep + Order Block + Session POC",
                entryPrice = SmcAnalysisEngine.roundToDecimals(liveBid, spec.decimals),
                initialStopLoss = SmcAnalysisEngine.roundToDecimals(sl15, spec.decimals),
                atrTrailingStop15 = SmcAnalysisEngine.roundToDecimals(sl15, spec.decimals),
                atrTrailingStop10 = SmcAnalysisEngine.roundToDecimals(sl10, spec.decimals),
                atrValue = SmcAnalysisEngine.roundToDecimals(atr, spec.decimals + 1),
                tp1 = SmcAnalysisEngine.roundToDecimals(if (isLong) liveBid + trail15Dist * 1.5 else liveBid - trail15Dist * 1.5, spec.decimals),
                tp2 = SmcAnalysisEngine.roundToDecimals(if (isLong) liveBid + trail15Dist * 2.5 else liveBid - trail15Dist * 2.5, spec.decimals),
                tp3 = SmcAnalysisEngine.roundToDecimals(if (isLong) liveBid + trail15Dist * 4.0 else liveBid - trail15Dist * 4.0, spec.decimals),
                rrTp3 = 4.0,
                confidence = 91,
                status = "ACTIVE"
            )

            repository.saveSignal(currentUserId, signal)
            val posted = SignalNotificationDispatcher.dispatchSignalNotification(appContext, signal)
            if (posted) {
                _toastEvents.tryEmit("Push alert dispatched for ${signal.symbol} (${signal.direction})")
            } else {
                _toastEvents.tryEmit("Signal saved. Enable notification permission to receive device push alerts.")
            }
        }
    }

    fun syncCalculatorToInstrument(symbol: String, settings: SmcSettings) {
        val spec = MarketCatalog.bySymbol(symbol)
        val liveBid = quotes.value[symbol]?.bid ?: spec.basePrice
        val candles = marketFeed.getCandles(symbol, _selectedTimeframe.value, 110)
        val atr = SmcAnalysisEngine.computeAtrSeries(candles, 14).lastOrNull() ?: (spec.volatilityFactor * 1.5)
        val mult = settings.defaultAtrMultiplier
        val stopDist = atr * mult
        val stopPips = (stopDist / spec.pipSize).coerceAtLeast(5.0)
        val sl = liveBid - stopDist
        _calculatorDraft.value = CalculatorDraft(
            symbol = symbol,
            direction = "LONG",
            accountBalance = settings.accountBalance,
            riskPercent = settings.defaultRiskPercent,
            entryPrice = SmcAnalysisEngine.roundToDecimals(liveBid, spec.decimals),
            stopLossPrice = SmcAnalysisEngine.roundToDecimals(sl, spec.decimals),
            stopDistancePips = SmcAnalysisEngine.roundToDecimals(stopPips, 1),
            atrMultiplier = mult,
            tp1 = SmcAnalysisEngine.roundToDecimals(liveBid + stopDist * 1.5, spec.decimals),
            tp2 = SmcAnalysisEngine.roundToDecimals(liveBid + stopDist * 2.5, spec.decimals),
            tp3 = SmcAnalysisEngine.roundToDecimals(liveBid + stopDist * 4.0, spec.decimals)
        )
    }

    fun applySignalToCalculator(signal: SavedSignal) {
        val spec = MarketCatalog.bySymbol(signal.symbol)
        val settings = settingsState.value
        val atrMult = settings.defaultAtrMultiplier
        val sl = if (atrMult == 1.0) signal.atrTrailingStop10 else signal.atrTrailingStop15
        val stopPips = kotlin.math.abs(signal.entryPrice - sl) / spec.pipSize
        _selectedSymbol.value = signal.symbol
        _calculatorDraft.value = CalculatorDraft(
            symbol = signal.symbol,
            direction = signal.direction,
            accountBalance = settings.accountBalance,
            riskPercent = settings.defaultRiskPercent,
            entryPrice = signal.entryPrice,
            stopLossPrice = sl,
            stopDistancePips = SmcAnalysisEngine.roundToDecimals(stopPips.coerceAtLeast(4.0), 1),
            atrMultiplier = atrMult,
            tp1 = signal.tp1,
            tp2 = signal.tp2,
            tp3 = signal.tp3
        )
    }

    fun updateCalculatorDraft(transform: (CalculatorDraft) -> CalculatorDraft) {
        _calculatorDraft.value = transform(_calculatorDraft.value)
    }

    fun computePositionSize(draft: CalculatorDraft): PositionSizingResult {
        val spec = MarketCatalog.bySymbol(draft.symbol)
        return SmcAnalysisEngine.calculatePositionSize(
            instrument = spec,
            accountBalance = draft.accountBalance,
            riskPercent = draft.riskPercent,
            stopDistancePips = draft.stopDistancePips
        )
    }

    fun prefillJournalFromSignal(signal: SavedSignal) {
        _journalPrefillSignal.value = signal
    }

    fun clearJournalPrefill() {
        _journalPrefillSignal.value = null
    }

    fun logJournalEntry(entry: JournalEntry, triggerAiAfterSave: Boolean = settingsState.value.autoAnalyzeJournal) {
        viewModelScope.launch {
            val result = if (entry.id.isBlank()) {
                repository.addJournalEntry(currentUserId, entry)
            } else {
                repository.updateJournalEntry(currentUserId, entry)
            }
            if (result.isSuccess) {
                _toastEvents.tryEmit("Trade logged to Cloud Journal")
                if (triggerAiAfterSave) {
                    val existingList = (journalState.value as? UiState.Success)?.data ?: emptyList()
                    val combined = if (entry.id.isBlank()) listOf(entry) + existingList else existingList.map { if (it.id == entry.id) entry else it }
                    runAiJournalAnalysis(combined)
                }
            } else {
                _toastEvents.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save trade")
            }
        }
    }

    fun deleteJournalEntry(entryId: String) {
        viewModelScope.launch {
            val res = repository.deleteJournalEntry(currentUserId, entryId)
            if (res.isFailure) {
                _toastEvents.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Failed to delete trade")
            }
        }
    }

    fun markSignalLogged(signal: SavedSignal) {
        viewModelScope.launch {
            if (signal.id.isNotBlank()) {
                repository.updateSignalStatus(currentUserId, signal.id, "LOGGED")
            }
        }
    }

    fun dismissSignal(signal: SavedSignal) {
        viewModelScope.launch {
            if (signal.id.isNotBlank()) {
                repository.updateSignalStatus(currentUserId, signal.id, "DISMISSED")
            }
        }
    }

    fun runAiJournalAnalysis(overrideEntries: List<JournalEntry>? = null) {
        val entries = overrideEntries ?: (journalState.value as? UiState.Success)?.data.orEmpty()
        if (entries.isEmpty()) {
            _aiAnalysisStatus.value = UiState.Error("Log at least one trade to unlock AI performance analysis.")
            return
        }
        viewModelScope.launch {
            _aiAnalysisStatus.value = UiState.Loading
            val res = aiCoachService.analyzeJournal(entries)
            if (res.isSuccess) {
                val report = res.getOrThrow().copy(userId = currentUserId)
                repository.saveAiReport(currentUserId, report)
                _aiAnalysisStatus.value = UiState.Success(report)
            } else {
                _aiAnalysisStatus.value = UiState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "AI Coach failed to analyze journal."
                )
            }
        }
    }

    fun updateBacktestConfig(transform: (BacktestConfig) -> BacktestConfig) {
        _backtestConfig.value = transform(_backtestConfig.value)
    }

    fun runBacktest() {
        val cfg = _backtestConfig.value
        viewModelScope.launch {
            _backtestResult.value = UiState.Loading
            val res = withContext(Dispatchers.Default) {
                val spec = MarketCatalog.bySymbol(cfg.symbol)
                val series = marketFeed.getBacktestSeries(cfg.symbol, cfg.timeframe, cfg.lookbackBars)
                SmcAnalysisEngine.runBacktest(spec, series, cfg)
            }
            _backtestResult.value = UiState.Success(res)
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()

    companion object {
        private const val TAG = "ApexVM"

        fun factory(currentUserId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                return ApexViewModel(
                    appContext = app,
                    repository = ApexFirestoreRepository(db),
                    marketFeed = MarketFeedService(),
                    aiCoachService = AiCoachService(),
                    currentUserId = currentUserId
                ) as T
            }
        }
    }
}
