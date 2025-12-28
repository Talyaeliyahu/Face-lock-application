package com.facelockapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

private data class Dot(val id: Int, val center: Offset)

@Composable
fun PatternScreen(
    title: String,
    subtitle: String,
    isSetup: Boolean,
    onPatternComplete: (String) -> Unit,
    onCancel: (() -> Unit)?,
    onVerifyPattern: (suspend (String) -> Boolean)? = null
) {
    var selectedDots by remember { mutableStateOf(emptyList<Dot>()) }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConfirming by remember { mutableStateOf(false) }
    var firstPattern by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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

        Text(text = if (isSetup && isConfirming) "אשר את התבנית" else title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        PatternLock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(size) { // Sticking with original key for minimal change
                    val dots = getDots(size)
                    detectDragGestures(
                        onDragStart = {
                            errorMessage = null
                            selectedDots = emptyList()
                            val startDot = findDotAt(it, dots, size)
                            if (startDot != null) selectedDots = listOf(startDot)
                        },
                        onDrag = { change, _ ->
                            currentDragPosition = change.position
                            val nextDot = findDotAt(change.position, dots, size)
                            if (nextDot != null && selectedDots.lastOrNull() != nextDot) {
                                if (!selectedDots.contains(nextDot)) {
                                    selectedDots = selectedDots + nextDot
                                }
                            }
                        },
                        onDragEnd = {
                            currentDragPosition = null
                            if (selectedDots.isEmpty()) return@detectDragGestures

                            val patternString = selectedDots.joinToString("-") { it.id.toString() }
                            if (selectedDots.size < 4) {
                                if (isSetup) errorMessage = "יש לחבר לפחות 4 נקודות"
                                selectedDots = emptyList()
                                return@detectDragGestures
                            }

                            if (isSetup) {
                                if (!isConfirming) {
                                    firstPattern = patternString
                                    isConfirming = true
                                    selectedDots = emptyList()
                                } else {
                                    if (patternString == firstPattern) {
                                        onPatternComplete(patternString)
                                    } else {
                                        errorMessage = "התבניות אינן תואמות. נסה שוב."
                                        isConfirming = false
                                        selectedDots = emptyList()
                                    }
                                }
                            } else {
                                scope.launch {
                                    val isCorrect = onVerifyPattern?.invoke(patternString)
                                    if (isCorrect == true) {
                                        onPatternComplete(patternString)
                                    } else {
                                        errorMessage = "תבנית שגויה. נסה שוב."
                                        selectedDots = emptyList()
                                    }
                                }
                            }
                        }
                    )
                },
            dots = selectedDots,
            currentDragPosition = currentDragPosition,
            size = size,
            onSizeChanged = { size = it }
        )

        Spacer(Modifier.weight(1f))

        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("ביטול")
            }
        }
    }
}

@Composable
private fun PatternLock(
    modifier: Modifier,
    dots: List<Dot>,
    size: IntSize,
    currentDragPosition: Offset?,
    onSizeChanged: (IntSize) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier.onSizeChanged(onSizeChanged)
    ) {
        val allDots = getDots(size)
        if (allDots.isEmpty()) return@Canvas

        val dotRadius = this.size.width / 24f
        val pathStrokeWidth = dotRadius * 0.8f

        // Draw lines
        if (dots.isNotEmpty()) {
            val path = Path().apply {
                moveTo(dots.first().center.x, dots.first().center.y)
                for (i in 1 until dots.size) {
                    lineTo(dots[i].center.x, dots[i].center.y)
                }
            }
            drawPath(path, color = primaryColor.copy(alpha = 0.5f), style = Stroke(pathStrokeWidth, cap = StrokeCap.Round))

            currentDragPosition?.let {
                val tempPath = Path().apply {
                    moveTo(dots.last().center.x, dots.last().center.y)
                    lineTo(it.x, it.y)
                }
                drawPath(tempPath, color = primaryColor.copy(alpha = 0.3f), style = Stroke(pathStrokeWidth, cap = StrokeCap.Round))
            }
        }

        // Draw dots
        allDots.forEach { dot ->
            val isSelected = dots.any { it.id == dot.id }
            drawCircle(
                color = if (isSelected) primaryColor else surfaceColor,
                radius = if (isSelected) dotRadius * 1.2f else dotRadius,
                center = dot.center
            )
        }
    }
}

private fun getDots(size: IntSize): List<Dot> {
    val dots = mutableListOf<Dot>()
    val side = minOf(size.width, size.height).toFloat()
    if (side == 0f) return emptyList()

    val cellWidth = side / 3
    for (i in 0..8) {
        val row = i / 3
        val col = i % 3
        dots.add(
            Dot(
                id = i,
                center = Offset(
                    x = (col * cellWidth) + cellWidth / 2f,
                    y = (row * cellWidth) + cellWidth / 2f
                )
            )
        )
    }
    return dots
}

private fun findDotAt(position: Offset, dots: List<Dot>, size: IntSize): Dot? {
    val cellWidth = minOf(size.width, size.height) / 3f
    val touchRadius = cellWidth * 0.4f
    return dots.firstOrNull { dot ->
        val distance = sqrt((position.x - dot.center.x).pow(2) + (position.y - dot.center.y).pow(2))
        distance < touchRadius
    }
}
