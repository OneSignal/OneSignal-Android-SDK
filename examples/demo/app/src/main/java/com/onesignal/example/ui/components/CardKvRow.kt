package com.onesignal.example.ui.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsGrey600
import com.onesignal.example.ui.theme.OsGrey700
import com.onesignal.example.ui.theme.rowLabel

// Appium reads this placeholder to tell "not set" apart from a real ID.
private const val EMPTY_VALUE = "—"

private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag != null) this.testTag(tag) else this

/** Label on the left, trailing content on the right (styles.md "Card Rows"). */
@Composable
fun CardRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DemoLayout.rowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.rowLabel,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

/** A read-only value with an optional copy button. A null or blank value shows "—". */
@Composable
fun CardKvRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    copyable: Boolean = false,
) {
    val shownValue = value?.takeIf { it.isNotBlank() }
    CardRow(label = label, modifier = modifier) {
        Text(
            text = shownValue ?: EMPTY_VALUE,
            style = MaterialTheme.typography.bodySmall,
            color = if (shownValue != null) OsGrey700 else OsGrey500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .optionalTestTag(valueTestTag),
        )
        if (copyable && shownValue != null) {
            Spacer(modifier = Modifier.width(1.dp))
            CopyButton(label = label, value = shownValue)
        }
    }
}

private val CopyIconSize = 14.dp
private val CopyButtonSize = 24.dp

@Composable
private fun CopyButton(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarController.current
    // Kept at row height so the row doesn't grow; Compose still extends the touch area to 48dp.
    // The offset lines the glyph up with the card's right padding.
    Box(
        modifier = Modifier
            .offset(x = (CopyButtonSize - CopyIconSize) / 2)
            .size(CopyButtonSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = CopyButtonSize * 2 / 3),
                role = Role.Button,
                onClickLabel = "Copy $label",
            ) {
                clipboard.setText(AnnotatedString(value))
                // Android 13+ shows its own clipboard confirmation, which a snackbar would overlap.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbar.show("Copied $label")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy $label",
            tint = OsGrey600,
            modifier = Modifier.size(CopyIconSize),
        )
    }
}
