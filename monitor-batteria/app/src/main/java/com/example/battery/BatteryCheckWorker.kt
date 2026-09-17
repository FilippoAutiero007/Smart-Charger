package com.example.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.MainActivity

class BatteryCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "BatteryCheckWorker"
        const val CHANNEL_ID = "battery_alerts_channel"
        const val NOTIFICATION_ID = 1001

        const val PREFS_NAME = "battery_monitor_prefs"
        const val KEY_THRESHOLD = "prefs_battery_threshold"
        const val KEY_ENABLED = "prefs_notifications_enabled"
        const val KEY_LAST_NOTIFIED_LEVEL = "prefs_last_notified_level"
        const val KEY_NOTIFIED_LOW = "prefs_notified_low"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Esecuzione controllo batteria in background...")

        val batteryIntent = context.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: run {
            Log.e(TAG, "Impossibile ricavare l'intent dello stato della batteria.")
            return Result.failure()
        }

        val state = BatteryMonitor.parseState(batteryIntent)
        Log.d(TAG, "Livello batteria: ${state.percentage}%, In Carica: ${state.isCharging}")
        BatteryAutomation.handleBatteryState(context, state, "Background")

        // Heartbeat rinnovo automatico: mantiene vivo l'abbonamento anche dopo reboot server (Render ephemeral)
        // Controllo leggero: solo se abilitato e ultima sync > 24h
        try {
            val sonoffPrefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
            val renewalEnabled = sonoffPrefs.getBoolean(SonoffController.KEY_RENEWAL_ENABLED, false)
            val renewalEmail = sonoffPrefs.getString(SonoffController.KEY_RENEWAL_EMAIL, "") ?: ""
            if (renewalEnabled && renewalEmail.isNotEmpty()) {
                val lastSent = sonoffPrefs.getLong(SonoffController.KEY_RENEWAL_LAST_SENT, 0)
                val now = System.currentTimeMillis()
                // heartbeat ogni 24h per tenere server allineato (token expiry può cambiare dopo refresh)
                if (now - lastSent > 24 * 60 * 60 * 1000L) {
                    Log.d(TAG, "heartbeat rinnovo per $renewalEmail")
                    val controller = SonoffController(context)
                    // Esegui in thread worker già background, ok sincrono
                    controller.syncRenewalSubscription()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "heartbeat renewal errore", e)
        }

        return Result.success()
    }
}
