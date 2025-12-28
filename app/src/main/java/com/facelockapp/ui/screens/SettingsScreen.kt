package com.facelockapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facelockapp.ui.components.FaceEmbedding

// Note: This screen is likely deprecated or unused after the refactor.
// It's being updated for compilation purposes.

enum class SettingsStep {
    MENU,
    CHANGE_FACE,
    CHANGE_PIN,
    CHANGE_PATTERN
}

@Composable
fun SettingsScreen(
    onFaceChanged: (FaceEmbedding) -> Unit,
    onPinChanged: (String) -> Unit,
    onPatternChanged: (String) -> Unit
) {
    var currentStep by remember { mutableStateOf(SettingsStep.MENU) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            SettingsStep.MENU -> {
                Text(
                    text = "הגדרות",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = { currentStep = SettingsStep.CHANGE_FACE },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("שינוי זיהוי פנים", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { currentStep = SettingsStep.CHANGE_PIN },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("שינוי PIN", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { currentStep = SettingsStep.CHANGE_PATTERN },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("שינוי תבנית", fontSize = 18.sp)
                }
            }

            SettingsStep.CHANGE_FACE -> {
                FaceRecognitionScreen(
                    title = "שינוי זיהוי פנים",
                    subtitle = "הצב/י את פניך מול המצלמה וסובב/י את הראש לאט לצדדים",
                    onFaceEnrolled = { embedding -> // Corrected parameter usage
                        onFaceChanged(embedding)
                        currentStep = SettingsStep.MENU
                    },
                    onCancel = {
                        currentStep = SettingsStep.MENU
                    }
                )
            }

            SettingsStep.CHANGE_PIN -> {
                PinScreen(
                    title = "שינוי PIN",
                    subtitle = "הקלד קוד חדש בן 4 ספרות",
                    isSetup = true,
                    onPinComplete = { pin ->
                        onPinChanged(pin)
                        currentStep = SettingsStep.MENU
                    },
                    onCancel = {
                        currentStep = SettingsStep.MENU
                    }
                )
            }

            SettingsStep.CHANGE_PATTERN -> {
                PatternScreen(
                    title = "שינוי תבנית",
                    subtitle = "צייר תבנית חדשה",
                    isSetup = true,
                    onPatternComplete = { pattern ->
                        onPatternChanged(pattern)
                        currentStep = SettingsStep.MENU
                    },
                    onCancel = {
                        currentStep = SettingsStep.MENU
                    }
                )
            }
        }
    }
}
