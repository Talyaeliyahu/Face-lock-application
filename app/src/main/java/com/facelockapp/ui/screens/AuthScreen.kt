package com.facelockapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facelockapp.ui.components.FaceEmbedding
import com.facelockapp.ui.components.FaceRecognitionView

@Composable
fun AuthScreen(
    hasPin: Boolean,
    hasPattern: Boolean,
    storedEmbedding: FaceEmbedding?,
    onAuthenticated: () -> Unit,
    onUsePin: () -> Unit,
    onUsePattern: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onVerifyPattern: suspend (String) -> Boolean
) {
    var selectedAuthMethod by remember { mutableStateOf<AuthMethod?>(null) }

    when (selectedAuthMethod) {
        null, AuthMethod.FACE -> {
            FaceAuthContent(
                storedEmbedding = storedEmbedding,
                onSuccess = onAuthenticated,
                onUseBackup = {
                    // תמיד עבור דרך מסך הבחירה, גם אם יש רק שיטה אחת
                    selectedAuthMethod = AuthMethod.CHOICE
                }
            )
        }
        AuthMethod.CHOICE -> {
            BackupChoiceScreen(
                hasPin = hasPin,
                hasPattern = hasPattern,
                onSelectPin = { selectedAuthMethod = AuthMethod.PIN },
                onSelectPattern = { selectedAuthMethod = AuthMethod.PATTERN },
                onReturnToFace = { selectedAuthMethod = AuthMethod.FACE }
            )
        }
        AuthMethod.PIN -> {
            PinScreen(
                title = "אימות PIN",
                subtitle = "הקלד את קוד הגיבוי שלך",
                isSetup = false,
                onPinComplete = { onAuthenticated() },
                onCancel = { selectedAuthMethod = null },
                onVerifyPin = onVerifyPin
            )
        }
        AuthMethod.PATTERN -> {
            PatternScreen(
                title = "אימות תבנית",
                subtitle = "צייר את תבנית הגיבוי שלך",
                isSetup = false,
                onPatternComplete = { onAuthenticated() },
                onCancel = { selectedAuthMethod = null },
                onVerifyPattern = onVerifyPattern
            )
        }
    }
}

@Composable
private fun FaceAuthContent(storedEmbedding: FaceEmbedding?, onSuccess: () -> Unit, onUseBackup: () -> Unit) {
    var showFaceError by remember { mutableStateOf(false) }
    var successCount by remember { mutableStateOf(0) }
    var verificationAttempts by remember { mutableStateOf(0) }
    var lastVerificationResult by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(0) } // Key to force recomposition and restart FaceRecognitionView
    var currentState by remember { mutableStateOf<com.facelockapp.ui.components.FaceRecognitionState?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    
    // בדיקה אם ה-embedding נטען
    LaunchedEffect(storedEmbedding) {
        // Stored embedding loaded
    }
    
    // פונקציה לאיפוס והתחלה מחדש (לא בשימוש יותר - הכפתור עושה את זה ישירות)
    // אבל נשאיר את זה למקרה שצריך
    fun resetAndRetry() {
        successCount = 0
        showFaceError = false
        verificationAttempts = 0
        lastVerificationResult = null
        currentState = com.facelockapp.ui.components.FaceRecognitionState.WAITING_FOR_FACE
        statusMessage = "מחפש פנים... הצב את פניך מול המצלמה"
        key++ // Force recomposition
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp), // הקטן עוד יותר
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
        Spacer(Modifier.height(4.dp)) // הקטן עוד יותר
        
        // Security icon with animation - opens when face is matched
        val isUnlocked = currentState == com.facelockapp.ui.components.FaceRecognitionState.FACE_MATCHED || successCount > 0
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = ""
        )
        
        Box(
            modifier = Modifier
                .size(50.dp) // הגדל מ-40dp ל-50dp
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                        )
                    ),
                    shape = CircleShape
                )
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(25.dp), // הגדל מ-20dp ל-25dp
                tint = if (isUnlocked) com.facelockapp.ui.theme.SuccessGreen else com.facelockapp.ui.theme.ErrorRed
            )
        }
        
        Spacer(Modifier.height(8.dp)) // הקטן עוד יותר
        
        Text(
            "אמת את זהותך כדי לגשת להגדרות",
            fontSize = 12.sp, // הקטן
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        // הודעה אם אין embedding
        if (storedEmbedding == null) {
            Spacer(Modifier.height(4.dp)) // הקטן עוד יותר
            Text(
                text = "⚠️ לא נמצא זיהוי פנים שמור. אנא הגדר זיהוי פנים בהגדרות.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp, // הקטן
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(Modifier.height(8.dp)) // הקטן עוד יותר

        key(key) { // Force recomposition when key changes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f), // הקטן את גודל המצלמה מ-3/4 ל-4/5
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = if (showFaceError) {
                    androidx.compose.foundation.BorderStroke(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else null
            ) {
                FaceRecognitionView(
                isEnrollment = false,
                storedEmbedding = storedEmbedding,
                onStateChanged = { state, message ->
                    currentState = state
                    statusMessage = message
                },
                onFaceVerified = { isMatch ->
                    verificationAttempts++
                    lastVerificationResult = if (isMatch) "✅ התאמה" else "❌ לא תואם"
                    
                    if (isMatch) {
                        showFaceError = false
                        successCount++
                        onSuccess()
                    } else {
                        if (successCount > 0) {
                            // Match failed
                        }
                        successCount = 0
                        showFaceError = true
                        currentState = com.facelockapp.ui.components.FaceRecognitionState.WAITING_FOR_FACE
                        statusMessage = "מחפש פנים... הצב את פניך מול המצלמה"
                    }
                }
            )
            }
        }

        // הצג הודעת סטטוס - רק אם יש מצב משמעותי
        Spacer(Modifier.height(4.dp)) // הקטן עוד יותר
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // הצג הודעה רק אם יש מצב משמעותי, לא כל פעם שהמצב משתנה
            if (currentState != null && currentState != com.facelockapp.ui.components.FaceRecognitionState.WAITING_FOR_FACE) {
                when (currentState) {
                    com.facelockapp.ui.components.FaceRecognitionState.CHECKING_QUALITY -> {
                        Text(
                            text = statusMessage ?: "בודק איכות הפנים...",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp, // הקטן
                            textAlign = TextAlign.Center
                        )
                    }
                    com.facelockapp.ui.components.FaceRecognitionState.VERIFYING -> {
                        Text(
                            text = statusMessage ?: "בודק התאמה...",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp, // הקטן
                            textAlign = TextAlign.Center
                        )
                    }
                    com.facelockapp.ui.components.FaceRecognitionState.QUALITY_CHECK_FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp) // הקטן
                        )
                        Spacer(Modifier.width(6.dp)) // הקטן
                        Text(
                            text = statusMessage ?: "איכות הפנים לא מספיקה",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, // הקטן
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(2.dp)) // הקטן
                    Text(
                        text = "הזז את הפנים למרכז והתקרב למצלמה",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp, // הקטן
                        textAlign = TextAlign.Center
                    )
                }
                    com.facelockapp.ui.components.FaceRecognitionState.NO_FACE_DETECTED -> {
                        // אל נציג הודעה כל פעם שאין פנים - זה גורם לקפיצות
                    }
                    com.facelockapp.ui.components.FaceRecognitionState.FACE_NOT_MATCHED -> {
                        Text(
                            text = statusMessage ?: "הפנים לא תואמות",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, // הקטן
                            textAlign = TextAlign.Center
                        )
                        if (verificationAttempts > 0) {
                            Spacer(Modifier.height(2.dp)) // הקטן
                            Text(
                                text = "ניסיון #$verificationAttempts",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp, // הקטן
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    com.facelockapp.ui.components.FaceRecognitionState.FACE_MATCHED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp) // הקטן
                            )
                            Spacer(Modifier.width(6.dp)) // הקטן
                            Text(
                                text = statusMessage ?: "פנים תואמות! ✅",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp, // הקטן
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        // מצבים אחרים לא מוצגים כדי למנוע קפיצות
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp)) // הקטן עוד יותר
        
        // Spacer כדי שהכפתור לא יוסתר
        Spacer(Modifier.height(60.dp))
        }
        
        // כפתור קבוע בתחתית
        OutlinedButton(
            onClick = onUseBackup,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
                .height(48.dp), // הקטן מ-56dp ל-48dp
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SettingsBackupRestore,
                contentDescription = null,
                modifier = Modifier.size(18.dp) // הקטן
            )
            Spacer(Modifier.width(6.dp)) // הקטן
            Text("השתמש בשיטת גיבוי", fontSize = 14.sp) // הקטן
        }
    }
}

@Composable
private fun BackupChoiceScreen(
    hasPin: Boolean,
    hasPattern: Boolean,
    onSelectPin: () -> Unit,
    onSelectPattern: () -> Unit,
    onReturnToFace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SettingsBackupRestore,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "שיטות גיבוי",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "בחר שיטת אימות חלופית",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onSelectPin,
            enabled = hasPin,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasPin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasPin) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Password,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (hasPin) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasPin) "השתמש ב-PIN" else "לא הוגדר קוד PIN",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSelectPattern,
            enabled = hasPattern,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasPattern) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasPattern) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (hasPattern) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasPattern) "השתמש בתבנית" else "לא הוגדרה תבנית",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(
            onClick = onReturnToFace,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("חזור לזיהוי פנים", fontSize = 16.sp)
        }
        
        // הוסף padding לתחתית כדי שהתוכן לא יוסתר מאחורי ה-navigation bar
        Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

private enum class AuthMethod {
    FACE, CHOICE, PIN, PATTERN
}
