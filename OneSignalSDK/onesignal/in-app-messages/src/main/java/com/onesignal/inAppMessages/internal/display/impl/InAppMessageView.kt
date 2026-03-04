package com.onesignal.inAppMessages.internal.display.impl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.webkit.WebView
import android.widget.RelativeLayout
import androidx.cardview.widget.CardView
import androidx.core.widget.PopupWindowCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.common.ViewUtils
import com.onesignal.common.threading.Waiter
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Layout Documentation
 * ### Modals & Banners ###
 * - WebView
 * - width  = MATCH_PARENT
 * - height = PX height provided via a JS event for the content
 * - Parent Layouts
 * - width  = MATCH_PARENT
 * - height = WRAP_CONTENT - Since the WebView is providing the height.
 * ### Fullscreen ###
 * - WebView
 * - width  = MATCH_PARENT
 * - height = MATCH_PARENT
 * - Parent Layouts
 * - width  = MATCH_PARENT
 * - height = MATCH_PARENT
 */
internal class InAppMessageView(
    private var webView: WebView?,
    private val messageContent: InAppMessageContent,
    private val disableDragDismiss: Boolean,
    private val hideGrayOverlay: Boolean,
) {
    private var popupWindow: OSPopupWindow? = null

    internal interface InAppMessageViewListener {
        fun onMessageWasDisplayed()

        fun onMessageWillDismiss()

        fun onMessageWasDismissed()
    }

    private var currentActivity: Activity? = null
    private val pageWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var pageHeight: Int = messageContent.pageHeight
    private var marginPxSizeLeft = ViewUtils.dpToPx(24)
    private var marginPxSizeRight = ViewUtils.dpToPx(24)
    private var marginPxSizeTop = ViewUtils.dpToPx(24)
    private var marginPxSizeBottom = ViewUtils.dpToPx(24)
    val displayPosition: WebViewManager.Position = messageContent.displayLocation!!
    private val displayDuration: Double = if (messageContent.displayDuration == null) 0.0 else messageContent.displayDuration!!
    private val hasBackground: Boolean = !displayPosition.isBanner
    private var shouldDismissWhenActive = false

    /**
     * Simple getter to know when the MessageView is in a dragging state
     */
    var isDragging = false
        private set

    private var parentRelativeLayout: RelativeLayout? = null
    private var draggableRelativeLayout: DraggableRelativeLayout? = null
    private var messageController: InAppMessageViewListener? = null

    private var isDismissTimerSet: Boolean = false
    private var cancelDismissTimer: Boolean = false

    private val popupWindowListener =
        object : OSPopupWindow.PopupWindowListener {
            override fun onDismiss(wasDismissedManually: Boolean?) {
                if (wasDismissedManually != true) {
                    Logging.debug("PopupWindowListener.onDismiss called by the system.")
                    messageController?.onMessageWasDismissed()
                }
            }
        }

    init {
        setMarginsFromContent(messageContent)
    }

    /**
     * For now we only support default margin or no margin.
     * Any non-zero value will be treated as default margin
     * @param content in app message content and style
     */
    private fun setMarginsFromContent(content: InAppMessageContent) {
        marginPxSizeTop = if (content.useHeightMargin) ViewUtils.dpToPx(24) else 0
        marginPxSizeBottom = if (content.useHeightMargin) ViewUtils.dpToPx(24) else 0
        marginPxSizeLeft = if (content.useWidthMargin) ViewUtils.dpToPx(24) else 0
        marginPxSizeRight = if (content.useWidthMargin) ViewUtils.dpToPx(24) else 0
    }

    fun setWebView(webView: WebView) {
        this.webView = webView
        this.webView?.setBackgroundColor(Color.TRANSPARENT)
    }

    fun setMessageController(messageController: InAppMessageViewListener?) {
        this.messageController = messageController
    }

    suspend fun showView(activity: Activity) {
        delayShowUntilAvailable(activity)
    }

    suspend fun checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            shouldDismissWhenActive = false
            finishAfterDelay()
        }
    }

    /**
     * This will fired when the device is rotated for example with a new provided height for the WebView
     * Called to shrink or grow the WebView when it receives a JS resize event with a new height.
     *
     * @param pageHeight the provided height
     */
    suspend fun updateHeight(pageHeight: Int) {
        this.pageHeight = pageHeight

        withContext(Dispatchers.Main) {
            if (webView == null) {
                Logging.warn("WebView height update skipped, new height will be used once it is displayed.")
                return@withContext
            }
            val layoutParams = webView!!.layoutParams
            if (layoutParams == null) {
                Logging.warn("WebView height update skipped because of null layoutParams, new height will be used once it is displayed.")
                return@withContext
            }
            layoutParams.height = pageHeight
            // We only need to update the WebView size since it's parent layouts are set to
            //   WRAP_CONTENT to always match the height of the WebView. (Expect for fullscreen)
            webView!!.layoutParams = layoutParams

            // draggableRelativeLayout comes in null here sometimes, this is due to the IAM
            //  not being ready to be shown yet
            // When preparing the IAM, the correct height will be set and handle this job, so
            //  all bases are covered and the draggableRelativeLayout will never have the wrong height
            if (draggableRelativeLayout != null) {
                draggableRelativeLayout!!.setParams(
                    createDraggableLayoutParams(pageHeight, displayPosition, disableDragDismiss),
                )
            }
        }
    }

    suspend fun showInAppMessageView(currentActivity: Activity?) {
        /* IMPORTANT
         * The only place where currentActivity should be assigned to InAppMessageView */
        this.currentActivity = currentActivity
        val webViewLayoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight,
            )
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        val relativeLayoutParams = if (hasBackground) createParentRelativeLayoutParams() else null
        showDraggableView(
            displayPosition,
            webViewLayoutParams,
            relativeLayoutParams,
            createDraggableLayoutParams(pageHeight, displayPosition, disableDragDismiss),
        )
    }

    private val displayYSize: Int
        private get() = ViewUtils.getWindowHeight(currentActivity!!)

    private fun createParentRelativeLayoutParams(): RelativeLayout.LayoutParams {
        val relativeLayoutParams =
            RelativeLayout.LayoutParams(pageWidth, RelativeLayout.LayoutParams.MATCH_PARENT)
        when (displayPosition) {
            WebViewManager.Position.TOP_BANNER -> {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            }
            WebViewManager.Position.BOTTOM_BANNER -> {
                relativeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                relativeLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
            }
            WebViewManager.Position.CENTER_MODAL, WebViewManager.Position.FULL_SCREEN ->
                relativeLayoutParams.addRule(
                    RelativeLayout.CENTER_IN_PARENT,
                )
        }
        return relativeLayoutParams
    }

    private fun createDraggableLayoutParams(
        pageHeight: Int,
        displayLocation: WebViewManager.Position,
        disableDragging: Boolean,
    ): DraggableRelativeLayout.Params {
        var pageHeight = pageHeight
        val draggableParams = DraggableRelativeLayout.Params()
        draggableParams.maxXPos = marginPxSizeRight
        draggableParams.maxYPos = marginPxSizeTop
        draggableParams.draggingDisabled = disableDragging
        draggableParams.messageHeight = pageHeight
        draggableParams.height = displayYSize
        when (displayLocation) {
            WebViewManager.Position.TOP_BANNER ->
                draggableParams.dragThresholdY =
                    marginPxSizeTop - DRAG_THRESHOLD_PX_SIZE
            WebViewManager.Position.BOTTOM_BANNER -> {
                draggableParams.posY = displayYSize - pageHeight
                draggableParams.dragThresholdY = marginPxSizeBottom + DRAG_THRESHOLD_PX_SIZE
            }
            WebViewManager.Position.FULL_SCREEN -> {
                run {
                    pageHeight = displayYSize - (marginPxSizeBottom + marginPxSizeTop)
                    draggableParams.messageHeight = pageHeight
                }
                val y = displayYSize / 2 - pageHeight / 2
                draggableParams.dragThresholdY = y + DRAG_THRESHOLD_PX_SIZE
                draggableParams.maxYPos = y
                draggableParams.posY = y
            }
            WebViewManager.Position.CENTER_MODAL -> {
                val y = displayYSize / 2 - pageHeight / 2
                draggableParams.dragThresholdY = y + DRAG_THRESHOLD_PX_SIZE
                draggableParams.maxYPos = y
                draggableParams.posY = y
            }
        }
        draggableParams.dragDirection =
            if (displayLocation == WebViewManager.Position.TOP_BANNER) DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP else DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN
        return draggableParams
    }

    private suspend fun showDraggableView(
        displayLocation: WebViewManager.Position,
        relativeLayoutParams: RelativeLayout.LayoutParams,
        draggableRelativeLayoutParams: RelativeLayout.LayoutParams?,
        webViewLayoutParams: DraggableRelativeLayout.Params,
    ) {
        withContext(Dispatchers.Main) {
            if (webView == null) {
                return@withContext
            }

            webView!!.layoutParams = relativeLayoutParams
            setUpDraggableLayout(currentActivity!!, draggableRelativeLayoutParams, webViewLayoutParams)
            setUpParentRelativeLayout(currentActivity!!)
            createPopupWindow(parentRelativeLayout!!)
            if (messageController != null) {
                animateInAppMessage(displayLocation, draggableRelativeLayout!!, parentRelativeLayout!!)
            }
            startDismissTimerIfNeeded()
        }
    }

    /**
     * Create a new Android PopupWindow that draws over the current Activity
     *
     * @param parentRelativeLayout root layout to attach to the pop up window
     */
    private fun createPopupWindow(parentRelativeLayout: RelativeLayout) {
        popupWindow =
            OSPopupWindow(
                parentRelativeLayout,
                if (hasBackground) WindowManager.LayoutParams.MATCH_PARENT else pageWidth,
                if (hasBackground) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                false,
                popupWindowListener,
            )
        popupWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow?.isTouchable = true
        // Focusable allows keyboard input for HTML IAMs, but also prevents interacting under banners
        popupWindow?.isFocusable = !displayPosition.isBanner

        // NOTE: This is required for getting fullscreen under notches working in portrait mode
        popupWindow?.isClippingEnabled = false
        var gravity = 0
        if (!hasBackground) {
            gravity =
                when (displayPosition) {
                    WebViewManager.Position.TOP_BANNER -> Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    WebViewManager.Position.BOTTOM_BANNER -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    WebViewManager.Position.CENTER_MODAL, WebViewManager.Position.FULL_SCREEN -> Gravity.CENTER_HORIZONTAL
                }
        }

        // Using panel for fullbleed IAMs and dialog for non-fullbleed. The attached dialog type
        // does not allow content to bleed under notches but panel does.
        val displayType =
            if (messageContent.isFullBleed) WindowManager.LayoutParams.TYPE_APPLICATION_PANEL else WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        PopupWindowCompat.setWindowLayoutType(
            popupWindow!!,
            displayType,
        )
        popupWindow?.showAtLocation(
            currentActivity!!.window.decorView.rootView,
            gravity,
            0,
            0,
        )
    }

    private fun setUpParentRelativeLayout(context: Context) {
        parentRelativeLayout = RelativeLayout(context)
        parentRelativeLayout!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        parentRelativeLayout!!.clipChildren = false
        parentRelativeLayout!!.clipToPadding = false
        parentRelativeLayout!!.addView(draggableRelativeLayout)
    }

    private fun setUpDraggableLayout(
        context: Context,
        relativeLayoutParams: RelativeLayout.LayoutParams?,
        draggableParams: DraggableRelativeLayout.Params,
    ) {
        draggableRelativeLayout = DraggableRelativeLayout(context)
        if (relativeLayoutParams != null) {
            draggableRelativeLayout!!.setLayoutParams(
                relativeLayoutParams,
            )
        }
        draggableRelativeLayout!!.setParams(draggableParams)
        draggableRelativeLayout!!.setListener(
            object : DraggableRelativeLayout.DraggableListener {
                override fun onDismiss() {
                    if (messageController != null) {
                        messageController!!.onMessageWillDismiss()
                    }

                    suspendifyOnThread {
                        finishAfterDelay()
                    }
                }

                override fun onDragStart() {
                    isDragging = true
                }

                override fun onDragEnd() {
                    isDragging = false
                }
            },
        )
        if (webView!!.parent != null) (webView!!.parent as ViewGroup).removeAllViews()
        val cardView = createCardView(context)
        cardView.tag = IN_APP_MESSAGE_CARD_VIEW_TAG
        cardView.addView(webView)
        draggableRelativeLayout!!.setPadding(
            marginPxSizeLeft,
            marginPxSizeTop,
            marginPxSizeRight,
            marginPxSizeBottom,
        )
        draggableRelativeLayout!!.setClipChildren(false)
        draggableRelativeLayout!!.setClipToPadding(false)
        draggableRelativeLayout!!.addView(cardView)
    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     */
    private fun createCardView(context: Context): CardView {
        val cardView = CardView(context)
        val height =
            if (displayPosition == WebViewManager.Position.FULL_SCREEN) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val cardViewLayoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
            )
        cardViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        cardView.layoutParams = cardViewLayoutParams

        // Set the initial elevation of the CardView to 0dp if using Android 6 API 23
        //  Fixes bug when animating a elevated CardView class
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            cardView.cardElevation =
                0f
        } else {
            if (getHideDropShadow(context)) {
                cardView.cardElevation = 0f
            } else {
                cardView.cardElevation = ViewUtils.dpToPx(5).toFloat()
            }
        }
        cardView.radius = ViewUtils.dpToPx(8).toFloat()
        cardView.clipChildren = false
        cardView.clipToPadding = false
        cardView.preventCornerOverlap = false
        cardView.setCardBackgroundColor(Color.TRANSPARENT)
        return cardView
    }

    private fun getHideDropShadow(context: Context): Boolean {
        return AndroidUtils.getManifestMetaBoolean(context, "com.onesignal.inAppMessageHideDropShadow")
    }

    /**
     * Schedule dismiss behavior, if IAM has a dismiss after X number of seconds timer.
     */
    private suspend fun startDismissTimerIfNeeded() {
        if (displayDuration <= 0 || isDismissTimerSet) {
            return
        }

        isDismissTimerSet = true
        delay(displayDuration.toLong() * 1000)

        if (cancelDismissTimer) {
            cancelDismissTimer = false
            return
        }

        if (messageController != null) {
            messageController!!.onMessageWillDismiss()
        }
        if (currentActivity != null) {
            dismissAndAwaitNextMessage()
            isDismissTimerSet = false
        } else {
            // For cases when the app is on background and the dismiss is triggered
            shouldDismissWhenActive = true
        }
    }

    // Do not add view until activity is ready
    private suspend fun delayShowUntilAvailable(currentActivity: Activity) {
        if (AndroidUtils.isActivityFullyReady(currentActivity) && parentRelativeLayout == null) {
            showInAppMessageView(currentActivity)
            return
        }

        delay(ACTIVITY_INIT_DELAY.toLong())
        delayShowUntilAvailable(currentActivity)
    }

    /**
     * Trigger the [.draggableRelativeLayout] dismiss animation
     */
    suspend fun dismissAndAwaitNextMessage() {
        if (draggableRelativeLayout == null) {
            Logging.error("No host presenter to trigger dismiss animation, counting as dismissed already")
            dereferenceViews()
            return
        }
        draggableRelativeLayout!!.dismiss()
        finishAfterDelay()
    }

    /**
     * Finishing on a timer as continueSettling does not return false
     * when using smoothSlideViewTo on Android 4.4
     */
    private suspend fun finishAfterDelay() {
        withContext(Dispatchers.Main) {
            delay(ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS.toLong())
            if (hasBackground && parentRelativeLayout != null) {
                animateAndDismissLayout(parentRelativeLayout!!)
            } else {
                cleanupViewsAfterDismiss()
            }
        }
    }

    /**
     * IAM has been fully dismissed, remove all views and call the onMessageWasDismissed callback
     */
    private fun cleanupViewsAfterDismiss() {
        removeAllViews()
        messageController?.onMessageWasDismissed()
    }

    /**
     * Remove all views and dismiss PopupWindow
     */
    fun removeAllViews() {
        Logging.debug("InAppMessageView.removeAllViews()")
        popupWindow?.wasDismissedManually = true
        if (isDismissTimerSet) {
            // Dismissed before the dismiss delay
            cancelDismissTimer = true
        }

        draggableRelativeLayout?.removeAllViews()
        popupWindow?.dismiss()

        dereferenceViews()
    }

    /**
     * Cleans all layout references so this can be cleaned up in the next GC
     */
    private fun dereferenceViews() {
        // Dereference so this can be cleaned up in the next GC
        parentRelativeLayout = null
        draggableRelativeLayout = null
        webView = null
    }

    private fun animateInAppMessage(
        displayLocation: WebViewManager.Position,
        messageView: View,
        backgroundView: View,
    ) {
        val messageViewCardView =
            messageView!!.findViewWithTag<CardView>(
                IN_APP_MESSAGE_CARD_VIEW_TAG,
            )
        val cardViewAnimCallback = createAnimationListener(messageViewCardView)
        when (displayLocation) {
            WebViewManager.Position.TOP_BANNER ->
                animateTop(
                    messageViewCardView,
                    webView!!.height,
                    cardViewAnimCallback,
                )
            WebViewManager.Position.BOTTOM_BANNER ->
                animateBottom(
                    messageViewCardView,
                    webView!!.height,
                    cardViewAnimCallback,
                )
            WebViewManager.Position.CENTER_MODAL, WebViewManager.Position.FULL_SCREEN ->
                animateCenter(
                    messageView,
                    backgroundView,
                    cardViewAnimCallback,
                    null,
                )
        }
    }

    private fun createAnimationListener(messageViewCardView: CardView): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                // For Android 6 API 23 devices, waits until end of animation to set elevation of CardView class
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                    messageViewCardView.cardElevation = ViewUtils.dpToPx(5).toFloat()
                }
                if (messageController != null) {
                    messageController!!.onMessageWasDisplayed()
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        }
    }

    private fun animateTop(
        messageView: View,
        height: Int,
        cardViewAnimCallback: Animation.AnimationListener,
    ) {
        // Animate the message view from above the screen downward to the top
        OneSignalAnimate.animateViewByTranslation(
            messageView,
            (
                -height - marginPxSizeTop
                ).toFloat(),
            0f,
            IN_APP_BANNER_ANIMATION_DURATION_MS,
            OneSignalBounceInterpolator(0.1, 8.0),
            cardViewAnimCallback,
        )
            .start()
    }

    private fun animateBottom(
        messageView: View,
        height: Int,
        cardViewAnimCallback: Animation.AnimationListener,
    ) {
        // Animate the message view from under the screen upward to the bottom
        OneSignalAnimate.animateViewByTranslation(
            messageView,
            (
                height + marginPxSizeBottom
                ).toFloat(),
            0f,
            IN_APP_BANNER_ANIMATION_DURATION_MS,
            OneSignalBounceInterpolator(0.1, 8.0),
            cardViewAnimCallback,
        )
            .start()
    }

    private fun animateCenter(
        messageView: View,
        backgroundView: View,
        cardViewAnimCallback: Animation.AnimationListener,
        backgroundAnimCallback: Animator.AnimatorListener?,
    ) {
        // Animate the message view by scale since it settles at the center of the screen
        val messageAnimation =
            OneSignalAnimate.animateViewSmallToLarge(
                messageView,
                IN_APP_CENTER_ANIMATION_DURATION_MS,
                OneSignalBounceInterpolator(0.1, 8.0),
                cardViewAnimCallback,
            )

        // Animate background behind the message so it doesn't just show the dark transparency
        val backgroundAnimation =
            animateBackgroundColor(
                backgroundView,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                getOverlayColor(),
                backgroundAnimCallback,
            )
        messageAnimation.start()
        backgroundAnimation.start()
    }

    private suspend fun animateAndDismissLayout(backgroundView: View) {
        val waiter = Waiter()
        val animCallback: Animator.AnimatorListener =
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanupViewsAfterDismiss()
                    waiter.wake()
                }
            }

        // Animate background behind the message so it hides before being removed from the view
        animateBackgroundColor(
            backgroundView,
            IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
            getOverlayColor(),
            ACTIVITY_BACKGROUND_COLOR_EMPTY,
            animCallback,
        )
            .start()

        waiter.waitForWake()
    }

    private fun animateBackgroundColor(
        backgroundView: View,
        duration: Int,
        startColor: Int,
        endColor: Int,
        animCallback: Animator.AnimatorListener?,
    ): ValueAnimator {
        return OneSignalAnimate.animateViewColor(
            backgroundView,
            duration,
            startColor,
            endColor,
            animCallback,
        )
    }

    override fun toString(): String {
        return "InAppMessageView{" +
            "currentActivity=" + currentActivity +
            ", pageWidth=" + pageWidth +
            ", pageHeight=" + pageHeight +
            ", displayDuration=" + displayDuration +
            ", hasBackground=" + hasBackground +
            ", shouldDismissWhenActive=" + shouldDismissWhenActive +
            ", isDragging=" + isDragging +
            ", disableDragDismiss=" + disableDragDismiss +
            ", displayLocation=" + displayPosition +
            ", webView=" + webView +
            '}'
    }

    private fun getOverlayColor(): Int {
        return if (hideGrayOverlay) {
            ACTIVITY_BACKGROUND_COLOR_EMPTY
        } else {
            ACTIVITY_BACKGROUND_COLOR_FULL
        }
    }

    companion object {
        private const val IN_APP_MESSAGE_CARD_VIEW_TAG = "IN_APP_MESSAGE_CARD_VIEW_TAG"
        private const val ACTIVITY_BACKGROUND_COLOR_EMPTY = Color.TRANSPARENT
        private val ACTIVITY_BACKGROUND_COLOR_FULL = Color.parseColor("#BB000000")
        private const val IN_APP_BANNER_ANIMATION_DURATION_MS = 1000
        private const val IN_APP_CENTER_ANIMATION_DURATION_MS = 1000
        private const val IN_APP_BACKGROUND_ANIMATION_DURATION_MS = 400
        private const val ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600
        private const val ACTIVITY_INIT_DELAY = 200
        private val DRAG_THRESHOLD_PX_SIZE = ViewUtils.dpToPx(4)
    }
}
