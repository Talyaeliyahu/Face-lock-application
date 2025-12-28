package com.facelockapp.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinScreen(
    title: String,
    subtitle: String,
    isSetup: Boolean,
    onPinComplete: (String) -> Unit,
    onCancel: (() -> Unit)?,
    onVerifyPin: (suspend (String) -> Boolean)? = null
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf<String?>(null) }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.5f))

        Text(
            text = if (isSetup && isConfirming) "אשר את הקוד" else title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // PIN Dots - Force LTR direction for the dots
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Number Pad
        NumberPad(
            onNumberClick = {
                errorMessage = null
                if (pin.length < 4) {
                    pin += it
                }
            },
            onBackspaceClick = {
                errorMessage = null
                pin = pin.dropLast(1)
            }
        )

        Spacer(Modifier.weight(0.5f))

        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("ביטול")
            }
        }
    }

    // Logic to handle PIN completion
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (isSetup) {
                if (!isConfirming) {
                    // First entry, move to confirmation
                    confirmPin = pin
                    pin = ""
                    isConfirming = true
                } else {
                    // Second entry, verify and complete
                    if (pin == confirmPin) {
                        onPinComplete(pin)
                    } else {
                        errorMessage = "הקודים אינם תואמים. נסה שוב."
                        pin = ""
                        isConfirming = false
                    }
                }
            } else {
                // Verification mode
                val isCorrect = onVerifyPin?.invoke(pin)
                if (isCorrect == true) {
                    onPinComplete(pin)
                } else {
                    errorMessage = "קוד שגוי. נסה שוב."
                    pin = ""
                }
            }
        }
    }
}

@Composable
private fun NumberPad(onNumberClick: (String) -> Unit, onBackspaceClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Force LTR for the number pad layout itself
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val buttonModifier = Modifier.size(80.dp)
            // Rows 1-3
            (1..9).chunked(3).forEach { rowNumbers ->
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    rowNumbers.forEach { number ->
                        NumberButton(number.toString(), buttonModifier, onNumberClick)
                    }
                }
            }
            // Row 4 (0 and backspace)
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Spacer(modifier = Modifier.size(80.dp)) // Placeholder for alignment
                NumberButton("0", buttonModifier, onNumberClick)
                BackspaceButton(buttonModifier, onBackspaceClick)
            }
        }
    }
}

@Composable
private fun NumberButton(number: String, modifier: Modifier, onClick: (String) -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick(number) }
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(number, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BackspaceButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("⌫", fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}
