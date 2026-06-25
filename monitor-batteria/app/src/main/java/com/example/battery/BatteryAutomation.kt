package com.example.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.playground.PlaygroundAutomation

object BatteryAutomation {
    private const val TAG = "BatteryAutomation"

    fun handleBatteryState(context: Context, state: BatteryState, source: String) {
        val appContext = context.applicationContext
        LocalLogService.saveLog(appContext, state.percentage, state.isCharging, source)
        handleBatteryAlert(appContext, state)
        handleSonoffControl(appContext, state.percentage)
        PlaygroundAutomation.handleBatteryState(appContext, state, source)
    }

    private fun handleBatteryAlert(context: Context, state: BatteryState) {
        val sharedPrefs = context.getSharedPreferences(BatteryCheckWorker.PREFS_NAME, MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean(BatteryCheckWorker.KEY_ENABLED, true)
        if (!isEnabled) return

        val threshold = sharedPrefs.getInt(BatteryCheckWorker.KEY_THRESHOLD, 20)
        val hasNotified = sharedPrefs.getBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false)

        if (state.percentage <= threshold) {
            if (!state.isCharging && !hasNotified) {
                sendLowBatteryNotification(context, state.percentage, threshold)
                sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, true).apply()
            } else if (state.isCharging && hasNotified) {
                sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false).apply()
            }
        } else if (hasNotified) {
            sharedPrefs.edit().putBoolean(BatteryCheckWorker.KEY_NOTIFIED_LOW, false).apply()
        }
    }

    private fun handleSonoffControl(context: Context, batteryPercentage: Int) {
        val sharedPrefs = context.getSharedPreferences(SonoffController.PREFS_NAME, MODE_PRIVATE)
        if (!sharedPrefs.getBoolean(SonoffController.KEY_ENABLED, false)) {
            Log.d(TAG, "handleSonoffControl: disabled")
            return
        }

        val deviceId = sharedPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: ""
        val accessToken = sharedPrefs.getString(SonoffController.KEY_ACCESS_TOKEN, "") ?: ""
        if (deviceId.isEmpty() || accessToken.isEmpty()) {
            Log.d(TAG, "handleSonoffControl: missing credentials/device")
            return
        }

        val onThreshold = sharedPrefs.getInt(SonoffController.KEY_ON_THRESHOLD, 30)
        val offThreshold = sharedPrefs.getInt(SonoffController.KEY_OFF_THRESHOLD, 80)
        val lastCommand = sharedPrefs.getString(SonoffController.KEY_LAST_COMMAND, "") ?: ""
        val controller = SonoffController(context)

        when {
            batteryPercentage >= offThreshold && lastCommand != "off" -> {
                Log.d(TAG, "handleSonoffControl: OFF at $batteryPercentage%")
                controller.turnOff(deviceId)
            }
            batteryPercentage <= onThreshold && lastCommand != "on" -> {
                Log.d(TAG, "handleSonoffControl: ON at $batteryPercentage%")
                controller.turnOn(deviceId)
            }
            batteryPercentage >= offThreshold -> Log.d(TAG, "handleSonoffControl: off already sent")
            batteryPercentage <= onThreshold -> Log.d(TAG, "handleSonoffControl: on already sent")
            else -> Log.d(TAG, "handleSonoffControl: dead zone")
        }
    }

    private fun sendLowBatteryNotification(context: Context, percentage: Int, threshold: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BatteryCheckWorker.CHANNEL_ID,
                "Allerte Batteria",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche inviate quando la batteria scende sotto la soglia impostata"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = "La tua batteria e' al $percentage%, al di sotto della soglia impostata di $threshold%."
        val builder = NotificationCompat.Builder(context, BatteryCheckWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Batteria Quasi Scarica!")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(BatteryCheckWorker.NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile mostrare la notifica", e)
        }
    }
}
