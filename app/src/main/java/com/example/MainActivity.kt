package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.notifications.SignalNotificationDispatcher
import com.example.security.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalculatorAndLabScreen
import com.example.ui.screens.JournalAndAiCoachScreen
import com.example.ui.screens.SignalsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.signOutUser
import com.example.ui.theme.ApexFxTheme
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.EmeraldBullDim
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ApexViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ROUTE_TERMINAL = "terminal"
private const val ROUTE_SIGNALS = "signals"
private const val ROUTE_LAB = "risk_lab"
private const val ROUTE_JOURNAL = "journal"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val appTabs = listOf(
    NavTab(ROUTE_TERMINAL, "Chart", Icons.Filled.CandlestickChart, Icons.Outlined.CandlestickChart),
    NavTab(ROUTE_SIGNALS, "Signals", Icons.Filled.NotificationsActive, Icons.Outlined.Notifications),
    NavTab(ROUTE_LAB, "Risk Lab", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    NavTab(ROUTE_JOURNAL, "Journal", Icons.Filled.Book, Icons.Outlined.Book)
)

class MainActivity : ComponentActivity() {
    private var deepLinkSymbol by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)
        SignalNotificationDispatcher.ensureChannel(this)
        deepLinkSymbol = intent?.getStringExtra(SignalNotificationDispatcher.EXTRA_SIGNAL_SYMBOL)

        enableEdgeToEdge()
        setContent {
            ApexFxTheme {
                AppNavigation(
                    deepLinkSymbol = deepLinkSymbol,
                    onConsumeDeepLink = { deepLinkSymbol = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkSymbol = intent.getStringExtra(SignalNotificationDispatcher.EXTRA_SIGNAL_SYMBOL)
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    deepLinkSymbol: String?,
    onConsumeDeepLink: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            ApexMainShell(
                currentUserId = user.uid,
                userEmail = user.email ?: "Trader",
                deepLinkSymbol = deepLinkSymbol,
                onConsumeDeepLink = onConsumeDeepLink
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApexMainShell(
    currentUserId: String,
    userEmail: String,
    deepLinkSymbol: String?,
    onConsumeDeepLink: () -> Unit
) {
    val vm: ApexViewModel = viewModel(
        key = currentUserId,
        factory = ApexViewModel.factory(currentUserId)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TERMINAL
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val selectedSymbol by vm.selectedSymbol.collectAsStateWithLifecycle()
    val selectedTimeframe by vm.selectedTimeframe.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val isLiveRestConnected by vm.isLiveRestConnected.collectAsStateWithLifecycle()
    val chartSnapshot by vm.chartSnapshot.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val signalsState by vm.signalsState.collectAsStateWithLifecycle()
    val journalState by vm.journalState.collectAsStateWithLifecycle()
    val latestAiReport by vm.latestAiReportState.collectAsStateWithLifecycle()
    val aiAnalysisStatus by vm.aiAnalysisStatus.collectAsStateWithLifecycle()
    val calculatorDraft by vm.calculatorDraft.collectAsStateWithLifecycle()
    val backtestConfig by vm.backtestConfig.collectAsStateWithLifecycle()
    val backtestResultState by vm.backtestResult.collectAsStateWithLifecycle()
    val journalPrefill by vm.journalPrefillSignal.collectAsStateWithLifecycle()

    LaunchedEffect(deepLinkSymbol) {
        if (!deepLinkSymbol.isNullOrBlank()) {
            vm.selectSymbol(deepLinkSymbol)
            navController.navigate(ROUTE_TERMINAL) {
                launchSingleTop = true
            }
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(vm) {
        vm.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ApexFX",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMC",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldBull
                            )
                        }
                    },
                    actions = {
                        Text(
                            text = userEmail.substringBefore("@"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        IconButton(
                            onClick = { signOutUser(credentialManager, scope) },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TerminalSurface,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = TerminalSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_TERMINAL) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldBull,
                                    selectedTextColor = EmeraldBull,
                                    indicatorColor = EmeraldBullDim,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = TerminalSurface) {
                        appTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_TERMINAL) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBull,
                                    selectedTextColor = EmeraldBull,
                                    indicatorColor = EmeraldBullDim,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TERMINAL
                    ) {
                        composable(ROUTE_TERMINAL) {
                            TerminalScreen(
                                selectedSymbol = selectedSymbol,
                                selectedTimeframe = selectedTimeframe,
                                quotes = quotes,
                                isLiveRestConnected = isLiveRestConnected,
                                chartSnapshot = chartSnapshot,
                                settings = settings,
                                onSelectSymbol = vm::selectSymbol,
                                onSelectTimeframe = vm::selectTimeframe,
                                onUpdateSettings = vm::updateSettings,
                                onSendTestPush = vm::sendTestSignalPush,
                                onUseSignalInCalculator = { sig ->
                                    vm.applySignalToCalculator(sig)
                                    navController.navigate(ROUTE_LAB) { launchSingleTop = true }
                                },
                                onLogSignalToJournal = { sig ->
                                    vm.prefillJournalFromSignal(sig)
                                    vm.markSignalLogged(sig)
                                    navController.navigate(ROUTE_JOURNAL) { launchSingleTop = true }
                                }
                            )
                        }

                        composable(ROUTE_SIGNALS) {
                            SignalsScreen(
                                signalsState = signalsState,
                                settings = settings,
                                onUpdateSettings = vm::updateSettings,
                                onSendTestPush = vm::sendTestSignalPush,
                                onUseSignalInCalculator = { sig ->
                                    vm.applySignalToCalculator(sig)
                                    navController.navigate(ROUTE_LAB) { launchSingleTop = true }
                                },
                                onLogSignalToJournal = { sig ->
                                    vm.prefillJournalFromSignal(sig)
                                    vm.markSignalLogged(sig)
                                    navController.navigate(ROUTE_JOURNAL) { launchSingleTop = true }
                                },
                                onDismissSignal = vm::dismissSignal
                            )
                        }

                        composable(ROUTE_LAB) {
                            CalculatorAndLabScreen(
                                draft = calculatorDraft,
                                sizingResult = vm.computePositionSize(calculatorDraft),
                                chartSnapshot = chartSnapshot,
                                backtestConfig = backtestConfig,
                                backtestResultState = backtestResultState,
                                onUpdateDraft = vm::updateCalculatorDraft,
                                onSelectSymbolForCalc = { sym ->
                                    vm.selectSymbol(sym)
                                },
                                onUpdateBacktestConfig = vm::updateBacktestConfig,
                                onRunBacktest = vm::runBacktest
                            )
                        }

                        composable(ROUTE_JOURNAL) {
                            JournalAndAiCoachScreen(
                                journalState = journalState,
                                latestAiReport = latestAiReport,
                                aiAnalysisStatus = aiAnalysisStatus,
                                settings = settings,
                                prefillSignal = journalPrefill,
                                onClearPrefill = vm::clearJournalPrefill,
                                onLogTrade = { entry -> vm.logJournalEntry(entry) },
                                onDeleteTrade = vm::deleteJournalEntry,
                                onRunAiAnalysis = { vm.runAiJournalAnalysis() }
                            )
                        }
                    }
                }
            }
        }
    }
}
