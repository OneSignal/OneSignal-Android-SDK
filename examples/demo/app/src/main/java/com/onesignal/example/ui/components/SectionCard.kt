package com.onesignal.example.ui.components

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
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsCardBorder
import com.onesignal.example.ui.theme.OsGrey500

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    showCard: Boolean = true,
    sectionKey: String? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerModifier = if (sectionKey != null) {
        modifier.fillMaxWidth().testTag("${sectionKey}_section")
    } else {
        modifier.fillMaxWidth()
    }
    Column(modifier = containerModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DemoLayout.pagePadding)
                .padding(bottom = DemoLayout.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (onInfoClick != null) {
                val infoModifier = if (sectionKey != null) {
                    Modifier.size(28.dp).testTag("${sectionKey}_info_icon")
                } else {
                    Modifier.size(28.dp)
                }
                IconButton(onClick = onInfoClick, modifier = infoModifier) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "$title info",
                        tint = OsGrey500,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showCard) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DemoLayout.pagePadding),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(DemoLayout.cardBorderWidth, OsCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DemoLayout.cardPadding),
                    content = content,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Wraps a top-level demo section with 24dp bottom spacing (styles.md section spacing). */
@Composable
fun DemoSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(bottom = DemoLayout.sectionGap), content = content)
}
