package com.onesignal.inAppMessages.internal.display.impl

import android.annotation.TargetApi
import android.app.Activity
import android.os.Build
import android.util.Base64
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.common.InAppHelper
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.session.internal.influence.IInfluenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.
// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.
@TargetApi(Build.VERSION_CODES.KITKAT)
internal class InAppDisplayer(
    private val _applicationService: IApplicationService,
    private val _lifecycle: IInAppLifecycleService,
    private val _promptFactory: IInAppMessagePromptFactory,
    private val _backend: IInAppBackendService,
    private val _influenceManager: IInfluenceManager,
    private val _configModelStore: ConfigModelStore,
    private val _languageContext: ILanguageContext,
    private val _time: ITime,
) : IInAppDisplayer {
    private var lastInstance: WebViewManager? = null

    override suspend fun displayMessage(message: InAppMessage): Boolean? {
        var response =
            _backend.getIAMData(
                _configModelStore.model.appId,
                message.messageId,
                InAppHelper.variantIdForMessage(message, _languageContext),
            )

        if (response.content != null) {
            message.displayDuration = response.content!!.displayDuration!!
            _influenceManager.onInAppMessageDisplayed(message.messageId)
            showMessageContent(message, response.content!!)
            return true
        } else {
            return if (response.shouldRetry) {
                // Retry displaying the same IAM
                // Using the queueMessageForDisplay method follows safety checks to prevent issues
                // like having 2 IAMs showing at once or duplicate IAMs in the queue
                null
            } else {
                false
            }
        }
    }

    override suspend fun displayPreviewMessage(previewUUID: String): Boolean {
        val message = InAppMessage(true, _time)
        val content = _backend.getIAMPreviewData(_configModelStore.model.appId, previewUUID)

        return if (content == null) {
            false
        } else {
            message.displayDuration = content.displayDuration!!
            showMessageContent(message, content)
            true
        }
    }

    /**
     * Creates a new WebView
     * Dismiss WebView if already showing one and the new one is a Preview
     *
     * @param message the message to show
     * @param content the html to display on the WebView
     */
    private suspend fun showMessageContent(
        message: InAppMessage,
        content: InAppMessageContent,
    ) {
        val currentActivity = _applicationService.current
        Logging.debug("InAppDisplayer.showMessageContent: in app message on currentActivity: $currentActivity")

        /* IMPORTANT
         * This is the starting route for grabbing the current Activity and passing it to InAppMessageView */
        if (currentActivity != null) {
            // Only a preview will be dismissed, this prevents normal messages from being
            // removed when a preview is sent into the app
            if (lastInstance != null && message.isPreview) {
                // Created a callback for dismissing a message and preparing the next one
                lastInstance!!.dismissAndAwaitNextMessage()
                lastInstance = null
                initInAppMessage(currentActivity, message, content)
            } else {
                initInAppMessage(currentActivity, message, content)
            }
            return
        }

        delay(IN_APP_MESSAGE_INIT_DELAY.toLong())
        showMessageContent(message, content)
    }

    override fun dismissCurrentInAppMessage() {
        Logging.debug("WebViewManager IAM dismissAndAwaitNextMessage lastInstance: $lastInstance")

        if (lastInstance != null) {
            lastInstance!!.backgroundDismissAndAwaitNextMessage()
        }
    }

    private suspend fun initInAppMessage(
        currentActivity: Activity,
        message: InAppMessage,
        content: InAppMessageContent,
    ) {
        try {
            val base64Str =
                Base64.encodeToString(
                    content.contentHtml!!.toByteArray(charset("UTF-8")),
                    Base64.NO_WRAP,
                )
            val webViewManager = WebViewManager(message, currentActivity, content, _lifecycle, _applicationService, _promptFactory)
            lastInstance = webViewManager

            if (content.isFullBleed) {
                webViewManager.setContentSafeAreaInsets(content, currentActivity)
            }

            // Web view must be created on the main thread.
            withContext(Dispatchers.Main) {
                // Handles exception "MissingWebViewPackageException: Failed to load WebView provider: No WebView installed"
                try {
                    webViewManager.setupWebView(currentActivity, base64Str, content.isFullBleed)
                } catch (e: Exception) {
                    // Need to check error message to only catch MissingWebViewPackageException as it isn't public
                    if (e.message != null && e.message!!.contains("No WebView installed")) {
                        Logging.info("Error setting up WebView: ", e)
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: UnsupportedEncodingException) {
            Logging.error("Catch on initInAppMessage: ", e)
        }
    }

    companion object {
        private const val IN_APP_MESSAGE_INIT_DELAY = 200
    }
}
