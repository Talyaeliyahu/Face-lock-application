package com.facelockapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.facelockapp.navigation.LockScreenNavGraph
import com.facelockapp.ui.theme.FaceLockAppTheme
import com.facelockapp.ui.viewmodel.LockScreenState
import com.facelockapp.ui.viewmodel.LockViewModel
import kotlin.system.exitProcess

class LockScreenActivity : ComponentActivity() {
    private val viewModel: LockViewModel by viewModels()
    private var isUnlocked: Boolean = false
    
    // Handler לבדיקה תקופתית (backup mechanism)
    private val safetyCheckHandler = Handler(Looper.getMainLooper())
    private var safetyCheckRunnable: Runnable? = null
    private var lastBringToFrontTime: Long = 0
    
    // State להצגת הדיאלוג
    private val showWarningDialog = mutableStateOf(false)
    
    // דגל שמציין שהמשתמש לחץ על כפתור ניווט
    private var userTriedToLeave = false
    
    // דגל שנועל את המסך כשיש דיאלוג
    private var isDialogLockActive = false
    
    // מונה קריאות להחזרה לחזית (למניעת קריסות)
    private var bringToFrontCallCount = 0
    private var lastBringToFrontResetTime = System.currentTimeMillis()
    private val MAX_CALLS_PER_SECOND = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // טיפול בקריסות בלתי צפויות
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(throwable)
        }
        
        enableEdgeToEdge()

        // הפעל immersive mode מלא
        enableImmersiveMode()

        // הפעל את המסך מיד ללא אנימציות
        overridePendingTransition(0, 0)

        setContent {
            FaceLockAppTheme {
                val lockState by viewModel.lockScreenState.collectAsState()
                var showDialog by remember { showWarningDialog }

                when (val state = lockState) {
                    is LockScreenState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is LockScreenState.Ready -> {
                        LockScreenNavGraph(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel,
                            lockState = state,
                            onUnlock = {
                                isUnlocked = true
                                // עצור את הבדיקה התקופתית
                                stopSafetyCheck()
                                // העבר את המשימה לרקע כדי לחזור למצב הקודם (מסך בית, אפליקציה אחרת וכו')
                                moveTaskToBack(true)
                                // סיים ללא אנימציה
                                finish()
                                overridePendingTransition(0, 0)
                            }
                        )
                        
                        // דיאלוג אזהרה - יופיע רק אחרי לחיצה על כפתור ניווט
                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { 
                                    // לא מאפשרים סגירה על ידי לחיצה מחוץ לדיאלוג
                                },
                                title = { Text("נעילת מכשיר פעילה") },
                                text = { Text("יש לזהות את הפנים שלך כדי לפתוח את המכשיר") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            // סגור את הדיאלוג
                                            showDialog = false
                                            // איפוס הדגלים
                                            userTriedToLeave = false
                                            isDialogLockActive = false
                                            // איפוס מונה הקריאות
                                            bringToFrontCallCount = 0
                                            // החזר למסך הזיהוי
                                            safetyCheckHandler.postDelayed({
                                                if (!isUnlocked && !isFinishing) {
                                                    bringToFrontSafe()
                                                }
                                            }, 100)
                                        }
                                    ) {
                                        Text("אישור")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // התחל בדיקת בטיחות תקופתית (backup mechanism)
        startSafetyCheck()
    }

    /**
     * מטפל בקריסות ומפעיל מחדש את האפליקציה
     */
    private fun handleCrash(throwable: Throwable) {
        try {
            // רשום את השגיאה (אופציונלי)
            android.util.Log.e("LockScreenActivity", "Crash detected", throwable)
            
            if (!isUnlocked) {
                // הפעל מחדש את האפליקציה
                val intent = Intent(this, LockScreenActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                }
                startActivity(intent)
            }
            
            // סיים את התהליך הנוכחי
            exitProcess(0)
        } catch (e: Exception) {
            // אם גם הטיפול בקריסה נכשל - פשוט צא
            exitProcess(1)
        }
    }

    override fun onBackPressed() {
        // המשתמש לחץ על כפתור חזרה - הצג דיאלוג
        if (!isUnlocked && !isFinishing) {
            userTriedToLeave = true
            isDialogLockActive = true
            showWarningDialog.value = true
            // החזר מיד לחזית
            bringToFrontAggressively()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // המשתמש לחץ על כפתור הבית או האפליקציות האחרונות - הצג דיאלוג
        if (!isUnlocked && !isFinishing) {
            userTriedToLeave = true
            isDialogLockActive = true
            showWarningDialog.value = true
            // החזר מיד לחזית
            bringToFrontAggressively()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isUnlocked && !isFinishing) {
            // אם יש נעילת דיאלוג פעילה - החזר אגרסיבית
            if (isDialogLockActive) {
                bringToFrontAggressively()
            } else if (!userTriedToLeave) {
                // אחרת, החזר רגיל
                safetyCheckHandler.postDelayed({
                    if (!isUnlocked && !isFinishing && !isDialogLockActive) {
                        bringToFrontSafe()
                    }
                }, 50)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isUnlocked && !isFinishing) {
            // אם יש נעילת דיאלוג פעילה - החזר אגרסיבית
            if (isDialogLockActive) {
                bringToFrontAggressively()
            } else if (!userTriedToLeave) {
                safetyCheckHandler.postDelayed({
                    if (!isUnlocked && !isFinishing && !isDialogLockActive) {
                        bringToFrontSafe()
                    }
                }, 50)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ודא שה-immersive mode פעיל
        enableImmersiveMode()
        // איפוס הזמן האחרון
        lastBringToFrontTime = 0
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isUnlocked && !isFinishing) {
            // איבדנו focus
            if (isDialogLockActive) {
                // אם יש נעילת דיאלוג - החזר מיד אגרסיבית
                bringToFrontAggressively()
            } else if (!userTriedToLeave) {
                // אחרת, החזר רגיל
                safetyCheckHandler.postDelayed({
                    if (!isUnlocked && !isFinishing && !isDialogLockActive) {
                        bringToFrontSafe()
                    }
                }, 30)
            }
        }
        if (hasFocus) {
            // קיבלנו focus - ודא שה-immersive mode פעיל
            enableImmersiveMode()
        }
    }

    /**
     * בדיקת בטיחות תקופתית - כל 150ms כשיש נעילת דיאלוג, 250ms אחרת
     */
    private fun startSafetyCheck() {
        safetyCheckRunnable = object : Runnable {
            override fun run() {
                if (!isUnlocked && !isFinishing && !isDestroyed) {
                    // בדוק אם יש לנו focus
                    if (!hasWindowFocus()) {
                        if (isDialogLockActive) {
                            // אם יש נעילת דיאלוג - החזר אגרסיבית
                            bringToFrontAggressively()
                        } else if (!userTriedToLeave) {
                            bringToFrontSafe()
                        }
                    }
                    // תזמן את הבדיקה הבאה - יותר תכוף אם יש נעילת דיאלוג
                    val checkInterval = if (isDialogLockActive) 150L else 250L
                    safetyCheckHandler.postDelayed(this, checkInterval)
                }
            }
        }
        safetyCheckHandler.postDelayed(safetyCheckRunnable!!, 250)
    }

    /**
     * עוצר את בדיקת הבטיחות
     */
    private fun stopSafetyCheck() {
        safetyCheckRunnable?.let {
            safetyCheckHandler.removeCallbacks(it)
            safetyCheckRunnable = null
        }
    }

    /**
     * בדיקה אם עברנו את מגבלת הקריאות לשנייה
     */
    private fun shouldThrottleCalls(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // איפוס המונה כל שנייה
        if (currentTime - lastBringToFrontResetTime > 1000) {
            bringToFrontCallCount = 0
            lastBringToFrontResetTime = currentTime
        }
        
        bringToFrontCallCount++
        
        // אם עברנו את המגבלה - דחה את הקריאה
        return bringToFrontCallCount > MAX_CALLS_PER_SECOND
    }

    /**
     * מחזיר את האפליקציה לחזית באופן אגרסיבי (עם הגנה מפני קריסות)
     */
    private fun bringToFrontAggressively() {
        // בדוק אם עברנו את מגבלת הקריאות
        if (shouldThrottleCalls()) {
            return
        }
        
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            // רשום שגיאה אבל המשך
            android.util.Log.e("LockScreenActivity", "Failed to bring to front aggressively", e)
        }
        
        // תזמן בדיקה נוספת אחרי 50ms (פחות תכוף)
        safetyCheckHandler.postDelayed({
            if (!isUnlocked && !isFinishing && isDialogLockActive && !shouldThrottleCalls()) {
                try {
                    val intent = Intent(this, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                } catch (e: Exception) {
                    android.util.Log.e("LockScreenActivity", "Failed secondary bring to front", e)
                }
            }
        }, 50)
    }

    /**
     * מחזיר את האפליקציה לחזית בצורה בטוחה (עם debouncing והגנה)
     */
    private fun bringToFrontSafe() {
        // בדוק אם עברנו את מגבלת הקריאות
        if (shouldThrottleCalls()) {
            return
        }
        
        // מניעת קריאות תכופות מדי (debouncing)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBringToFrontTime < 100) {
            return
        }
        lastBringToFrontTime = currentTime
        
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            android.util.Log.e("LockScreenActivity", "Failed to bring to front", e)
        }
    }

    private fun enableImmersiveMode() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
                val navigationBarsVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                if (navigationBarsVisible && !isUnlocked) {
                    window.decorView.post {
                        windowInsetsController?.hide(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
                    }
                }
                ViewCompat.onApplyWindowInsets(view, insets)
            }
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LockScreenActivity", "Failed to enable immersive mode", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // עצור את הבדיקה התקופתית
        stopSafetyCheck()
        
        // אם ה-Activity נהרס בלי לפתוח את הנעילה - הפעל אותו מחדש
        if (!isUnlocked && !isChangingConfigurations) {
            try {
                val intent = Intent(this, LockScreenActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("LockScreenActivity", "Failed to restart on destroy", e)
            }
        }
    }
}