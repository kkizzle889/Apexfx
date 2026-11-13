package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedSignal
import com.example.data.model.SmcSettings
import com.example.domain.MarketCatalog
import com.example.domain.formatPrice
import com.example.notifications.SignalNotificationDispatcher
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CrimsonBearDim
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.EmeraldBullDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.UiState

@Composable
fun SignalsScreen(
    signalsState: UiState<List<SavedSignal>>,
    settings: SmcSettings,
    onUpdateSettings: ((SmcSettings) -> SmcSettings) -> Unit,
    onSendTestPush: () -> Unit,
    onUseSignalInCalculator: (SavedSignal) -> Unit,
    onLogSignalToJournal: (SavedSignal) -> Unit,
    onDismissSignal: (SavedSignal) -> Unit
) {
    val context = LocalContext.current
    var hasNotifPerm by remember {
        mutableStateOf(SignalNotificationDispatcher.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Mobile Push Permission & Test Trigger Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, if (hasNotifPerm) CyanPulse.copy(alpha = 0.45f) else CrimsonBear.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
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
                            Icon(
                                imageVector = if (hasNotifPerm) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                contentDescription = "Notification status",
                                tint = if (hasNotifPerm) EmeraldBull else CrimsonBear
                            )
                            Column {
                                Text(
                                    text = "Real-Time Signal Push Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (hasNotifPerm) {
                                        "Active • Alerts include Entry, TP1–TP3 & 1.5x ATR Trail SL"
                                    } else {
                                        "Grant notification permission to receive instant signal alerts"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldBull,
                                    contentColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_notifications_button")
                            ) {
                                Text("Enable Push", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Button(
                            onClick = onSendTestPush,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_test_push_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dispatch Live Signal Push", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 2. Alert Methodology Toggles
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                border = BorderStroke(1.dp, HairlineBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ALERT TRIGGERS & SMC CONFLUENCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPulse
                    )
                    AlertToggleRow(
                        title = "Master Push Alerts",
                        subtitle = "Send high-priority mobile notifications on signal trigger",
                        checked = settings.enablePushNotifications
                    ) { checked ->
                        onUpdateSettings { it.copy(enablePushNotifications = checked) }
                    }
                    AlertToggleRow(
                        title = "Liquidity Sweep Alerts (BSL / SSL)",
                        subtitle = "Trigger when stop-hunt wicks sweep prior highs/lows",
                        checked = settings.alertLiquiditySweeps
                    ) { checked ->
                        onUpdateSettings { it.copy(alertLiquiditySweeps = checked) }
                    }
                    AlertToggleRow(
                        title = "Institutional Order Block Retests",
                        subtitle = "Alert when price mitigates unmitigated M15/H1 Order Blocks",
                        checked = settings.alertOrderBlockRetest
                    ) { checked ->
                        onUpdateSettings { it.copy(alertOrderBlockRetest = checked) }
                    }
                    AlertToggleRow(
                        title = "Multi-Confluence Entry Signals",
                        subtitle = "Require BOS/CHoCH + Session POC + ATR(14) alignment",
                        checked = settings.alertMultiConfluenceEntry
                    ) { checked ->
                        onUpdateSettings { it.copy(alertMultiConfluenceEntry = checked) }
                    }
                }
            }
        }

        // 3. Signal Feed Header
        item {
            Text(
                text = "SIGNAL TAPE (3 TP + 1.5x ATR TRAILING STOP)",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        when (signalsState) {
            UiState.Idle, UiState.Loading -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = CyanPulse)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Syncing signal tape...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
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
                            text = signalsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonBear,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val visibleSignals = signalsState.data.filter { it.status != "DISMISSED" }
                if (visibleSignals.isEmpty()) {
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
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = CyanPulse,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Scanner Monitoring Live Tick Feed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Signals automatically log here and fire a mobile push notification when SMC setups form. Tap 'Dispatch Live Signal Push' above to test immediately.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(visibleSignals, key = { it.id }) { sig ->
                        val spec = MarketCatalog.bySymbol(sig.symbol)
                        val d = spec.decimals
                        val dirColor = if (sig.direction == "LONG") EmeraldBull else CrimsonBear

                        Card(
                            colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                            border = BorderStroke(1.dp, dirColor.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp)
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
                                        Surface(
                                            color = if (sig.direction == "LONG") EmeraldBullDim else CrimsonBearDim,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = sig.direction,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = dirColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "${sig.symbol} • ${sig.timeframe}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        if (sig.status == "LOGGED") {
                                            Surface(color = CyanPulse.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = "LOGGED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CyanPulse,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = { onDismissSignal(sig) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Dismiss signal",
                                            tint = TextMuted
                                        )
                                    }
                                }

                                Text(
                                    text = "${sig.setupSummary} • ${sig.session} (${sig.confidence}% conf)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TelemetryStat("Entry", formatPrice(sig.entryPrice, d), TextPrimary)
                                    TelemetryStat("1.5x ATR SL", formatPrice(sig.atrTrailingStop15, d), CrimsonBear)
                                    TelemetryStat("1.0x ATR", formatPrice(sig.atrTrailingStop10, d), TextSecondary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TelemetryStat("TP1 (1:1.5)", formatPrice(sig.tp1, d), EmeraldBull)
                                    TelemetryStat("TP2 (1:2.5)", formatPrice(sig.tp2, d), EmeraldBull)
                                    TelemetryStat("TP3 (1:4.0)", formatPrice(sig.tp3, d), EmeraldBull)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onUseSignalInCalculator(sig) },
                                        border = BorderStroke(1.dp, CyanPulse),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lot Size", style = MaterialTheme.typography.labelLarge, color = CyanPulse)
                                    }
                                    Button(
                                        onClick = { onLogSignalToJournal(sig) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianBg),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Log Trade", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = EmeraldBull,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = HairlineBorder
            )
        )
    }
}
