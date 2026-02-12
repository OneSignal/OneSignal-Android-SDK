package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.sdktest.ui.theme.OneSignalRed

private val ButtonShape = RoundedCornerShape(10.dp)

/**
 * Primary action button (full width, colored background).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.4f)
        ),
        enabled = enabled,
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp
            )
        )
    }
}

/**
 * Destructive button (outlined red style).
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OneSignalRed
        ),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) OneSignalRed.copy(alpha = 0.5f) else OneSignalRed.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = OneSignalRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp
            )
        )
    }
}

/**
 * Button with icon on the right side.
 */
@Composable
fun IconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = OneSignalRed
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
