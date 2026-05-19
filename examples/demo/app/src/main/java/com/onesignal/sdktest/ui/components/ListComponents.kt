package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onesignal.sdktest.ui.theme.DividerColor

/**
 * A row displaying a key-value pair with delete button.
 */
@Composable
fun PairItem(
    key: String,
    value: String,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val keyModifier = if (sectionKey != null) {
                Modifier.testTag("${sectionKey}_pair_key_$key")
            } else {
                Modifier
            }
            Text(
                text = key,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = keyModifier
            )
            val valueModifier = if (sectionKey != null) {
                Modifier.testTag("${sectionKey}_pair_value_$key")
            } else {
                Modifier
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = valueModifier
            )
        }
        if (onDelete != null) {
            val removeModifier = if (sectionKey != null) {
                Modifier.size(32.dp).testTag("${sectionKey}_remove_$key")
            } else {
                Modifier.size(32.dp)
            }
            IconButton(onClick = onDelete, modifier = removeModifier) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $key",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * A row displaying a single value with delete button.
 */
@Composable
fun SingleItem(
    value: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val valueModifier = Modifier
            .weight(1f)
            .let { if (sectionKey != null) it.testTag("${sectionKey}_value_$value") else it }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = valueModifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val removeModifier = if (sectionKey != null) {
            Modifier.size(32.dp).testTag("${sectionKey}_remove_$value")
        } else {
            Modifier.size(32.dp)
        }
        IconButton(onClick = onDelete, modifier = removeModifier) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $value",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Empty state text.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    val containerModifier = modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp)
        .let { if (sectionKey != null) it.testTag("${sectionKey}_empty") else it }
    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Inline loading state shown in the empty list slot while an SDK fetch is in
 * flight. Mirrors Capacitor's `LoadingState` in `ListWidgets.tsx`.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    val containerModifier = modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp)
        .let { if (sectionKey != null) it.testTag("${sectionKey}_loading") else it }
    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Collapsible list section for emails/SMS (shows "X more available" when collapsed).
 */
@Composable
fun CollapsibleSingleList(
    items: List<String>,
    emptyText: String,
    onDelete: (String) -> Unit,
    maxCollapsedItems: Int = 5,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
    loading: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val shouldCollapse = items.size > maxCollapsedItems
    val displayItems = if (shouldCollapse && !expanded) {
        items.take(maxCollapsedItems)
    } else {
        items
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            if (loading) {
                LoadingState(sectionKey = sectionKey)
            } else {
                EmptyState(text = emptyText, sectionKey = sectionKey)
            }
        } else {
            displayItems.forEachIndexed { index, item ->
                SingleItem(
                    value = item,
                    onDelete = { onDelete(item) },
                    sectionKey = sectionKey
                )
                if (index < displayItems.lastIndex) {
                    HorizontalDivider(
                        color = DividerColor,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (shouldCollapse) {
                HorizontalDivider(
                    color = DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Show less" else "${items.size - maxCollapsedItems} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * List of key-value pairs with optional action buttons.
 */
@Composable
fun PairList(
    items: List<Pair<String, String>>,
    emptyText: String,
    onDelete: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
    loading: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            if (loading) {
                LoadingState(sectionKey = sectionKey)
            } else {
                EmptyState(text = emptyText, sectionKey = sectionKey)
            }
        } else {
            items.forEachIndexed { index, (key, value) ->
                PairItem(
                    key = key,
                    value = value,
                    onDelete = onDelete?.let { { it(key) } },
                    sectionKey = sectionKey
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = DividerColor,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}
