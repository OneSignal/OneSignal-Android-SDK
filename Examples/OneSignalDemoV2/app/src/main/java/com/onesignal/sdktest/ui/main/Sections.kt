package com.onesignal.sdktest.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.sdktest.data.model.InAppMessageType
import com.onesignal.sdktest.ui.components.CollapsibleSingleList
import com.onesignal.sdktest.ui.components.DestructiveButton
import com.onesignal.sdktest.ui.components.OutlineButton
import com.onesignal.sdktest.ui.components.PairList
import com.onesignal.sdktest.ui.components.PrimaryButton
import com.onesignal.sdktest.ui.components.SectionCard
import com.onesignal.sdktest.ui.components.ToggleRow
import com.onesignal.sdktest.ui.theme.DividerColor
import com.onesignal.sdktest.ui.theme.OneSignalGreen
import com.onesignal.sdktest.ui.theme.OneSignalGreenLight
import com.onesignal.sdktest.ui.theme.OneSignalRed
import com.onesignal.sdktest.ui.theme.WarningBackground

// === APP SECTION ===
@Composable
fun AppSection(
    appId: String,
    consentRequired: Boolean,
    onConsentRequiredChange: (Boolean) -> Unit,
    privacyConsentGiven: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onGetKeysClick: () -> Unit
) {
    SectionCard(title = "App") {
        // App ID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "App ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = appId,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Guidance Banner
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = WarningBackground),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Color(0xFFFFE082).copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Add your own App ID, then rebuild to fully test all functionality.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Get your keys at onesignal.com",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = OneSignalRed,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onGetKeysClick() }
            )
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Privacy Consent Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ToggleRow(
            label = "Consent Required",
            description = "Require consent before SDK processes data",
            checked = consentRequired,
            onCheckedChange = onConsentRequiredChange
        )
        if (consentRequired) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ToggleRow(
                label = "Privacy Consent",
                description = "Consent given for data collection",
                checked = privacyConsentGiven,
                onCheckedChange = onConsentChange
            )
        }
    }
}

// === USER SECTION ===
@Composable
fun UserSection(
    externalUserId: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val isLoggedIn = !externalUserId.isNullOrEmpty()

    SectionCard(title = "User") {
        // Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isLoggedIn) "Logged In" else "Anonymous",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (isLoggedIn) OneSignalGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        // External ID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "External ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = externalUserId ?: "—",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PrimaryButton(
        text = if (isLoggedIn) "SWITCH USER" else "LOGIN USER",
        onClick = onLoginClick
    )

    if (isLoggedIn) {
        OutlineButton(
            text = "LOGOUT USER",
            onClick = onLogoutClick
        )
    }
}

// === PUSH SECTION ===
@Composable
fun PushSection(
    pushSubscriptionId: String?,
    pushEnabled: Boolean,
    hasPermission: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPromptPush: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Push", onInfoClick = onInfoClick) {
        // Push ID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Push ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pushSubscriptionId ?: "Not Available",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (pushSubscriptionId != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            )
        }
        
        HorizontalDivider(
            color = DividerColor,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Enabled Toggle
        ToggleRow(
            label = "Enabled",
            checked = pushEnabled,
            onCheckedChange = onEnabledChange,
            enabled = hasPermission
        )
    }
    
    if (!hasPermission) {
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(
            text = "PROMPT PUSH",
            onClick = onPromptPush
        )
    }
}

// === SEND PUSH NOTIFICATION SECTION ===
@Composable
fun SendPushSection(
    onSimpleClick: () -> Unit,
    onImageClick: () -> Unit,
    onCustomClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Send Push Notification", showCard = false, onInfoClick = onInfoClick) {
        PrimaryButton(text = "SIMPLE", onClick = onSimpleClick)
        PrimaryButton(text = "WITH IMAGE", onClick = onImageClick)
        OutlineButton(text = "CUSTOM", onClick = onCustomClick)
    }
}

// === IN-APP MESSAGING SECTION ===
@Composable
fun InAppMessagingSection(
    isPaused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "In-App Messaging", onInfoClick = onInfoClick) {
        ToggleRow(
            label = "Pause In-App Messages",
            description = "Toggle in-app message display",
            checked = isPaused,
            onCheckedChange = onPausedChange
        )
    }
}

// === SEND IN-APP MESSAGE SECTION ===
@Composable
fun SendInAppMessageSection(
    onSendMessage: (InAppMessageType) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Send In-App Message", showCard = false, onInfoClick = onInfoClick) {
        InAppMessageType.values().forEach { type ->
            Button(
                onClick = { onSendMessage(type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OneSignalRed),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        type.title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }
        }
    }
}

// === ALIASES SECTION ===
@Composable
fun AliasesSection(
    aliases: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onAddMultipleClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Aliases", onInfoClick = onInfoClick) {
        PairList(
            items = aliases,
            emptyText = "No aliases added"
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick)
    PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
}

// === EMAILS SECTION ===
@Composable
fun EmailsSection(
    emails: List<String>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Emails", onInfoClick = onInfoClick) {
        CollapsibleSingleList(
            items = emails,
            emptyText = "No emails added",
            onDelete = onRemove
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD EMAIL", onClick = onAddClick)
}

// === SMS SECTION ===
@Composable
fun SmsSection(
    smsNumbers: List<String>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "SMS", onInfoClick = onInfoClick) {
        CollapsibleSingleList(
            items = smsNumbers,
            emptyText = "No SMS added",
            onDelete = onRemove
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD SMS", onClick = onAddClick)
}

// === TAGS SECTION ===
@Composable
fun TagsSection(
    tags: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onAddMultipleClick: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveSelected: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Tags", onInfoClick = onInfoClick) {
        PairList(
            items = tags,
            emptyText = "No tags added",
            onDelete = onRemove
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick)
    PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
    
    if (tags.isNotEmpty()) {
        DestructiveButton(text = "REMOVE SELECTED", onClick = onRemoveSelected)
    }
}

// === OUTCOME SECTION ===
@Composable
fun OutcomeSection(
    onSendOutcome: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Outcome Events", showCard = false, onInfoClick = onInfoClick) {
        PrimaryButton(text = "SEND OUTCOME", onClick = onSendOutcome)
    }
}

// === TRIGGERS SECTION ===
@Composable
fun TriggersSection(
    triggers: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onAddMultipleClick: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveSelected: () -> Unit,
    onClearAll: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Triggers", onInfoClick = onInfoClick) {
        PairList(
            items = triggers,
            emptyText = "No triggers added",
            onDelete = onRemove
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick)
    PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
    
    if (triggers.isNotEmpty()) {
        DestructiveButton(text = "REMOVE SELECTED", onClick = onRemoveSelected)
        DestructiveButton(text = "CLEAR ALL", onClick = onClearAll)
    }
}

// === TRACK EVENT SECTION ===
@Composable
fun TrackEventSection(
    onTrackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Track Event", showCard = false, onInfoClick = onInfoClick) {
        PrimaryButton(text = "TRACK EVENT", onClick = onTrackClick)
    }
}

// === LOCATION SECTION ===
@Composable
fun LocationSection(
    locationShared: Boolean,
    onLocationSharedChange: (Boolean) -> Unit,
    onPromptLocation: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Location", onInfoClick = onInfoClick) {
        ToggleRow(
            label = "Location Shared",
            description = "Share device location with OneSignal",
            checked = locationShared,
            onCheckedChange = onLocationSharedChange
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "PROMPT LOCATION", onClick = onPromptLocation)
}
