package com.onesignal.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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

private const val MIN_TEXT_SCALE = 0.7f
private const val TEXT_SCALE_STEP = 0.95f

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

enum class ButtonStyle { Primary, Outline }

data class GridAction(
    val text: String,
    val onClick: () -> Unit,
    val testTag: String? = null,
    val style: ButtonStyle = ButtonStyle.Primary,
    val enabled: Boolean = true,
)

/**
 * Two equal columns per styles.md "Button Grid". An odd last action spans both
 * columns. Callers drop hidden actions from the list so the rest reflow.
 */
@Composable
fun ButtonGrid(actions: List<GridAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DemoLayout.pagePadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(DemoLayout.gap),
    ) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DemoLayout.gap)) {
                row.forEach { action -> GridButton(action, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GridButton(action: GridAction, modifier: Modifier) {
    val buttonModifier = modifier.height(DemoLayout.buttonHeight).applyTestTag(action.testTag)
    val contentPadding = PaddingValues(horizontal = DemoLayout.gap)
    when (action.style) {
        ButtonStyle.Primary -> Button(
            onClick = action.onClick,
            modifier = buttonModifier,
            enabled = action.enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = OsGrey500,
            ),
            shape = ButtonShape,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            contentPadding = contentPadding,
        ) {
            ScaleDownText(text = action.text, style = ButtonTextStyle, color = Color.White)
        }
        ButtonStyle.Outline -> {
            val color = if (action.enabled) OsPrimary else OsGrey500
            OutlinedButton(
                onClick = action.onClick,
                modifier = buttonModifier,
                enabled = action.enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = OsGrey500),
                shape = ButtonShape,
                border = BorderStroke(1.dp, color),
                contentPadding = contentPadding,
            ) {
                ScaleDownText(text = action.text, style = ButtonTextStyle, color = color)
            }
        }
    }
}

@Composable
private fun ScaleDownText(text: String, style: TextStyle, color: Color) = BoxWithConstraints(
    contentAlignment = Alignment.Center,
) {
    // Keyed on width so a cell that grows after a reflow scales back up.
    var scaledStyle by remember(text, style, maxWidth) { mutableStateOf(style) }
    var readyToDraw by remember(text, style, maxWidth) { mutableStateOf(false) }
    Text(
        text = text,
        style = scaledStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            val canShrink = scaledStyle.fontSize.value * TEXT_SCALE_STEP >= style.fontSize.value * MIN_TEXT_SCALE
            if (result.didOverflowWidth && canShrink) {
                scaledStyle = scaledStyle.copy(fontSize = scaledStyle.fontSize * TEXT_SCALE_STEP)
            } else {
                readyToDraw = true
            }
        },
    )
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
