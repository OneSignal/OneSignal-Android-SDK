package com.onesignal.onesignal.iam.internal.display.impl

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.onesignal.onesignal.core.internal.common.ViewUtils

internal class DraggableRelativeLayout(context: Context?) : RelativeLayout(context) {
    internal interface DraggableListener {
        // Callback for dismissing the MessageView
        fun onDismiss()

        // Callbacks for knowing when dragging has started and ended
        fun onDragStart()
        fun onDragEnd()
    }

    private var mListener: DraggableListener? = null
    private var mDragHelper: ViewDragHelper? = null
    private var dismissing = false
    private val draggingDisabled = false

    internal class Params {
        var posY = 0
        var maxYPos = 0
        var dragThresholdY = // Y value associated with trigger for onDragStart() callback
            0
        var maxXPos = 0
        var height = 0
        var messageHeight = 0
        var dragDirection = 0
        var draggingDisabled = false
        var dismissingYVelocity = 0
        var offScreenYPos = 0
        var dismissingYPos = 0

        companion object {
            const val DRAGGABLE_DIRECTION_UP = 0
            const val DRAGGABLE_DIRECTION_DOWN = 1
        }
    }

    private var params: Params? = null
    fun setListener(listener: DraggableListener?) {
        mListener = listener
    }

    fun setParams(params: Params) {
        this.params = params
        params.offScreenYPos =
            params.messageHeight + params.posY + (Resources.getSystem().displayMetrics.heightPixels - params.messageHeight - params.posY) + EXTRA_PX_DISMISS
        params.dismissingYVelocity = ViewUtils.dpToPx(3000)
        if (params.dragDirection == Params.DRAGGABLE_DIRECTION_UP) {
            params.offScreenYPos = -params.messageHeight - MARGIN_PX_SIZE
            params.dismissingYVelocity = -params.dismissingYVelocity
            params.dismissingYPos = params.offScreenYPos / 3
        } else params.dismissingYPos = params.messageHeight / 3 + params.maxYPos * 2
    }

    private fun createDragHelper() {
        mDragHelper = ViewDragHelper.create(
            this, 1.0f,
            object : ViewDragHelper.Callback() {
                private var lastYPos = 0
                override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                    return true
                }

                override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
                    if (params!!.draggingDisabled) {
                        return params!!.maxYPos
                    }
                    lastYPos = top
                    if (params!!.dragDirection == Params.DRAGGABLE_DIRECTION_DOWN) {
                        // Dragging down
                        // If the top of the message is past the dragThresholdY trigger the onDragStart() callback
                        if (top >= params!!.dragThresholdY && mListener != null) mListener!!.onDragStart()
                        if (top < params!!.maxYPos) return params!!.maxYPos
                    } else {
                        // Dragging up
                        // If the top of the message is past the dragThresholdY trigger the onDragStart() callback
                        if (top <= params!!.dragThresholdY && mListener != null) mListener!!.onDragStart()
                        if (top > params!!.maxYPos) return params!!.maxYPos
                    }
                    return top
                }

                override fun clampViewPositionHorizontal(child: View, right: Int, dy: Int): Int {
                    return params!!.maxXPos
                }

                // Base on position and scroll speed decide if we need to dismiss
                override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                    var settleDestY = params!!.maxYPos
                    if (!dismissing) {
                        if (params!!.dragDirection == Params.DRAGGABLE_DIRECTION_DOWN) {
                            if (lastYPos > params!!.dismissingYPos || yvel > params!!.dismissingYVelocity) {
                                settleDestY = params!!.offScreenYPos
                                dismissing = true
                                if (mListener != null) mListener!!.onDismiss()
                            }
                        } else {
                            if (lastYPos < params!!.dismissingYPos || yvel < params!!.dismissingYVelocity) {
                                settleDestY = params!!.offScreenYPos
                                dismissing = true
                                if (mListener != null) mListener!!.onDismiss()
                            }
                        }
                    }
                    if (mDragHelper!!.settleCapturedViewAt(
                            params!!.maxXPos,
                            settleDestY
                        )
                    ) ViewCompat.postInvalidateOnAnimation(this@DraggableRelativeLayout)
                }
            }
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Prevent any possible extra clicks or stopping the dismissing animation
        if (dismissing) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> // On touch downs we want to trigger the onDragEnd() callback

                // This seems confusing, but because the JS action within the WebView will trigger on
                // touch up we need to stay in the onDragStart() state

                // The fix for this is using on touch down to go to onDragEnd() state since we can
                // confirm that the view will be at its origin position

                // Therefore, the logic inside of onDragEnd() would be executed before touch up
                // again and our JS action will execute properly
                if (mListener != null) mListener!!.onDragEnd()
        }
        mDragHelper!!.processTouchEvent(event)

        // Let child views get all touch events;
        return false
    }

    override fun computeScroll() {
        super.computeScroll()
        // Required for settleCapturedViewAt
        val settling = mDragHelper!!.continueSettling(true)
        if (settling) ViewCompat.postInvalidateOnAnimation(this)
    }

    fun dismiss() {
        dismissing = true
        mDragHelper!!.smoothSlideViewTo(this, left, params!!.offScreenYPos)
        ViewCompat.postInvalidateOnAnimation(this)
    }

    companion object {
        private val MARGIN_PX_SIZE = ViewUtils.dpToPx(28)
        private val EXTRA_PX_DISMISS = ViewUtils.dpToPx(64)
    }

    init {
        clipChildren = false
        createDragHelper()
    }
}
