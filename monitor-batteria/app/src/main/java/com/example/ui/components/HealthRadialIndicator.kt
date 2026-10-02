package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElegantPurple
import com.example.ui.theme.GreenHealthy
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.RedAlert
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HealthRadialIndicator(
    healthPercent: Int,
    estimatedMah: Int,
    designMah: Int,
    modifier: Modifier = Modifier
) {
    val progress = (healthPercent.coerceIn(0, 100).toFloat() / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "healthProgress"
    )

    Box(
        modifier = modifier
            .size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 16.dp.toPx()
            val canvasSize = size.minDimension - strokeWidth * 2
            val topLeft = Offset(
                (size.width - canvasSize) / 2,
                (size.height - canvasSize) / 2
            )

            // Background circle
            drawArc(
                color = OutlineDark.copy(alpha = 0.35f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(canvasSize, canvasSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Health color logic: healthy >=80%, warning 70-80%, alert <70%
            val activeColor = when {
                healthPercent >= 80 -> GreenHealthy
                healthPercent >= 70 -> Color(0xFFF59E0B)
                else -> RedAlert
            }

            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(canvasSize, canvasSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inner text readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$healthPercent%",
                color = TextPrimary,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Text(
                text = "SALUTE BATTERIA",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$estimatedMah / $designMah mAh",
                color = ElegantPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
