package com.example.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R

class BatteryCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "BatteryCheckWorker"
        const val CHANNEL_ID = "battery_alerts_channel"
        private const val NOTIFICATION_ID = 1001

        const val PREFS_NAME = "battery_monitor_prefs"
        const val KEY_THRESHOLD = "prefs_battery_threshold"
        const val KEY_ENABLED = "prefs_notifications_enabled"
        const val KEY_LAST_NOTIFIED_LEVEL = "prefs_last_notified_level"
        const val KEY_NOTIFIED_LOW = "prefs_notified_low"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Esecuzione controllo batteria in background...")

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean(KEY_ENABLED, true)
        if (!isEnabled) {
            Log.d(TAG, "Notifiche disabilitate dall'utente.")
            return Result.success()
        }

        val threshold = sharedPrefs.getInt(KEY_THRESHOLD, 20)

        // Ottieni lo stato corrente della batteria
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent == null) {
            Log.e(TAG, "Impossibile ricavare l'intent dello stato della batteria.")
            return Result.failure()
        }

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level == -1 || scale == -1) {
            return Result.failure()
        }

        val batteryPercentage = (level * 100 / scale.toFloat()).toInt()
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                      status == BatteryManager.BATTERY_STATUS_FULL

        Log.d(TAG, "Livello batteria: $batteryPercentage%, In Carica: $isCharging, Soglia: $threshold%")

        // Salva cronologia locale
        LocalLogService.saveLog(context, batteryPercentage, isCharging, "Background")

        // Controllo Sonoff
        handleSonoffControl(batteryPercentage)

        val hasNotified = sharedPrefs.getBoolean(KEY_NOTIFIED_LOW, false)

        if (batteryPercentage <= threshold) {
            // Inviamo la notifica solo se NON è in carica (altrimenti la batteria è in fase di salita)
            // e solo se non abbiamo ancora notificato per questa discesa (evita lo spam)
            if (!isCharging) {
                if (!hasNotified) {
                    sendNotification(batteryPercentage, threshold)
                    sharedPrefs.edit().putBoolean(KEY_NOTIFIED_LOW, true).apply()
                } else {
                    Log.d(TAG, "Notifica di carica bassa già inviata per questo ciclo di scaricamento.")
                }
            } else {
                Log.d(TAG, "Batteria sotto la soglia ma in carica. Allarme non inviato.")
                // Se è in carica, resettiamo lo stato così che se viene ricollegato/scollegato possa ri-notificare se necessario
                if (hasNotified) {
                    sharedPrefs.edit().putBoolean(KEY_NOTIFIED_LOW, false).apply()
                }
            }
        } else {
            // Se la batteria è ritornata sopra la soglia, resettiamo lo stato di notifica
            if (hasNotified) {
                sharedPrefs.edit().putBoolean(KEY_NOTIFIED_LOW, false).apply()
                Log.d(TAG, "Batteria sopra la soglia. Reset dello stato di notifica.")
            }
        }

        return Result.success()
    }

    private fun handleSonoffControl(batteryPercentage: Int) {
        val sharedPrefs = context.getSharedPreferences(SonoffController.PREFS_NAME, Context.MODE_PRIVATE)
        val sonoffEnabled = sharedPrefs.getBoolean(SonoffController.KEY_ENABLED, false)
        if (!sonoffEnabled) {
            Log.d(TAG, "handleSonoffControl: sonoffEnabled=false, SKIP")
            return
        }

        val deviceId = sharedPrefs.getString(SonoffController.KEY_DEVICE_ID, "") ?: ""
        if (deviceId.isEmpty()) {
            Log.d(TAG, "handleSonoffControl: deviceId vuoto, SKIP")
            return
        }

        val onThreshold = sharedPrefs.getInt(SonoffController.KEY_ON_THRESHOLD, 30)
        val offThreshold = sharedPrefs.getInt(SonoffController.KEY_OFF_THRESHOLD, 80)
        val lastCommand = sharedPrefs.getString(SonoffController.KEY_LAST_COMMAND, "") ?: ""
        val accessToken = sharedPrefs.getString(SonoffController.KEY_ACCESS_TOKEN, "") ?: ""
        val region = sharedPrefs.getString(SonoffController.KEY_REGION, "eu") ?: "eu"
        if (accessToken.isEmpty()) {
            Log.d(TAG, "handleSonoffControl: accessToken vuoto, SKIP")
            return
        }

        val controller = SonoffController(context)

        Log.d(TAG, "handleSonoffControl START: battery=$batteryPercentage%, on<=$onThreshold%, off>=$offThreshold%, lastCommand=$lastCommand, deviceId=$deviceId, region=$region, atLen=${accessToken.length}")

        when {
            batteryPercentage >= offThreshold -> {
                Log.d(TAG, "handleSonoffControl DECISION: $batteryPercentage% >= $offThreshold% → SPEGNI (invio sempre, prima lastCommand era '$lastCommand')")
                Thread { controller.turnOff(deviceId) }.start()
            }
            batteryPercentage <= onThreshold -> {
                Log.d(TAG, "handleSonoffControl DECISION: $batteryPercentage% <= $onThreshold% → ACCENDI (invio sempre, prima lastCommand era '$lastCommand')")
                Thread { controller.turnOn(deviceId) }.start()
            }
            else -> {
                Log.d(TAG, "handleSonoffControl: batteria $batteryPercentage% in zona morta ($onThreshold < x < $offThreshold), nessuna azione")
            }
        }
    }

    private fun sendNotification(percentage: Int, threshold: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Creazione canale su Android Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
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

        val title = "Batteria Quasi Scarica!"
        val message = "La tua batteria è al $percentage%, al di sotto della soglia impostata di $threshold%."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning) // Icona di sistema classica
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Notifica inviata con successo!")
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile mostrare la notifica", e)
        }
    }
}
