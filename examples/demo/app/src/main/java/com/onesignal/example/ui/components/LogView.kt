package com.onesignal.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsGrey600
import com.onesignal.example.ui.theme.OsLogBackground
import com.onesignal.example.ui.theme.OsLogText
import com.onesignal.example.util.DemoLog

private val entryStyle = TextStyle(
    color = OsLogText,
    fontFamily = FontFamily.Monospace,
    fontSize = 8.sp,
    lineHeight = 1.4.em,
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
            .testTag("log_view")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(start = 16.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOGS (${logs.size})",
                modifier = Modifier.weight(1f),
                color = OsGrey500,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
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

        if (!collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                if (logs.isEmpty()) {
                    Text("No logs yet", style = entryStyle.copy(color = OsGrey600))
                }
                logs.forEachIndexed { index, entry ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = OsGrey600)) { append(entry.time) }
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
    // The header is only 24dp tall, so widen the touch target instead.
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
            tint = OsGrey500,
        )
    }
}
