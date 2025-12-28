package com.facelockapp.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.facelockapp.service.ScreenMonitorService
import com.facelockapp.ui.screens.*
import com.facelockapp.ui.viewmodel.LockScreenState
import com.facelockapp.ui.viewmodel.LockViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Setup : Screen("setup")
    object Lock : Screen("lock")
    object PinSetup : Screen("pin_setup")
    object PatternSetup : Screen("pattern_setup")
    object FaceEnroll : Screen("face_enroll")
    object PinVerify : Screen("pin_verify")
    object PatternVerify : Screen("pattern_verify")
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: LockViewModel = viewModel(),
    startDestination: String
) {
    NavHost(navController = navController, modifier = modifier, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            val storedEmbedding by viewModel.storedEmbedding.collectAsState()
            val hasPin by viewModel.hasPin.collectAsState()
            val hasPattern by viewModel.hasPattern.collectAsState()

            AuthScreen(
                hasPin = hasPin,
                hasPattern = hasPattern,
                storedEmbedding = storedEmbedding,
                onAuthenticated = { navController.navigate(Screen.Setup.route) { popUpTo(Screen.Auth.route) { inclusive = true } } },
                onUsePin = { navController.navigate(Screen.PinVerify.route) },
                onUsePattern = { navController.navigate(Screen.PatternVerify.route) },
                onVerifyPin = viewModel::verifyPin,
                onVerifyPattern = viewModel::verifyPattern
            )
        }

        composable(Screen.Setup.route) {
            val context = LocalContext.current
            val isLockEnabled by viewModel.isLockEnabled.collectAsState()
            val hasPin by viewModel.hasPin.collectAsState()
            val hasPattern by viewModel.hasPattern.collectAsState()
            val hasFace by viewModel.hasFace.collectAsState()

            SetupScreen(
                isLockEnabled = isLockEnabled, hasPin = hasPin, hasPattern = hasPattern, hasFace = hasFace,
                onLockEnabledChanged = { enabled ->
                    viewModel.setLockEnabled(enabled)
                    toggleScreenMonitorService(context, enabled)
                },
                onPinSetupClick = { navController.navigate(Screen.PinSetup.route) },
                onPatternSetupClick = { navController.navigate(Screen.PatternSetup.route) },
                onFaceEnrollClick = { navController.navigate(Screen.FaceEnroll.route) },
                onRemovePin = { viewModel.removePin() },
                onRemovePattern = { viewModel.removePattern() }
            )
        }

        composable(Screen.PinSetup.route) {
            PinScreen(
                title = "הגדרת PIN", subtitle = "הקלד קוד בן 4 ספרות", isSetup = true,
                onPinComplete = { viewModel.savePinCode(it); navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.PatternSetup.route) {
            PatternScreen(
                title = "הגדרת תבנית", subtitle = "צייר תבנית על ידי חיבור הנקודות", isSetup = true,
                onPatternComplete = { viewModel.savePattern(it); navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.FaceEnroll.route) {
            FaceRecognitionScreen(
                title = "הגדרת זיהוי פנים",
                subtitle = "הצב/י את פניך מול המצלמה וסובב/י את הראש לאט לצדדים",
                onFaceEnrolled = { embedding ->
                    viewModel.saveFaceEmbedding(embedding)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun LockScreenNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: LockViewModel,
    lockState: LockScreenState.Ready,
    onUnlock: () -> Unit
) {
    NavHost(navController = navController, startDestination = Screen.Lock.route, modifier = modifier) {
        composable(Screen.Lock.route) {
            LockScreen(
                storedEmbedding = lockState.storedEmbedding,
                onUnlock = onUnlock,
                onUsePinBackup = { if (lockState.hasPin) navController.navigate(Screen.PinVerify.route) },
                onUsePatternBackup = { if (lockState.hasPattern) navController.navigate(Screen.PatternVerify.route) },
                hasPin = lockState.hasPin,
                hasPattern = lockState.hasPattern
            )
        }

        composable(Screen.PinVerify.route) {
            PinScreen(
                title = "אימות PIN", subtitle = "הקלד את קוד הגיבוי שלך", isSetup = false,
                onPinComplete = { onUnlock() },
                onCancel = { navController.popBackStack() },
                onVerifyPin = viewModel::verifyPin
            )
        }

        composable(Screen.PatternVerify.route) {
            PatternScreen(
                title = "אימות תבנית", subtitle = "צייר את תבנית הגיבוי שלך", isSetup = false,
                onPatternComplete = { onUnlock() },
                onCancel = { navController.popBackStack() },
                onVerifyPattern = viewModel::verifyPattern
            )
        }
    }
}

private fun toggleScreenMonitorService(context: Context, enable: Boolean) {
    val intent = Intent(context, ScreenMonitorService::class.java)
    try {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    } catch (e: Exception) {
        // Handle exceptions
    }
}
