package com.onesignal.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onesignal.example.ui.theme.OsLogBackground
import com.onesignal.example.ui.theme.OsLogBadge
import com.onesignal.example.ui.theme.OsLogBorder
import com.onesignal.example.ui.theme.OsLogHeader
import com.onesignal.example.ui.theme.OsLogMuted
import com.onesignal.example.ui.theme.GoogleSansFlex
import com.onesignal.example.ui.theme.OsLogText
import com.onesignal.example.util.DemoLog

private val HeaderHeight = 28.dp

// Fixed so the page below never shifts as entries arrive; seven entries fill it.
private val BodyHeight = 100.dp

// Proportional rather than monospace so long IDs and titles fit on one line.
private val entryStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    color = OsLogText,
    fontSize = 9.sp,
    lineHeight = 1.5.em,
    // Each entry is a single-line Text, so the default trim would drop all of the extra height.
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
)

@Composable
fun LogView(modifier: Modifier = Modifier) {
    val logs by DemoLog.entries.collectAsState()
    var collapsed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(logs, collapsed) {
        if (!collapsed) scrollState.scrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OsLogBackground)
            .testTag("log_view"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OsLogHeader)
                .height(HeaderHeight)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOGS",
                color = OsLogText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${logs.size}",
                color = OsLogMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(OsLogBadge, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            HeaderButton(
                icon = Icons.Filled.Delete,
                iconSize = 14.dp,
                label = "Clear logs",
                testTag = "log_clear_button",
                onClick = DemoLog::clear,
            )
            HeaderButton(
                icon = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                iconSize = 18.dp,
                label = if (collapsed) "Expand logs" else "Collapse logs",
                testTag = "log_toggle_button",
                onClick = { collapsed = !collapsed },
            )
        }

        // UiAutomator skips zero-size nodes, so hiding the body can't keep entries queryable.
        if (!collapsed) {
            HorizontalDivider(thickness = 1.dp, color = OsLogBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BodyHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 3.dp),
            ) {
                if (logs.isEmpty()) {
                    Text("No logs yet", style = entryStyle.copy(color = OsLogMuted))
                }
                logs.forEachIndexed { index, entry ->
                    Text(
                        text = buildAnnotatedString {
                            // Tabular digits keep every timestamp the same width so messages line up.
                            withStyle(SpanStyle(color = OsLogMuted, fontFeatureSettings = "tnum")) {
                                append(entry.time)
                            }
                            append(" ${entry.message}")
                        },
                        modifier = Modifier.testTag("log_entry_$index"),
                        style = entryStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    iconSize: Dp,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    // The header is shorter than a standard touch target, so widen it instead.
    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = OsLogMuted,
        )
    }
}
