package com.zenpaper.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zenpaper.WallpaperController
import java.util.concurrent.TimeUnit

class WallpaperUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WallpaperUpdateWorker"
        private const val CHANNEL_ID = "zenpaper_channel"
        private const val NOTIFICATION_ID = 1001
        
        fun rescheduleNextWork(context: Context) {
            val prefs = context.getSharedPreferences("zenpaper_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("pref_background_service_enabled", true)
            if (enabled) {
                val interval = prefs.getInt("pref_sync_interval", 15)
                Log.d(TAG, "Rescheduling next background update in $interval minutes")

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val nextWork = OneTimeWorkRequestBuilder<WallpaperUpdateWorker>()
                    .setInitialDelay(interval.toLong(), TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ZenPaperBackgroundWork",
                    ExistingWorkPolicy.REPLACE, // Chain recursively to bypass WorkManager's 15m minimum
                    nextWork
                )
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic wallpaper update task")
        val context = applicationContext
        val prefs = context.getSharedPreferences("zenpaper_prefs", Context.MODE_PRIVATE)
        
        try {
            val category = prefs.getString("pref_category", "all") ?: "all"
            val color = prefs.getString("pref_color", "all") ?: "all"
            val target = prefs.getString("pref_target", "both") ?: "both"

            // Delegate to controller to fetch, crop-scale to resolution, and apply wallpaper
            val appliedUrl = WallpaperController.executeWallpaperUpdate(context, category, color, target)

            // Save last sync logs
            prefs.edit()
                .putLong("pref_last_sync_time", System.currentTimeMillis())
                .putString("pref_last_sync_status", "Success")
                .putString("pref_last_sync_wallpaper", appliedUrl.substringAfterLast("/"))
                .apply()

            showNotification(
                context, 
                "Wallpaper Updated!", 
                "Successfully set a new wallpaper from $category category."
            )

            // Enqueue the next loop recursively
            rescheduleNextWork(context)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating wallpaper in background", e)
            
            // Save failure logs
            prefs.edit()
                .putLong("pref_last_sync_time", System.currentTimeMillis())
                .putString("pref_last_sync_status", "Failed: ${e.localizedMessage ?: e.message}")
                .apply()

            showNotification(
                applicationContext, 
                "Wallpaper Update Failed", 
                "Error: ${e.message}"
            )
            
            // Always chain next loop recursively even on failure, to keep background updates active!
            rescheduleNextWork(context)
            return Result.failure()
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZenPaper Wallpaper Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when mobile wallpaper is refreshed"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
