package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.*
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

@Composable
fun MainScaffold(
    chargingUiState: ChargingUiState = ChargingUiState(),
    dischargingUiState: DischargingUiState = DischargingUiState(),
    healthUiState: HealthUiState = HealthUiState(),
    automationUiState: AutomationUiState = AutomationUiState(),
    onThresholdChange: (Int) -> Unit = {},
    onSonoffToggle: (Boolean) -> Unit = {},
    onSoundToggle: (Boolean) -> Unit = {}
) {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Charging) }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            NavigationBar(
                containerColor = CardDark,
                tonalElevation = 8.dp
            ) {
                for (screen in Screen.items) {
                    key(screen.route) {
                        val isSelected = selectedScreen == screen
                        val icon = if (isSelected) screen.selectedIcon else screen.unselectedIcon
                        val label = screen.title
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp
                                )
                            },
                            selected = isSelected,
                            onClick = { selectedScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElegantPurple,
                                selectedTextColor = ElegantPurple,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = TranslucentElegantPurple
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedScreen) {
                Screen.Charging -> ChargingScreen(state = chargingUiState)
                Screen.Discharging -> DischargingScreen(state = dischargingUiState)
                Screen.Health -> HealthScreen(state = healthUiState)
                Screen.Automation -> AutomationScreen(
                    state = automationUiState,
                    onThresholdChange = onThresholdChange,
                    onSonoffToggle = onSonoffToggle,
                    onSoundToggle = onSoundToggle
                )
            }
        }
    }
}
