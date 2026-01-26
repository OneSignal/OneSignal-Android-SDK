package com.onesignal.inAppMessages.internal

import android.app.AlertDialog
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IDManager
import com.onesignal.common.JSONUtils
import com.onesignal.common.consistency.IamFetchReadyCondition
import com.onesignal.common.consistency.RywData
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.events.EventProducer
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnDefault
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.inAppMessages.InAppMessageActionUrlType
import com.onesignal.inAppMessages.R
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.common.InAppHelper
import com.onesignal.inAppMessages.internal.common.OneSignalChromeTab
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleEventHandler
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.inAppMessages.internal.triggers.ITriggerHandler
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.subscriptions.ISubscriptionChangedHandler
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.ISubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InAppMessagesManager(
    private val _applicationService: IApplicationService,
    private val _sessionService: ISessionService,
    private val _influenceManager: IInfluenceManager,
    private val _configModelStore: ConfigModelStore,
    private val _userManager: IUserManager,
    private val _identityModelStore: IdentityModelStore,
    private val _subscriptionManager: ISubscriptionManager,
    private val _outcomeEventsController: IOutcomeEventsController,
    private val _state: InAppStateService,
    private val _prefs: IInAppPreferencesController,
    private val _repository: IInAppRepository,
    private val _backend: IInAppBackendService,
    private val _triggerController: ITriggerController,
    private val _triggerModelStore: TriggerModelStore,
    private val _displayer: IInAppDisplayer,
    private val _lifecycle: IInAppLifecycleService,
    private val _languageContext: ILanguageContext,
    private val _time: ITime,
    private val _consistencyManager: IConsistencyManager,
) : IInAppMessagesManager,
    IStartableService,
    ISubscriptionChangedHandler,
    ISingletonModelStoreChangeHandler<ConfigModel>,
    IInAppLifecycleEventHandler,
    ITriggerHandler,
    ISessionLifecycleHandler,
    IApplicationLifecycleHandler {
    private val lifecycleCallback = EventProducer<IInAppMessageLifecycleListener>()
    private val messageClickCallback = EventProducer<IInAppMessageClickListener>()

    // IAMs loaded remotely from on_session
    //   If on_session won't be called this will be loaded from cache
    private var messages: MutableList<InAppMessage> = mutableListOf()

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
    private val messageDisplayQueueMutex = Mutex()

    // IAMs displayed with last displayed time and quantity of displays data
    // This is retrieved from a DB Table that take care of each object to be unique
    private val redisplayedInAppMessages: MutableList<InAppMessage> = mutableListOf()

    private val fetchIAMMutex = Mutex()
    private var lastTimeFetchedIAMs: Long? = null

    // Tracks whether the first IAM fetch has completed since this cold start
    private var hasCompletedFirstFetch: Boolean = false

    // Tracks trigger keys added early on cold start (before first fetch completes), for redisplay logic
    private val earlySessionTriggers: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    private val identityModelChangeHandler =
        object : ISingletonModelStoreChangeHandler<IdentityModel> {
            override fun onModelReplaced(
                model: IdentityModel,
                tag: String,
            ) { }

            override fun onModelUpdated(
                args: ModelChangedArgs,
                tag: String,
            ) {
                if (args.property == IdentityConstants.ONESIGNAL_ID) {
                    val oldOneSignalId = args.oldValue as String
                    val newOneSignalId = args.newValue as String

                    // Create a IAM fetch condition when a backend OneSignalID is retrieved for the first time
                    if (IDManager.isLocalId(oldOneSignalId) && !IDManager.isLocalId(newOneSignalId)) {
                        suspendifyOnIO {
                            val updateConditionDeferred =
                                _consistencyManager.getRywDataFromAwaitableCondition(IamFetchReadyCondition(newOneSignalId))
                            val rywToken = updateConditionDeferred.await()
                            if (rywToken != null) {
                                fetchMessages(rywToken)
                            }
                        }
                    }
                }
            }
        }

    override var paused: Boolean
        get() = _state.paused
        set(value) {
            Logging.debug("InAppMessagesManager.setPaused(value: $value)")
            _state.paused = value

            // If paused is true and an In-App Message is showing, dismiss it
            if (value && _state.inAppMessageIdShowing != null) {
                suspendifyOnMain {
                    _displayer.dismissCurrentInAppMessage()
                }
            }

            if (!value) {
                suspendifyOnDefault {
                    evaluateInAppMessages()
                }
            }
        }

    override fun start() {
        val tempDismissedSet = _prefs.dismissedMessagesId
        if (tempDismissedSet != null) {
            dismissedMessages.addAll(tempDismissedSet)
        }

        val tempLastTimeInAppDismissed = _prefs.lastTimeInAppDismissed
        if (tempLastTimeInAppDismissed != null) {
            _state.lastTimeInAppDismissed = tempLastTimeInAppDismissed
        }

        _subscriptionManager.subscribe(this)
        _configModelStore.subscribe(this)
        _lifecycle.subscribe(this)
        _triggerController.subscribe(this)
        _sessionService.subscribe(this)
        _applicationService.addApplicationLifecycleHandler(this)
        _identityModelStore.subscribe(identityModelChangeHandler)

        suspendifyOnIO {
            _repository.cleanCachedInAppMessages()

            // get saved IAMs from database
            redisplayedInAppMessages.addAll(_repository.listInAppMessages())

            // reset all messages for redisplay to indicate not shown
            for (redisplayInAppMessage in redisplayedInAppMessages) {
                redisplayInAppMessage.isDisplayedInSession = false
            }
        }
    }

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) {
        Logging.debug("InAppMessagesManager.addLifecycleListener(listener: $listener)")
        lifecycleCallback.subscribe(listener)
    }

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) {
        Logging.debug("InAppMessagesManager.removeLifecycleListener(listener: $listener)")
        lifecycleCallback.unsubscribe(listener)
    }

    override fun addClickListener(listener: IInAppMessageClickListener) {
        Logging.debug("InAppMessagesManager.addClickListener(listener: $listener)")
        messageClickCallback.subscribe(listener)
    }

    override fun removeClickListener(listener: IInAppMessageClickListener) {
        Logging.debug("InAppMessagesManager.removeClickListener(listener: $listener)")
        messageClickCallback.unsubscribe(listener)
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        if (args.property != ConfigModel::appId.name) {
            return
        }

        fetchMessagesWhenConditionIsMet()
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        fetchMessagesWhenConditionIsMet()
    }

    override fun onSubscriptionAdded(subscription: ISubscription) { }

    override fun onSubscriptionRemoved(subscription: ISubscription) { }

    override fun onSubscriptionChanged(
        subscription: ISubscription,
        args: ModelChangedArgs,
    ) {
        if (subscription !is IPushSubscription || args.path != SubscriptionModel::id.name) {
            return
        }

        fetchMessagesWhenConditionIsMet()
    }

    override fun onSessionStarted() {
        for (redisplayInAppMessage in redisplayedInAppMessages) {
            redisplayInAppMessage.isDisplayedInSession = false
        }

        fetchMessagesWhenConditionIsMet()
    }

    override fun onSessionActive() { }

    override fun onSessionEnded(duration: Long) { }

    private fun fetchMessagesWhenConditionIsMet() {
        suspendifyOnIO {
            val onesignalId = _userManager.onesignalId
            val iamFetchCondition =
                _consistencyManager.getRywDataFromAwaitableCondition(IamFetchReadyCondition(onesignalId))
            val rywData = iamFetchCondition.await()

            if (rywData != null) {
                fetchMessages(rywData)
            }
        }
    }

    // called when a new push subscription is added, or the app id is updated, or a new session starts
    private suspend fun fetchMessages(rywData: RywData) {
        // We only want to fetch IAMs if we know the app is in the
        // foreground, as we don't want to do this for background
        // events (such as push received), wasting resources for
        // IAMs that are never shown.
        if (!_applicationService.isInForeground) {
            return
        }

        val appId = _configModelStore.model.appId
        val subscriptionId = _subscriptionManager.subscriptions.push.id

        if (subscriptionId.isEmpty() || IDManager.isLocalId(subscriptionId) || appId.isEmpty()) {
            return
        }

        fetchIAMMutex.withLock {
            val now = _time.currentTimeMillis
            if (lastTimeFetchedIAMs != null && (now - lastTimeFetchedIAMs!!) < _configModelStore.model.fetchIAMMinInterval) {
                return
            }

            lastTimeFetchedIAMs = now
        }

        // lambda so that it is updated on each potential retry
        val sessionDurationProvider = { _time.currentTimeMillis - _sessionService.startTime }
        val newMessages = _backend.listInAppMessages(appId, subscriptionId, rywData, sessionDurationProvider)

        if (newMessages != null) {
            this.messages = newMessages as MutableList<InAppMessage>

            // Apply isTriggerChanged for messages that match triggers added too early on cold start
            synchronized(earlySessionTriggers) {
                if (earlySessionTriggers.isNotEmpty()) {
                    Logging.verbose("InAppMessagesManager: Processing triggers added early on cold start: $earlySessionTriggers")
                    for (message in this.messages) {
                        val isMessageDisplayed = redisplayedInAppMessages.contains(message)
                        val isTriggerOnMessage =
                            _triggerController.isTriggerOnMessage(message, earlySessionTriggers)
                        if (isMessageDisplayed && isTriggerOnMessage) {
                            Logging.verbose("InAppMessagesManager: Setting isTriggerChanged=true for message ${message.messageId}")
                            message.isTriggerChanged = true
                        }
                    }
                    earlySessionTriggers.clear()
                }
                // Mark that first fetch has completed
                hasCompletedFirstFetch = true
            }

            evaluateInAppMessages()
        }
    }

    /**
     * Iterate through the messages and determine if they should be shown to the user.
     */
    private suspend fun evaluateInAppMessages() {
        Logging.debug("InAppMessagesManager.evaluateInAppMessages()")
        val messagesToQueue = mutableListOf<InAppMessage>()

        synchronized(messages) {
            for (message in messages) {
                if (_triggerController.evaluateMessageTriggers(message)) {
                    setDataForRedisplay(message)
                    if (!dismissedMessages.contains(message.messageId) && !message.isFinished) {
                        messagesToQueue.add(message)
                    }
                }
            }
        }

        for (message in messagesToQueue) {
            queueMessageForDisplay(message)
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
            Logging.debug("InAppMessagesManager.setDataForRedisplay: $message triggerHasChanged: $triggerHasChanged")

            // Check if conditions are correct for redisplay
            if (triggerHasChanged &&
                message.redisplayStats.isDelayTimeSatisfied &&
                message.redisplayStats.shouldDisplayAgain()
            ) {
                Logging.debug("InAppMessagesManager.setDataForRedisplay message available for redisplay: " + message.messageId)
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
        messageDisplayQueueMutex.withLock {
            // Make sure no message is ever added to the queue more than once
            if (!messageDisplayQueue.contains(message) && _state.inAppMessageIdShowing != message.messageId) {
                messageDisplayQueue.add(message)
                Logging.debug(
                    "InAppMessagesManager.queueMessageForDisplay: In app message with id: " + message.messageId + ", added to the queue",
                )
            }
        }

        attemptToShowInAppMessage()
    }

    private suspend fun attemptToShowInAppMessage() {
        // We need to wait for system conditions to be the correct ones
        if (!_applicationService.waitUntilSystemConditionsAvailable()) {
            Logging.warn("InAppMessagesManager.attemptToShowInAppMessage: In app message not showing due to system condition not correct")
            return
        }

        var messageToDisplay: InAppMessage? = null

        messageDisplayQueueMutex.withLock {
            Logging.debug("InAppMessagesManager.attemptToShowInAppMessage: $messageDisplayQueue")
            // If there are IAMs in the queue and nothing showing, show first in the queue
            if (paused) {
                Logging.warn(
                    "InAppMessagesManager.attemptToShowInAppMessage: In app messaging is currently paused, in app messages will not be shown!",
                )
            } else if (messageDisplayQueue.isEmpty()) {
                Logging.debug("InAppMessagesManager.attemptToShowInAppMessage: There are no IAMs left in the queue!")
            } else if (_state.inAppMessageIdShowing != null) {
                Logging.debug("InAppMessagesManager.attemptToShowInAppMessage: There is an IAM currently showing!")
            } else {
                Logging.debug("InAppMessagesManager.attemptToShowInAppMessage: No IAM showing currently, showing first item in the queue!")
                messageToDisplay = messageDisplayQueue.removeAt(0)

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
                messages.remove(messageToDisplay)
                messageWasDismissed(messageToDisplay!!, true)
            }
        }
    }

    /**
     * Called after an In-App message is closed and it's dismiss animation has completed
     */
    private suspend fun messageWasDismissed(
        message: InAppMessage,
        failed: Boolean = false,
    ) {
        if (!message.isPreview) {
            dismissedMessages.add(message.messageId)

            // If failed we will retry on next session
            if (!failed) {
                _prefs.dismissedMessagesId = dismissedMessages

                // Don't keep track of last displayed time for a preview
                _state.lastTimeInAppDismissed = _time.currentTimeMillis
                // Only increase IAM display quantity if IAM was truly displayed
                persistInAppMessage(message)
            }

            Logging.debug("InAppMessagesManager.messageWasDismissed: dismissedMessages: $dismissedMessages")
        }

        // Remove DIRECT influence due to ClickHandler of ClickAction outcomes
        _influenceManager.onInAppMessageDismissed()

        if (_state.currentPrompt != null) {
            Logging.debug(
                "InAppMessagesManager.messageWasDismissed: Stop evaluateMessageDisplayQueue because prompt is currently displayed",
            )
            return
        }

        // fire the external callback
        if (lifecycleCallback.hasSubscribers) {
            lifecycleCallback.fireOnMain { it.onDidDismiss(InAppMessageLifecycleEvent(message)) }
        }

        _state.inAppMessageIdShowing = null

        // Display the next message in the queue, or attempt to add more IAMs to the queue
        if (messageDisplayQueue.isNotEmpty()) {
            Logging.debug("InAppMessagesManager.messageWasDismissed: In app message on queue available, attempting to show")
            attemptToShowInAppMessage()
        } else {
            Logging.debug("InAppMessagesManager.messageWasDismissed: In app message dismissed evaluating messages")
            evaluateInAppMessages()
        }
    }

    /**
     * Part of redisplay logic
     *
     *
     * Make all messages with redisplay available if:
     * - Already displayed
     * - At least one existing Trigger has changed OR a new trigger is added when there is only dynamic trigger
     */
    private fun makeRedisplayMessagesAvailableWithTriggers(
        newTriggersKeys: Collection<String>,
        isNewTriggerAdded: Boolean,
    ) {
        synchronized(messages) {
            for (message in messages) {
                val isMessageDisplayed = redisplayedInAppMessages.contains(message)
                val isTriggerOnMessage =
                    _triggerController.isTriggerOnMessage(message, newTriggersKeys)
                val isOnlyDynamicTriggers =
                    _triggerController.messageHasOnlyDynamicTriggers(message)
                if (!message.isTriggerChanged && isMessageDisplayed && (isTriggerOnMessage || isNewTriggerAdded && isOnlyDynamicTriggers)) {
                    Logging.debug("InAppMessagesManager.makeRedisplayMessagesAvailableWithTriggers: Trigger changed for message: $message")
                    message.isTriggerChanged = true
                }
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
        val index = redisplayedInAppMessages.indexOf(message)
        if (index != -1) {
            redisplayedInAppMessages.set(index, message)
        } else {
            redisplayedInAppMessages.add(message)
        }

        Logging.debug("InAppMessagesManager.persistInAppMessage: $message with msg array data: $redisplayedInAppMessages")
    }

    override fun addTriggers(triggers: Map<String, String>) {
        Logging.debug("InAppMessagesManager.addTriggers(triggers: $triggers)")

        triggers.forEach { addTrigger(it.key, it.value) }
    }

    override fun addTrigger(
        key: String,
        value: String,
    ) {
        Logging.debug("InAppMessagesManager.addTrigger(key: $key, value: $value)")

        // Track triggers added early on cold start (before first fetch completes) for redisplay logic
        synchronized(earlySessionTriggers) {
            if (!hasCompletedFirstFetch) {
                Logging.verbose("InAppMessagesManager: Tracking trigger added early on cold start: $key")
                earlySessionTriggers.add(key)
            }
        }

        var triggerModel = _triggerModelStore.get(key)
        if (triggerModel != null) {
            triggerModel.value = value
        } else {
            triggerModel =
                com.onesignal.inAppMessages.internal.triggers
                    .TriggerModel()
            triggerModel.id = key
            triggerModel.key = key
            triggerModel.value = value
            _triggerModelStore.add(triggerModel)
        }
    }

    override fun removeTriggers(keys: Collection<String>) {
        Logging.debug("InAppMessagesManager.removeTriggers(keys: $keys)")

        keys.forEach { removeTrigger(it) }
    }

    override fun removeTrigger(key: String) {
        Logging.debug("InAppMessagesManager.removeTrigger(key: $key)")

        _triggerModelStore.remove(key)
    }

    override fun clearTriggers() {
        Logging.debug("InAppMessagesManager.clearTriggers()")
        _triggerModelStore.clear()
    }

    // IAM LIFECYCLE CALLBACKS
    override fun onMessageWillDisplay(message: InAppMessage) {
        if (!lifecycleCallback.hasSubscribers) {
            Logging.verbose("InAppMessagesManager.onMessageWillDisplay: inAppMessageLifecycleHandler is null")
            return
        }
        lifecycleCallback.fireOnMain { it.onWillDisplay(InAppMessageLifecycleEvent(message)) }
    }

    override fun onMessageWasDisplayed(message: InAppMessage) {
        if (lifecycleCallback.hasSubscribers) {
            lifecycleCallback.fireOnMain { it.onDidDisplay(InAppMessageLifecycleEvent(message)) }
        } else {
            Logging.verbose("InAppMessagesManager.onMessageWasDisplayed: inAppMessageLifecycleHandler is null")
        }

        if (message.isPreview) {
            return
        }

        // Check that the messageId is in impressioned messages so we return early without a second post being made
        if (impressionedMessages.contains(message.messageId)) return

        // Add the messageId to impressioned messages so no second request is made
        impressionedMessages.add(message.messageId)

        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return

        suspendifyOnIO {
            try {
                _backend.sendIAMImpression(
                    _configModelStore.model.appId,
                    _subscriptionManager.subscriptions.push.id,
                    variantId,
                    message.messageId,
                )

                _prefs.impressionesMessagesId = impressionedMessages
            } catch (ex: BackendException) {
                // Post failed, impressioned messages should be removed and this way another post can be attempted
                impressionedMessages.remove(message.messageId)
            }
        }
    }

    override fun onMessageActionOccurredOnPreview(
        message: InAppMessage,
        action: InAppMessageClickResult,
    ) {
        suspendifyOnIO {
            action.isFirstClick = message.takeActionAsUnique()

            firePublicClickHandler(message, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            logInAppMessagePreviewActions(action)
        }
    }

    override fun onMessageActionOccurredOnMessage(
        message: InAppMessage,
        action: InAppMessageClickResult,
    ) {
        suspendifyOnIO {
            action.isFirstClick = message.takeActionAsUnique()
            firePublicClickHandler(message, action)
            beginProcessingPrompts(message, action.prompts)
            fireClickAction(action)
            fireRESTCallForClick(message, action)
            fireTagCallForClick(action)
            fireOutcomesForClick(message.messageId, action.outcomes)
        }
    }

    override fun onMessagePageChanged(
        message: InAppMessage,
        page: InAppMessagePage,
    ) {
        if (message.isPreview) {
            return
        }

        suspendifyOnIO {
            fireRESTCallForPageChange(message, page)
        }
    }

    override fun onMessageWillDismiss(message: InAppMessage) {
        if (!lifecycleCallback.hasSubscribers) {
            Logging.verbose("InAppMessagesManager.onMessageWillDismiss: inAppMessageLifecycleHandler is null")
            return
        }
        lifecycleCallback.fireOnMain { it.onWillDismiss(InAppMessageLifecycleEvent(message)) }
    }

    override fun onMessageWasDismissed(message: InAppMessage) {
        suspendifyOnIO {
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
        Logging.debug("InAppMessagesManager.onTriggerCompleted: called with triggerId: $triggerId")
        val triggerIds: MutableSet<String> = HashSet()
        triggerIds.add(triggerId)
    }

    /**
     * Dynamic trigger logic
     *
     * This will re evaluate messages due to dynamic triggers evaluating to true potentially
     *
     * @see OSInAppMessageController.setDataForRedisplay
     */
    override fun onTriggerConditionChanged(triggerId: String) {
        Logging.debug("InAppMessagesManager.onTriggerConditionChanged()")

        makeRedisplayMessagesAvailableWithTriggers(listOf(triggerId), false)

        suspendifyOnDefault {
            // This method is called when a time-based trigger timer fires, meaning the message can
            //  probably be shown now. So the current message conditions should be re-evaluated
            evaluateInAppMessages()
        }
    }

    override fun onTriggerChanged(newTriggerKey: String) {
        Logging.debug("InAppMessagesManager.onTriggerChanged(newTriggerKey: $newTriggerKey)")

        makeRedisplayMessagesAvailableWithTriggers(listOf(newTriggerKey), true)

        suspendifyOnDefault {
            // This method is called when a time-based trigger timer fires, meaning the message can
            //  probably be shown now. So the current message conditions should be re-evaluated
            evaluateInAppMessages()
        }
    }

    // END TRIGGER FIRED CALLBACKS

    private suspend fun beginProcessingPrompts(
        message: InAppMessage,
        prompts: List<InAppMessagePrompt>,
    ) {
        if (prompts.isNotEmpty()) {
            Logging.debug("InAppMessagesManager.beginProcessingPrompts: IAM showing prompts from IAM: $message")

            // TODO until we don't fix the activity going forward or back dismissing the IAM, we need to auto dismiss
            _displayer.dismissCurrentInAppMessage()
            showMultiplePrompts(message, prompts)
        }
    }

    private suspend fun fireOutcomesForClick(
        messageId: String,
        outcomes: List<InAppMessageOutcome>,
    ) {
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

    private fun fireTagCallForClick(action: InAppMessageClickResult) {
        if (action.tags != null) {
            val tags = action.tags
            if (tags?.tagsToAdd != null) {
                val tagsAsMap = JSONUtils.newStringMapFromJSONObject(tags.tagsToAdd!!)
                _userManager.addTags(tagsAsMap)
            }

            if (tags?.tagsToRemove != null) {
                val tagKeys = JSONUtils.newStringSetFromJSONArray(tags?.tagsToRemove!!)
                _userManager.removeTags(tagKeys)
            }
        }
    }

    private suspend fun showMultiplePrompts(
        inAppMessage: InAppMessage,
        prompts: List<InAppMessagePrompt>,
    ) {
        for (prompt in prompts) {
            // Don't show prompt twice
            if (!prompt.hasPrompted()) {
                _state.currentPrompt = prompt

                Logging.debug("InAppMessagesManager.showMultiplePrompts: IAM prompt to handle: " + _state.currentPrompt.toString())
                _state.currentPrompt!!.setPrompted(true)
                val result = _state.currentPrompt!!.handlePrompt()
                _state.currentPrompt = null
                Logging.debug("InAppMessagesManager.showMultiplePrompts: IAM prompt to handle finished with result: $result")

                // On preview mode we show informative alert dialogs
                if (inAppMessage.isPreview && result == InAppMessagePrompt.PromptActionResult.LOCATION_PERMISSIONS_MISSING_MANIFEST) {
                    showAlertDialogMessage(inAppMessage, prompts)
                    break
                }
            }
        }

        if (_state.currentPrompt == null) {
            Logging.debug("InAppMessagesManager.showMultiplePrompts: No IAM prompt to handle, dismiss message: " + inAppMessage.messageId)
            messageWasDismissed(inAppMessage)
        }
    }

    private fun fireClickAction(action: InAppMessageClickResult) {
        if (action.url != null && action.url.isNotEmpty()) {
            if (action.urlTarget == InAppMessageActionUrlType.BROWSER) {
                AndroidUtils.openURLInBrowser(_applicationService.appContext, action.url)
            } else if (action.urlTarget == InAppMessageActionUrlType.IN_APP_WEBVIEW) {
                OneSignalChromeTab.open(action.url, true, _applicationService.appContext)
            }
        }
    }

    // End IAM Lifecycle methods
    private fun logInAppMessagePreviewActions(action: InAppMessageClickResult) {
        if (action.tags != null) {
            Logging.debug(
                "InAppMessagesManager.logInAppMessagePreviewActions: Tags detected inside of the action click payload, ignoring because action came from IAM preview:: " +
                    action.tags.toString(),
            )
        }

        if (action.outcomes.size > 0) {
            Logging.debug(
                "InAppMessagesManager.logInAppMessagePreviewActions: Outcomes detected inside of the action click payload, ignoring because action came from IAM preview: " +
                    action.outcomes.toString(),
            )
        }

        // TODO: Add more action payload preview logs here in future
    }

    private suspend fun firePublicClickHandler(
        message: InAppMessage,
        action: InAppMessageClickResult,
    ) {
        if (!messageClickCallback.hasSubscribers) {
            return
        }

        // Send public outcome not from handler
        // Check that only on the handler
        // Any outcome sent on this callback should count as DIRECT from this IAM
        _influenceManager.onDirectInfluenceFromIAM(message.messageId)
        val result = InAppMessageClickEvent(message, action)
        messageClickCallback.suspendingFireOnMain { it.onClick(result) }
    }

    private suspend fun fireRESTCallForPageChange(
        message: InAppMessage,
        page: InAppMessagePage,
    ) {
        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return
        val pageId = page.pageId
        val messagePrefixedPageId = message.messageId + pageId

        // Never send multiple page impressions for the same message UUID unless that page change is from an IAM with redisplay
        if (viewedPageIds.contains(messagePrefixedPageId)) {
            Logging.verbose("InAppMessagesManager: Already sent page impression for id: $pageId")
            return
        }
        viewedPageIds.add(messagePrefixedPageId)

        try {
            _backend.sendIAMPageImpression(
                _configModelStore.model.appId,
                _subscriptionManager.subscriptions.push.id,
                variantId,
                message.messageId,
                pageId,
            )

            _prefs.viewPageImpressionedIds = viewedPageIds
        } catch (ex: BackendException) {
            // Post failed, viewed page should be removed and this way another post can be attempted
            viewedPageIds.remove(messagePrefixedPageId)
        }
    }

    private suspend fun fireRESTCallForClick(
        message: InAppMessage,
        action: InAppMessageClickResult,
    ) {
        val variantId = InAppHelper.variantIdForMessage(message, _languageContext) ?: return
        val clickId = action.clickId

        // If IAM has redisplay the clickId may be available
        val clickAvailableByRedisplay = message.redisplayStats.isRedisplayEnabled && clickId != null && message.isClickAvailable(clickId)

        // Never count multiple clicks for the same click UUID unless that click is from an IAM with redisplay
        if (!clickAvailableByRedisplay && clickedClickIds.contains(clickId)) {
            return
        }

        if (clickId != null) {
            clickedClickIds.add(clickId)
            // Track clickId per IAM
            message.addClickId(clickId)
        }

        try {
            _backend.sendIAMClick(
                _configModelStore.model.appId,
                _subscriptionManager.subscriptions.push.id,
                variantId,
                message.messageId,
                clickId,
                action.isFirstClick,
            )

            // Persist success click to disk. Id already added to set before making the network call
            _prefs.clickedMessagesId = clickedClickIds
        } catch (ex: BackendException) {
            clickedClickIds.remove(clickId)

            if (clickId != null) {
                message.removeClickId(clickId)
            }
        }
    }

    private fun showAlertDialogMessage(
        inAppMessage: InAppMessage,
        prompts: List<InAppMessagePrompt>,
    ) {
        val messageTitle = _applicationService.appContext.getString(R.string.location_permission_missing_title)
        val message = _applicationService.appContext.getString(R.string.location_permission_missing_message)
        AlertDialog
            .Builder(_applicationService.current)
            .setTitle(messageTitle)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> suspendifyOnIO { showMultiplePrompts(inAppMessage, prompts) } }
            .show()
    }

    override fun onFocus(firedOnSubscribe: Boolean) { }

    override fun onUnfocused() { }
}
