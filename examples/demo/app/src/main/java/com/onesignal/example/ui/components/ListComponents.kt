package com.onesignal.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsDivider
import com.onesignal.example.ui.theme.OsGrey500
import com.onesignal.example.ui.theme.OsGrey600
import com.onesignal.example.ui.theme.OsPrimary
import com.onesignal.example.ui.theme.rowLabel

// Rows carry no vertical padding of their own, so the card's padding alone sets the list's top and bottom edge.
// The divider sits lower than center: the row above ends in descender space and the row below starts
// with ascender space, so equal padding reads as closer to the row above.
private val ItemDividerSpacingAbove = 5.dp
private val ItemDividerSpacingBelow = 1.dp
private val StateSlotHeight = 20.dp
private val RemoveButtonSize = 28.dp
private val RemoveIconSize = 16.dp

@Composable
private fun RemoveButton(contentDescription: String, onClick: () -> Unit, testTag: String?) {
    // Shift by the button's inner padding so the X lines up with the card's content edge.
    val modifier = Modifier
        .size(RemoveButtonSize)
        .offset(x = (RemoveButtonSize - RemoveIconSize) / 2)
        .let { if (testTag != null) it.testTag(testTag) else it }
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = contentDescription,
            tint = OsGrey600,
            modifier = Modifier.size(RemoveIconSize),
        )
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        color = OsDivider,
        modifier = Modifier.padding(top = ItemDividerSpacingAbove, bottom = ItemDividerSpacingBelow),
    )
}

@Composable
fun PairItem(
    key: String,
    value: String,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val keyModifier = if (sectionKey != null) {
                Modifier.testTag("${sectionKey}_pair_key_$key")
            } else {
                Modifier
            }
            Text(
                text = key,
                style = MaterialTheme.typography.rowLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = keyModifier,
            )
            val valueModifier = if (sectionKey != null) {
                Modifier.testTag("${sectionKey}_pair_value_$key")
            } else {
                Modifier
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = OsGrey600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = valueModifier,
            )
        }
        if (onDelete != null) {
            RemoveButton(
                contentDescription = "Remove $key",
                onClick = onDelete,
                testTag = sectionKey?.let { "${it}_remove_$key" },
            )
        }
    }
}

@Composable
fun SingleItem(
    value: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val valueModifier = Modifier
            .weight(1f)
            .let { if (sectionKey != null) it.testTag("${sectionKey}_value_$value") else it }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = valueModifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RemoveButton(
            contentDescription = "Remove $value",
            onClick = onDelete,
            testTag = sectionKey?.let { "${it}_remove_$value" },
        )
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    val containerModifier = modifier
        .fillMaxWidth()
        .height(StateSlotHeight)
    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = OsGrey500,
            modifier = if (sectionKey != null) Modifier.testTag("${sectionKey}_empty") else Modifier,
        )
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    sectionKey: String? = null,
) {
    val containerModifier = modifier
        .fillMaxWidth()
        .height(StateSlotHeight)
        .let { if (sectionKey != null) it.testTag("${sectionKey}_loading") else it }
    Box(modifier = containerModifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = OsPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )
    }
}

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
                SingleItem(value = item, onDelete = { onDelete(item) }, sectionKey = sectionKey)
                if (index < displayItems.lastIndex) {
                    ItemDivider()
                }
            }

            if (shouldCollapse) {
                HorizontalDivider(color = OsDivider, modifier = Modifier.padding(top = ItemDividerSpacingAbove))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = DemoLayout.gap / 2),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "Show less" else "${items.size - maxCollapsedItems} more",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = OsPrimary,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = OsPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

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
                    sectionKey = sectionKey,
                )
                if (index < items.lastIndex) {
                    ItemDivider()
                }
            }
        }
    }
}
