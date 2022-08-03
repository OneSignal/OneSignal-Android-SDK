package com.onesignal.onesignal.iam.internal

import android.app.AlertDialog
import com.onesignal.R
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.outcomes.OutcomeEventsController
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.internal.session.ISessionService
import com.onesignal.onesignal.core.internal.user.ISubscriptionChangedHandler
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.onesignal.core.internal.user.subscriptions.PushSubscription
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.subscriptions.ISubscription
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler
import com.onesignal.onesignal.iam.InAppMessageActionUrlType
import com.onesignal.onesignal.iam.internal.backend.InAppBackendController
import com.onesignal.onesignal.iam.internal.data.InAppDataController
import com.onesignal.onesignal.iam.internal.display.IAMDisplayer
import com.onesignal.onesignal.iam.internal.lifecycle.IIAMLifecycleEventHandler
import com.onesignal.onesignal.iam.internal.lifecycle.IIAMLifecycleService
import com.onesignal.onesignal.iam.internal.preferences.InAppPreferencesController
import com.onesignal.onesignal.iam.internal.prompt.InAppMessagePrompt
import com.onesignal.onesignal.iam.internal.triggers.TriggerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.*

interface IIAMPreviewDisplayer

internal interface IIAMDisplayer {
    suspend fun displayMessage(message: InAppMessage)

    suspend fun displayPreviewMessage(previewUUID: String)
}

internal class IAMManager (
    private val _applicationService: IApplicationService,
    private val _deviceService: IDeviceService,
    private val _sessionService: ISessionService,
    private val _httpClient: IHttpClient,
    private val _configModelStore: ConfigModelStore,
    private val _userManager: IUserManager,
    private val _subscriptionManager: ISubscriptionManager,
    private val _outcomeEventsController: OutcomeEventsController,
    private val _prefs: InAppPreferencesController,
    private val _data: InAppDataController,
    private val _backend: InAppBackendController,
    private val _triggerController: TriggerController,
    private val _displayer: IAMDisplayer,
    private val _lifecycle: IIAMLifecycleService,
    private val _time: ITime
        ) : IIAMManager,
            IStartableService,
            ISubscriptionChangedHandler,
            ISingletonModelStoreChangeHandler<ConfigModel>,
            IIAMPreviewDisplayer,
            IIAMDisplayer,
            IIAMLifecycleEventHandler {

    private val _lifecycleCallback: ICallbackProducer<IInAppMessageLifecycleHandler> = CallbackProducer()
    private val _messageClickCallback: ICallbackProducer<IInAppMessageClickHandler> = CallbackProducer()
    private var _paused: Boolean = false

    private var inAppMessageShowing = false

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    private var messages: List<InAppMessage> = listOf()

    // IAMs that have been dismissed by the user
    //   This mean they have already displayed to the user
    private val dismissedMessages: MutableSet<String> = mutableSetOf()

    // IAMs that have been displayed to the user
    //   This means their impression has been successfully posted to our backend and should not be counted again
    private val impressionedMessages: MutableSet<String> = mutableSetOf()

    //   This means their impression has been successfully posted to our backend and should not be counted again
    private val viewedPageIds: MutableSet<String> = mutableSetOf()

    // IAM clicks that have been successfully posted to our backend and should not be counted again
    private val clickedClickIds: MutableSet<String> = mutableSetOf()

    // Ordered IAMs queued to display, includes the message currently displaying, if any.
    private val messageDisplayQueue: MutableList<InAppMessage> = mutableListOf()

    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    private val redisplayedInAppMessages: MutableList<InAppMessage> = mutableListOf()

    private var lastTimeInAppDismissed: Date? = null
    private var currentPrompt: InAppMessagePrompt? = null
//    private var userTagsString: String? = null
//    private var waitForTags = false
//    private var pendingMessageContent: InAppMessageContent? = null

    override var paused: Boolean
        get() = _paused
        set(value) {
            Logging.log(LogLevel.DEBUG, "setPaused(value: $value)")
            _paused = value
        }

    override fun start() {
        _subscriptionManager.subscribe(this)
        _configModelStore.subscribe(this)
        _lifecycle.subscribe(this)

        suspendifyOnThread {
            // get saved IAMs from database
            messages = _data.listInAppMessages()

            // attempt to fetch messages from the backend (if we have the pre-requisite data already)
            fetchMessages()
        }
    }

    override fun onModelUpdated(model: ConfigModel, property: String, oldValue: Any?, newValue: Any?) {
        if (property != ConfigModel::appId.name)
            return

        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onSubscriptionAdded(subscription: ISubscription) {
        if(subscription !is PushSubscription)
            return

        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onSubscriptionRemoved(subscription: ISubscription) { }

    // called when a new push subscription is added, or the app id is updated
    // TODO: What about on session start?
    private suspend fun fetchMessages() {
        val appId = _configModelStore.get().appId
        val subscriptionId = _subscriptionManager.subscriptions.push?.id

        if(subscriptionId == null || appId == null)
            return

        // Retrieve any in app messages that might exist
        val jsonBody = JSONObject()
        jsonBody.put("app_id", appId)

        // TODO: This will be replaced by dedicated iam endpoint once it's available
        val response = _httpClient.post("players/${subscriptionId}/on_session", jsonBody)

        if(response.isSuccess) {
            val jsonResponse = JSONObject(response.payload)

            if (jsonResponse.has("in_app_messages")) {
                val iamMessagesAsJSON = jsonResponse.getJSONArray("in_app_messages")
                // Cache copy for quick cold starts
                _prefs.savedIAMs = iamMessagesAsJSON.toString()

                // TODO: I feel like this might be a "new session" thing, not a "fetch IAMs" thing?
                for (redisplayInAppMessage in messages) {
                    redisplayInAppMessage.isDisplayedInSession = false
                }


                val newMessages = ArrayList<InAppMessage>()
                for (i in 0 until iamMessagesAsJSON.length()) {
                    val messageJson: JSONObject = iamMessagesAsJSON.getJSONObject(i)
                    val message = InAppMessage(messageJson, _time)
                    // Avoid null checks later if IAM already comes with null id
                    if (message.messageId != null) {
                        newMessages.add(message)
                    }
                }

                this.messages = newMessages
                evaluateInAppMessages()
            }
        }
    }

    private suspend fun evaluateInAppMessages() {
        Logging.debug("Starting evaluateInAppMessages")

        for (message in messages) {
            // Make trigger evaluation first, dynamic trigger might change "trigger changed" flag value for redisplay messages
            if (_triggerController.evaluateMessageTriggers(message)) {
                setDataForRedisplay(message)
                if (!dismissedMessages.contains(message.messageId) && !message.isFinished) {
                    queueMessageForDisplay(message)
                }
            }
        }
    }

    /**
     * Part of redisplay logic
     *
     *
     * In order to redisplay an IAM, the following conditions must be satisfied:
     * 1. IAM has redisplay property
     * 2. Time delay between redisplay satisfied
     * 3. Has more redisplays
     * 4. An IAM trigger was satisfied
     *
     *
     * For redisplay, the message need to be removed from the arrays that track the display/impression
     * For click counting, every message has it click id array
     */
    private fun setDataForRedisplay(message: InAppMessage) {
        val messageDismissed: Boolean = dismissedMessages.contains(message.messageId)
        val index: Int = redisplayedInAppMessages.indexOf(message)
        if (messageDismissed && index != -1) {
            val savedIAM: InAppMessage = redisplayedInAppMessages.get(index)
            message.redisplayStats.setDisplayStats(savedIAM.redisplayStats)
            message.isDisplayedInSession = savedIAM.isDisplayedInSession

            val triggerHasChanged: Boolean = hasMessageTriggerChanged(message)
            Logging.debug("setDataForRedisplay: $message triggerHasChanged: $triggerHasChanged")

            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                message.redisplayStats.isDelayTimeSatisfied &&
                message.redisplayStats.shouldDisplayAgain()
            ) {
                Logging.debug("setDataForRedisplay message available for redisplay: " + message.messageId)
                dismissedMessages.remove(message.messageId)
                impressionedMessages.remove(message.messageId)
                // Pages from different IAMs should not impact each other so we can clear the entire
                // list when an IAM is dismissed or we are re-displaying the same one
                viewedPageIds.clear()
                _prefs.viewPageImpressionedIds = viewedPageIds
                message.clearClickIds()
            }
        }
    }

    private fun hasMessageTriggerChanged(message: InAppMessage): Boolean {
        // Message that only have dynamic trigger should display only once per session
        val messageHasOnlyDynamicTrigger: Boolean = _triggerController.messageHasOnlyDynamicTriggers(message)
        if (messageHasOnlyDynamicTrigger) return !message.isDisplayedInSession

        // Message that don't have triggers should display only once per session
        val shouldMessageDisplayInSession =
            !message.isDisplayedInSession && message.triggers.isEmpty()
        return message.isTriggerChanged || shouldMessageDisplayInSession
    }

    /**
     * Message has passed triggers and de-duplication logic.
     * Display message now or add it to the queue to be displayed.
     */
    private suspend fun queueMessageForDisplay(message: InAppMessage) {
        // Make sure no message is ever added to the queue more than once
        if (!messageDisplayQueue.contains(message)) {
            messageDisplayQueue.add(message)
            Logging.debug("In app message with id: " + message.messageId + ", added to the queue")
        }
        attemptToShowInAppMessage()
    }

    private suspend fun attemptToShowInAppMessage() {
        // We need to wait for system conditions to be the correct ones
        if (!_applicationService.waitUntilSystemConditionsAvailable()) {
            Logging.warn("In app message not showing due to system condition not correct")
            return
        }

        Logging.debug("displayFirstIAMOnQueue: $messageDisplayQueue")
        // If there are IAMs in the queue and nothing showing, show first in the queue
        if (messageDisplayQueue.size > 0 && !inAppMessageShowing) {
            Logging.debug("No IAM showing currently, showing first item in the queue!")
            displayMessage(messageDisplayQueue.get(0))
            return
        }
        Logging.debug("In app message is currently showing or there are no IAMs left in the queue! isInAppMessageShowing: $inAppMessageShowing")
    }

    override suspend fun displayMessage(message: InAppMessage) {

        if (_paused) {
            Logging.verbose("In app messaging is currently paused, in app messages will not be shown!")
            return
        }
        inAppMessageShowing = true
        var response = _backend.getIAMData(_configModelStore.get().appId!!, message.messageId, variantIdForMessage(message))

        if(response.isSuccess) {
            try {
                val jsonResponse = JSONObject(response.payload!!)
                val content: InAppMessageContent = parseMessageContentData(jsonResponse, message)
                if (content.contentHtml == null) {
                    Logging.debug("displayMessage:OnSuccess: No HTML retrieved from loadMessageContent")
                    return
                }

                _sessionService.onInAppMessageReceived(message.messageId)
                onMessageWillDisplay(message)
                content.contentHtml = taggedHTMLString(content.contentHtml!!)
                _displayer.showMessageContent(message, content)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        else {
            inAppMessageShowing = false
            try {
                val jsonResponse = JSONObject(response.payload!!)
                val retry =
                    jsonResponse.getBoolean(InAppBackendController.IAM_DATA_RESPONSE_RETRY_KEY)
                if (retry) {
                    // Retry displaying the same IAM
                    // Using the queueMessageForDisplay method follows safety checks to prevent issues
                    // like having 2 IAMs showing at once or duplicate IAMs in the queue
                    queueMessageForDisplay(message)
                } else {
                    messageWasDismissed(message, true)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun displayPreviewMessage(previewUUID: String) {
        inAppMessageShowing = true
        val message = InAppMessage(true, _time)
        val response = _backend.getIAMPreviewData(_configModelStore.get().appId!!, previewUUID)

        if(!response.isSuccess) {
            dismissCurrentMessage(null)
        } else
        {
            try {
                val jsonResponse = JSONObject(response.payload!!)
                val content: InAppMessageContent = parseMessageContentData(jsonResponse, message)
                if (content.contentHtml == null) {
                    Logging.debug("displayPreviewMessage:OnSuccess: No HTML retrieved from loadMessageContent")
                    return
                }
                onMessageWillDisplay(message)
                content.contentHtml = taggedHTMLString(content.contentHtml!!)
                _displayer.showMessageContent(message, content)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun parseMessageContentData(data: JSONObject, message: InAppMessage): InAppMessageContent {
        val content = InAppMessageContent(data)
        message.displayDuration = content.displayDuration!!
        return content
    }

    fun taggedHTMLString(untaggedString: String): String {
        val tagsAsJson = JSONObject(_userManager.tags)
        val tagsString = tagsAsJson.toString()
        val tagsDict: String = tagsString
        val tagScript = LIQUID_TAG_SCRIPT
        return untaggedString + String.format(tagScript, tagsDict)
    }

    private fun variantIdForMessage(message: InAppMessage): String? {
        // TODO: Language context stuff
//        val language: String = languageContext.getLanguage()
        var language = "en"
        for (variant in PREFERRED_VARIANT_ORDER) {
            if (!message.variants.containsKey(variant)) continue
            val variantMap = message.variants[variant]!!
            return if (variantMap.containsKey(language)) variantMap[language] else variantMap["default"]
        }
        return null
    }

    /**
     * Called after an In-App message is closed and it's dismiss animation has completed
     */
    suspend fun messageWasDismissed(message: InAppMessage) {
        messageWasDismissed(message, false)
    }

    suspend fun messageWasDismissed(message: InAppMessage, failed: Boolean) {
        if (!message.isPreview) {
            dismissedMessages.add(message.messageId)
            // If failed we will retry on next session
            if (!failed) {
                _prefs.dismissedMessagesId = dismissedMessages

                // Don't keep track of last displayed time for a preview
                lastTimeInAppDismissed = Date()
                // Only increase IAM display quantity if IAM was truly displayed
                persistInAppMessage(message)
            }
            Logging.debug("OSInAppMessageController messageWasDismissed dismissedMessages: $dismissedMessages")
        }
        if (!shouldWaitForPromptsBeforeDismiss())
            onMessageWasDismissed(message)

        dismissCurrentMessage(message)
    }

    private fun shouldWaitForPromptsBeforeDismiss(): Boolean {
        return currentPrompt != null
    }

    /**
     * Removes first item from the queue and attempts to show the next IAM in the queue
     *
     * @param message The message dismissed, preview messages are null
     */
    private suspend fun dismissCurrentMessage(message: InAppMessage?) {
        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
        _sessionService.directInfluenceFromIAMClickFinished()

        if (shouldWaitForPromptsBeforeDismiss()) {
            Logging.debug("Stop evaluateMessageDisplayQueue because prompt is currently displayed")
            return
        }
        inAppMessageShowing = false

        if (message != null && !message.isPreview && messageDisplayQueue.size > 0) {
            if (!messageDisplayQueue.contains(message)) {
                Logging.debug("Message already removed from the queue!")
                return
            } else {
                val removedMessageId = messageDisplayQueue.removeAt(0).messageId
                Logging.debug("In app message with id: $removedMessageId, dismissed (removed) from the queue!")
            }
        }

        // Display the next message in the queue, or attempt to add more IAMs to the queue
        if (messageDisplayQueue.size > 0) {
            Logging.debug("In app message on queue available: " + messageDisplayQueue[0].messageId)
            displayMessage(messageDisplayQueue[0])
        } else {
            Logging.debug("In app message dismissed evaluating messages")
            evaluateInAppMessages()
        }
    }

    private suspend fun persistInAppMessage(message: InAppMessage) {
        val displayTimeSeconds = _time.currentTimeMillis / 1000
        message.redisplayStats.lastDisplayTime = displayTimeSeconds
        message.redisplayStats.incrementDisplayQuantity()
        message.isTriggerChanged = false
        message.isDisplayedInSession = true

        _data.saveInAppMessage(message)
        _prefs.lastTimeInAppDismissed = lastTimeInAppDismissed

        // Update the data to enable future re displays
        // Avoid calling the repository data again
        val index = redisplayedInAppMessages.indexOf(message)
        if (index != -1) {
            redisplayedInAppMessages.set(index, message)
        } else {
            redisplayedInAppMessages.add(message)
        }

        Logging.debug("persistInAppMessageForRedisplay: $message with msg array data: $redisplayedInAppMessages")
    }

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageLifecycleHandler(handler: $handler)")
        _lifecycleCallback.set(handler)
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageClickHandler(handler: $handler)")
        _messageClickCallback.set(handler)
    }

    companion object {
        private val PREFERRED_VARIANT_ORDER: List<String> = listOf("android", "app", "all")
        private const val LIQUID_TAG_SCRIPT = "\n\n" +
                "<script>\n" +
                "    setPlayerTags(%s);\n" +
                "</script>"
    }

    override fun onMessageActionOccurredOnPreview(message: InAppMessage, actionJson: JSONObject) {
        suspendifyOnThread {
            val action = InAppMessageAction(actionJson)
            action.isFirstClick = message.takeActionAsUnique()

            firePublicClickHandler(message.messageId, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            logInAppMessagePreviewActions(action)
        }
    }

    override fun onMessageActionOccurredOnMessage(message: InAppMessage, actionJson: JSONObject) {
        suspendifyOnThread {
            val action = InAppMessageAction(actionJson)
            action.isFirstClick = message.takeActionAsUnique()
            firePublicClickHandler(message.messageId, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            fireRESTCallForClick(message, action)
            fireTagCallForClick(action)
            fireOutcomesForClick(message.messageId, action.outcomes)
        }
    }

    private suspend fun fireOutcomesForClick(messageId: String, outcomes: List<InAppMessageOutcome>) {
        _sessionService.directInfluenceFromIAMClick(messageId)
        _outcomeEventsController.sendClickActionOutcomes(outcomes)
    }

    override fun onPageChanged(message: InAppMessage, eventJson: JSONObject) {
        val newPage = InAppMessagePage(eventJson)
        if (message.isPreview) {
            return
        }

        suspendifyOnThread {
            fireRESTCallForPageChange(message, newPage)
        }
    }

    override fun onMessageWasShown(message: InAppMessage) {

        onMessageDidDisplay(message)

        if (message.isPreview) return

        // Check that the messageId is in impressionedMessages so we return early without a second post being made

        // Check that the messageId is in impressionedMessages so we return early without a second post being made
        if (impressionedMessages.contains(message.messageId)) return

        // Add the messageId to impressionedMessages so no second request is made

        // Add the messageId to impressionedMessages so no second request is made
        impressionedMessages.add(message.messageId)

        val variantId = variantIdForMessage(message) ?: return

        suspendifyOnThread {
            val response = _backend.sendIAMImpression(
                _configModelStore.get().appId!!,
                _subscriptionManager.subscriptions.push?.id.toString(),
                variantId,
                _deviceService.deviceType,
                message.messageId,
                impressionedMessages
            )

            if (response.isSuccess) {
                // Everything handled by repository
            } else {
                // Post failed, impressioned Messages should be removed and this way another post can be attempted
                impressionedMessages.remove(message.messageId)
            }
        }
    }

    private fun onMessageWillDisplay(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageWillDisplay: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onWillDisplayInAppMessage(message) }
    }

    private fun onMessageDidDisplay(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageDidDisplay: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onDidDisplayInAppMessage(message) }
    }

    override fun onMessageWillDismiss(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageWillDismiss: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onWillDismissInAppMessage(message) }
    }

    override fun onMessageWasDismissed(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageDidDismiss: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onDidDismissInAppMessage(message) }
    }

    private suspend fun beginProcessingPrompts(message: InAppMessage, prompts: List<InAppMessagePrompt>) {
        if (prompts.isNotEmpty()) {
            Logging.debug("IAM showing prompts from IAM: $message")

            // TODO until we don't fix the activity going forward or back dismissing the IAM, we need to auto dismiss
            _displayer.dismissCurrentInAppMessage()
            showMultiplePrompts(message, prompts)
        }
    }

    private fun fireTagCallForClick(action: InAppMessageAction) {
        if (action.tags != null) {
            val tags = action.tags
            if (tags?.tagsToAdd != null) {
                val tagsAsMap = JSONUtils.newStringMapFromJSONObject(tags.tagsToAdd!!)
                _userManager.setTags(tagsAsMap)
            }

            if (tags?.tagsToRemove != null) {
                val tagKeys = JSONUtils.newStringSetFromJSONArray(tags?.tagsToRemove!!)
                _userManager.removeTags(tagKeys)
            }
        }
    }

    private suspend fun showMultiplePrompts(inAppMessage: InAppMessage, prompts: List<InAppMessagePrompt>) {
        for (prompt in prompts) {
            // Don't show prompt twice
            if (!prompt.hasPrompted()) {
                currentPrompt = prompt

                Logging.debug("IAM prompt to handle: " + currentPrompt.toString())
                currentPrompt!!.setPrompted(true)
                val result = currentPrompt!!.handlePrompt()
                currentPrompt = null
                Logging.debug("IAM prompt to handle finished with result: $result")

                // On preview mode we show informative alert dialogs
                if (inAppMessage.isPreview && result == InAppMessagePrompt.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST) {
                    showAlertDialogMessage(inAppMessage, prompts)
                    break
                }
            }
        }

        if (currentPrompt == null) {
            Logging.debug("No IAM prompt to handle, dismiss message: " + inAppMessage.messageId)
            messageWasDismissed(inAppMessage)
        }
    }

    private fun fireClickAction(action: InAppMessageAction) {
        if (action.clickUrl != null && !action.clickUrl!!.isEmpty()) {
            if (action.urlTarget == InAppMessageActionUrlType.BROWSER)
                AndroidUtils.openURLInBrowser(_applicationService.appContext!!, action.clickUrl!!)
            else if (action.urlTarget == InAppMessageActionUrlType.IN_APP_WEBVIEW)
                OneSignalChromeTab.open(action.clickUrl, true, _applicationService.appContext!!)
        }
    }

    /* End IAM Lifecycle methods */
    private fun logInAppMessagePreviewActions(action: InAppMessageAction) {
        if (action.tags != null)
            Logging.debug("Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " + action.tags.toString())

        if (action.outcomes.size > 0)
            Logging.debug("Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " + action.outcomes.toString())

        // TODO: Add more action payload preview logs here in future
    }

    private suspend fun firePublicClickHandler(messageId: String, action: InAppMessageAction) {

        if(!_messageClickCallback.hasCallback)
            return

        // Send public outcome not from handler
        // Check that only on the handler
        // Any outcome sent on this callback should count as DIRECT from this IAM
        _sessionService.directInfluenceFromIAMClick(messageId)

        withContext(Dispatchers.Main) {
            _messageClickCallback.fire { it.inAppMessageClicked(action) }
        }
    }

    private suspend fun fireRESTCallForPageChange(message: InAppMessage, page: InAppMessagePage) {
        val variantId = variantIdForMessage(message) ?: return
        val pageId = page.pageId
        val messagePrefixedPageId = message.messageId + pageId

        // Never send multiple page impressions for the same message UUID unless that page change is from an IAM with redisplay
        if (viewedPageIds.contains(messagePrefixedPageId)) {
            Logging.verbose("Already sent page impression for id: $pageId")
            return
        }
        viewedPageIds.add(messagePrefixedPageId)
        var response = _backend.sendIAMPageImpression(
            _configModelStore.get().appId,
            _subscriptionManager.subscriptions.push?.id.toString(),
            variantId,
            _deviceService.deviceType,
            message.messageId,
            pageId,
            viewedPageIds)

        if(response.isSuccess) {
            // Everything handled by repository
        }
        else {
            // Post failed, viewed page should be removed and this way another post can be attempted
            viewedPageIds.remove(messagePrefixedPageId)
        }
    }

    private suspend fun fireRESTCallForClick(message: InAppMessage, action: InAppMessageAction) {
        val variantId = variantIdForMessage(message) ?: return
        val clickId = action.clickId

        // If IAM has redisplay the clickId may be available
        val clickAvailableByRedisplay = message.redisplayStats.isRedisplayEnabled && clickId != null && message.isClickAvailable(clickId)

        // Never count multiple clicks for the same click UUID unless that click is from an IAM with redisplay
        if (!clickAvailableByRedisplay && clickedClickIds.contains(clickId))
            return

        if(clickId != null) {
            clickedClickIds.add(clickId)
            // Track clickId per IAM
            message.addClickId(clickId)
        }

        val response = _backend.sendIAMClick(
            _configModelStore.get().appId!!,
            _subscriptionManager.subscriptions.push?.id.toString(),
            variantId,
            _deviceService.deviceType,
            message.messageId,
            clickId,
            action.isFirstClick,
            clickedClickIds)

        if(response.isSuccess) {
            // Everything handled by repository
        }
        else {
            clickedClickIds.remove(clickId)

            if(clickId != null) {
                message.removeClickId(clickId)
            }
        }
    }

    private fun showAlertDialogMessage(inAppMessage: InAppMessage, prompts: List<InAppMessagePrompt>) {
        val messageTitle =  _applicationService.appContext!!.getString(R.string.location_permission_missing_title)
        val message = _applicationService.appContext!!.getString(R.string.location_permission_missing_message)
        AlertDialog.Builder(_applicationService.current)
            .setTitle(messageTitle)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> suspendifyOnThread { showMultiplePrompts(inAppMessage, prompts) } }
            .show()
    }
}
