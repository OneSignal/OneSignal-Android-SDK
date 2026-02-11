package com.onesignal.sdktest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OneSignal brand colors
val OneSignalRed = Color(0xFFE9444E)
val OneSignalRedDark = Color(0xFFB8363E)
val OneSignalGreen = Color(0xFF2E7D32)
val OneSignalGreenLight = Color(0xFFE8F5E9)
val DarkText = Color(0xFF333333)
val LightBackground = Color(0xFFF5F5F5)
val CardBackground = Color.White
val DividerColor = Color(0xFFE0E0E0)
val WarningBackground = Color(0xFFFFF9E6)

private val LightColorScheme = lightColorScheme(
    primary = OneSignalRed,
    onPrimary = Color.White,
    secondary = OneSignalGreen,
    onSecondary = Color.White,
    background = LightBackground,
    surface = CardBackground,
    onBackground = DarkText,
    onSurface = DarkText,
    error = OneSignalRed,
    onError = Color.White
)

@Composable
fun OneSignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
