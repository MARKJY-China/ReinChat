package com.reinchat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.reinchat.app.MainActivity
import com.reinchat.app.R

/**
 * Keeps the process alive while an AI response is streaming, so HyperOS / MIUI
 * does not freeze the OkHttp read loop when the app is sent to background.
 *
 * This is a foreground-service bridge, not a server-side continuation: if the
 * whole process is killed, the relay stage still needs the cloud job API.
 */
class ChatStreamService : Service() {
    companion object {
        private const val CHANNEL_ID = "reinchat_stream"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.reinchat.app.STREAM_START"
        private const val ACTION_STOP = "com.reinchat.app.STREAM_STOP"

        fun start(context: Context) {
            try {
                val intent = Intent(context, ChatStreamService::class.java).setAction(ACTION_START)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                // Some vendor ROMs block background FGS starts; generation still
                // works while the process remains alive.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ChatStreamService::class.java).setAction(ACTION_STOP))
            } catch (_: Throwable) {
                // no-op
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ReinChat 正在生成回复")
            .setContentText("切回 ReinChat 可继续查看，或在输入框停止生成")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI 生成状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在后台生成回复时保持连接"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}
