package com.localai.companion.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localai.companion.LocalAIApplication
import com.localai.companion.R

/**
 * Foreground service for running inference in the background.
 */
class InferenceService : Service() {

    companion object {
        const val CHANNEL_ID = "inference_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.localai.companion.action.START_INFERENCE"
        const val ACTION_STOP = "com.localai.companion.action.STOP_INFERENCE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.status_generating))
            .setSmallIcon(R.drawable.ic_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Inference",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background inference"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
