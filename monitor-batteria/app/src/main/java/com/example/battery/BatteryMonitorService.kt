package com.example.battery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

class BatteryMonitorService : Service() {
    companion object {
        private const val TAG = "BatteryMonitorService"
        private const val FOREGROUND_CHANNEL_ID = "battery_monitor_foreground"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ACTION_START = "com.example.battery.action.START"
    }

    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = BatteryMonitor.parseState(intent)
            Thread {
                BatteryAutomation.handleBatteryState(applicationContext, state, "Service")
            }.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun registerBatteryReceiver() {
        if (receiverRegistered) return

        val stickyIntent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiverRegistered = true

        if (stickyIntent != null) {
            val state = BatteryMonitor.parseState(stickyIntent)
            Thread {
                BatteryAutomation.handleBatteryState(applicationContext, state, "Service")
            }.start()
        }
    }

    private fun startForegroundNotification() {
        createForegroundChannel()
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitor batteria attivo in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createForegroundChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Monitor batteria in background",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifica persistente del monitor batteria"
        }
        notificationManager.createNotificationChannel(channel)
    }
}
