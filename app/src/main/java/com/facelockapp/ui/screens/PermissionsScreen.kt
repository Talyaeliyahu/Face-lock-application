package com.facelockapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for the standard camera permission
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // State to track the special overlay permission. We need to re-check it manually.
    var hasOverlayPermission by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) }

    // Re-check overlay permission when the app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allPermissionsGranted = cameraPermissionState.status.isGranted && hasOverlayPermission

    // Proceed automatically once all permissions are granted
    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("נדרשות הרשאות", fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "כדי להפעיל את נעילת הפנים, האפליקציה צריכה מספר הרשאות חיוניות. אנא אשר אותן כדי להמשיך.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Request Camera permission if not granted
        if (!cameraPermissionState.status.isGranted) {
            PermissionRequestBox(
                text = "הרשאת מצלמה נדרשת לזיהוי הפנים.",
                buttonText = "אפשר גישה למצלמה",
                onClick = { cameraPermissionState.launchPermissionRequest() }
            )
        }

        // Request Overlay permission if camera is granted but overlay is not
        if (cameraPermissionState.status.isGranted && !hasOverlayPermission) {
            PermissionRequestBox(
                text = "הרשאת הצגה מעל אפליקציות אחרות נדרשת להצגת מסך הנעילה.",
                buttonText = "פתח הגדרות הצגה",
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun PermissionRequestBox(text: String, buttonText: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}
