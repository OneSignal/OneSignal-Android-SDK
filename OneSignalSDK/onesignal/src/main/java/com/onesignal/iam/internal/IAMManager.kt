package com.onesignal.iam.internal

import android.app.AlertDialog
import com.onesignal.R
import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.BackendException
import com.onesignal.core.internal.common.AndroidUtils
import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.common.JSONUtils
import com.onesignal.core.internal.common.events.CallbackProducer
import com.onesignal.core.internal.common.events.ICallbackProducer
import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.core.internal.modeling.ModelChangedArgs
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.internal.session.ISessionLifecycleHandler
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.core.internal.user.ISubscriptionChangedHandler
import com.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.core.user.IUserManager
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.iam.IInAppMessageLifecycleHandler
import com.onesignal.iam.InAppMessageActionUrlType
import com.onesignal.iam.internal.backend.IInAppBackendService
import com.onesignal.iam.internal.common.InAppHelper
import com.onesignal.iam.internal.common.OneSignalChromeTab
import com.onesignal.iam.internal.display.IInAppDisplayer
import com.onesignal.iam.internal.lifecycle.IInAppLifecycleEventHandler
import com.onesignal.iam.internal.lifecycle.IInAppLifecycleService
import com.onesignal.iam.internal.preferences.IInAppPreferencesController
import com.onesignal.iam.internal.prompt.impl.InAppMessagePrompt
import com.onesignal.iam.internal.repositories.IInAppRepository
import com.onesignal.iam.internal.state.InAppStateService
import com.onesignal.iam.internal.triggers.ITriggerController
import com.onesignal.iam.internal.triggers.ITriggerHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class IAMManager(
    private val _applicationService: IApplicationService,
    private val _sessionService: ISessionService,
    private val _influenceManager: IInfluenceManager,
    private val _configModelStore: ConfigModelStore,
    private val _userManager: IUserManager,
    private val _subscriptionManager: ISubscriptionManager,
    private val _outcomeEventsController: IOutcomeEventsController,
    private val _state: InAppStateService,
    private val _prefs: IInAppPreferencesController,
    private val _repository: IInAppRepository,
    private val _backend: IInAppBackendService,
    private val _triggerController: ITriggerController,
    private val _displayer: IInAppDisplayer,
    private val _lifecycle: IInAppLifecycleService,
    private val _languageContext: ILanguageContext,
    private val _time: ITime
) : IIAMManager,
    IStartableService,
    ISubscriptionChangedHandler,
    ISingletonModelStoreChangeHandler<ConfigModel>,
    IInAppLifecycleEventHandler,
    ITriggerHandler,
    ISessionLifecycleHandler {

    private val _lifecycleCallback: ICallbackProducer<IInAppMessageLifecycleHandler> = CallbackProducer()
    private val _messageClickCallback: ICallbackProducer<IInAppMessageClickHandler> = CallbackProducer()

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    private var _messages: List<InAppMessage> = listOf()

    // IAMs that have been dismissed by the user
    //   This mean they have already displayed to the user
    private val _dismissedMessages: MutableSet<String> = mutableSetOf()

    // IAMs that have been displayed to the user
    //   This means their impression has been successfully posted to our backend and should not be counted again
    private val _impressionedMessages: MutableSet<String> = mutableSetOf()

    //   This means their impression has been successfully posted to our backend and should not be counted again
    private val _viewedPageIds: MutableSet<String> = mutableSetOf()

    // IAM clicks that have been successfully posted to our backend and should not be counted again
    private val _clickedClickIds: MutableSet<String> = mutableSetOf()

    // Ordered IAMs queued to display, includes the message currently displaying, if any.
    private val _messageDisplayQueue: MutableList<InAppMessage> = mutableListOf()
    private val _messageDisplayQueueMutex = Mutex()

    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    private val _redisplayedInAppMessages: MutableList<InAppMessage> = mutableListOf()

    private val _fetchIAMMutex = Mutex()
    private var _lastTimeFetchedIAMs: Long? = null

    override var paused: Boolean
        get() = _state.paused
        set(value) {
            Logging.log(LogLevel.DEBUG, "IAMManager.setPaused(value: $value)")
            _state.paused = value
        }

    override fun start() {
        val tempLastTimeInAppDismissed = _prefs.lastTimeInAppDismissed
        if (tempLastTimeInAppDismissed != null) {
            _state.lastTimeInAppDismissed = tempLastTimeInAppDismissed
        }

        _subscriptionManager.subscribe(this)
        _configModelStore.subscribe(this)
        _lifecycle.subscribe(this)
        _triggerController.subscribe(this)
        _sessionService.subscribe(this)

        suspendifyOnThread {
            // get saved IAMs from database
            _redisplayedInAppMessages.addAll(_repository.listInAppMessages())

            // reset all messages for redisplay to indicate not shown
            for (redisplayInAppMessage in _redisplayedInAppMessages) {
                redisplayInAppMessage.isDisplayedInSession = false
            }

            // attempt to fetch messages from the backend (if we have the pre-requisite data already)
            fetchMessages()
        }
    }

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) {
        Logging.log(LogLevel.DEBUG, "IAMManager.setInAppMessageLifecycleHandler(handler: $handler)")
        _lifecycleCallback.set(handler)
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) {
        Logging.log(LogLevel.DEBUG, "IAMManager.setInAppMessageClickHandler(handler: $handler)")
        _messageClickCallback.set(handler)
    }

    override fun onModelUpdated(args: ModelChangedArgs, tag: String) {
        if (args.property != ConfigModel::appId.name) {
            return
        }

        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onModelReplaced(model: ConfigModel, tag: String) {
        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onSubscriptionsChanged() {
        if (_subscriptionManager.subscriptions.push == null) {
            return
        }

        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onSessionStarted() {
        for (redisplayInAppMessage in _redisplayedInAppMessages) {
            redisplayInAppMessage.isDisplayedInSession = false
        }

        suspendifyOnThread {
            fetchMessages()
        }
    }

    override fun onSessionActive() { }
    override fun onSessionEnded(duration: Long) { }

    // called when a new push subscription is added, or the app id is updated, or a new session starts
    private suspend fun fetchMessages() {
        val appId = _configModelStore.model.appId
        val subscriptionId = _subscriptionManager.subscriptions.push?.id

        if (subscriptionId == null || IDManager.isIdLocalOnly(subscriptionId) || appId.isEmpty()) {
            return
        }

        _fetchIAMMutex.withLock {
            val now = _time.currentTimeMillis
            if (_lastTimeFetchedIAMs != null && (now - _lastTimeFetchedIAMs!!) < _configModelStore.model.fetchIAMMinInterval) {
                return
            }

            _lastTimeFetchedIAMs = now
        }

        val newMessages = _backend.listInAppMessages(appId, subscriptionId.toString())

        if (newMessages != null) {
            this._messages = newMessages
            evaluateInAppMessages()
        }
    }

    /**
     * Iterate through the messages and determine if they should be shown to the user.
     */
    private suspend fun evaluateInAppMessages() {
        Logging.debug("IAMManager.evaluateInAppMessages()")

        for (message in _messages) {
            // Make trigger evaluation first, dynamic trigger might change "trigger changed" flag value for redisplay messages
            if (_triggerController.evaluateMessageTriggers(message)) {
                setDataForRedisplay(message)
                if (!_dismissedMessages.contains(message.messageId) && !message.isFinished) {
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
        val messageDismissed: Boolean = _dismissedMessages.contains(message.messageId)
        val index: Int = _redisplayedInAppMessages.indexOf(message)
        if (messageDismissed && index != -1) {
            val savedIAM: InAppMessage = _redisplayedInAppMessages.get(index)
            message.redisplayStats.setDisplayStats(savedIAM.redisplayStats)
            message.isDisplayedInSession = savedIAM.isDisplayedInSession

            val triggerHasChanged: Boolean = hasMessageTriggerChanged(message)
            Logging.debug("IAMManager.setDataForRedisplay: $message triggerHasChanged: $triggerHasChanged")

            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                message.redisplayStats.isDelayTimeSatisfied &&
                message.redisplayStats.shouldDisplayAgain()
            ) {
                Logging.debug("IAMManager.setDataForRedisplay message available for redisplay: " + message.messageId)
                _dismissedMessages.remove(message.messageId)
                _impressionedMessages.remove(message.messageId)
                // Pages from different IAMs should not impact each other so we can clear the entire
                // list when an IAM is dismissed or we are re-displaying the same one
                _viewedPageIds.clear()
                _prefs.viewPageImpressionedIds = _viewedPageIds
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
        _messageDisplayQueueMutex.withLock {
            // Make sure no message is ever added to the queue more than once
            if (!_messageDisplayQueue.contains(message) && _state.inAppMessageIdShowing != message.messageId) {
                _messageDisplayQueue.add(message)
                Logging.debug("IAMManager.queueMessageForDisplay: In app message with id: " + message.messageId + ", added to the queue")
            }
        }

        attemptToShowInAppMessage()
    }

    private suspend fun attemptToShowInAppMessage() {
        // We need to wait for system conditions to be the correct ones
        if (!_applicationService.waitUntilSystemConditionsAvailable()) {
            Logging.warn("IAMManager.attemptToShowInAppMessage: In app message not showing due to system condition not correct")
            return
        }

        var messageToDisplay: InAppMessage? = null

        _messageDisplayQueueMutex.withLock {
            Logging.debug("IAMManager.attemptToShowInAppMessage: $_messageDisplayQueue")
            // If there are IAMs in the queue and nothing showing, show first in the queue
            if (paused) {
                Logging.verbose("IAMManager.attemptToShowInAppMessage: In app messaging is currently paused, in app messages will not be shown!")
            } else if (_messageDisplayQueue.isEmpty()) {
                Logging.debug("IAMManager.attemptToShowInAppMessage: There are no IAMs left in the queue!")
            } else if (_state.inAppMessageIdShowing != null) {
                Logging.debug("IAMManager.attemptToShowInAppMessage: There is an IAM currently showing!")
            } else {
                Logging.debug("IAMManager.attemptToShowInAppMessage: No IAM showing currently, showing first item in the queue!")
                messageToDisplay = _messageDisplayQueue.removeAt(0)

                // set the state while the mutex is held so the next one coming in will pick up
                // the correct state.
                _state.inAppMessageIdShowing = messageToDisplay!!.messageId
            }
        }

        // If there was a message dequeued, display it
        if (messageToDisplay != null) {
            var result = _displayer.displayMessage(messageToDisplay!!)

            if (result == null) {
                _state.inAppMessageIdShowing = null
                // Retry displaying the same IAM
                // Using the queueMessageForDisplay method follows safety checks to prevent issues
                // like having 2 IAMs showing at once or duplicate IAMs in the queue
                queueMessageForDisplay(messageToDisplay!!)
            } else if (result == false) {
                _state.inAppMessageIdShowing = null
                messageWasDismissed(messageToDisplay!!, true)
            }
        }
    }

    /**
     * Called after an In-App message is closed and it's dismiss animation has completed
     */
    private suspend fun messageWasDismissed(message: InAppMessage, failed: Boolean = false) {
        if (!message.isPreview) {
            _dismissedMessages.add(message.messageId)

            // If failed we will retry on next session
            if (!failed) {
                _prefs.dismissedMessagesId = _dismissedMessages

                // Don't keep track of last displayed time for a preview
                _state.lastTimeInAppDismissed = _time.currentTimeMillis
                // Only increase IAM display quantity if IAM was truly displayed
                persistInAppMessage(message)
            }

            Logging.debug("IAMManager.messageWasDismissed: dismissedMessages: $_dismissedMessages")
        }

        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
        _influenceManager.onInAppMessageDismissed()

        if (_state.currentPrompt != null) {
            Logging.debug("IAMManager.messageWasDismissed: Stop evaluateMessageDisplayQueue because prompt is currently displayed")
            return
        }

        // fire the external callback
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("IAMManager.messageWasDismissed: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onDidDismissInAppMessage(message) }

        _state.inAppMessageIdShowing = null

        // Display the next message in the queue, or attempt to add more IAMs to the queue
        if (_messageDisplayQueue.isNotEmpty()) {
            Logging.debug("IAMManager.messageWasDismissed: In app message on queue available, attempting to show")
            attemptToShowInAppMessage()
        } else {
            Logging.debug("IAMManager.messageWasDismissed: In app message dismissed evaluating messages")
            evaluateInAppMessages()
        }
    }

    /**
     * Part of redisplay logic
     *
     *
     * Make all messages with redisplay available if:
     * - Already displayed
     * - At least one Trigger has changed
     */
    private fun makeRedisplayMessagesAvailableWithTriggers(newTriggersKeys: Collection<String>) {
        for (message in _messages) {
            if (!message.isTriggerChanged && _redisplayedInAppMessages.contains(message) &&
                _triggerController.isTriggerOnMessage(message, newTriggersKeys)
            ) {
                Logging.debug("IAMManager.makeRedisplayMessagesAvailableWithTriggers: Trigger changed for message: $message")
                message.isTriggerChanged = true
            }
        }
    }

    private suspend fun persistInAppMessage(message: InAppMessage) {
        val displayTimeSeconds = _time.currentTimeMillis / 1000
        message.redisplayStats.lastDisplayTime = displayTimeSeconds
        message.redisplayStats.incrementDisplayQuantity()
        message.isTriggerChanged = false
        message.isDisplayedInSession = true

        _repository.saveInAppMessage(message)
        _prefs.lastTimeInAppDismissed = _state.lastTimeInAppDismissed

        // Update the data to enable future re displays
        // Avoid calling the repository data again
        val index = _redisplayedInAppMessages.indexOf(message)
        if (index != -1) {
            _redisplayedInAppMessages.set(index, message)
        } else {
            _redisplayedInAppMessages.add(message)
        }

        Logging.debug("IAMManager.persistInAppMessage: $message with msg array data: $_redisplayedInAppMessages")
    }

    // IAM LIFECYCLE CALLBACKS
    override fun onMessageWillDisplay(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("IAMManager.onMessageWillDisplay: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onWillDisplayInAppMessage(message) }
    }

    override fun onMessageWasDisplayed(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("IAMManager.onMessageWasDisplayed: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onDidDisplayInAppMessage(message) }

        if (message.isPreview) {
            return
        }

        // Check that the messageId is in impressioned messages so we return early without a second post being made
        if (_impressionedMessages.contains(message.messageId)) return

        // Add the messageId to impressioned messages so no second request is made
        _impressionedMessages.add(message.messageId)

        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return

        suspendifyOnThread {
            try {
                _backend.sendIAMImpression(
                    _configModelStore.model.appId,
                    _subscriptionManager.subscriptions.push?.id.toString(),
                    variantId,
                    message.messageId
                )

                _prefs.impressionesMessagesId = _impressionedMessages
            } catch (ex: BackendException) {
                // Post failed, impressioned messages should be removed and this way another post can be attempted
                _impressionedMessages.remove(message.messageId)
            }
        }
    }

    override fun onMessageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageAction) {
        suspendifyOnThread {
            action.isFirstClick = message.takeActionAsUnique()

            firePublicClickHandler(message.messageId, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            logInAppMessagePreviewActions(action)
        }
    }

    override fun onMessageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageAction) {
        suspendifyOnThread {
            action.isFirstClick = message.takeActionAsUnique()
            firePublicClickHandler(message.messageId, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            fireRESTCallForClick(message, action)
            fireTagCallForClick(action)
            fireOutcomesForClick(message.messageId, action.outcomes)
        }
    }

    override fun onMessagePageChanged(message: InAppMessage, page: InAppMessagePage) {
        if (message.isPreview) {
            return
        }

        suspendifyOnThread {
            fireRESTCallForPageChange(message, page)
        }
    }

    override fun onMessageWillDismiss(message: InAppMessage) {
        if (!_lifecycleCallback.hasCallback) {
            Logging.verbose("IAMManager.onMessageWillDismiss: inAppMessageLifecycleHandler is null")
            return
        }
        _lifecycleCallback.fire { it.onWillDismissInAppMessage(message) }
    }

    override fun onMessageWasDismissed(message: InAppMessage) {
        suspendifyOnThread {
            messageWasDismissed(message)
        }
    }
    // END IAM LIFECYCLE CALLBACKS

    // TRIGGER FIRED CALLBACKS
    /**
     * Part of redisplay logic
     *
     *
     * Will update redisplay messages depending on dynamic triggers before setDataForRedisplay is called.
     * @see OSInAppMessageController.setDataForRedisplay
     * @see OSInAppMessageController.messageTriggerConditionChanged
     */
    override fun onTriggerCompleted(triggerId: String) {
        Logging.debug("IAMManager.onTriggerCompleted: called with triggerId: $triggerId")
        val triggerIds: MutableSet<String> = HashSet()
        triggerIds.add(triggerId)
        makeRedisplayMessagesAvailableWithTriggers(triggerIds)
    }

    /**
     * Dynamic trigger logic
     *
     * This will re evaluate messages due to dynamic triggers evaluating to true potentially
     *
     * @see OSInAppMessageController.setDataForRedisplay
     */
    override fun onTriggerConditionChanged() {
        Logging.debug("IAMManager.onTriggerConditionChanged()")

        suspendifyOnThread {
            // This method is called when a time-based trigger timer fires, meaning the message can
            //  probably be shown now. So the current message conditions should be re-evaluated
            evaluateInAppMessages()
        }
    }

    override fun onTriggerChanged(newTriggerKey: String) {
        Logging.debug("IAMManager.onTriggerChanged(newTriggerKey: $newTriggerKey)")

        makeRedisplayMessagesAvailableWithTriggers(listOf(newTriggerKey))

        suspendifyOnThread {
            // This method is called when a time-based trigger timer fires, meaning the message can
            //  probably be shown now. So the current message conditions should be re-evaluated
            evaluateInAppMessages()
        }
    }

    // END TRIGGER FIRED CALLBACKS

    private suspend fun beginProcessingPrompts(message: InAppMessage, prompts: List<InAppMessagePrompt>) {
        if (prompts.isNotEmpty()) {
            Logging.debug("IAMManager.beginProcessingPrompts: IAM showing prompts from IAM: $message")

            // TODO until we don't fix the activity going forward or back dismissing the IAM, we need to auto dismiss
            _displayer.dismissCurrentInAppMessage()
            showMultiplePrompts(message, prompts)
        }
    }

    private suspend fun fireOutcomesForClick(messageId: String, outcomes: List<InAppMessageOutcome>) {
        _influenceManager.onDirectInfluenceFromIAM(messageId)

        for (outcome in outcomes) {
            val name: String = outcome.name
            if (outcome.isUnique) {
                _outcomeEventsController.sendUniqueOutcomeEvent(name)
            } else if (outcome.weight > 0) {
                _outcomeEventsController.sendOutcomeEventWithValue(name, outcome.weight)
            } else {
                _outcomeEventsController.sendOutcomeEvent(name)
            }
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
                _state.currentPrompt = prompt

                Logging.debug("IAMManager.showMultiplePrompts: IAM prompt to handle: " + _state.currentPrompt.toString())
                _state.currentPrompt!!.setPrompted(true)
                val result = _state.currentPrompt!!.handlePrompt()
                _state.currentPrompt = null
                Logging.debug("IAMManager.showMultiplePrompts: IAM prompt to handle finished with result: $result")

                // On preview mode we show informative alert dialogs
                if (inAppMessage.isPreview && result == InAppMessagePrompt.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST) {
                    showAlertDialogMessage(inAppMessage, prompts)
                    break
                }
            }
        }

        if (_state.currentPrompt == null) {
            Logging.debug("IAMManager.showMultiplePrompts: No IAM prompt to handle, dismiss message: " + inAppMessage.messageId)
            messageWasDismissed(inAppMessage)
        }
    }

    private fun fireClickAction(action: InAppMessageAction) {
        if (action.clickUrl != null && action.clickUrl.isNotEmpty()) {
            if (action.urlTarget == InAppMessageActionUrlType.BROWSER) {
                AndroidUtils.openURLInBrowser(_applicationService.appContext, action.clickUrl)
            } else if (action.urlTarget == InAppMessageActionUrlType.IN_APP_WEBVIEW) {
                OneSignalChromeTab.open(action.clickUrl, true, _applicationService.appContext)
            }
        }
    }

    /* End IAM Lifecycle methods */
    private fun logInAppMessagePreviewActions(action: InAppMessageAction) {
        if (action.tags != null) {
            Logging.debug("IAMManager.logInAppMessagePreviewActions: Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " + action.tags.toString())
        }

        if (action.outcomes.size > 0) {
            Logging.debug("IAMManager.logInAppMessagePreviewActions: Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " + action.outcomes.toString())
        }

        // TODO: Add more action payload preview logs here in future
    }

    private suspend fun firePublicClickHandler(messageId: String, action: InAppMessageAction) {
        if (!_messageClickCallback.hasCallback) {
            return
        }

        // Send public outcome not from handler
        // Check that only on the handler
        // Any outcome sent on this callback should count as DIRECT from this IAM
        _influenceManager.onDirectInfluenceFromIAM(messageId)

        withContext(Dispatchers.Main) {
            _messageClickCallback.fire { it.inAppMessageClicked(action) }
        }
    }

    private suspend fun fireRESTCallForPageChange(message: InAppMessage, page: InAppMessagePage) {
        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return
        val pageId = page.pageId
        val messagePrefixedPageId = message.messageId + pageId

        // Never send multiple page impressions for the same message UUID unless that page change is from an IAM with redisplay
        if (_viewedPageIds.contains(messagePrefixedPageId)) {
            Logging.verbose("IAMManager: Already sent page impression for id: $pageId")
            return
        }
        _viewedPageIds.add(messagePrefixedPageId)

        try {
            _backend.sendIAMPageImpression(
                _configModelStore.model.appId,
                _subscriptionManager.subscriptions.push?.id.toString(),
                variantId,
                message.messageId,
                pageId
            )

            _prefs.viewPageImpressionedIds = _viewedPageIds
        } catch (ex: BackendException) {
            // Post failed, viewed page should be removed and this way another post can be attempted
            _viewedPageIds.remove(messagePrefixedPageId)
        }
    }

    private suspend fun fireRESTCallForClick(message: InAppMessage, action: InAppMessageAction) {
        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return
        val clickId = action.clickId

        // If IAM has redisplay the clickId may be available
        val clickAvailableByRedisplay = message.redisplayStats.isRedisplayEnabled && clickId != null && message.isClickAvailable(clickId)

        // Never count multiple clicks for the same click UUID unless that click is from an IAM with redisplay
        if (!clickAvailableByRedisplay && _clickedClickIds.contains(clickId)) {
            return
        }

        if (clickId != null) {
            _clickedClickIds.add(clickId)
            // Track clickId per IAM
            message.addClickId(clickId)
        }

        try {
            _backend.sendIAMClick(
                _configModelStore.model.appId,
                _subscriptionManager.subscriptions.push?.id.toString(),
                variantId,
                message.messageId,
                clickId,
                action.isFirstClick
            )

            // Persist success click to disk. Id already added to set before making the network call
            _prefs.clickedMessagesId = _clickedClickIds
        } catch (ex: BackendException) {
            _clickedClickIds.remove(clickId)

            if (clickId != null) {
                message.removeClickId(clickId)
            }
        }
    }

    private fun showAlertDialogMessage(inAppMessage: InAppMessage, prompts: List<InAppMessagePrompt>) {
        val messageTitle = _applicationService.appContext.getString(R.string.location_permission_missing_title)
        val message = _applicationService.appContext.getString(R.string.location_permission_missing_message)
        AlertDialog.Builder(_applicationService.current)
            .setTitle(messageTitle)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> suspendifyOnThread { showMultiplePrompts(inAppMessage, prompts) } }
            .show()
    }
}
