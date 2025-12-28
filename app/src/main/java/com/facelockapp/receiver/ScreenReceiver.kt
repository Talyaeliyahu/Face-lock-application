package com.facelockapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.facelockapp.LockScreenActivity
import com.facelockapp.data.PreferenceManager

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // הפעל את LockScreenActivity מיד כשהמסך נדלק (ACTION_SCREEN_ON)
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
            // בדוק בצורה מהירה מאוד מ-SharedPreferences (ללא עיכובים)
            val preferenceManager = PreferenceManager(context)
            val isLockEnabled = preferenceManager.isLockEnabledSync()
            
            if (isLockEnabled) {
                Log.d("ScreenReceiver", "Lock enabled, showing lock screen immediately")
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
                    Log.e("ScreenReceiver", "Error starting activity", e)
                }
            } else {
                Log.d("ScreenReceiver", "Lock disabled, not showing lock screen")
            }
        }
    }
}
