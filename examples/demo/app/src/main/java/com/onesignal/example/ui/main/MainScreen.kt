package com.onesignal.example.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.onesignal.example.ui.components.LocalSnackbarController
import com.onesignal.example.ui.components.PrimaryButton
import com.onesignal.example.ui.components.TooltipDialog
import com.onesignal.example.ui.components.rememberSnackbarController
import com.onesignal.example.ui.secondary.SecondaryActivity
import com.onesignal.example.ui.theme.DemoLayout
import com.onesignal.example.util.TooltipHelper

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

    var showTooltipDialog by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarController = rememberSnackbarController(snackbarHostState)

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
        CompositionLocalProvider(LocalSnackbarController provides snackbarController) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .testTag("main_scroll_view")
            ) {
                AppSection(
                    appId = appId,
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
                    onLogin = { userId, jwt -> viewModel.loginUser(userId, jwt) },
                    onLogout = { viewModel.logoutUser() },
                    onUpdateJwt = { externalId, token -> viewModel.updateUserJwt(externalId, token) },
                    isLoading = isLoading
                )

                PushSection(
                    pushSubscriptionId = pushSubscriptionId,
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
                    onCustomNotification = { title, body -> viewModel.sendCustomNotification(title, body) },
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
                    onAdd = { key, value -> viewModel.addAlias(key, value) },
                    onAddMultiple = { pairs -> viewModel.addAliases(pairs) },
                    onInfoClick = { showTooltipDialog = "aliases" },
                    loading = isLoading
                )

                EmailsSection(
                    emails = emails,
                    onAdd = { viewModel.addEmail(it) },
                    onRemove = { viewModel.removeEmail(it) },
                    onInfoClick = { showTooltipDialog = "emails" },
                    loading = isLoading
                )

                SmsSection(
                    smsNumbers = smsNumbers,
                    onAdd = { viewModel.addSms(it) },
                    onRemove = { viewModel.removeSms(it) },
                    onInfoClick = { showTooltipDialog = "sms" },
                    loading = isLoading
                )

                TagsSection(
                    tags = tags,
                    onAdd = { key, value -> viewModel.addTag(key, value) },
                    onAddMultiple = { pairs -> viewModel.addTags(pairs) },
                    onRemove = { viewModel.removeTag(it) },
                    onRemoveSelected = { viewModel.removeSelectedTags(it) },
                    onInfoClick = { showTooltipDialog = "tags" },
                    loading = isLoading
                )

                OutcomeSection(
                    onSendNormal = { viewModel.sendOutcome(it) },
                    onSendUnique = { viewModel.sendUniqueOutcome(it) },
                    onSendWithValue = { name, value -> viewModel.sendOutcomeWithValue(name, value) },
                    onInfoClick = { showTooltipDialog = "outcomes" }
                )

                TriggersSection(
                    triggers = triggers,
                    onAdd = { key, value -> viewModel.addTrigger(key, value) },
                    onAddMultiple = { pairs -> viewModel.addTriggers(pairs) },
                    onRemove = { viewModel.removeTrigger(it) },
                    onRemoveSelected = { viewModel.removeSelectedTriggers(it) },
                    onClearAll = { viewModel.clearTriggers() },
                    onInfoClick = { showTooltipDialog = "triggers" }
                )

                CustomEventsSection(
                    onTrackEvent = { name, properties -> viewModel.trackEvent(name, properties) },
                    onInfoClick = { showTooltipDialog = "customEvents" }
                )

                LocationSection(
                    locationShared = locationShared,
                    onLocationSharedChange = { viewModel.setLocationShared(it) },
                    onCheckLocationShared = { viewModel.checkLocationShared() },
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
