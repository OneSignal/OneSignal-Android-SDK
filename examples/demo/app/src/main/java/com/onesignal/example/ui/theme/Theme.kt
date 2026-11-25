package com.onesignal.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.example.R

// sdk-shared/demo/styles.md tokens
val OsPrimary = Color(0xFFE54B4D)
val OsInk = Color(0xFF1F2328)
val OsGrey700 = Color(0xFF57606A)
val OsGrey600 = Color(0xFF6E7781)
val OsGrey500 = Color(0xFF9AA2AC)
val OsLightBackground = Color(0xFFF7F7F7)
val OsCardBackground = Color.White
val OsCardBorder = Color(0xFFE5E7EB)
val OsControlBorder = Color(0xFFD5DAE1)
val OsDivider = Color(0xFFECEEF2)
val OsSwitchTrack = Color(0xFFD0D5DD)
val OsSuccess = Color(0xFF34A853)
val OsNeutralBackground = Color(0xFFEEF0F3)
val OsWarningBackground = Color(0xFFFFF9E6)
val OsWarningBorder = Color(0xFFF2D68A)
val OsWarningIcon = Color(0xFFB7791F)
val OsWarningText = Color(0xFF6B4A12)
val OsBackdrop = Color(0x8A000000)
val OsLogBackground = Color(0xFF16181D)
val OsLogHeader = Color(0xFF1E2128)
val OsLogBorder = Color(0x14FFFFFF)
val OsLogBadge = Color(0xFF2B303B)
val OsLogMuted = Color(0xFF7E8594)
val OsLogText = Color(0xFFD8DEE9)

private val LightColorScheme = lightColorScheme(
    primary = OsPrimary,
    onPrimary = Color.White,
    secondary = OsSuccess,
    onSecondary = Color.White,
    background = OsLightBackground,
    surface = OsCardBackground,
    // Material tints raised surfaces with the primary color; the demo keeps them white.
    surfaceTint = Color.Transparent,
    onBackground = OsInk,
    onSurface = OsInk,
    onSurfaceVariant = OsGrey600,
    outline = OsControlBorder,
    error = OsPrimary,
    onError = Color.White,
)

// No colors here: a style color would override the content color that buttons and text fields
// pass down (e.g. a disabled action or a focused field label). Text defaults to onSurface.
// Static instances cut from the Google Sans Flex variable font (OFL), since variable weights need API 26.
val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

private val DefaultTypography = Typography()

private fun TextStyle.withAppFont() = copy(fontFamily = GoogleSansFlex)

private val AppTypography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.withAppFont(),
    displayMedium = DefaultTypography.displayMedium.withAppFont(),
    displaySmall = DefaultTypography.displaySmall.withAppFont(),
    headlineLarge = DefaultTypography.headlineLarge.withAppFont(),
    headlineMedium = DefaultTypography.headlineMedium.withAppFont(),
    titleLarge = DefaultTypography.titleLarge.withAppFont(),
    titleSmall = DefaultTypography.titleSmall.withAppFont(),
    labelMedium = DefaultTypography.labelMedium.withAppFont(),
    labelSmall = DefaultTypography.labelSmall.withAppFont(),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
)

val Typography.rowLabel: TextStyle
    get() = bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(DemoLayout.cardRadius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(DemoLayout.dialogRadius),
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
