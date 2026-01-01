package com.facelockapp.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.facelockapp.receiver.ScreenReceiver

class ScreenMonitorService : Service() {
    private var screenReceiver: ScreenReceiver? = null
    private val CHANNEL_ID = "ScreenMonitorChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (screenReceiver == null) {
            registerScreenReceiver()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
            }
            screenReceiver = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 500, // Restart in 0.5 seconds
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    private fun registerScreenReceiver() {
        screenReceiver = ScreenReceiver()
        val filter = IntentFilter().apply {
            // ACTION_SCREEN_ON קורה מיד כשהמסך נדלק, לפני פתיחת המכשיר
            // זה מאפשר להציג את מסך הנעילה מיד ללא delay
            addAction(Intent.ACTION_SCREEN_ON)
            // ACTION_USER_PRESENT נשאר כגיבוי למקרה ש-ACTION_SCREEN_ON לא עובד
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (e: Exception) {
            // Error registering receiver
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App Lock Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the app lock service running."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceLockApp is active")
            .setContentText("App lock service is running to protect your apps.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
