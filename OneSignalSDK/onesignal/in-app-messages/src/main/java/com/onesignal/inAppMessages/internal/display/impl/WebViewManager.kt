package com.onesignal.inAppMessages.internal.display.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
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

    // dismissFired prevents onDidDismiss from getting called multiple times
    private var dismissFired = false

    // closing prevents IAM being redisplayed when the activity changes during an actionHandler
    private var closing = false

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
        return try {
            val pageHeight = jsonObject.getJSONObject("rect").getInt("height")
            var pxHeight = ViewUtils.dpToPx(pageHeight)
            Logging.debug("getPageHeightData:pxHeight: $pxHeight")
            val maxPxHeight = getWebViewMaxSizeY(activity)
            if (pxHeight > maxPxHeight) {
                pxHeight = maxPxHeight
                Logging.debug("getPageHeightData:pxHeight is over screen max: $maxPxHeight")
            }
            pxHeight
        } catch (e: JSONException) {
            Logging.error("pageRectToViewHeight could not get page height", e)
            -1
        }
    }

    private suspend fun updateSafeAreaInsets() {
        withContext(Dispatchers.Main) {
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
            webView!!.evaluateJavascript(safeAreaInsetsFunction, null)
        }
    }

    // Every time an Activity is shown we update the height of the WebView since the available
    //   screen size may have changed. (Expect for Fullscreen)
    private suspend fun calculateHeightAndShowWebViewAfterNewActivity() {
        if (messageView == null) return

        // Don't need a CSS / HTML height update for fullscreen unless its fullbleed
        if (messageView!!.displayPosition == Position.FULL_SCREEN && !messageContent.isFullBleed) {
            showMessageView(null)
            return
        }
        Logging.debug("In app message new activity, calculate height and show ")

        _applicationService.waitUntilActivityReady()

        // At time point the webView isn't attached to a view
        // Set the WebView to the max screen size then run JS to evaluate the height.
        setWebViewToMaxSize(activity)
        if (messageContent.isFullBleed) {
            updateSafeAreaInsets()
        }

        webView!!.evaluateJavascript(GET_PAGE_META_DATA_JS_FUNCTION) { value ->
            try {
                val pagePxHeight = pageRectToViewHeight(activity, JSONObject(value))

                suspendifyOnIO {
                    showMessageView(pagePxHeight)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
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
            if (messageView == null) {
                Logging.warn("No messageView found to update a with a new height.")
                return
            }
            Logging.debug("In app message, showing first one with height: $newHeight")

            messageView?.setWebView(webView!!)
            if (newHeight != null) {
                lastPageHeight = newHeight
                messageView?.updateHeight(newHeight)
            }
            // showView does not return until in-app is dismissed
            messageView?.showView(activity)
            messageView?.checkIfShouldDismiss()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    suspend fun setupWebView(
        currentActivity: Activity,
        base64Message: String,
        isFullScreen: Boolean,
    ) {
        enableWebViewRemoteDebugging()
        webView = OSWebView(currentActivity)
        webView!!.overScrollMode = View.OVER_SCROLL_NEVER
        webView!!.isVerticalScrollBarEnabled = false
        webView!!.isHorizontalScrollBarEnabled = false
        webView!!.settings.javaScriptEnabled = true

        // Setup receiver for page events / data from JS
        webView!!.addJavascriptInterface(OSJavaScriptInterface(), JS_OBJ_NAME)
        if (isFullScreen) {
            webView!!.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                webView!!.fitsSystemWindows = false
            }
        }

        _lifecycle.messageWillDisplay(message)
        _applicationService.waitUntilActivityReady()
        setWebViewToMaxSize(currentActivity)
        webView!!.loadData(base64Message, "text/html; charset=utf-8", "base64")
    }

    // This sets the WebView view port sizes to the max screen sizes so the initialize
    //   max content height can be calculated.
    // A render complete or resize event will fire from JS to tell Java it's height and will then display
    //  it via this SDK's InAppMessageView class. If smaller than the screen it will correctly
    //  set it's height to match.
    private fun setWebViewToMaxSize(activity: Activity) {
        webView!!.layout(0, 0, getWebViewMaxSizeX(activity), getWebViewMaxSizeY(activity))
    }

    private fun setMessageView(view: InAppMessageView?) {
        messageView = view
    }

    fun createNewInAppMessageView(dragToDismissDisabled: Boolean) {
        lastPageHeight = messageContent.pageHeight
        val hideGrayOverlay = AndroidUtils.getManifestMetaBoolean(_applicationService.appContext, "com.onesignal.inAppMessageHideGrayOverlay")
        val newView = InAppMessageView(webView!!, messageContent, dragToDismissDisabled, hideGrayOverlay)
        setMessageView(newView)
        val self = this
        messageView!!.setMessageController(
            object : InAppMessageView.InAppMessageViewListener {
                override fun onMessageWasDisplayed() {
                    _lifecycle.messageWasDisplayed(message)
                }

                override fun onMessageWillDismiss() {
                    _lifecycle.messageWillDismiss(message)
                }

                override fun onMessageWasDismissed() {
                    _lifecycle.messageWasDismissed(message)
                    _applicationService.removeActivityLifecycleHandler(self)
                }
            },
        )

        // Fires event if available, which will call messageView.showInAppMessageView() for us.
        _applicationService.addActivityLifecycleHandler(self)
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
     * Trigger the [.messageView] dismiss animation flow
     */
    suspend fun dismissAndAwaitNextMessage() {
        val locMessageView = messageView

        if (locMessageView == null || dismissFired) {
            return
        }

        dismissFired = true
        _lifecycle.messageWillDismiss(message)
        locMessageView.dismissAndAwaitNextMessage()
        dismissFired = false

        setMessageView(null)
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
