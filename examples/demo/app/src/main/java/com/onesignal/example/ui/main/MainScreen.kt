package com.onesignal.example.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.onesignal.example.ui.components.DemoAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.onesignal.example.R
import com.onesignal.example.data.model.NotificationType
import com.onesignal.example.ui.components.CustomNotificationDialog
import com.onesignal.example.ui.components.LoginDialog
import com.onesignal.example.ui.components.MultiPairInputDialog
import com.onesignal.example.ui.components.MultiSelectRemoveDialog
import com.onesignal.example.ui.components.OutcomeDialog
import com.onesignal.example.ui.components.PairInputDialog
import com.onesignal.example.ui.components.PrimaryButton
import com.onesignal.example.ui.components.SingleInputDialog
import com.onesignal.example.ui.components.TooltipDialog
import com.onesignal.example.ui.components.TrackEventDialog
import com.onesignal.example.ui.secondary.SecondaryActivity
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.util.TooltipHelper
import com.onesignal.example.util.maskValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val appId by viewModel.appId.observeAsState("")
    val pushSubscriptionId by viewModel.pushSubscriptionId.observeAsState()
    val pushEnabled by viewModel.pushEnabled.observeAsState(false)
    val hasNotificationPermission by viewModel.hasNotificationPermission.observeAsState(false)
    val consentRequired by viewModel.consentRequired.observeAsState(false)
    val privacyConsentGiven by viewModel.privacyConsentGiven.observeAsState(false)
    val externalUserId by viewModel.externalUserId.observeAsState()
    val useIdentityVerification by viewModel.useIdentityVerification.observeAsState(false)
    val aliases by viewModel.aliases.observeAsState(emptyList())
    val emails by viewModel.emails.observeAsState(emptyList())
    val smsNumbers by viewModel.smsNumbers.observeAsState(emptyList())
    val tags by viewModel.tags.observeAsState(emptyList())
    val triggers by viewModel.triggers.observeAsState(emptyList())
    val inAppMessagesPaused by viewModel.inAppMessagesPaused.observeAsState(false)
    val locationShared by viewModel.locationShared.observeAsState(false)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val toastMessage by viewModel.toastMessage.observeAsState()

    // Dialog states
    var showLoginDialog by remember { mutableStateOf(false) }
    var showUpdateJwtDialog by remember { mutableStateOf(false) }
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var showAddMultipleAliasDialog by remember { mutableStateOf(false) }
    var showAddEmailDialog by remember { mutableStateOf(false) }
    var showAddSmsDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAddMultipleTagDialog by remember { mutableStateOf(false) }
    var showRemoveTagsDialog by remember { mutableStateOf(false) }
    var showAddTriggerDialog by remember { mutableStateOf(false) }
    var showAddMultipleTriggerDialog by remember { mutableStateOf(false) }
    var showRemoveTriggersDialog by remember { mutableStateOf(false) }
    var showOutcomeDialog by remember { mutableStateOf(false) }
    var showTrackEventDialog by remember { mutableStateOf(false) }
    var showCustomNotificationDialog by remember { mutableStateOf(false) }
    var showTooltipDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.promptPush()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Bridge the LiveData toast queue to a Compose Snackbar so the snackbar can be
    // targeted with `Modifier.testTag("snackbar_toast")` in E2E (matches Capacitor).
    LaunchedEffect(toastMessage) {
        val message = toastMessage
        if (!message.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            DemoAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.onesignal_rectangle),
                            contentDescription = "OneSignal Logo",
                            modifier = Modifier.height(DemoLayout.logoHeight),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Android",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.testTag("snackbar_toast"),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = data.visuals.message,
                        color = Color.White,
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .testTag("main_scroll_view")
        ) {
            AppSection(
                appId = maskValue(appId),
                consentRequired = consentRequired,
                onConsentRequiredChange = { viewModel.setConsentRequired(it) },
                privacyConsentGiven = privacyConsentGiven,
                onConsentChange = { viewModel.setPrivacyConsent(it) },
                onGetKeysClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://onesignal.com")))
                }
            )

            UserSection(
                externalUserId = externalUserId,
                useIdentityVerification = useIdentityVerification,
                onUseIdentityVerificationChange = { viewModel.setUseIdentityVerification(it) },
                onLoginClick = { showLoginDialog = true },
                onLogoutClick = { viewModel.logoutUser() },
                onUpdateJwtClick = { showUpdateJwtDialog = true },
                isLoading = isLoading
            )

            PushSection(
                pushSubscriptionId = pushSubscriptionId?.let { maskValue(it) },
                pushEnabled = pushEnabled,
                hasPermission = hasNotificationPermission,
                onEnabledChange = { viewModel.setPushEnabled(it) },
                onPromptPush = { viewModel.promptPush() },
                onInfoClick = { showTooltipDialog = "push" }
            )

            SendPushSection(
                onSimpleClick = { viewModel.sendNotification(NotificationType.SIMPLE) },
                onImageClick = { viewModel.sendNotification(NotificationType.WITH_IMAGE) },
                onSoundClick = { viewModel.sendNotification(NotificationType.WITH_SOUND) },
                onCustomClick = { showCustomNotificationDialog = true },
                onClearAllClick = { viewModel.clearAllNotifications() },
                onInfoClick = { showTooltipDialog = "sendPushNotification" }
            )

            InAppMessagingSection(
                isPaused = inAppMessagesPaused,
                onPausedChange = { viewModel.setInAppMessagesPaused(it) },
                onInfoClick = { showTooltipDialog = "inAppMessaging" }
            )

            SendInAppMessageSection(
                onSendMessage = { type ->
                    viewModel.sendInAppMessage(type.title, type.triggerKey, type.triggerValue)
                },
                onInfoClick = { showTooltipDialog = "sendInAppMessage" }
            )

            AliasesSection(
                aliases = aliases,
                onAddClick = { showAddAliasDialog = true },
                onAddMultipleClick = { showAddMultipleAliasDialog = true },
                onInfoClick = { showTooltipDialog = "aliases" },
                loading = isLoading
            )

            EmailsSection(
                emails = emails,
                onAddClick = { showAddEmailDialog = true },
                onRemove = { viewModel.removeEmail(it) },
                onInfoClick = { showTooltipDialog = "emails" },
                loading = isLoading
            )

            SmsSection(
                smsNumbers = smsNumbers,
                onAddClick = { showAddSmsDialog = true },
                onRemove = { viewModel.removeSms(it) },
                onInfoClick = { showTooltipDialog = "sms" },
                loading = isLoading
            )

            TagsSection(
                tags = tags,
                onAddClick = { showAddTagDialog = true },
                onAddMultipleClick = { showAddMultipleTagDialog = true },
                onRemove = { viewModel.removeTag(it) },
                onRemoveSelected = { showRemoveTagsDialog = true },
                onInfoClick = { showTooltipDialog = "tags" },
                loading = isLoading
            )

            OutcomeSection(
                onSendOutcome = { showOutcomeDialog = true },
                onInfoClick = { showTooltipDialog = "outcomes" }
            )

            TriggersSection(
                triggers = triggers,
                onAddClick = { showAddTriggerDialog = true },
                onAddMultipleClick = { showAddMultipleTriggerDialog = true },
                onRemove = { viewModel.removeTrigger(it) },
                onRemoveSelected = { showRemoveTriggersDialog = true },
                onClearAll = { viewModel.clearTriggers() },
                onInfoClick = { showTooltipDialog = "triggers" }
            )

            CustomEventsSection(
                onTrackClick = { showTrackEventDialog = true },
                onInfoClick = { showTooltipDialog = "customEvents" }
            )

            LocationSection(
                locationShared = locationShared,
                onLocationSharedChange = { viewModel.setLocationShared(it) },
                onPromptLocation = { viewModel.promptLocation() },
                onInfoClick = { showTooltipDialog = "location" }
            )

            PrimaryButton(
                text = "NEXT SCREEN",
                onClick = {
                    context.startActivity(Intent(context, SecondaryActivity::class.java))
                },
                testTag = "next_screen_button"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // === DIALOGS ===
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onConfirm = { userId, jwt ->
                viewModel.loginUser(userId, jwt)
                showLoginDialog = false
            }
        )
    }

    if (showUpdateJwtDialog) {
        PairInputDialog(
            title = "Update User JWT",
            keyLabel = "External User Id",
            valueLabel = "JWT Token",
            onDismiss = { showUpdateJwtDialog = false },
            onConfirm = { externalId, token ->
                viewModel.updateUserJwt(externalId, token)
                showUpdateJwtDialog = false
            }
        )
    }

    if (showAddAliasDialog) {
        PairInputDialog(
            title = "Add Alias",
            keyLabel = "Label",
            valueLabel = "ID",
            onDismiss = { showAddAliasDialog = false },
            onConfirm = { key, value ->
                viewModel.addAlias(key, value)
                showAddAliasDialog = false
            },
            keyTestTag = "alias_label_input",
            valueTestTag = "alias_id_input"
        )
    }

    if (showAddMultipleAliasDialog) {
        MultiPairInputDialog(
            title = "Add Multiple Aliases",
            keyLabel = "Label",
            valueLabel = "ID",
            onDismiss = { showAddMultipleAliasDialog = false },
            onConfirm = { pairs ->
                viewModel.addAliases(pairs)
                showAddMultipleAliasDialog = false
            }
        )
    }

    if (showAddEmailDialog) {
        SingleInputDialog(
            title = "Add Email",
            label = "Email",
            onDismiss = { showAddEmailDialog = false },
            onConfirm = { email ->
                viewModel.addEmail(email)
                showAddEmailDialog = false
            },
            inputTestTag = "email_input"
        )
    }

    if (showAddSmsDialog) {
        SingleInputDialog(
            title = "Add SMS",
            label = "Phone Number",
            onDismiss = { showAddSmsDialog = false },
            onConfirm = { sms ->
                viewModel.addSms(sms)
                showAddSmsDialog = false
            },
            inputTestTag = "sms_input"
        )
    }

    if (showAddTagDialog) {
        PairInputDialog(
            title = "Add Tag",
            onDismiss = { showAddTagDialog = false },
            onConfirm = { key, value ->
                viewModel.addTag(key, value)
                showAddTagDialog = false
            },
            keyTestTag = "tag_key_input",
            valueTestTag = "tag_value_input"
        )
    }

    if (showAddMultipleTagDialog) {
        MultiPairInputDialog(
            title = "Add Multiple Tags",
            onDismiss = { showAddMultipleTagDialog = false },
            onConfirm = { pairs ->
                viewModel.addTags(pairs)
                showAddMultipleTagDialog = false
            }
        )
    }

    if (showRemoveTagsDialog && tags.isNotEmpty()) {
        MultiSelectRemoveDialog(
            title = "Remove Tags",
            items = tags,
            onDismiss = { showRemoveTagsDialog = false },
            onConfirm = { keys ->
                viewModel.removeSelectedTags(keys)
                showRemoveTagsDialog = false
            }
        )
    }

    if (showAddTriggerDialog) {
        PairInputDialog(
            title = "Add Trigger",
            onDismiss = { showAddTriggerDialog = false },
            onConfirm = { key, value ->
                viewModel.addTrigger(key, value)
                showAddTriggerDialog = false
            },
            keyTestTag = "trigger_key_input",
            valueTestTag = "trigger_value_input"
        )
    }

    if (showAddMultipleTriggerDialog) {
        MultiPairInputDialog(
            title = "Add Multiple Triggers",
            onDismiss = { showAddMultipleTriggerDialog = false },
            onConfirm = { pairs ->
                viewModel.addTriggers(pairs)
                showAddMultipleTriggerDialog = false
            }
        )
    }

    if (showRemoveTriggersDialog && triggers.isNotEmpty()) {
        MultiSelectRemoveDialog(
            title = "Remove Triggers",
            items = triggers,
            onDismiss = { showRemoveTriggersDialog = false },
            onConfirm = { keys ->
                viewModel.removeSelectedTriggers(keys)
                showRemoveTriggersDialog = false
            }
        )
    }

    if (showOutcomeDialog) {
        OutcomeDialog(
            onDismiss = { showOutcomeDialog = false },
            onSendNormal = { name ->
                viewModel.sendOutcome(name)
                showOutcomeDialog = false
            },
            onSendUnique = { name ->
                viewModel.sendUniqueOutcome(name)
                showOutcomeDialog = false
            },
            onSendWithValue = { name, value ->
                viewModel.sendOutcomeWithValue(name, value)
                showOutcomeDialog = false
            }
        )
    }

    if (showTrackEventDialog) {
        TrackEventDialog(
            onDismiss = { showTrackEventDialog = false },
            onConfirm = { name, properties ->
                viewModel.trackEvent(name, properties)
                showTrackEventDialog = false
            }
        )
    }

    if (showCustomNotificationDialog) {
        CustomNotificationDialog(
            onDismiss = { showCustomNotificationDialog = false },
            onConfirm = { title, body ->
                viewModel.sendCustomNotification(title, body)
                showCustomNotificationDialog = false
            }
        )
    }

    showTooltipDialog?.let { key ->
        val tooltip = TooltipHelper.getTooltip(key)
        if (tooltip != null) {
            TooltipDialog(
                title = tooltip.title,
                description = tooltip.description,
                options = tooltip.options?.map { it.name to it.description },
                onDismiss = { showTooltipDialog = null }
            )
        }
    }
}
