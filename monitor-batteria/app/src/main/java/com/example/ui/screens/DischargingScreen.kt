package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MetricCard
import com.example.ui.components.StackedDrainBar
import com.example.ui.model.DischargingUiState
import com.example.ui.theme.*

@Composable
fun DischargingScreen(
    state: DischargingUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Card: Discharge Speed & Autonomy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "VELOCITÀ DI SCARICA ATTUALE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.1f", state.dischargeRatePerHour),
                            color = TextPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "% / ora",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Autonomia Residua",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = state.estimatedTimeRemaining,
                            color = ElegantPurple,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Deep Sleep & Standby Analysis Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EFFICIENZA STANDBY & DEEP SLEEP",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${(state.deepSleepRatio * 100).toInt()}% Deep Sleep",
                        color = GreenHealthy,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Visual stacked horizontal breakdown bar
                StackedDrainBar(
                    screenOnDrain = state.screenOnDrainPercent,
                    screenOffDrain = state.screenOffDrainPercent,
                    deepSleepDrain = state.deepSleepDrainPercent
                )
            }
        }

        // Detailed Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Screen On",
                value = formatHoursMinutes(state.screenOnTimeSec),
                unit = "",
                icon = Icons.Filled.PhoneAndroid,
                iconTint = Amber500,
                subtitle = "Consumo: ${state.screenOnDrainPercent.toInt()}%",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Deep Sleep",
                value = formatHoursMinutes(state.deepSleepTimeSec),
                unit = "",
                icon = Icons.Filled.Bedtime,
                iconTint = GreenHealthy,
                subtitle = "Consumo: ${state.deepSleepDrainPercent.toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Consumo Medio",
                value = "-${state.currentDrainMa}",
                unit = "mA",
                icon = Icons.Filled.TrendingDown,
                iconTint = RedAlert,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Screen Off",
                value = formatHoursMinutes(state.screenOffTimeSec),
                unit = "",
                icon = Icons.Filled.HourglassBottom,
                iconTint = Color(0xFF6366F1),
                subtitle = "Consumo: ${state.screenOffDrainPercent.toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatHoursMinutes(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}
