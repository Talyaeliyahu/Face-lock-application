package com.facelockapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(
    isLockEnabled: Boolean,
    hasPin: Boolean,
    hasPattern: Boolean,
    hasFace: Boolean,
    onLockEnabledChanged: (Boolean) -> Unit,
    onPinSetupClick: () -> Unit,
    onPatternSetupClick: () -> Unit,
    onFaceEnrollClick: () -> Unit,
    onRemovePin: () -> Unit,
    onRemovePattern: () -> Unit
) {
    val context = LocalContext.current
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var showRemovePatternDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var permissionRequested by remember { mutableStateOf(false) }

    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status.isGranted && permissionRequested) {
            onFaceEnrollClick()
            permissionRequested = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(24.dp) // החזר את ה-padding המקורי
    ) {
        // Header with icon
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "הגדרות נעילה",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Main toggle card with gradient effect
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), // הקטן את המרווח התחתון
            colors = CardDefaults.cardColors(
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // הוסף מסגרת בהירה
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp), // הגדל את ה-padding האנכי כדי שיהיה באותו גודל כמו שאר הכרטיסים
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp), // הקטן עוד יותר מ-24dp ל-22dp
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "הפעל נעילת פנים",
                            fontSize = 17.sp, // הקטן עוד יותר מ-18sp ל-17sp
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            if (isLockEnabled) "הנעילה פעילה" else "הנעילה כבויה",
                            fontSize = 12.sp, // הקטן עוד יותר מ-13sp ל-12sp
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Switch(
                    checked = isLockEnabled,
                    onCheckedChange = {
                    if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        showOverlayPermissionDialog = true
                    } else {
                        onLockEnabledChanged(it)
                    }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isLockEnabled,
            enter = androidx.compose.animation.fadeIn(tween(300)) + androidx.compose.animation.slideInVertically(tween(300)),
            exit = androidx.compose.animation.fadeOut(tween(300)) + androidx.compose.animation.slideOutVertically(tween(300))
        ) {
            Column {
                // Face recognition setup
                SetupOption(
                    icon = Icons.Default.Face,
                    title = if (hasFace) "הגדר מחדש זיהוי פנים" else "הגדר זיהוי פנים",
                    subtitle = if (hasFace) "זיהוי הפנים הוגדר" else "חובה להגדיר זיהוי פנים",
                    isCompleted = hasFace,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = { 
                        if (cameraPermissionState.status.isGranted) {
                            onFaceEnrollClick()
                        } else {
                            permissionRequested = true
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Backup methods header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsBackupRestore,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "שיטות גיבוי",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                SetupOption(
                    icon = Icons.Default.Password,
                    title = if (hasPin) "שנה PIN" else "הגדר PIN",
                    subtitle = if (hasPin) "קוד ה-PIN שלך הוגדר" else "מומלץ להגדיר גיבוי",
                    isCompleted = hasPin,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    onClick = onPinSetupClick,
                    onRemove = if (hasPin) { { showRemovePinDialog = true } } else null
                )

                Spacer(modifier = Modifier.height(4.dp))

                SetupOption(
                    icon = Icons.Default.TouchApp,
                    title = if (hasPattern) "שנה תבנית" else "הגדר תבנית",
                    subtitle = if (hasPattern) "התבנית שלך הוגדרה" else "מומלץ להגדיר גיבוי",
                    isCompleted = hasPattern,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onPatternSetupClick,
                    onRemove = if (hasPattern) { { showRemovePatternDialog = true } } else null
                )

                if (!hasPin && !hasPattern) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "נדרש להגדיר לפחות שיטת גיבוי אחת (PIN או תבנית).",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRemovePinDialog) {
        RemoveDialog(name = "PIN", onConfirm = { onRemovePin(); showRemovePinDialog = false }, onDismiss = { showRemovePinDialog = false })
    }
    if (showRemovePatternDialog) {
        RemoveDialog(name = "תבנית", onConfirm = { onRemovePattern(); showRemovePatternDialog = false }, onDismiss = { showRemovePatternDialog = false })
    }
    if (showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            onConfirm = {
                showOverlayPermissionDialog = false
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            },
            onDismiss = { showOverlayPermissionDialog = false }
        )
    }
}

@Composable
private fun OverlayPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("נדרשת הרשאה מיוחדת") },
        text = { Text("כדי להציג את מסך הנעילה, האפליקציה צריכה הרשאה להציג מעל אפליקציות אחרות. אנא אפשר זאת במסך ההגדרות שיוצג.") },
        confirmButton = { Button(onClick = onConfirm) { Text("פתח הגדרות") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
private fun SetupOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with background circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isCompleted) {
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Setup",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RemoveDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הסר $name") },
        text = { Text("האם אתה בטוח שברצונך להסיר את ה-$name שלך?") },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("הסר") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

