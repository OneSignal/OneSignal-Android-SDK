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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsControlBorder
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsInk
import com.onesignal.example.ui.theme.OsNeutralBackground
import com.onesignal.example.ui.theme.OsPrimary

/** Primary is filled red; Secondary is outlined with a dark label. */
enum class ButtonStyle { Primary, Secondary }

data class GridAction(
    val text: String,
    val onClick: () -> Unit,
    val testTag: String? = null,
    val style: ButtonStyle = ButtonStyle.Primary,
    val enabled: Boolean = true,
)

private val ButtonShape = RoundedCornerShape(DemoLayout.buttonRadius)
private val ButtonContentPadding = PaddingValues(horizontal = 12.dp)

private val ButtonTextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)

private const val MIN_TEXT_SCALE = 0.7f
private const val TEXT_SCALE_STEP = 0.95f

// Press always darkens, on red and white alike; the default ripple takes the label color and turns red buttons pink.
@Immutable
private object DarkenRippleTheme : RippleTheme {
    private val alpha = RippleAlpha(
        draggedAlpha = 0.12f,
        focusedAlpha = 0.12f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.12f,
    )

    @Composable
    override fun defaultColor(): Color = Color.Black

    @Composable
    override fun rippleAlpha(): RippleAlpha = alpha
}

private class ButtonColors(val container: Color, val content: Color, val border: Color?)

private fun ButtonStyle.colors(enabled: Boolean): ButtonColors = when {
    !enabled && this == ButtonStyle.Primary -> ButtonColors(OsNeutralBackground, OsGrey500, null)
    !enabled -> ButtonColors(Color.Transparent, OsGrey500, OsControlBorder)
    this == ButtonStyle.Primary -> ButtonColors(OsPrimary, Color.White, null)
    else -> ButtonColors(Color.Transparent, OsInk, OsControlBorder)
}

private fun Modifier.applyTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

private fun Modifier.fullWidthButton(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = DemoLayout.pagePadding)

@Composable
private fun DemoButton(
    text: String,
    onClick: () -> Unit,
    style: ButtonStyle,
    modifier: Modifier,
    enabled: Boolean,
    testTag: String?,
) {
    val colors = style.colors(enabled)
    CompositionLocalProvider(LocalRippleTheme provides DarkenRippleTheme) {
        Button(
            onClick = onClick,
            modifier = modifier.height(DemoLayout.buttonHeight).applyTestTag(testTag),
            enabled = enabled,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.container,
                contentColor = colors.content,
                disabledContainerColor = colors.container,
                disabledContentColor = colors.content,
            ),
            elevation = null,
            border = colors.border?.let { BorderStroke(DemoLayout.buttonBorderWidth, it) },
            contentPadding = ButtonContentPadding,
        ) {
            ScaleDownText(text = text, style = ButtonTextStyle, color = colors.content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) = DemoButton(text, onClick, ButtonStyle.Primary, modifier.fullWidthButton(), enabled, testTag)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) = DemoButton(text, onClick, ButtonStyle.Secondary, modifier.fullWidthButton(), enabled, testTag)

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
            .padding(horizontal = DemoLayout.pagePadding),
        verticalArrangement = Arrangement.spacedBy(DemoLayout.gap),
    ) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DemoLayout.gap)) {
                row.forEach { action ->
                    DemoButton(
                        text = action.text,
                        onClick = action.onClick,
                        style = action.style,
                        modifier = Modifier.weight(1f),
                        enabled = action.enabled,
                        testTag = action.testTag,
                    )
                }
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
