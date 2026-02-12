package com.onesignal.sdktest.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.sdktest.util.LogEntry
import com.onesignal.sdktest.util.LogLevel
import com.onesignal.sdktest.util.LogManager

private val LogBackground = Color(0xFF1E1E1E)
private val LogHeaderBackground = Color(0xFF2D2D2D)
private val LogDebugColor = Color(0xFF9CDCFE)
private val LogInfoColor = Color(0xFFDCDCAA)
private val LogWarnColor = Color(0xFFFFD700)
private val LogErrorColor = Color(0xFFF44747)
private val LogTimestampColor = Color(0xFF808080)

/**
 * Collapsible log view that displays app logs at the top of the screen.
 */
@Composable
fun LogView(
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    val logs = LogManager.logs
    val listState = rememberLazyListState()
    
    // Auto-scroll to top when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("log_view_container")
    ) {
        // Header with toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LogHeaderBackground)
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("log_view_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOGS",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("log_view_title")
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "(${logs.size})",
                color = LogTimestampColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .testTag("log_view_count")
                    .semantics { contentDescription = "Log count: ${logs.size}" }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Clear button
            if (logs.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear logs",
                    tint = LogTimestampColor,
                    modifier = Modifier
                        .clickable { LogManager.clear() }
                        .padding(4.dp)
                        .height(16.dp)
                        .testTag("log_view_clear_button")
                )
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White
            )
        }
        
        // Log content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "No logs yet",
                    color = LogTimestampColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LogBackground)
                        .padding(12.dp)
                        .testTag("log_view_empty")
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp) // ~5 lines
                        .background(LogBackground)
                        .testTag("log_view_list")
                ) {
                    items(logs, key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
                        LogEntryRow(entry = entry, index = logs.indexOf(entry))
                    }
                }
            }
        }
        
        HorizontalDivider(color = LogHeaderBackground, thickness = 1.dp)
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, index: Int) {
    val levelText = when (entry.level) {
        LogLevel.DEBUG -> "D"
        LogLevel.INFO -> "I"
        LogLevel.WARN -> "W"
        LogLevel.ERROR -> "E"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("log_entry_$index")
            .semantics { 
                contentDescription = "Log $index: $levelText ${entry.message}" 
            },
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = entry.timestamp,
            color = LogTimestampColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("log_entry_${index}_timestamp")
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Level indicator
        Text(
            text = levelText,
            color = when (entry.level) {
                LogLevel.DEBUG -> LogDebugColor
                LogLevel.INFO -> LogInfoColor
                LogLevel.WARN -> LogWarnColor
                LogLevel.ERROR -> LogErrorColor
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("log_entry_${index}_level")
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Message
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag("log_entry_${index}_message")
        )
    }
}
