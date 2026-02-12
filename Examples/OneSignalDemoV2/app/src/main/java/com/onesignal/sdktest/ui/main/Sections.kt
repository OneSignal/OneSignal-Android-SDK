package com.onesignal.sdktest.ui.main

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.onesignal.sdktest.ui.components.PairList
import com.onesignal.sdktest.ui.components.PrimaryButton
import com.onesignal.sdktest.ui.components.SectionCard
import com.onesignal.sdktest.ui.components.ToggleRow
import com.onesignal.sdktest.ui.theme.CardBackground
import com.onesignal.sdktest.ui.theme.DividerColor
import com.onesignal.sdktest.ui.theme.OneSignalGreen
import com.onesignal.sdktest.ui.theme.OneSignalGreenLight
import com.onesignal.sdktest.ui.theme.OneSignalRed
import com.onesignal.sdktest.ui.theme.WarningBackground

// === APP SECTION ===
@Composable
fun AppSection(
    appId: String,
    privacyConsentGiven: Boolean,
    onConsentChange: (Boolean) -> Unit,
    externalUserId: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onGetKeysClick: () -> Unit
) {
    val isLoggedIn = !externalUserId.isNullOrEmpty()
    
    SectionCard(title = "App") {
        // App ID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App-Id:", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = appId,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
    }
    
    // Guidance Banner
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = WarningBackground)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Add your own App ID, then rebuild to fully test all functionality.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Get your keys at onesignal.com",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = OneSignalRed,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onGetKeysClick() }
            )
        }
    }
    
    // Privacy Consent Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        ToggleRow(
            label = "Privacy Consent",
            description = "Consent given for data collection",
            checked = privacyConsentGiven,
            onCheckedChange = onConsentChange
        )
    }
    
    // Logged In As (shown above buttons when logged in)
    if (isLoggedIn) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = OneSignalGreenLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Logged in as:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    externalUserId ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = OneSignalGreen,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    
    // Login / Switch User Button
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        PrimaryButton(
            text = if (isLoggedIn) "SWITCH USER" else "LOGIN USER",
            onClick = onLoginClick
        )
    }
    
    // Logout Button (only when logged in)
    if (isLoggedIn) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            PrimaryButton(
                text = "LOGOUT USER",
                onClick = onLogoutClick
            )
        }
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
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Push-Id:", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = pushSubscriptionId ?: "Not Available",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
        
        HorizontalDivider(color = DividerColor)
        
        // Enabled Toggle
        ToggleRow(
            label = "Enabled",
            checked = pushEnabled,
            onCheckedChange = onEnabledChange,
            enabled = hasPermission
        )
        
        // Prompt Push Button (only if no permission)
        if (!hasPermission) {
            HorizontalDivider(color = DividerColor)
            PrimaryButton(
                text = "PROMPT PUSH",
                onClick = onPromptPush
            )
        }
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
    SectionCard(title = "Send Push Notification", onInfoClick = onInfoClick) {
        PrimaryButton(text = "SIMPLE", onClick = onSimpleClick)
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "WITH IMAGE", onClick = onImageClick)
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "CUSTOM", onClick = onCustomClick)
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
    SectionCard(title = "Send In-App Message", onInfoClick = onInfoClick) {
        InAppMessageType.values().forEachIndexed { index, type ->
            Button(
                onClick = { onSendMessage(type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OneSignalRed),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(type.title)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
            if (index < InAppMessageType.values().lastIndex) {
                HorizontalDivider(color = DividerColor)
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
    onRemove: (String) -> Unit,
    onRemoveSelected: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Aliases", onInfoClick = onInfoClick) {
        PairList(
            items = aliases,
            emptyText = "No Aliases Added",
            onDelete = onRemove
        )
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD", onClick = onAddClick)
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
        
        if (aliases.isNotEmpty()) {
            HorizontalDivider(color = DividerColor)
            DestructiveButton(text = "REMOVE SELECTED", onClick = onRemoveSelected)
        }
    }
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
            emptyText = "No Emails Added",
            onDelete = onRemove
        )
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD EMAIL", onClick = onAddClick)
    }
}

// === SMS SECTION ===
@Composable
fun SmsSection(
    smsNumbers: List<String>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "SMSs", onInfoClick = onInfoClick) {
        CollapsibleSingleList(
            items = smsNumbers,
            emptyText = "No SMSs Added",
            onDelete = onRemove
        )
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD SMS", onClick = onAddClick)
    }
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
            emptyText = "No Tags Added",
            onDelete = onRemove
        )
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD", onClick = onAddClick)
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
        
        if (tags.isNotEmpty()) {
            HorizontalDivider(color = DividerColor)
            DestructiveButton(text = "REMOVE SELECTED", onClick = onRemoveSelected)
        }
    }
}

// === OUTCOME SECTION ===
@Composable
fun OutcomeSection(
    onSendOutcome: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Outcome Events", onInfoClick = onInfoClick) {
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
            emptyText = "No Triggers Added",
            onDelete = onRemove
        )
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD", onClick = onAddClick)
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick)
        
        if (triggers.isNotEmpty()) {
            HorizontalDivider(color = DividerColor)
            DestructiveButton(text = "REMOVE SELECTED", onClick = onRemoveSelected)
            HorizontalDivider(color = DividerColor)
            DestructiveButton(text = "CLEAR ALL", onClick = onClearAll)
        }
    }
}

// === TRACK EVENT SECTION ===
@Composable
fun TrackEventSection(
    onTrackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "Track Event", onInfoClick = onInfoClick) {
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
        HorizontalDivider(color = DividerColor)
        PrimaryButton(text = "PROMPT LOCATION", onClick = onPromptLocation)
    }
}
