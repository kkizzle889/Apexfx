package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.SavedSignal
import com.example.domain.MarketCatalog
import com.example.domain.formatPrice

object SignalNotificationDispatcher {

    const val CHANNEL_ID = "apexfx_smc_signals"
    const val EXTRA_SIGNAL_SYMBOL = "extra_signal_symbol"
    const val EXTRA_SIGNAL_ID = "extra_signal_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SMC Entry & Liquidity Signals",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time Forex & CFD signal alerts with Entry, 3 TP levels, and 1.5x ATR trailing stop."
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun dispatchSignalNotification(context: Context, signal: SavedSignal): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false

        val spec = MarketCatalog.bySymbol(signal.symbol)
        val d = spec.decimals
        val entryStr = formatPrice(signal.entryPrice, d)
        val sl15Str = formatPrice(signal.atrTrailingStop15, d)
        val tp1Str = formatPrice(signal.tp1, d)
        val tp2Str = formatPrice(signal.tp2, d)
        val tp3Str = formatPrice(signal.tp3, d)

        val title = "${signal.direction} ${signal.symbol} • ${signal.timeframe} SMC Signal"
        val compact = "Entry $entryStr | 1.5 ATR SL $sl15Str | TP1 $tp1Str"
        val expanded = buildString {
            append("Asset: ${signal.symbol} (${signal.session})\n")
            append("Setup: ${signal.setupSummary}\n")
            append("Entry Price: $entryStr\n")
            append("1.5x ATR Trailing SL: $sl15Str\n")
            append("TP1 (1:1.5): $tp1Str  •  TP2 (1:2.5): $tp2Str\n")
            append("TP3 (1:4.0): $tp3Str  •  Confidence: ${signal.confidence}%")
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SIGNAL_SYMBOL, signal.symbol)
            putExtra(EXTRA_SIGNAL_ID, signal.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            signal.symbol.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(compact)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
