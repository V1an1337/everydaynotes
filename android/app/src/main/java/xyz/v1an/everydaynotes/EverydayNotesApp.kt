package xyz.v1an.everydaynotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class EverydayNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CAPTURE,
                "EverydayNotes",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_CAPTURE = "everydaynotes_capture"
    }
}

