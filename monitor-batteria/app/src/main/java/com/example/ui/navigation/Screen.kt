package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Charging : Screen(
        route = "charging",
        title = "Carica",
        selectedIcon = Icons.Filled.ElectricBolt,
        unselectedIcon = Icons.Outlined.Bolt
    )

    object Discharging : Screen(
        route = "discharging",
        title = "Scarica",
        selectedIcon = Icons.Filled.TrendingDown,
        unselectedIcon = Icons.Outlined.TrendingDown
    )

    object Health : Screen(
        route = "health",
        title = "Salute",
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder
    )

    object Automation : Screen(
        route = "automation",
        title = "Automazione",
        selectedIcon = Icons.Filled.Power,
        unselectedIcon = Icons.Outlined.Power
    )

    companion object {
        val items: List<Screen> get() = listOf(Charging, Discharging, Health, Automation)
    }
}
