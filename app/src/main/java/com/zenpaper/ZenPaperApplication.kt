package com.zenpaper

import android.app.Application
import com.zenpaper.worker.WallpaperUpdateWorker

class ZenPaperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupWallpaperWorker()
    }

    fun setupWallpaperWorker() {
        // Enqueue the custom recursive sync loop chain on startup
        WallpaperUpdateWorker.rescheduleNextWork(this)
    }
}
