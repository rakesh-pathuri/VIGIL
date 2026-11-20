package com.vigil.security.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class SecurityColors(
    val safe: Color,
    val safeContainer: Color,
    val caution: Color,
    val cautionContainer: Color,
    val dangerous: Color,
    val dangerousContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
    val scanning: Color,
    val scanningContainer: Color,
    val border: Color
)

val LocalSecurityColors = staticCompositionLocalOf {
    SecurityColors(
        safe = SecuritySafeLight,
        safeContainer = SecuritySafeContainerLight,
        caution = SecurityCautionLight,
        cautionContainer = SecurityCautionContainerLight,
        dangerous = SecurityDangerousLight,
        dangerousContainer = SecurityDangerousContainerLight,
        unknown = SecurityUnknownLight,
        unknownContainer = SecurityUnknownContainerLight,
        scanning = Color(0xFF2563EB),
        scanningContainer = Color(0xFFEFF6FF),
        border = LightBorder
    )
}

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = LightPrimaryAccent,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightMainText,
    surface = LightSurface,
    onSurface = LightMainText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF031525),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = DarkPrimaryAccent,
    onSecondary = Color(0xFF031525),
    background = DarkBackground,
    onBackground = DarkMainText,
    surface = DarkSurface,
    onSurface = DarkMainText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder
)

@Composable
fun VigilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val securityColors = if (darkTheme) {
        SecurityColors(
            safe = SecuritySafeDark,
            safeContainer = SecuritySafeContainerDark,
            caution = SecurityCautionDark,
            cautionContainer = SecurityCautionContainerDark,
            dangerous = SecurityDangerousDark,
            dangerousContainer = SecurityDangerousContainerDark,
            unknown = SecurityUnknownDark,
            unknownContainer = SecurityUnknownContainerDark,
            scanning = Color(0xFF38BDF8),
            scanningContainer = Color(0xFF082F49),
            border = DarkBorder
        )
    } else {
        SecurityColors(
            safe = SecuritySafeLight,
            safeContainer = SecuritySafeContainerLight,
            caution = SecurityCautionLight,
            cautionContainer = SecurityCautionContainerLight,
            dangerous = SecurityDangerousLight,
            dangerousContainer = SecurityDangerousContainerLight,
            unknown = SecurityUnknownLight,
            unknownContainer = SecurityUnknownContainerLight,
            scanning = Color(0xFF2563EB),
            scanningContainer = Color(0xFFEFF6FF),
            border = LightBorder
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Exactly match the Android status bar and navigation bar with app background
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSecurityColors provides securityColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VigilTypography,
            content = content
        )
    }
}

object VigilTheme {
    val securityColors: SecurityColors
        @Composable
        get() = LocalSecurityColors.current
}
