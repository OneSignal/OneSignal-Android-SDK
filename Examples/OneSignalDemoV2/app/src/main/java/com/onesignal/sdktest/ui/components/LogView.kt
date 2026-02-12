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
import androidx.compose.material3.Icon
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

private val LogBackground = Color(0xFF1A1B1E)
private val LogHeaderBackground = Color(0xFF25262A)
private val LogDebugColor = Color(0xFF82AAFF)
private val LogInfoColor = Color(0xFFC3E88D)
private val LogWarnColor = Color(0xFFFFCB6B)
private val LogErrorColor = Color(0xFFFF5370)
private val LogTimestampColor = Color(0xFF676E7B)

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
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("log_view_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOGS",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.testTag("log_view_title")
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
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
                        .size(14.dp)
                        .testTag("log_view_clear_button")
                )
                
                Spacer(modifier = Modifier.width(10.dp))
            }
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
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
                        .padding(14.dp)
                        .testTag("log_view_empty")
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(LogBackground)
                        .testTag("log_view_list")
                ) {
                    items(logs.size) { index ->
                        LogEntryRow(entry = logs[index], index = index)
                    }
                }
            }
        }
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
            .padding(horizontal = 14.dp, vertical = 4.dp)
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
            color = Color.White.copy(alpha = 0.85f),
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
