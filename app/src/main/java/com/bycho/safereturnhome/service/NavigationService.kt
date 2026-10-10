package com.bycho.safereturnhome.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bycho.safereturnhome.MainActivity
import com.bycho.safereturnhome.R
import com.bycho.safereturnhome.navigation.Routes

class NavigationService : Service() {

    companion object {
        const val CHANNEL_ID = "navigation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val EXTRA_REMAINING_DISTANCE = "EXTRA_REMAINING_DISTANCE"
        const val EXTRA_ETA_MINUTES = "EXTRA_ETA_MINUTES"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startNavigationService()
            ACTION_UPDATE -> updateNotification(intent)
            ACTION_STOP -> stopNavigationService()
        }
        return START_NOT_STICKY
    }

    private fun startNavigationService() {
        createNotificationChannel()
        val notification = buildNotification(0, 0, 0)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(intent: Intent) {
        val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
        val remainingDistance = intent.getIntExtra(EXTRA_REMAINING_DISTANCE, 0)
        val etaMinutes = intent.getIntExtra(EXTRA_ETA_MINUTES, 0)

        val notification = buildNotification(progress, remainingDistance, etaMinutes)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopNavigationService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(progress: Int, remainingDistance: Int, etaMinutes: Int): Notification {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(Routes.Map.deepLinkUri),
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = "남은 거리: ${remainingDistance}m · 예상 시간: 약 ${etaMinutes}분"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("경로 안내 중")
            .setContentText(contentText)
            .setSubText("진행률: $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "경로 안내",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "경로 안내 진행 상황을 표시합니다."
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
