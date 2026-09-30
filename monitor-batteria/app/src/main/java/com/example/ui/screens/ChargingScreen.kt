package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GaugeSpeedometer
import com.example.ui.components.MetricCard
import com.example.ui.model.ChargingUiState
import com.example.ui.theme.*

@Composable
fun ChargingScreen(
    state: ChargingUiState,
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
        // Sonoff Status Header Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.sonoffConnected) Emerald500 else Rose500)
                )
                Text(
                    text = if (state.sonoffConnected) "Sonoff: ${state.sonoffDeviceName}" else "Sonoff Offline",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.sonoffRelayOn) Emerald500.copy(alpha = 0.2f) else Slate700)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (state.sonoffRelayOn) "PRESA ATTIVA" else "PRESA STACCATA",
                    color = if (state.sonoffRelayOn) Emerald500 else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Hero Gauge (Current in mA)
        GaugeSpeedometer(
            currentMa = state.currentMa,
            isCharging = state.isCharging
        )

        // Charging Speed & Time to Target Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "TARGET PROTEZIONE: ${state.targetCutoffPercent}%",
                    color = Amber500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = state.estimatedTimeToTarget,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Emerald500.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = state.chargingSpeedType,
                    color = Emerald500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Grid Metrics: Tensione, Potenza, Temperatura, Livello
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Tensione",
                    value = "${state.voltageMv / 1000}.${(state.voltageMv % 1000) / 100}",
                    unit = "V",
                    icon = Icons.Filled.ElectricMeter,
                    iconTint = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Potenza",
                    value = String.format("%.1f", state.powerWatts),
                    unit = "W",
                    icon = Icons.Filled.Bolt,
                    iconTint = Amber500,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Temperatura",
                    value = String.format("%.1f", state.temperatureCelsius),
                    unit = "°C",
                    icon = Icons.Filled.Thermostat,
                    iconTint = if (state.temperatureCelsius > 40f) Rose500 else Emerald500,
                    subtitle = if (state.temperatureCelsius > 40f) "Attenzione: surriscaldamento" else "Temperatura ottimale",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Livello Batteria",
                    value = "${state.batteryLevel}",
                    unit = "%",
                    icon = Icons.Filled.Power,
                    iconTint = Emerald500,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
