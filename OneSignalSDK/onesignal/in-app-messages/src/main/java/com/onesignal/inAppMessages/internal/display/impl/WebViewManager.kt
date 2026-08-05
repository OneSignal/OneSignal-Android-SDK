package com.onesignal.inAppMessages.internal.display.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import com.onesignal.common.AndroidUtils
import com.onesignal.common.ViewUtils
import com.onesignal.common.safeString
import com.onesignal.common.threading.suspendifyOnDefault
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageClickResult
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.InAppMessagePage
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

// Manages WebView instances by pre-loading them, displaying them, and closing them when dismissed.
//   Includes a static map for pre-loading, showing, and dismissed so these events can't be duplicated.
// Flow for Displaying WebView
// 1. showHTMLString - Creates WebView and loads page.
// 2. Wait for JavaScriptInterface.postMessage to fire with "rendering_complete"
// 3. This calls showActivity which starts a new WebView
// 4. WebViewActivity will call WebViewManager.instanceFromIam(...) to get this instance and
//       add it's prepared WebView add add it to the Activity.
internal class WebViewManager(
    private val message: InAppMessage,
    private var activity: Activity,
    private val messageContent: InAppMessageContent,
    private val _lifecycle: IInAppLifecycleService,
    private val _applicationService: IApplicationService,
    private val _promptFactory: IInAppMessagePromptFactory,
) : IActivityLifecycleHandler {
    private val messageViewMutex: Mutex = Mutex()

    internal enum class Position {
        TOP_BANNER,
        BOTTOM_BANNER,
        CENTER_MODAL,
        FULL_SCREEN,
        ;

        val isBanner: Boolean
            get() {
                when (this) {
                    TOP_BANNER, BOTTOM_BANNER -> return true
                    else -> {
                        return false
                    }
                }
                return false
            }
    }

    private var webView: OSWebView? = null
    private var messageView: InAppMessageView? = null
    private var currentActivityName: String? = null
    private var lastPageHeight: Int? = null

    // Serializes setup / dismiss so a dismiss during WebView creation cannot leave orphans
    // or race createNewInAppMessageView(webView!!).
    private val lifecycleLock = Any()

    // Terminal: once true, this WebViewManager never shows content again.
    @Volatile
    private var dismissed = false

    // Ensures finishDismiss runs exactly once.
    private var cleanedUp = false

    // Ensures messageWasDismissed lifecycle is fired exactly once.
    private var lifecycleDismissNotified = false

    // closing prevents IAM being redisplayed when the activity changes during an actionHandler
    private var closing = false

    // Invoked once after dismiss cleanup so owners can drop references (e.g. InAppDisplayer.lastInstance)
    var onDismissed: (() -> Unit)? = null

    // Lets JS from the page send JSON payloads to this class
    internal inner class OSJavaScriptInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            try {
                Logging.debug("OSJavaScriptInterface:postMessage: $message")
                val jsonObject = JSONObject(message)
                val messageType = jsonObject.getString(EVENT_TYPE_KEY)
                when (messageType) {
                    EVENT_TYPE_RENDERING_COMPLETE -> handleRenderComplete(jsonObject)
                    EVENT_TYPE_ACTION_TAKEN -> // Added handling so that click actions won't trigger while dragging the IAM
                        if (messageView?.isDragging == false) handleActionTaken(jsonObject)
                    EVENT_TYPE_RESIZE -> {}
                    EVENT_TYPE_PAGE_CHANGE -> handlePageChange(jsonObject)
                    else -> {}
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        private fun handleRenderComplete(jsonObject: JSONObject) {
            if (dismissed) {
                return
            }
            val displayType = getDisplayLocation(jsonObject)
            val pageHeight =
                if (displayType == Position.FULL_SCREEN) -1 else getPageHeightData(jsonObject)
            val dragToDismissDisabled = getDragToDismissDisabled(jsonObject)
            messageContent.displayLocation = displayType
            messageContent.pageHeight = pageHeight
            createNewInAppMessageView(dragToDismissDisabled)
        }

        private fun getPageHeightData(jsonObject: JSONObject): Int {
            return try {
                pageRectToViewHeight(
                    activity,
                    jsonObject.getJSONObject(IAM_PAGE_META_DATA_KEY),
                )
            } catch (e: JSONException) {
                -1
            }
        }

        private fun getDisplayLocation(jsonObject: JSONObject): Position {
            var displayLocation = Position.FULL_SCREEN
            try {
                if (jsonObject.has(IAM_DISPLAY_LOCATION_KEY) && jsonObject[IAM_DISPLAY_LOCATION_KEY] != "") {
                    displayLocation =
                        Position.valueOf(
                            jsonObject.optString(
                                IAM_DISPLAY_LOCATION_KEY,
                                "FULL_SCREEN",
                            ).uppercase(Locale.getDefault()),
                        )
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return displayLocation
        }

        private fun getDragToDismissDisabled(jsonObject: JSONObject): Boolean {
            return try {
                jsonObject.getBoolean(IAM_DRAG_TO_DISMISS_DISABLED_KEY)
            } catch (e: JSONException) {
                false
            }
        }

        @Throws(JSONException::class)
        private fun handleActionTaken(jsonObject: JSONObject) {
            val body = jsonObject.getJSONObject("body")
            val id = body.safeString("id")
            closing = body.getBoolean("close")
            if (message.isPreview) {
                var action = InAppMessageClickResult(body, _promptFactory)
                _lifecycle.messageActionOccurredOnPreview(message, action)
            } else if (id != null) {
                var action = InAppMessageClickResult(body, _promptFactory)
                _lifecycle.messageActionOccurredOnMessage(message, action)
            }
            if (closing) {
                backgroundDismissAndAwaitNextMessage()
            }
        }

        @Throws(JSONException::class)
        private fun handlePageChange(jsonObject: JSONObject) {
            val page = InAppMessagePage(jsonObject)
            _lifecycle.messagePageChanged(message, page)
        }
    }

    private fun pageRectToViewHeight(
        activity: Activity,
        jsonObject: JSONObject,
    ): Int {
        // SDK-4494: avoid throw-then-catch on `rect` being absent. The IAM HTML's
        // `getPageMetaData()` can legitimately return a payload without `rect` for
        // benign/recoverable reasons (e.g. JS not yet defined when the activity
        // rotates, custom IAM template, partial metadata). The previous
        // `getJSONObject("rect")` raised `JSONException` which we caught and logged
        // at ERROR with a full stack trace, flooding OTel/Datadog with non-actionable
        // alerts. Use `optJSONObject` and `optInt` so missing fields are a structured
        // null/sentinel instead, and downgrade the log to a single WARN line.
        val rect = jsonObject.optJSONObject("rect")
        val pageHeight = rect?.optInt("height", -1) ?: -1
        if (pageHeight < 0) {
            Logging.warn(
                "pageRectToViewHeight could not get page height (missing/invalid 'rect.height'); " +
                    "snippet=${bodySnippet(jsonObject.toString())}",
            )
            return -1
        }
        var pxHeight = ViewUtils.dpToPx(pageHeight)
        Logging.debug("getPageHeightData:pxHeight: $pxHeight")
        val maxPxHeight = getWebViewMaxSizeY(activity)
        if (pxHeight > maxPxHeight) {
            pxHeight = maxPxHeight
            Logging.debug("getPageHeightData:pxHeight is over screen max: $maxPxHeight")
        }
        return pxHeight
    }

    /**
     * Trim [body] to a short, single-line snippet safe for logcat / OTel. See
     * SDK-4494 - we only want enough context to debug shape mismatches without
     * dumping the full WebView payload into log pipelines.
     */
    private fun bodySnippet(body: String?): String {
        if (body.isNullOrEmpty()) return "<empty>"
        val flattened = body.replace('\n', ' ').replace('\r', ' ')
        return if (flattened.length <= LOG_BODY_SNIPPET_MAX_CHARS) {
            flattened
        } else {
            flattened.take(LOG_BODY_SNIPPET_MAX_CHARS) + "…"
        }
    }

    private suspend fun updateSafeAreaInsets() {
        withContext(Dispatchers.Main) {
            val localWebView = webView ?: return@withContext
            val insets = ViewUtils.getCutoutAndStatusBarInsets(activity)
            val safeAreaInsetsObject =
                String.format(
                    SAFE_AREA_JS_OBJECT,
                    insets[0],
                    insets[1],
                    insets[2],
                    insets[3],
                )
            val safeAreaInsetsFunction =
                String.format(
                    SET_SAFE_AREA_INSETS_JS_FUNCTION,
                    safeAreaInsetsObject,
                )
            localWebView.evaluateJavascript(safeAreaInsetsFunction, null)
        }
    }

    // Every time an Activity is shown we update the height of the WebView since the available
    //   screen size may have changed. (Expect for Fullscreen)
    private suspend fun calculateHeightAndShowWebViewAfterNewActivity() {
        if (dismissed || messageView == null || webView == null) return

        // Don't need a CSS / HTML height update for fullscreen unless its fullbleed
        if (messageView!!.displayPosition == Position.FULL_SCREEN && !messageContent.isFullBleed) {
            showMessageView(null)
            return
        }
        Logging.debug("In app message new activity, calculate height and show ")

        _applicationService.waitUntilActivityReady()

        val localWebView = webView
        if (dismissed || localWebView == null) {
            return
        }

        // At time point the webView isn't attached to a view
        // Set the WebView to the max screen size then run JS to evaluate the height.
        setWebViewToMaxSize(activity, localWebView)
        if (messageContent.isFullBleed) {
            updateSafeAreaInsets()
        }

        localWebView.evaluateJavascript(GET_PAGE_META_DATA_JS_FUNCTION) { value ->
            // SDK-4494: `evaluateJavascript` returns the JSON-encoded result of the
            // expression. When the JS function is undefined or returns `undefined`
            // (e.g. WebView not fully loaded yet) the callback receives the literal
            // string "null", which `JSONObject(...)` rejects. Bail out early instead
            // of throwing+catching, and route any remaining surprise through Logging
            // (was previously `e.printStackTrace()`, which bypassed our log pipeline).
            if (value.isNullOrBlank() || value == "null") {
                Logging.warn(
                    "calculateHeightAndShowWebViewAfterNewActivity: empty/null page metadata " +
                        "from WebView; skipping height update",
                )
                return@evaluateJavascript
            }
            try {
                val pagePxHeight = pageRectToViewHeight(activity, JSONObject(value))

                suspendifyOnIO {
                    showMessageView(pagePxHeight)
                }
            } catch (e: JSONException) {
                Logging.warn(
                    "calculateHeightAndShowWebViewAfterNewActivity: could not parse page metadata; " +
                        "snippet=${bodySnippet(value)}",
                    e,
                )
            }
        }
    }

    override fun onActivityAvailable(activity: Activity) {
        val lastActivityName = currentActivityName
        this.activity = activity
        currentActivityName = activity.localClassName
        Logging.debug("In app message activity available currentActivityName: $currentActivityName lastActivityName: $lastActivityName")

        suspendifyOnMain {
            if (lastActivityName == null) {
                showMessageView(null)
            } else if (lastActivityName != currentActivityName) {
                if (!closing) {
                    // Navigate to new activity while displaying current IAM
                    if (messageView != null) {
                        messageView!!.removeAllViews()
                    }
                    showMessageView(lastPageHeight)
                }
            } else {
                // Activity rotated
                calculateHeightAndShowWebViewAfterNewActivity()
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Logging.debug(
            """
            In app message activity stopped, cleaning views, currentActivityName: $currentActivityName
            activity: ${this.activity}
            messageView: $messageView
            """.trimIndent(),
        )
        if (messageView != null && activity.localClassName == currentActivityName) {
            messageView!!.removeAllViews()
        }
    }

    private suspend fun showMessageView(newHeight: Int?) {
        messageViewMutex.withLock {
            if (dismissed) {
                return
            }
            val localMessageView = messageView
            val localWebView = webView
            if (localMessageView == null || localWebView == null) {
                Logging.warn("No messageView found to update a with a new height.")
                return
            }
            Logging.debug("In app message, showing first one with height: $newHeight")

            localMessageView.setWebView(localWebView)
            if (newHeight != null) {
                lastPageHeight = newHeight
                localMessageView.updateHeight(newHeight)
            }
            // showView does not return until in-app is dismissed
            localMessageView.showView(activity)
            localMessageView.checkIfShouldDismiss()
        }
    }

    suspend fun setupWebView(
        currentActivity: Activity,
        base64Message: String,
        isFullScreen: Boolean,
    ) {
        if (dismissed) {
            return
        }

        enableWebViewRemoteDebugging()
        val localWebView = OSWebView(currentActivity)
        localWebView.overScrollMode = View.OVER_SCROLL_NEVER
        localWebView.isVerticalScrollBarEnabled = false
        localWebView.isHorizontalScrollBarEnabled = false
        secureSetup(localWebView)

        // Setup receiver for page events / data from JS
        localWebView.addJavascriptInterface(OSJavaScriptInterface(), JS_OBJ_NAME)
        if (isFullScreen) {
            localWebView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                localWebView.fitsSystemWindows = false
            }
        }

        synchronized(lifecycleLock) {
            if (dismissed) {
                destroyWebViewInstance(localWebView)
                return
            }
            webView = localWebView
        }

        _lifecycle.messageWillDisplay(message)
        _applicationService.waitUntilActivityReady()

        synchronized(lifecycleLock) {
            if (dismissed || webView !== localWebView) {
                destroyWebViewInstance(localWebView)
                return
            }
            setWebViewToMaxSize(currentActivity, localWebView)
            localWebView.loadData(base64Message, "text/html; charset=utf-8", "base64")
        }
    }

    /**
     * Applies security hardening to the WebView to prevent common vulnerabilities.
     *
     * Security measures:
     * - JavaScript is enabled for IAM functionality but file access is completely blocked
     * - Prevents file:// URL access to mitigate local file inclusion attacks
     * - Blocks cross-origin access from file URLs to prevent data exfiltration
     * - Disables mixed content (HTTP resources on HTTPS pages) to prevent MITM attacks
     *
     * This configuration protects against:
     * 1. Malicious JavaScript accessing local device files
     * 2. Cross-site scripting (XSS) attacks via file:// protocol
     * 3. Man-in-the-middle attacks via downgraded HTTP content
     *
     * @SuppressLint is used because JavaScript is required for IAM functionality,
     * but we mitigate the risk through strict file access controls.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun secureSetup(webView: WebView) =
        with(webView.settings) {
            javaScriptEnabled = true
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

    // This sets the WebView view port sizes to the max screen sizes so the initialize
    //   max content height can be calculated.
    // A render complete or resize event will fire from JS to tell Java it's height and will then display
    //  it via this SDK's InAppMessageView class. If smaller than the screen it will correctly
    //  set it's height to match.
    private fun setWebViewToMaxSize(
        activity: Activity,
        targetWebView: OSWebView = webView!!,
    ) {
        targetWebView.layout(0, 0, getWebViewMaxSizeX(activity), getWebViewMaxSizeY(activity))
    }

    private fun setMessageView(view: InAppMessageView?) {
        messageView = view
    }

    fun createNewInAppMessageView(dragToDismissDisabled: Boolean) {
        val currentWebView: OSWebView
        synchronized(lifecycleLock) {
            if (dismissed) {
                return
            }
            currentWebView = webView ?: return
            lastPageHeight = messageContent.pageHeight
            val hideGrayOverlay =
                AndroidUtils.getManifestMetaBoolean(
                    _applicationService.appContext,
                    "com.onesignal.inAppMessageHideGrayOverlay",
                )
            val newView = InAppMessageView(currentWebView, messageContent, dragToDismissDisabled, hideGrayOverlay)
            setMessageView(newView)
            val self = this
            newView.setMessageController(
                object : InAppMessageView.InAppMessageViewListener {
                    override fun onMessageWasDisplayed() {
                        _lifecycle.messageWasDisplayed(message)
                    }

                    override fun onMessageWillDismiss() {
                        _lifecycle.messageWillDismiss(message)
                    }

                    override fun onMessageWasDismissed() {
                        // Mark dismissed first so in-flight setup cannot recreate content,
                        // then finish cleanup + lifecycle notification exactly once.
                        synchronized(self.lifecycleLock) {
                            self.dismissed = true
                            self.closing = true
                        }
                        self.finishDismiss()
                    }
                },
            )
        }

        // Fires event if available, which will call messageView.showInAppMessageView() for us.
        _applicationService.addActivityLifecycleHandler(this)
        synchronized(lifecycleLock) {
            // Dismiss may have completed between view creation and registration.
            if (dismissed) {
                _applicationService.removeActivityLifecycleHandler(this)
            }
        }
    }

    private fun getWebViewMaxSizeX(activity: Activity): Int {
        if (messageContent.isFullBleed) {
            return ViewUtils.getFullbleedWindowWidth(activity)
        }
        val margin = MARGIN_PX_SIZE * 2
        return ViewUtils.getWindowWidth(activity) - margin
    }

    private fun getWebViewMaxSizeY(activity: Activity): Int {
        val margin = if (messageContent.isFullBleed) 0 else MARGIN_PX_SIZE * 2
        return ViewUtils.getWindowHeight(activity) - margin
    }

    fun backgroundDismissAndAwaitNextMessage() {
        suspendifyOnDefault {
            dismissAndAwaitNextMessage()
        }
    }

    /**
     * Trigger the [.messageView] dismiss animation flow when present, then always complete
     * cleanup. Safe under concurrent callers: [dismissed] is terminal and [finishDismiss]
     * runs once.
     */
    suspend fun dismissAndAwaitNextMessage() {
        val locMessageView: InAppMessageView?
        synchronized(lifecycleLock) {
            if (dismissed) {
                return
            }
            dismissed = true
            closing = true
            locMessageView = messageView
        }

        _lifecycle.messageWillDismiss(message)
        locMessageView?.dismissAndAwaitNextMessage()
        finishDismiss()
    }

    /**
     * Completes dismiss exactly once: destroys the WebView, clears owner refs, and fires
     * [IInAppLifecycleService.messageWasDismissed] so IAM queue state cannot get stuck.
     */
    private fun finishDismiss() {
        val callback: (() -> Unit)?
        val shouldNotifyLifecycle: Boolean
        val viewToDestroy: OSWebView?

        synchronized(lifecycleLock) {
            dismissed = true
            closing = true
            if (cleanedUp) {
                return
            }
            cleanedUp = true

            shouldNotifyLifecycle = !lifecycleDismissNotified
            lifecycleDismissNotified = true

            _applicationService.removeActivityLifecycleHandler(this)
            setMessageView(null)
            viewToDestroy = webView
            webView = null

            callback = onDismissed
            onDismissed = null
        }

        if (viewToDestroy != null) {
            destroyWebViewInstance(viewToDestroy)
        }

        if (shouldNotifyLifecycle) {
            _lifecycle.messageWasDismissed(message)
        }
        callback?.invoke()
    }

    private fun destroyWebViewInstance(view: OSWebView) {
        val destroy = {
            try {
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            } catch (t: Throwable) {
                Logging.warn("Error destroying IAM WebView", t)
            }
        }
        // WebView.destroy() must run on the main thread
        if (AndroidUtils.isRunningOnMainThread()) {
            destroy()
        } else {
            Handler(Looper.getMainLooper()).post(destroy)
        }
    }

    fun setContentSafeAreaInsets(
        content: InAppMessageContent,
        activity: Activity,
    ) {
        var html = content.contentHtml
        var safeAreaInsetsScript = SET_SAFE_AREA_INSETS_SCRIPT
        val insets = ViewUtils.getCutoutAndStatusBarInsets(activity)
        val safeAreaJSObject =
            String.format(
                SAFE_AREA_JS_OBJECT,
                insets[0],
                insets[1],
                insets[2],
                insets[3],
            )
        safeAreaInsetsScript = String.format(safeAreaInsetsScript, safeAreaJSObject)
        html += safeAreaInsetsScript
        content.contentHtml = html
    }

    // Allow Chrome Remote Debugging if OneSignal.LOG_LEVEL.DEBUG or higher
    private fun enableWebViewRemoteDebugging() {
        if (Logging.atLogLevel(LogLevel.DEBUG)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    companion object {
        private val MARGIN_PX_SIZE = ViewUtils.dpToPx(24)

        // SDK-4494: cap the body snippet included in WARN logs so a malformed/large
        // WebView payload can't blow up the OTel log entry. Same pattern as
        // FeatureFlagsBackendService.
        private const val LOG_BODY_SNIPPET_MAX_CHARS = 200

        const val JS_OBJ_NAME = "OSAndroid"
        const val GET_PAGE_META_DATA_JS_FUNCTION = "getPageMetaData()"
        const val SET_SAFE_AREA_INSETS_JS_FUNCTION = "setSafeAreaInsets(%s)"
        const val SAFE_AREA_JS_OBJECT =
            "{\n" +
                "   top: %d,\n" +
                "   bottom: %d,\n" +
                "   right: %d,\n" +
                "   left: %d,\n" +
                "}"
        const val SET_SAFE_AREA_INSETS_SCRIPT =
            "\n\n" +
                "<script>\n" +
                "    setSafeAreaInsets(%s);\n" +
                "</script>"
        const val EVENT_TYPE_KEY = "type"
        const val EVENT_TYPE_RENDERING_COMPLETE = "rendering_complete"
        const val EVENT_TYPE_RESIZE = "resize"
        const val EVENT_TYPE_ACTION_TAKEN = "action_taken"
        const val EVENT_TYPE_PAGE_CHANGE = "page_change"
        const val IAM_DISPLAY_LOCATION_KEY = "displayLocation"
        const val IAM_PAGE_META_DATA_KEY = "pageMetaData"
        const val IAM_DRAG_TO_DISMISS_DISABLED_KEY = "dragToDismissDisabled"
    }
}
