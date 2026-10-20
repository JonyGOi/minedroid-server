package com.example.minedroidserver.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MinecraftEmerald,
    onPrimary = MinecraftObsidian,
    primaryContainer = MinecraftEmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = MinecraftLapis,
    onSecondary = TextPrimary,
    tertiary = MinecraftGold,
    background = MinecraftObsidian,
    onBackground = TextPrimary,
    surface = MinecraftDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = MinecraftCardSurface,
    onSurfaceVariant = TextSecondary,
    outline = MinecraftBorder,
    error = MinecraftRedstone,
    onError = TextPrimary
)

@Suppress("DEPRECATION")
@Composable
fun MineDroidServerTheme(
    darkTheme: Boolean = true, // Default to sleek Minecraft dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MinecraftObsidian.toArgb()
            window.navigationBarColor = MinecraftObsidian.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
