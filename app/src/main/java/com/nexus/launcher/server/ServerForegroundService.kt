package com.nexus.launcher.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexus.launcher.MainActivity
import com.nexus.launcher.R

/**
 * Foreground service while a Minecraft server process is running.
 * Reduces chance Android kills the process; shows ongoing notification.
 *
 * Does NOT guarantee 24/7 uptime — phone sleep, thermal, and OEM killers still apply.
 */
class ServerForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val name = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "Minecraft Server"
                val port = intent?.getIntExtra(EXTRA_PORT, 25565) ?: 25565
                startForeground(NOTIFICATION_ID, buildNotification(name, port))
            }
        }
        return START_STICKY
    }

    private fun buildNotification(name: String, port: Int): Notification {
        createChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Server: $name")
            .setContentText("Running on port $port · tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(open)
            .addAction(0, "Stop service", stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Server hosting",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while an on-device Minecraft server is running"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "nexus_server_hosting"
        const val NOTIFICATION_ID = 7101
        const val ACTION_STOP = "com.nexus.launcher.server.STOP_FG"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_PORT = "port"

        fun start(context: Context, serverName: String, port: Int) {
            val i = Intent(context, ServerForegroundService::class.java).apply {
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ServerForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
