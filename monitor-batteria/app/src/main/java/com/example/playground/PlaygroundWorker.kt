package com.example.playground

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PlaygroundWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "PlaygroundWorker doWork")
        return try {
            PlaygroundEngine.evaluateTimerProjects(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PlaygroundWorker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PlaygroundWorker"
        const val UNIQUE_WORK_NAME = "PlaygroundTimerWork"
        private const val INTERVAL_MINUTES = 1L

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<PlaygroundWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "PlaygroundWorker scheduled every $INTERVAL_MINUTES min")
        }
    }
}
