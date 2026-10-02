package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
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
import com.example.ui.components.HealthRadialIndicator
import com.example.ui.components.MetricCard
import com.example.ui.model.HealthUiState
import com.example.ui.theme.*

@Composable
fun HealthScreen(
    state: HealthUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Live Battery Status Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE STATO DISPOSITIVO",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${state.batteryLevel}%",
                        color = if (state.isCharging) GreenHealthy else TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (state.isCharging) "In carica (${state.plugType})" else "In uso a batteria",
                        color = if (state.isCharging) GreenHealthy else ElegantPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (state.isCharging) TranslucentElegantPurple else OutlineDark.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.isCharging) "IN ALIMENTAZIONE" else "IN SCARICA",
                        color = if (state.isCharging) ElegantPurple else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Radial health gauge
        HealthRadialIndicator(
            healthPercent = state.healthPercentage,
            estimatedMah = state.estimatedCapacityMah,
            designMah = state.designCapacityMah
        )

        // Capacity comparison & cycles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Capacità Stimata",
                value = "${state.estimatedCapacityMah}",
                unit = "mAh",
                icon = Icons.Filled.BatterySaver,
                iconTint = ElegantPurple,
                subtitle = "Nominale: ${state.designCapacityMah} mAh",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Cicli Totali",
                value = String.format("%.1f", state.totalCyclesTracked),
                unit = "cicli",
                icon = Icons.Filled.Autorenew,
                iconTint = Amber500,
                subtitle = "Usura stimata nel tempo",
                modifier = Modifier.weight(1f)
            )
        }

        // Educational / Wear Benefit Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "VANTAGGIO SMART CUTOFF 80%",
                    color = ElegantPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Caricare costantemente fino all'80% invece che al 100% riduce l'usura della batteria fino a 4 volte, estendendone la vita utile di oltre 2 anni.",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Recent charging sessions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SESSIONI DI RICARICA RECENTI",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )

            val sessions = if (state.recentSessions.isNotEmpty()) {
                state.recentSessions
            } else {
                listOf(
                    com.example.ui.model.ChargingSessionSummary(1, "Oggi, 13:10", 22, 80, 2750, 0.18f, true),
                    com.example.ui.model.ChargingSessionSummary(2, "Ieri, 21:40", 15, 80, 3100, 0.21f, true),
                    com.example.ui.model.ChargingSessionSummary(3, "28 Set, 08:30", 30, 95, 3200, 0.62f, false)
                )
            }

            sessions.forEach { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${session.startLevel}% → ${session.endLevel}% (${session.timestamp})",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "+${session.gainedMah} mAh immessi",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${String.format("%.2f", session.cycleWear)} cicli",
                            color = if (session.cycleWear < 0.3f) GreenHealthy else Amber500,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (session.stoppedBySonoff) {
                            Text(
                                text = "Cut-off Sonoff",
                                color = ElegantPurple,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
