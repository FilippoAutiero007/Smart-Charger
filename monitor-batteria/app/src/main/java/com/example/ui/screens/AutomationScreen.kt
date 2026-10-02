package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.AutomationUiState
import com.example.ui.theme.*

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    onThresholdChange: (Int) -> Unit = {},
    onSonoffToggle: (Boolean) -> Unit = {},
    onSoundToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentThreshold by remember(state.cutoffThreshold) { mutableStateOf(state.cutoffThreshold.toFloat()) }
    var sonoffActive by remember(state.sonoffEnabled) { mutableStateOf(state.sonoffEnabled) }
    var soundActive by remember(state.notificationSoundEnabled) { mutableStateOf(state.notificationSoundEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Cut-off Threshold Slider Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOGLIA DI CUT-OFF BATTERIA",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${currentThreshold.toInt()}%",
                        color = ElegantPurple,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentThreshold,
                    onValueChange = {
                        currentThreshold = it
                        onThresholdChange(it.toInt())
                    },
                    valueRange = 50f..100f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = ElegantPurple,
                        activeTrackColor = ElegantPurple,
                        inactiveTrackColor = OutlineDark
                    )
                )

                Text(
                    text = "Consigliato: 80% per preservare la chimica al litio e ridurre lo stress da tensione.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Sonoff Integration Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Presa Smart Sonoff",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.sonoffDeviceStatus,
                            color = if (state.sonoffApiKeyConfigured) GreenHealthy else Amber500,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = sonoffActive,
                        onCheckedChange = {
                            sonoffActive = it
                            onSonoffToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElegantPurple,
                            checkedTrackColor = ElegantPurple.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = OutlineDark
                        )
                    )
                }

                Text(
                    text = "Spegne la presa Wi-Fi automaticamente al raggiungimento della soglia impostata.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Sound & Notifications Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Avviso Sonoro",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (soundActive) "Allarme audio attivo" else "Silenzioso",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = soundActive,
                        onCheckedChange = {
                            soundActive = it
                            onSoundToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElegantPurple,
                            checkedTrackColor = ElegantPurple.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = OutlineDark
                        )
                    )
                }
            }
        }
    }
}
