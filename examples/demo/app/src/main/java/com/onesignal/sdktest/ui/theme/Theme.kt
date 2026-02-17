package com.onesignal.sdktest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// OneSignal brand colors
val OneSignalRed = Color(0xFFE54B4D)
val OneSignalRedDark = Color(0xFFCE3E40)
val OneSignalGreen = Color(0xFF34A853)
val OneSignalGreenLight = Color(0xFFE6F4EA)
val DarkText = Color(0xFF1F1F1F)
val SecondaryText = Color(0xFF5F6368)
val LightBackground = Color(0xFFF8F9FA)
val CardBackground = Color.White
val DividerColor = Color(0xFFE8EAED)
val WarningBackground = Color(0xFFFFF8E1)
val SurfaceBorder = Color(0xFFDADCE0)

private val LightColorScheme = lightColorScheme(
    primary = OneSignalRed,
    onPrimary = Color.White,
    secondary = OneSignalGreen,
    onSecondary = Color.White,
    background = LightBackground,
    surface = CardBackground,
    onBackground = DarkText,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = SecondaryText,
    outline = SurfaceBorder,
    error = OneSignalRed,
    onError = Color.White
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        color = DarkText
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        color = DarkText
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = SecondaryText
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = DarkText
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = DarkText
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = SecondaryText
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OneSignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
