package com.example.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device booted, re-scheduling battery monitor")

            val serviceIntent = Intent(context, BatteryMonitorService::class.java).apply {
                action = BatteryMonitorService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)

            val workRequest = PeriodicWorkRequestBuilder<BatteryCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BatteryMonitorWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
