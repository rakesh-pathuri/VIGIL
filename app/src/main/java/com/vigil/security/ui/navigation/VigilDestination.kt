package com.vigil.security.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class VigilDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Outlined.Shield),
    SCANNER("scanner", "Scanner", Icons.Outlined.Search),
    ACTIVITY("activity", "Activity", Icons.Outlined.History),
    REPORTS("reports", "Reports", Icons.Outlined.Analytics),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings)
}
