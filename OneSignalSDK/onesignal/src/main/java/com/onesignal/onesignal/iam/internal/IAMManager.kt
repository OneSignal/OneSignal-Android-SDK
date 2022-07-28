package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.service.IStartableService
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler
import com.onesignal.onesignal.iam.internal.backend.InAppBackendController
import com.onesignal.onesignal.iam.internal.data.InAppDataController
import com.onesignal.onesignal.iam.internal.display.IAMDisplayer
import com.onesignal.onesignal.iam.internal.preferences.InAppPreferencesController
import com.onesignal.onesignal.iam.internal.prompt.InAppMessagePrompt
import com.onesignal.onesignal.iam.internal.triggers.TriggerController
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal class IAMManager (
    private val _applicationService: IApplicationService,
    private val _httpClient: IHttpClient,
    private val _configModelStore: ConfigModelStore,
    private val _userManager: IUserManager,
    private val _prefs: InAppPreferencesController,
    private val _data: InAppDataController,
    private val _backend: InAppBackendController,
    private val _triggerController: TriggerController,
    private val _displayer: IAMDisplayer,
    private val _time: ITime
        ) : IIAMManager, IStartableService {

    private val _lifecycleCallback: ICallbackProducer<IInAppMessageLifecycleHandler> = CallbackProducer()
    private val _messageClickCallback: ICallbackProducer<IInAppMessageClickHandler> = CallbackProducer()
    private var _paused: Boolean = true

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
    private val currentPrompt: InAppMessagePrompt? = null
//    private var userTagsString: String? = null
//    private var waitForTags = false
//    private var pendingMessageContent: InAppMessageContent? = null

    override var paused: Boolean
        get() = _paused
        set(value) {
            Logging.log(LogLevel.DEBUG, "setPaused(value: $value)")
            _paused = value
        }

    override suspend fun start() {

        // get saved IAMs from database
        val messages = _data.listInAppMessages()

        val config = _configModelStore.get()

        // TODO: Only on start or should we do this on focus, or on session start?
        // Retrieve any in app messages that might exist
        val userId = config.userId!!
        val jsonBody = JSONObject()
        jsonBody.put("app_id", config.appId)

        // TODO: This will be replaced by dedicated iam endpoint once it's available
        val response = _httpClient.post("players/${userId}/on_session", jsonBody)

        if(response.isSuccess) {
            val jsonResponse = JSONObject(response.payload)

            if (jsonResponse.has("in_app_messages")) {
                val iamMessagesAsJSON = jsonResponse.getJSONArray("in_app_messages")
                // Cache copy for quick cold starts
                _prefs.savedIAMs = iamMessagesAsJSON.toString()

                // TODO: I feel like this might be a "new session" thing, not a "fetch IAMs" thing
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

    private suspend fun displayMessage(message: InAppMessage) {

        if (_paused) {
            Logging.verbose("In app messaging is currently paused, in app messages will not be shown!")
            return
        }
        inAppMessageShowing = true
        // TODO: Retrieve tags from either model or backend using suspend
        //getTagsForLiquidTemplating(message, false)
        var response = _backend.getIAMData(_configModelStore.get().appId!!, message.messageId, variantIdForMessage(message))

        if(response.isSuccess) {
            try {
                val jsonResponse = JSONObject(response.payload!!)
                val content: InAppMessageContent = parseMessageContentData(jsonResponse, message)
                if (content.contentHtml == null) {
                    Logging.debug("displayMessage:OnSuccess: No HTML retrieved from loadMessageContent")
                    return
                }
//                if (waitForTags) {
//                    pendingMessageContent = content
//                    return
//                }
                // TODO: Implement Influence Tracking
//                OneSignal.getSessionManager().onInAppMessageReceived(message.messageId)
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

    suspend fun displayPreviewMessage(previewUUID: String) {
        inAppMessageShowing = true
        val message = InAppMessage(true, _time)
//        getTagsForLiquidTemplating(message, true)
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

//    private fun getTagsForLiquidTemplating(message: InAppMessage, isPreview: Boolean) {
//        waitForTags = false
//        if (isPreview || message.hasLiquid) {
//            val tagsAsJson = JSONObject(_userManager.tags)
//            userTagsString = tagsAsJson.toString()
//
//            if (pendingMessageContent != null) {
//                if (!isPreview) {
//                    // TODO: Implement Influence Tracking
////                        OneSignal.getSessionManager().onInAppMessageReceived(message.messageId)
//                }
//
//                pendingMessageContent!!.contentHtml = taggedHTMLString(pendingMessageContent!!.contentHtml!!)
//                _webViewManager.showMessageContent(message, pendingMessageContent!!)
//                pendingMessageContent = null
//            }
//        }
//    }

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
            onMessageDidDismiss(message)

        dismissCurrentMessage(message)
    }

    private fun shouldWaitForPromptsBeforeDismiss(): Boolean {
        return currentPrompt != null
    }

    fun onMessageWillDisplay(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageWillDisplay: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onWillDisplayInAppMessage(message) }
    }

    fun onMessageDidDismiss(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("OSInAppMessageController onMessageDidDismiss: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onDidDismissInAppMessage(message) }
    }

    /**
     * Removes first item from the queue and attempts to show the next IAM in the queue
     *
     * @param message The message dismissed, preview messages are null
     */
    private suspend fun dismissCurrentMessage(message: InAppMessage?) {
        // TODO: Implement Influence Tracking
        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
//        OneSignal.getSessionManager().onDirectInfluenceFromIAMClickFinished()

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
}
