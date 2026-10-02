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
import com.example.AutomationSection
import com.example.SonoffDeviceSection
import com.example.ui.model.AutomationUiState
import com.example.ui.theme.*

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    onThresholdChange: (Int) -> Unit = {},
    onSonoffToggle: (Boolean) -> Unit = {},
    onSoundToggle: (Boolean) -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var notificationsEnabled by remember { mutableStateOf(state.notificationSoundEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // eWeLink & Sonoff Hardware Configuration (Reale e Completo)
        Text(
            text = "Accoppiamento eWeLink & Sonoff",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Collega il tuo account eWeLink cloud o LAN, seleziona il dispositivo e testa i comandi della presa.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        SonoffDeviceSection()

        Spacer(modifier = Modifier.height(10.dp))

        // Soglie di Carica & Notifiche Automatiche (Reale e Completo)
        Text(
            text = "Regole di Automazione & Allarmi",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Configura le soglie percentuali per staccare o riattaccare l'alimentazione automaticamente.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        AutomationSection(
            notificationsEnabled = notificationsEnabled,
            onNotificationsEnabledChange = {
                notificationsEnabled = it
                onSoundToggle(it)
            },
            onSendTestNotification = onSendTestNotification
        )
    }
}
