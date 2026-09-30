package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Slate700
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StackedDrainBar(
    screenOnDrain: Float,
    screenOffDrain: Float,
    deepSleepDrain: Float,
    modifier: Modifier = Modifier
) {
    val total = (screenOnDrain + screenOffDrain + deepSleepDrain).coerceAtLeast(1f)
    val screenOnWeight = (screenOnDrain / total).coerceAtLeast(0.02f)
    val screenOffWeight = (screenOffDrain / total).coerceAtLeast(0.02f)
    val deepSleepWeight = (deepSleepDrain / total).coerceAtLeast(0.02f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Multi-segment horizontal bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Slate700.copy(alpha = 0.3f))
        ) {
            // Screen On (Amber/Orange)
            Box(
                modifier = Modifier
                    .weight(screenOnWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFF59E0B))
            )
            // Screen Off (Indigo/Blue)
            Box(
                modifier = Modifier
                    .weight(screenOffWeight)
                    .fillMaxHeight()
                    .background(Color(0xFF6366F1))
            )
            // Deep Sleep (Emerald)
            Box(
                modifier = Modifier
                    .weight(deepSleepWeight)
                    .fillMaxHeight()
                    .background(Emerald500)
            )
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(color = Color(0xFFF59E0B), label = "Schermo Acceso", value = "${screenOnDrain.toInt()}%")
            LegendItem(color = Color(0xFF6366F1), label = "Schermo Spento", value = "${screenOffDrain.toInt()}%")
            LegendItem(color = Emerald500, label = "Deep Sleep", value = "${deepSleepDrain.toInt()}%")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column {
            Text(text = label, color = TextSecondary, fontSize = 11.sp)
            Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
