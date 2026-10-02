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
import com.example.battery.engine.StandbyTracker
import com.example.data.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {
    companion object {
        private const val TAG = "BatteryMonitorService"
        private const val FOREGROUND_CHANNEL_ID = "battery_monitor_foreground"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ACTION_START = "com.example.battery.action.START"

        private val _batteryStateFlow = MutableStateFlow(BatteryState())
        val batteryStateFlow = _batteryStateFlow.asStateFlow()

        fun updateFromSystem(context: Context): BatteryState {
            val stickyIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val state = if (stickyIntent != null) {
                BatteryMonitor.parseState(stickyIntent, context)
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
                BatteryState(percentage = level)
            }
            if (state.percentage > 0) {
                _batteryStateFlow.value = state
            }
            return state
        }
    }

    private var receiverRegistered = false
    private var screenReceiverRegistered = false
    private var wasCharging = false
    private var lastLevel = -1

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: BatteryRepository

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val currentLevel = if (lastLevel != -1) lastLevel else _batteryStateFlow.value.percentage
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    StandbyTracker.onScreenOff(applicationContext, currentLevel)
                    Log.d(TAG, "ACTION_SCREEN_OFF logged, standby tracking engaged")
                }
                Intent.ACTION_SCREEN_ON -> {
                    StandbyTracker.onScreenOn(applicationContext, currentLevel)
                    Log.d(TAG, "ACTION_SCREEN_ON logged, deep sleep computed without wakelocks")
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = BatteryMonitor.parseState(intent, applicationContext)
            _batteryStateFlow.value = state
            lastLevel = state.percentage

            serviceScope.launch {
                handleSessionTracking(state)
                BatteryAutomation.handleBatteryState(applicationContext, state, "Service")
            }
        }
    }

    private suspend fun handleSessionTracking(state: BatteryState) {
        if (state.isCharging) {
            if (!wasCharging) {
                wasCharging = true
                repository.onChargingStarted(state.percentage, state.voltage)
            } else {
                repository.updateChargingProgress(
                    currentLevel = state.percentage,
                    currentMa = state.currentMa,
                    voltageMv = state.voltage,
                    tempCelsius = state.temperature
                )
            }
        } else {
            if (wasCharging) {
                wasCharging = false
                repository.onChargingFinished(state.percentage, stoppedBySonoff = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = BatteryRepository(applicationContext)
        startForegroundNotification()
        registerBatteryReceiver()
        registerScreenReceiver()
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
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun registerBatteryReceiver() {
        if (receiverRegistered) return

        val stickyIntent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiverRegistered = true

        if (stickyIntent != null) {
            val state = BatteryMonitor.parseState(stickyIntent, applicationContext)
            _batteryStateFlow.value = state
            wasCharging = state.isCharging
            lastLevel = state.percentage
            serviceScope.launch {
                if (state.isCharging) {
                    repository.onChargingStarted(state.percentage, state.voltage)
                }
                BatteryAutomation.handleBatteryState(applicationContext, state, "Service")
            }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile registrare screenReceiver con flag", e)
            try {
                registerReceiver(screenReceiver, filter)
                screenReceiverRegistered = true
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback registrazione screenReceiver fallito", e2)
            }
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitor telemetria e ricarica attiva")
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
