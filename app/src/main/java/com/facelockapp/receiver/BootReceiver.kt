package com.facelockapp.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.facelockapp.LockScreenActivity
import com.facelockapp.data.PreferenceManager
import com.facelockapp.service.ScreenMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received broadcast: ${intent.action}")
        
        val action = intent.action
        val preferenceManager = PreferenceManager(context)
        
        // טיפול בבדיקה המתוזמנת
        if (action == "com.facelockapp.CHECK_SCREEN_AFTER_BOOT") {
            Log.d("BootReceiver", "Backup check triggered")
            checkScreenAndShowLock(context, preferenceManager)
            return
        }
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            (action == Intent.ACTION_PACKAGE_REPLACED && 
             intent.data?.schemeSpecificPart == context.packageName)) {
            
            Log.d("BootReceiver", "Boot completed, checking lock status...")
            
            // בדוק את מצב הנעילה מיד מ-SharedPreferences (מהיר מאוד)
            val isLockEnabled = preferenceManager.isLockEnabledSync()
            
            Log.d("BootReceiver", "Lock enabled: $isLockEnabled")
            
            if (isLockEnabled) {
                // התחל את השירות מיד ללא עיכוב
                val serviceIntent = Intent(context, ScreenMonitorService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                        Log.d("BootReceiver", "Started foreground service immediately")
                    } else {
                        context.startService(serviceIntent)
                        Log.d("BootReceiver", "Started service immediately")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error starting service", e)
                }
                
                // בדוק מיד אם המסך כרגע דולק (למקרה שהמכשיר נדלק בזמן boot)
                checkScreenAndShowLock(context, preferenceManager)
                
                // הוסף בדיקה נוספת אחרי 1 שנייה כגיבוי (אם השירות לא הספיק לבדוק)
                scheduleBackupCheck(context, preferenceManager)
            } else {
                Log.d("BootReceiver", "Lock is disabled, not starting service")
            }
        }
    }
    
    private fun checkScreenAndShowLock(context: Context, preferenceManager: PreferenceManager) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }
            
            if (isScreenOn && preferenceManager.isLockEnabledSync()) {
                Log.d("BootReceiver", "Screen is on after boot, showing lock screen immediately")
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                try {
                    context.startActivity(lockIntent)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error showing lock screen", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error checking screen state", e)
        }
    }
    
    private fun scheduleBackupCheck(context: Context, preferenceManager: PreferenceManager) {
        try {
            val checkIntent = Intent(context, BootReceiver::class.java).apply {
                action = "com.facelockapp.CHECK_SCREEN_AFTER_BOOT"
                putExtra("preference_manager_available", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                checkIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000, // אחרי 1 שנייה
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error scheduling backup check", e)
        }
    }
}
