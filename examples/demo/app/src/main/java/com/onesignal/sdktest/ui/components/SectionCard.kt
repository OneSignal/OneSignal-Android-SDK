package com.onesignal.sdktest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Reusable section card with title and optional info tooltip.
 * `sectionKey` mirrors the Capacitor demo's `sectionKey` prop and seeds the
 * snake_case test tags used by cross-platform E2E selectors.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    showCard: Boolean = true,
    sectionKey: String? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerModifier = if (sectionKey != null) {
        modifier.fillMaxWidth().testTag("${sectionKey}_section")
    } else {
        modifier.fillMaxWidth()
    }
    Column(modifier = containerModifier) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (onInfoClick != null) {
                val infoModifier = if (sectionKey != null) {
                    Modifier.size(28.dp).testTag("${sectionKey}_info_icon")
                } else {
                    Modifier.size(28.dp)
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = infoModifier
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "$title info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showCard) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}
