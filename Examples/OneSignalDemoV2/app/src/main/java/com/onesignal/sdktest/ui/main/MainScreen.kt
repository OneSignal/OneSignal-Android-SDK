package com.onesignal.sdktest.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onesignal.sdktest.data.model.InAppMessageType
import com.onesignal.sdktest.data.model.NotificationType
import com.onesignal.sdktest.ui.components.*
import com.onesignal.sdktest.ui.secondary.SecondaryActivity
import com.onesignal.sdktest.ui.theme.*
import com.onesignal.sdktest.util.TooltipHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    
    // Observe all LiveData as State
    val appId by viewModel.appId.observeAsState("")
    val pushSubscriptionId by viewModel.pushSubscriptionId.observeAsState()
    val pushEnabled by viewModel.pushEnabled.observeAsState(false)
    val hasNotificationPermission by viewModel.hasNotificationPermission.observeAsState(false)
    val privacyConsentGiven by viewModel.privacyConsentGiven.observeAsState(false)
    val externalUserId by viewModel.externalUserId.observeAsState()
    val aliases by viewModel.aliases.observeAsState(emptyList())
    val emails by viewModel.emails.observeAsState(emptyList())
    val smsNumbers by viewModel.smsNumbers.observeAsState(emptyList())
    val tags by viewModel.tags.observeAsState(emptyList())
    val triggers by viewModel.triggers.observeAsState(emptyList())
    val inAppMessagesPaused by viewModel.inAppMessagesPaused.observeAsState(true)
    val locationShared by viewModel.locationShared.observeAsState(false)
    val isLoading by viewModel.isLoading.observeAsState(false)
    val toastMessage by viewModel.toastMessage.observeAsState()
    
    // Dialog states
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAddAliasDialog by remember { mutableStateOf(false) }
    var showAddMultipleAliasDialog by remember { mutableStateOf(false) }
    var showRemoveAliasesDialog by remember { mutableStateOf(false) }
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
    
    // Toast
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            // In a real app, you'd use a Snackbar or custom toast composable
            viewModel.clearToast()
        }
    }
    
    // Auto prompt for notification permission
    LaunchedEffect(Unit) {
        viewModel.promptPush()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "OneSignal",
                            color = Color.White
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OneSignalRed
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(LightBackground)
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                
                // === APP SECTION ===
                AppSection(
                    appId = appId,
                    privacyConsentGiven = privacyConsentGiven,
                    onConsentChange = { viewModel.setPrivacyConsent(it) },
                    externalUserId = externalUserId,
                    onLoginClick = { showLoginDialog = true },
                    onLogoutClick = { viewModel.logoutUser() },
                    onGetKeysClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://onesignal.com")))
                    }
                )
                
                // === PUSH SECTION ===
                PushSection(
                    pushSubscriptionId = pushSubscriptionId,
                    pushEnabled = pushEnabled,
                    hasPermission = hasNotificationPermission,
                    onEnabledChange = { viewModel.setPushEnabled(it) },
                    onPromptPush = { viewModel.promptPush() },
                    onInfoClick = { showTooltipDialog = "push" }
                )
                
                // === SEND PUSH NOTIFICATION SECTION ===
                SendPushSection(
                    onSimpleClick = { viewModel.sendNotification(NotificationType.SIMPLE) },
                    onImageClick = { viewModel.sendNotification(NotificationType.WITH_IMAGE) },
                    onCustomClick = { showCustomNotificationDialog = true },
                    onInfoClick = { showTooltipDialog = "sendPushNotification" }
                )
                
                // === IN-APP MESSAGING SECTION ===
                InAppMessagingSection(
                    isPaused = inAppMessagesPaused,
                    onPausedChange = { viewModel.setInAppMessagesPaused(it) },
                    onInfoClick = { showTooltipDialog = "inAppMessaging" }
                )
                
                // === SEND IN-APP MESSAGE SECTION ===
                SendInAppMessageSection(
                    onSendMessage = { type ->
                        viewModel.sendInAppMessage(type.triggerKey, type.triggerValue)
                    },
                    onInfoClick = { showTooltipDialog = "sendInAppMessage" }
                )
                
                // === ALIASES SECTION ===
                AliasesSection(
                    aliases = aliases,
                    onAddClick = { showAddAliasDialog = true },
                    onAddMultipleClick = { showAddMultipleAliasDialog = true },
                    onRemove = { viewModel.removeAlias(it) },
                    onRemoveSelected = { showRemoveAliasesDialog = true },
                    onInfoClick = { showTooltipDialog = "aliases" }
                )
                
                // === EMAILS SECTION ===
                EmailsSection(
                    emails = emails,
                    onAddClick = { showAddEmailDialog = true },
                    onRemove = { viewModel.removeEmail(it) },
                    onInfoClick = { showTooltipDialog = "emails" }
                )
                
                // === SMS SECTION ===
                SmsSection(
                    smsNumbers = smsNumbers,
                    onAddClick = { showAddSmsDialog = true },
                    onRemove = { viewModel.removeSms(it) },
                    onInfoClick = { showTooltipDialog = "sms" }
                )
                
                // === TAGS SECTION ===
                TagsSection(
                    tags = tags,
                    onAddClick = { showAddTagDialog = true },
                    onAddMultipleClick = { showAddMultipleTagDialog = true },
                    onRemove = { viewModel.removeTag(it) },
                    onRemoveSelected = { showRemoveTagsDialog = true },
                    onInfoClick = { showTooltipDialog = "tags" }
                )
                
                // === OUTCOME EVENTS SECTION ===
                OutcomeSection(
                    onSendOutcome = { showOutcomeDialog = true },
                    onInfoClick = { showTooltipDialog = "outcomes" }
                )
                
                // === TRIGGERS SECTION ===
                TriggersSection(
                    triggers = triggers,
                    onAddClick = { showAddTriggerDialog = true },
                    onAddMultipleClick = { showAddMultipleTriggerDialog = true },
                    onRemove = { viewModel.removeTrigger(it) },
                    onRemoveSelected = { showRemoveTriggersDialog = true },
                    onClearAll = { viewModel.clearTriggers() },
                    onInfoClick = { showTooltipDialog = "triggers" }
                )
                
                // === TRACK EVENT SECTION ===
                TrackEventSection(
                    onTrackClick = { showTrackEventDialog = true },
                    onInfoClick = { showTooltipDialog = "trackEvent" }
                )
                
                // === LOCATION SECTION ===
                LocationSection(
                    locationShared = locationShared,
                    onLocationSharedChange = { viewModel.setLocationShared(it) },
                    onPromptLocation = { viewModel.promptLocation() },
                    onInfoClick = { showTooltipDialog = "location" }
                )
                
                // === NEXT ACTIVITY BUTTON ===
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    PrimaryButton(
                        text = "NEXT ACTIVITY",
                        onClick = {
                            context.startActivity(Intent(context, SecondaryActivity::class.java))
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        
        // Loading overlay
        LoadingOverlay(isLoading = isLoading)
    }
    
    // === DIALOGS ===
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onConfirm = { userId ->
                viewModel.loginUser(userId)
                showLoginDialog = false
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
            }
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
    
    if (showRemoveAliasesDialog && aliases.isNotEmpty()) {
        MultiSelectRemoveDialog(
            title = "Remove Aliases",
            items = aliases,
            onDismiss = { showRemoveAliasesDialog = false },
            onConfirm = { keys ->
                viewModel.removeSelectedAliases(keys)
                showRemoveAliasesDialog = false
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
            }
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
            }
        )
    }
    
    if (showAddTagDialog) {
        PairInputDialog(
            title = "Add Tag",
            onDismiss = { showAddTagDialog = false },
            onConfirm = { key, value ->
                viewModel.addTag(key, value)
                showAddTagDialog = false
            }
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
            }
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
