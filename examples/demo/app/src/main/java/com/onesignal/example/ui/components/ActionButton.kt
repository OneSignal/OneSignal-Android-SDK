package com.onesignal.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsPrimary

private val ButtonShape = RoundedCornerShape(DemoLayout.buttonRadius)

private val ButtonTextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.SemiBold,
    )

private fun Modifier.applyTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

private fun Modifier.buttonLayout(testTag: String?): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = DemoLayout.pagePadding, vertical = 4.dp)
    .height(DemoLayout.buttonHeight)
    .applyTestTag(testTag)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.buttonLayout(testTag),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = OsGrey500,
        ),
        enabled = enabled,
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(text = text, style = ButtonTextStyle, color = Color.White)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val color = if (enabled) OsPrimary else OsGrey500
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.buttonLayout(testTag),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = OsGrey500,
        ),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, color),
    ) {
        Text(text = text, style = ButtonTextStyle, color = color)
    }
}

@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    OutlineButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, testTag = testTag)
}

@Composable
fun IconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = OsPrimary,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.buttonLayout(testTag),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = ButtonTextStyle,
                color = Color.White,
                modifier = Modifier.padding(end = DemoLayout.gap),
            )
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
