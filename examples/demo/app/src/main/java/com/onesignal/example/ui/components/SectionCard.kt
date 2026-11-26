package com.onesignal.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsCardBorder
import com.onesignal.example.ui.theme.OsGrey600

private val InfoIconSize = 18.dp

// The icon sits centered in its touch box; shifting by the leftover space lines its glyph up with the card edge.
private val InfoIconOffset = (DemoLayout.sectionHeaderHeight - InfoIconSize) / 2

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    showCard: Boolean = true,
    sectionKey: String? = null,
    onInfoClick: (() -> Unit)? = null,
    aboveCard: (@Composable () -> Unit)? = null,
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
                .padding(bottom = DemoLayout.gap)
                .height(DemoLayout.sectionHeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            if (onInfoClick != null) {
                val infoModifier = Modifier
                    .size(DemoLayout.sectionHeaderHeight)
                    .offset(x = InfoIconOffset)
                    .let { if (sectionKey != null) it.testTag("${sectionKey}_info_icon") else it }
                IconButton(onClick = onInfoClick, modifier = infoModifier) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "$title info",
                        tint = OsGrey600,
                        modifier = Modifier.size(InfoIconSize),
                    )
                }
            }
        }

        if (aboveCard != null) {
            aboveCard()
            Spacer(modifier = Modifier.height(DemoLayout.gap))
        }

        if (showCard) {
            DemoCard(content = content)
        } else {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** White card with a hairline border (styles.md "Cards"); flat, no shadow. */
@Composable
fun DemoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
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
                .padding(
                    horizontal = DemoLayout.cardPaddingHorizontal,
                    vertical = DemoLayout.cardPaddingVertical,
                ),
            content = content,
        )
    }
}

/** Wraps a top-level demo section with the section spacing below it (styles.md "Spacing"). */
@Composable
fun DemoSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(bottom = DemoLayout.sectionGap), content = content)
}
