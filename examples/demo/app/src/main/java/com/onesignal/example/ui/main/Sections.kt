package com.onesignal.example.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onesignal.example.data.model.InAppMessageType
import com.onesignal.example.ui.components.CardKvRow
import com.onesignal.example.ui.components.CollapsibleSingleList
import com.onesignal.example.ui.components.CustomNotificationDialog
import com.onesignal.example.ui.components.DemoSection
import com.onesignal.example.ui.components.DestructiveButton
import com.onesignal.example.ui.components.LocalSnackbarController
import com.onesignal.example.ui.components.LoginDialog
import com.onesignal.example.ui.components.MultiPairInputDialog
import com.onesignal.example.ui.components.MultiSelectRemoveDialog
import com.onesignal.example.ui.components.OutcomeDialog
import com.onesignal.example.ui.components.OutlineButton
import com.onesignal.example.ui.components.PairInputDialog
import com.onesignal.example.ui.components.PairList
import com.onesignal.example.ui.components.PrimaryButton
import com.onesignal.example.ui.components.SectionCard
import com.onesignal.example.ui.components.SingleInputDialog
import com.onesignal.example.ui.components.ToggleRow
import com.onesignal.example.ui.components.TrackEventDialog
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.ui.theme.OsCardBorder
import com.onesignal.example.ui.theme.OsDivider
import com.onesignal.example.ui.theme.OsGrey600
import com.onesignal.example.ui.theme.OsGrey700
import com.onesignal.example.ui.theme.OsPrimary
import com.onesignal.example.ui.theme.OsSuccess
import com.onesignal.example.ui.theme.OsWarningBackground

@Composable
private fun DemoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DemoLayout.pagePadding),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(DemoLayout.cardBorderWidth, OsCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(DemoLayout.cardPadding), content = content)
    }
}

@Composable
fun AppSection(
    appId: String,
    consentRequired: Boolean,
    onConsentRequiredChange: (Boolean) -> Unit,
    privacyConsentGiven: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onGetKeysClick: () -> Unit,
) {
    DemoSection {
        SectionCard(title = "App", sectionKey = "app") {
            CardKvRow(label = "App ID", value = appId, valueTestTag = "app_id_value")
        }

        Spacer(modifier = Modifier.height(DemoLayout.gap))

        DemoCard(containerColor = OsWarningBackground) {
            Text(
                "Add your own App ID, then rebuild to fully test all functionality.",
                style = MaterialTheme.typography.bodySmall,
                color = OsGrey700,
            )
            Text(
                "Get your keys at onesignal.com",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = OsPrimary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onGetKeysClick() },
            )
        }

        Spacer(modifier = Modifier.height(DemoLayout.gap))

        DemoCard {
            ToggleRow(
                label = "Consent Required",
                description = "Require consent before SDK processes data",
                checked = consentRequired,
                onCheckedChange = onConsentRequiredChange,
                testTag = "consent_required_toggle",
                contentDescription = "Consent required",
            )
            if (consentRequired) {
                HorizontalDivider(color = OsDivider, modifier = Modifier.padding(vertical = DemoLayout.gap))
                ToggleRow(
                    label = "Privacy Consent",
                    description = "Consent given for data collection",
                    checked = privacyConsentGiven,
                    onCheckedChange = onConsentChange,
                    testTag = "privacy_consent_toggle",
                    contentDescription = "Privacy consent",
                )
            }
        }
    }
}

@Composable
fun UserSection(
    externalUserId: String?,
    useIdentityVerification: Boolean,
    onUseIdentityVerificationChange: (Boolean) -> Unit,
    onLogin: (String, String?) -> Unit,
    onLogout: () -> Unit,
    onUpdateJwt: (String, String) -> Unit,
    isLoading: Boolean = false,
) {
    val isLoggedIn = !externalUserId.isNullOrEmpty()
    var loginOpen by remember { mutableStateOf(false) }
    var updateJwtOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "User", sectionKey = "user") {
            ToggleRow(
                label = "Identity Verification",
                description = "Use external_id for API calls",
                checked = useIdentityVerification,
                onCheckedChange = onUseIdentityVerificationChange,
                testTag = "identity_verification_toggle",
                contentDescription = "Identity verification",
            )
            HorizontalDivider(color = OsDivider, modifier = Modifier.padding(vertical = DemoLayout.gap))
            CardKvRow(
                label = "Status",
                value = if (isLoggedIn) "Logged In" else "Anonymous",
                valueTestTag = "user_status_value",
                valueColor = if (isLoggedIn) OsSuccess else OsGrey600,
                monospaceValue = true,
            )
            HorizontalDivider(color = OsDivider, modifier = Modifier.padding(vertical = DemoLayout.gap))
            CardKvRow(
                label = "External ID",
                value = externalUserId ?: "—",
                valueTestTag = "user_external_id_value",
            )
        }

        Spacer(modifier = Modifier.height(DemoLayout.gap))

        PrimaryButton(
            text = if (isLoggedIn) "SWITCH USER" else "LOGIN USER",
            onClick = { loginOpen = true },
            enabled = !isLoading,
            testTag = "login_user_button",
        )

        if (isLoggedIn) {
            OutlineButton(
                text = "LOGOUT USER",
                onClick = onLogout,
                enabled = !isLoading,
                testTag = "logout_user_button",
            )
        }

        OutlineButton(
            text = "UPDATE USER JWT",
            onClick = { updateJwtOpen = true },
            testTag = "update_user_jwt_button",
        )
    }

    if (loginOpen) {
        LoginDialog(
            onDismiss = { loginOpen = false },
            onConfirm = { userId, jwt ->
                onLogin(userId, jwt)
                loginOpen = false
            },
        )
    }

    if (updateJwtOpen) {
        PairInputDialog(
            title = "Update User JWT",
            keyLabel = "External User Id",
            valueLabel = "JWT Token",
            onDismiss = { updateJwtOpen = false },
            onConfirm = { externalId, token ->
                onUpdateJwt(externalId, token)
                updateJwtOpen = false
            },
            keyTestTag = "update_jwt_external_id_input",
            valueTestTag = "update_jwt_token_input",
        )
    }
}

@Composable
fun PushSection(
    pushSubscriptionId: String?,
    pushEnabled: Boolean,
    hasPermission: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPromptPush: () -> Unit,
    onInfoClick: () -> Unit,
) {
    DemoSection {
        SectionCard(title = "Push", sectionKey = "push", onInfoClick = onInfoClick) {
            CardKvRow(
                label = "Push ID",
                value = pushSubscriptionId.takeUnless { it.isNullOrEmpty() } ?: "—",
                valueTestTag = "push_id_value",
                valueColor = if (!pushSubscriptionId.isNullOrEmpty()) OsGrey600 else OsGrey600.copy(alpha = 0.5f),
            )
            HorizontalDivider(color = OsDivider, modifier = Modifier.padding(vertical = DemoLayout.gap))
            ToggleRow(
                label = "Enabled",
                checked = pushEnabled,
                onCheckedChange = onEnabledChange,
                enabled = hasPermission,
                testTag = "push_enabled_toggle",
                contentDescription = "Push enabled",
            )
        }

        if (!hasPermission) {
            Spacer(modifier = Modifier.height(DemoLayout.gap))
            PrimaryButton(text = "PROMPT PUSH", onClick = onPromptPush, testTag = "prompt_push_button")
        }
    }
}

@Composable
fun SendPushSection(
    onSimpleClick: () -> Unit,
    onImageClick: () -> Unit,
    onSoundClick: () -> Unit,
    onCustomNotification: (String, String) -> Unit,
    onClearAllClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    var customOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(
            title = "Send Push Notification",
            showCard = false,
            sectionKey = "send_push",
            onInfoClick = onInfoClick,
        ) {
            PrimaryButton(text = "SIMPLE", onClick = onSimpleClick, testTag = "send_simple_button")
            PrimaryButton(text = "WITH IMAGE", onClick = onImageClick, testTag = "send_image_button")
            PrimaryButton(text = "WITH SOUND", onClick = onSoundClick, testTag = "send_sound_button")
            PrimaryButton(text = "CUSTOM", onClick = { customOpen = true }, testTag = "send_custom_button")
            OutlineButton(text = "CLEAR ALL", onClick = onClearAllClick, testTag = "clear_all_button")
        }
    }

    if (customOpen) {
        CustomNotificationDialog(
            onDismiss = { customOpen = false },
            onConfirm = { title, body ->
                onCustomNotification(title, body)
                customOpen = false
            },
        )
    }
}

@Composable
fun InAppMessagingSection(
    isPaused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
) {
    DemoSection {
        SectionCard(title = "In-App Messaging", sectionKey = "iam", onInfoClick = onInfoClick) {
            ToggleRow(
                label = "Pause In-App Messages",
                description = "Toggle in-app message display",
                checked = isPaused,
                onCheckedChange = onPausedChange,
                testTag = "pause_iam_toggle",
                contentDescription = "Pause in-app messages",
            )
        }
    }
}

@Composable
fun SendInAppMessageSection(
    onSendMessage: (InAppMessageType) -> Unit,
    onInfoClick: () -> Unit,
) {
    DemoSection {
        SectionCard(
            title = "Send In-App Message",
            showCard = false,
            sectionKey = "send_iam",
            onInfoClick = onInfoClick,
        ) {
            InAppMessageType.entries.forEach { type ->
                val typeTag = type.name.lowercase()
                PrimaryButton(
                    text = type.title,
                    onClick = { onSendMessage(type) },
                    testTag = "send_iam_${typeTag}_button",
                )
            }
        }
    }
}

@Composable
fun AliasesSection(
    aliases: List<Pair<String, String>>,
    onAdd: (String, String) -> Unit,
    onAddMultiple: (List<Pair<String, String>>) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    var addOpen by remember { mutableStateOf(false) }
    var addMultipleOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "Aliases", sectionKey = "aliases", onInfoClick = onInfoClick) {
            PairList(
                items = aliases,
                emptyText = "No aliases added",
                sectionKey = "aliases",
                loading = loading,
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "ADD ALIAS", onClick = { addOpen = true }, testTag = "add_alias_button")
        PrimaryButton(
            text = "ADD MULTIPLE ALIASES",
            onClick = { addMultipleOpen = true },
            testTag = "add_multiple_aliases_button",
        )
    }

    if (addOpen) {
        PairInputDialog(
            title = "Add Alias",
            keyLabel = "Label",
            valueLabel = "ID",
            onDismiss = { addOpen = false },
            onConfirm = { key, value ->
                onAdd(key, value)
                addOpen = false
            },
            keyTestTag = "alias_label_input",
            valueTestTag = "alias_id_input",
        )
    }

    if (addMultipleOpen) {
        MultiPairInputDialog(
            title = "Add Multiple Aliases",
            keyLabel = "Label",
            valueLabel = "ID",
            onDismiss = { addMultipleOpen = false },
            onConfirm = { pairs ->
                onAddMultiple(pairs)
                addMultipleOpen = false
            },
        )
    }
}

@Composable
fun EmailsSection(
    emails: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    var addOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "Emails", sectionKey = "emails", onInfoClick = onInfoClick) {
            CollapsibleSingleList(
                items = emails,
                emptyText = "No emails added",
                onDelete = onRemove,
                sectionKey = "emails",
                loading = loading,
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "ADD EMAIL", onClick = { addOpen = true }, testTag = "add_email_button")
    }

    if (addOpen) {
        SingleInputDialog(
            title = "Add Email",
            label = "Email",
            onDismiss = { addOpen = false },
            onConfirm = { email ->
                onAdd(email)
                addOpen = false
            },
            inputTestTag = "email_input",
        )
    }
}

@Composable
fun SmsSection(
    smsNumbers: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    var addOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "SMS", sectionKey = "sms", onInfoClick = onInfoClick) {
            CollapsibleSingleList(
                items = smsNumbers,
                emptyText = "No SMS added",
                onDelete = onRemove,
                sectionKey = "sms",
                loading = loading,
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "ADD SMS", onClick = { addOpen = true }, testTag = "add_sms_button")
    }

    if (addOpen) {
        SingleInputDialog(
            title = "Add SMS",
            label = "Phone Number",
            onDismiss = { addOpen = false },
            onConfirm = { sms ->
                onAdd(sms)
                addOpen = false
            },
            inputTestTag = "sms_input",
        )
    }
}

@Composable
fun TagsSection(
    tags: List<Pair<String, String>>,
    onAdd: (String, String) -> Unit,
    onAddMultiple: (List<Pair<String, String>>) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveSelected: (List<String>) -> Unit,
    onInfoClick: () -> Unit,
    loading: Boolean = false,
) {
    var addOpen by remember { mutableStateOf(false) }
    var addMultipleOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "Tags", sectionKey = "tags", onInfoClick = onInfoClick) {
            PairList(
                items = tags,
                emptyText = "No tags added",
                onDelete = onRemove,
                sectionKey = "tags",
                loading = loading,
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "ADD TAG", onClick = { addOpen = true }, testTag = "add_tag_button")
        PrimaryButton(
            text = "ADD MULTIPLE TAGS",
            onClick = { addMultipleOpen = true },
            testTag = "add_multiple_tags_button",
        )

        if (tags.isNotEmpty()) {
            DestructiveButton(
                text = "REMOVE TAGS",
                onClick = { removeOpen = true },
                testTag = "remove_tags_button",
            )
        }
    }

    if (addOpen) {
        PairInputDialog(
            title = "Add Tag",
            onDismiss = { addOpen = false },
            onConfirm = { key, value ->
                onAdd(key, value)
                addOpen = false
            },
            keyTestTag = "tag_key_input",
            valueTestTag = "tag_value_input",
        )
    }

    if (addMultipleOpen) {
        MultiPairInputDialog(
            title = "Add Multiple Tags",
            onDismiss = { addMultipleOpen = false },
            onConfirm = { pairs ->
                onAddMultiple(pairs)
                addMultipleOpen = false
            },
        )
    }

    if (removeOpen && tags.isNotEmpty()) {
        MultiSelectRemoveDialog(
            title = "Remove Tags",
            items = tags,
            onDismiss = { removeOpen = false },
            onConfirm = { keys ->
                onRemoveSelected(keys.toList())
                removeOpen = false
            },
        )
    }
}

@Composable
fun OutcomeSection(
    onSendNormal: (String) -> Unit,
    onSendUnique: (String) -> Unit,
    onSendWithValue: (String, Float) -> Unit,
    onInfoClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current

    DemoSection {
        SectionCard(
            title = "Outcome Events",
            showCard = false,
            sectionKey = "outcomes",
            onInfoClick = onInfoClick,
        ) {
            PrimaryButton(text = "SEND OUTCOME", onClick = { open = true }, testTag = "send_outcome_button")
        }
    }

    if (open) {
        OutcomeDialog(
            onDismiss = { open = false },
            onSendNormal = { name ->
                onSendNormal(name)
                snackbar.show("Outcome sent: $name")
                open = false
            },
            onSendUnique = { name ->
                onSendUnique(name)
                snackbar.show("Unique outcome sent: $name")
                open = false
            },
            onSendWithValue = { name, value ->
                onSendWithValue(name, value)
                snackbar.show("Outcome sent: $name = $value")
                open = false
            },
        )
    }
}

@Composable
fun TriggersSection(
    triggers: List<Pair<String, String>>,
    onAdd: (String, String) -> Unit,
    onAddMultiple: (List<Pair<String, String>>) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveSelected: (List<String>) -> Unit,
    onClearAll: () -> Unit,
    onInfoClick: () -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    var addMultipleOpen by remember { mutableStateOf(false) }
    var removeOpen by remember { mutableStateOf(false) }

    DemoSection {
        SectionCard(title = "Triggers", sectionKey = "triggers", onInfoClick = onInfoClick) {
            PairList(
                items = triggers,
                emptyText = "No triggers added",
                onDelete = onRemove,
                sectionKey = "triggers",
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "ADD TRIGGER", onClick = { addOpen = true }, testTag = "add_trigger_button")
        PrimaryButton(
            text = "ADD MULTIPLE TRIGGERS",
            onClick = { addMultipleOpen = true },
            testTag = "add_multiple_triggers_button",
        )

        if (triggers.isNotEmpty()) {
            DestructiveButton(
                text = "REMOVE TRIGGERS",
                onClick = { removeOpen = true },
                testTag = "remove_triggers_button",
            )
            DestructiveButton(
                text = "CLEAR ALL TRIGGERS",
                onClick = onClearAll,
                testTag = "clear_triggers_button",
            )
        }
    }

    if (addOpen) {
        PairInputDialog(
            title = "Add Trigger",
            onDismiss = { addOpen = false },
            onConfirm = { key, value ->
                onAdd(key, value)
                addOpen = false
            },
            keyTestTag = "trigger_key_input",
            valueTestTag = "trigger_value_input",
        )
    }

    if (addMultipleOpen) {
        MultiPairInputDialog(
            title = "Add Multiple Triggers",
            onDismiss = { addMultipleOpen = false },
            onConfirm = { pairs ->
                onAddMultiple(pairs)
                addMultipleOpen = false
            },
        )
    }

    if (removeOpen && triggers.isNotEmpty()) {
        MultiSelectRemoveDialog(
            title = "Remove Triggers",
            items = triggers,
            onDismiss = { removeOpen = false },
            onConfirm = { keys ->
                onRemoveSelected(keys.toList())
                removeOpen = false
            },
        )
    }
}

@Composable
fun CustomEventsSection(
    onTrackEvent: (String, Map<String, Any>?) -> Unit,
    onInfoClick: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current

    DemoSection {
        SectionCard(
            title = "Custom Events",
            showCard = false,
            sectionKey = "custom_events",
            onInfoClick = onInfoClick,
        ) {
            PrimaryButton(text = "TRACK EVENT", onClick = { open = true }, testTag = "track_event_button")
        }
    }

    if (open) {
        TrackEventDialog(
            onDismiss = { open = false },
            onConfirm = { name, properties ->
                onTrackEvent(name, properties)
                snackbar.show("Event tracked: $name")
                open = false
            },
        )
    }
}

@Composable
fun LocationSection(
    locationShared: Boolean,
    onLocationSharedChange: (Boolean) -> Unit,
    onCheckLocationShared: () -> Boolean,
    onPromptLocation: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val snackbar = LocalSnackbarController.current

    DemoSection {
        SectionCard(title = "Location", sectionKey = "location", onInfoClick = onInfoClick) {
            ToggleRow(
                label = "Location Shared",
                description = "Share device location with OneSignal",
                checked = locationShared,
                onCheckedChange = onLocationSharedChange,
                testTag = "location_shared_toggle",
                contentDescription = "Location shared",
            )
        }
        Spacer(modifier = Modifier.height(DemoLayout.gap))
        PrimaryButton(text = "PROMPT LOCATION", onClick = onPromptLocation, testTag = "prompt_location_button")
        PrimaryButton(
            text = "CHECK LOCATION",
            onClick = {
                val shared = onCheckLocationShared()
                snackbar.show("Location shared: $shared")
            },
            testTag = "check_location_button",
        )
    }
}
