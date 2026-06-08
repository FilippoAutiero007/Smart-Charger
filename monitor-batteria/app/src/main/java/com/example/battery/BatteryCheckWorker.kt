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
        return Result.success()
    }
}
