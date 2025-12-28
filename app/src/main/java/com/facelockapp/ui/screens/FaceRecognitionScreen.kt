package com.facelockapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facelockapp.ui.components.FaceEmbedding
import com.facelockapp.ui.components.FaceRecognitionView
import com.facelockapp.ui.components.FaceRecognitionState
import kotlinx.coroutines.delay

@Composable
fun FaceRecognitionScreen(
    title: String,
    subtitle: String,
    onFaceEnrolled: (FaceEmbedding) -> Unit,
    onCancel: () -> Unit
) {
    var isComplete by remember { mutableStateOf(false) }
    var enrollmentProgress by remember { mutableStateOf(0 to 4) } // (current, total) - שונה ל-4
    var currentState by remember { mutableStateOf<FaceRecognitionState?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // This LaunchedEffect will now just handle the visual part of completion.
    // The actual saving and navigation is handled by the caller.
    LaunchedEffect(isComplete) {
        if (isComplete) {
            delay(2500)
            // The onFaceEnrolled is called from the view, this just waits.
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            // Header with icon
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f), // הקטן את גובה המצלמה
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                FaceRecognitionView(
                    isEnrollment = true,
                    onFaceEnrolled = {
                        if (!isComplete) {
                            isComplete = true
                            enrollmentProgress = 4 to 4 // עדכן ל-100%
                            onFaceEnrolled(it)
                        }
                    },
                    onEnrollmentProgress = { current, total ->
                        enrollmentProgress = current to total
                    },
                    onStateChanged = { state, message ->
                        currentState = state
                        statusMessage = message
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // הודעת מצב או אינדיקטור התקדמות - תמיד שמור מקום כדי שהכפתור לא יקפוץ
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        enrollmentProgress.first > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        currentState == FaceRecognitionState.FACE_MATCHED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        currentState == FaceRecognitionState.TOO_FAR ||
                        currentState == FaceRecognitionState.TOO_CLOSE ||
                        currentState == FaceRecognitionState.FACE_NOT_MATCHED || 
                        currentState == FaceRecognitionState.QUALITY_CHECK_FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isComplete && enrollmentProgress.first > 0) {
                        // אינדיקטור התקדמות - מופיע במקום הודעת המצב
                        val progressPercent = (enrollmentProgress.first.toFloat() / enrollmentProgress.second.toFloat() * 100f).toInt()
                        
                        // Linear progress indicator
                        LinearProgressIndicator(
                            progress = enrollmentProgress.first.toFloat() / enrollmentProgress.second.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Progress text in a row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "קולט פנים... (${enrollmentProgress.first}/${enrollmentProgress.second})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$progressPercent%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (!isComplete && statusMessage != null) {
                        // הודעת מצב - מופיעה רק כשאין התקדמות, באותו גודל כמו האינדיקטור
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Spacer כדי להתאים לגובה של LinearProgressIndicator (8.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                when (currentState) {
                                    FaceRecognitionState.FACE_MATCHED -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    FaceRecognitionState.TOO_FAR,
                                    FaceRecognitionState.TOO_CLOSE -> {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    FaceRecognitionState.QUALITY_CHECK_FAILED -> {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusMessage ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when (currentState) {
                                        FaceRecognitionState.FACE_MATCHED -> MaterialTheme.colorScheme.primary
                                        FaceRecognitionState.TOO_FAR,
                                        FaceRecognitionState.TOO_CLOSE,
                                        FaceRecognitionState.FACE_NOT_MATCHED,
                                        FaceRecognitionState.QUALITY_CHECK_FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }
                    } else {
                        // שמור מקום גם כשאין כלום - באותו גודל כמו האינדיקטור
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Spacer(modifier = Modifier.height(24.dp)) // גובה הטקסט
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                enabled = !isComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ביטול", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }

        AnimatedVisibility(
            visible = isComplete,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            SuccessOverlay()
        }
    }
}

@Composable
private fun SuccessOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    ),
        contentAlignment = Alignment.Center
    ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
            )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "הוגדר בהצלחה!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "הפנים נשמרו במערכת",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}
