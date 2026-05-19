package com.onesignal.example.ui.theme

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

// sdk-shared/demo/styles.md tokens
val OsPrimary = Color(0xFFE54B4D)
val OsSuccess = Color(0xFF34A853)
val OsGrey700 = Color(0xFF616161)
val OsGrey600 = Color(0xFF757575)
val OsGrey500 = Color(0xFF9E9E9E)
val OsLightBackground = Color(0xFFF8F9FA)
val OsCardBackground = Color.White
val OsCardBorder = Color(0x1A000000)
val OsDivider = Color(0xFFE8EAED)
val OsWarningBackground = Color(0xFFFFF8E1)
val OsBackdrop = Color(0x8A000000)

/** @deprecated Use [OsPrimary] */
val OneSignalRed = OsPrimary

/** @deprecated Use [OsSuccess] */
val OneSignalGreen = OsSuccess

/** @deprecated Use [OsLightBackground] */
val LightBackground = OsLightBackground

/** @deprecated Use [OsCardBackground] */
val CardBackground = OsCardBackground

/** @deprecated Use [OsDivider] */
val DividerColor = OsDivider

/** @deprecated Use [OsWarningBackground] */
val WarningBackground = OsWarningBackground

private val LightColorScheme = lightColorScheme(
    primary = OsPrimary,
    onPrimary = Color.White,
    secondary = OsSuccess,
    onSecondary = Color.White,
    background = OsLightBackground,
    surface = OsCardBackground,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = OsGrey600,
    outline = OsGrey700,
    error = OsPrimary,
    onError = Color.White,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = Color.Black,
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = Color.Black,
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = OsGrey700,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = Color.Black,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = Color.Black,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        color = OsGrey600,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun OneSignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
