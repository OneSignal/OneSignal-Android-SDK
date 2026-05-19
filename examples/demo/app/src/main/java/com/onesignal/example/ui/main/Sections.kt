package com.onesignal.example.ui.main

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.example.data.model.InAppMessageType
import com.onesignal.example.ui.components.CollapsibleSingleList
import com.onesignal.example.ui.components.DestructiveButton
import com.onesignal.example.ui.components.OutlineButton
import com.onesignal.example.ui.components.PairList
import com.onesignal.example.ui.components.PrimaryButton
import com.onesignal.example.ui.components.SectionCard
import com.onesignal.example.ui.components.ToggleRow
import com.onesignal.example.ui.theme.DividerColor
import com.onesignal.example.ui.theme.OneSignalGreen
import com.onesignal.example.ui.theme.OneSignalRed
import com.onesignal.example.ui.theme.WarningBackground

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
    SectionCard(title = "App", sectionKey = "app") {
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.testTag("app_id_value")
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

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
            onCheckedChange = onConsentRequiredChange,
            testTag = "consent_required_toggle",
            contentDescription = "Consent required"
        )
        if (consentRequired) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ToggleRow(
                label = "Privacy Consent",
                description = "Consent given for data collection",
                checked = privacyConsentGiven,
                onCheckedChange = onConsentChange,
                testTag = "privacy_consent_toggle",
                contentDescription = "Privacy consent"
            )
        }
    }
}

// === USER SECTION ===
@Composable
fun UserSection(
    externalUserId: String?,
    useIdentityVerification: Boolean,
    onUseIdentityVerificationChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onUpdateJwtClick: () -> Unit,
    isLoading: Boolean = false,
) {
    val isLoggedIn = !externalUserId.isNullOrEmpty()

    SectionCard(title = "User", sectionKey = "user") {
        ToggleRow(
            label = "Identity Verification",
            description = "Use external_id for API calls",
            checked = useIdentityVerification,
            onCheckedChange = onUseIdentityVerificationChange,
            testTag = "identity_verification_toggle",
            contentDescription = "Identity verification"
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                ),
                modifier = Modifier.testTag("user_status_value")
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("user_external_id_value")
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    PrimaryButton(
        text = if (isLoggedIn) "SWITCH USER" else "LOGIN USER",
        onClick = onLoginClick,
        enabled = !isLoading,
        testTag = "login_user_button"
    )

    if (isLoggedIn) {
        OutlineButton(
            text = "LOGOUT USER",
            onClick = onLogoutClick,
            enabled = !isLoading,
            testTag = "logout_user_button"
        )
    }

    OutlineButton(
        text = "UPDATE USER JWT",
        onClick = onUpdateJwtClick,
        testTag = "update_user_jwt_button"
    )
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
    SectionCard(title = "Push", sectionKey = "push", onInfoClick = onInfoClick) {
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
                ),
                modifier = Modifier.testTag("push_id_value")
            )
        }

        HorizontalDivider(
            color = DividerColor,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        ToggleRow(
            label = "Enabled",
            checked = pushEnabled,
            onCheckedChange = onEnabledChange,
            enabled = hasPermission,
            testTag = "push_enabled_toggle",
            contentDescription = "Push enabled"
        )
    }

    if (!hasPermission) {
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryButton(
            text = "PROMPT PUSH",
            onClick = onPromptPush,
            testTag = "prompt_push_button"
        )
    }
}

// === SEND PUSH NOTIFICATION SECTION ===
@Composable
fun SendPushSection(
    onSimpleClick: () -> Unit,
    onImageClick: () -> Unit,
    onSoundClick: () -> Unit,
    onCustomClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(
        title = "Send Push Notification",
        showCard = false,
        sectionKey = "send_push",
        onInfoClick = onInfoClick
    ) {
        PrimaryButton(text = "SIMPLE", onClick = onSimpleClick, testTag = "send_simple_button")
        PrimaryButton(text = "WITH IMAGE", onClick = onImageClick, testTag = "send_image_button")
        PrimaryButton(text = "WITH SOUND", onClick = onSoundClick, testTag = "send_sound_button")
        PrimaryButton(text = "CUSTOM", onClick = onCustomClick, testTag = "send_custom_button")
        OutlineButton(text = "CLEAR ALL", onClick = onClearAllClick, testTag = "clear_all_button")
    }
}

// === IN-APP MESSAGING SECTION ===
@Composable
fun InAppMessagingSection(
    isPaused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(title = "In-App Messaging", sectionKey = "iam", onInfoClick = onInfoClick) {
        ToggleRow(
            label = "Pause In-App Messages",
            description = "Toggle in-app message display",
            checked = isPaused,
            onCheckedChange = onPausedChange,
            testTag = "pause_iam_toggle",
            contentDescription = "Pause in-app messages"
        )
    }
}

// === SEND IN-APP MESSAGE SECTION ===
@Composable
fun SendInAppMessageSection(
    onSendMessage: (InAppMessageType) -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(
        title = "Send In-App Message",
        showCard = false,
        sectionKey = "send_iam",
        onInfoClick = onInfoClick
    ) {
        InAppMessageType.values().forEach { type ->
            // Capacitor exposes `send_iam_${type}_button`; reuse the enum name lower-cased.
            val typeTag = type.name.lowercase()
            Button(
                onClick = { onSendMessage(type) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(44.dp)
                    .testTag("send_iam_${typeTag}_button"),
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
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    SectionCard(title = "Aliases", sectionKey = "aliases", onInfoClick = onInfoClick) {
        PairList(
            items = aliases,
            emptyText = "No aliases added",
            sectionKey = "aliases",
            loading = loading
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick, testTag = "add_alias_button")
    PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick, testTag = "add_multiple_aliases_button")
}

// === EMAILS SECTION ===
@Composable
fun EmailsSection(
    emails: List<String>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    SectionCard(title = "Emails", sectionKey = "emails", onInfoClick = onInfoClick) {
        CollapsibleSingleList(
            items = emails,
            emptyText = "No emails added",
            onDelete = onRemove,
            sectionKey = "emails",
            loading = loading
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD EMAIL", onClick = onAddClick, testTag = "add_email_button")
}

// === SMS SECTION ===
@Composable
fun SmsSection(
    smsNumbers: List<String>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    SectionCard(title = "SMS", sectionKey = "sms", onInfoClick = onInfoClick) {
        CollapsibleSingleList(
            items = smsNumbers,
            emptyText = "No SMS added",
            onDelete = onRemove,
            sectionKey = "sms",
            loading = loading
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD SMS", onClick = onAddClick, testTag = "add_sms_button")
}

// === TAGS SECTION ===
@Composable
fun TagsSection(
    tags: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onAddMultipleClick: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveSelected: () -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    SectionCard(title = "Tags", sectionKey = "tags", onInfoClick = onInfoClick) {
        PairList(
            items = tags,
            emptyText = "No tags added",
            onDelete = onRemove,
            sectionKey = "tags",
            loading = loading
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick, testTag = "add_tag_button")
    PrimaryButton(text = "ADD MULTIPLE", onClick = onAddMultipleClick, testTag = "add_multiple_tags_button")

    if (tags.isNotEmpty()) {
        DestructiveButton(
            text = "REMOVE SELECTED",
            onClick = onRemoveSelected,
            testTag = "remove_selected_tags_button"
        )
    }
}

// === OUTCOME SECTION ===
@Composable
fun OutcomeSection(
    onSendOutcome: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(
        title = "Outcome Events",
        showCard = false,
        sectionKey = "outcomes",
        onInfoClick = onInfoClick
    ) {
        PrimaryButton(text = "SEND OUTCOME", onClick = onSendOutcome, testTag = "send_outcome_button")
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
    SectionCard(title = "Triggers", sectionKey = "triggers", onInfoClick = onInfoClick) {
        PairList(
            items = triggers,
            emptyText = "No triggers added",
            onDelete = onRemove,
            sectionKey = "triggers"
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "ADD", onClick = onAddClick, testTag = "add_trigger_button")
    PrimaryButton(
        text = "ADD MULTIPLE",
        onClick = onAddMultipleClick,
        testTag = "add_multiple_triggers_button"
    )

    if (triggers.isNotEmpty()) {
        DestructiveButton(
            text = "REMOVE SELECTED",
            onClick = onRemoveSelected,
            testTag = "remove_selected_triggers_button"
        )
        DestructiveButton(
            text = "CLEAR ALL",
            onClick = onClearAll,
            testTag = "clear_all_triggers_button"
        )
    }
}

// === TRACK EVENT SECTION ===
@Composable
fun TrackEventSection(
    onTrackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SectionCard(
        title = "Track Event",
        showCard = false,
        sectionKey = "custom_events",
        onInfoClick = onInfoClick
    ) {
        PrimaryButton(text = "TRACK EVENT", onClick = onTrackClick, testTag = "track_event_button")
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
    SectionCard(title = "Location", sectionKey = "location", onInfoClick = onInfoClick) {
        ToggleRow(
            label = "Location Shared",
            description = "Share device location with OneSignal",
            checked = locationShared,
            onCheckedChange = onLocationSharedChange,
            testTag = "location_shared_toggle",
            contentDescription = "Location shared"
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    PrimaryButton(text = "PROMPT LOCATION", onClick = onPromptLocation, testTag = "prompt_location_button")
}
