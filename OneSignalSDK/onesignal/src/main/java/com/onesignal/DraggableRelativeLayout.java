package com.onesignal;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import static com.onesignal.OSViewUtils.dpToPx;

class DraggableRelativeLayout extends RelativeLayout {

   private static final int MARGIN_PX_SIZE = dpToPx(28);
   private static final int EXTRA_PX_DISMISS = dpToPx(64);

   interface DraggableListener {
      // Callback for dismissing the MessageView
      void onDismiss();

      // Callbacks for knowing when dragging has started and ended
      void onDragStart();
      void onDragEnd();
   }

   private DraggableListener mListener;
   private ViewDragHelper mDragHelper;
   private boolean dismissing;

   static class Params {
      static final int DRAGGABLE_DIRECTION_UP = 0;
      static final int DRAGGABLE_DIRECTION_DOWN = 1;

      int posY;
      int maxYPos;
      int dragThresholdY; // Y value associated with trigger for onDragStart() callback
      int maxXPos;
      int height;
      int messageHeight;
      int dragDirection;

      private int dismissingYVelocity;
      private int offScreenYPos;
      private int dismissingYPos;
   }

   private Params params;

   public DraggableRelativeLayout(Context context) {
      super(context);

      setClipChildren(false);
      createDragHelper();
   }

   void setListener(DraggableListener listener) {
      this.mListener = listener;
   }

   void setParams(Params params) {
      this.params = params;

      params.offScreenYPos = params.messageHeight + params.posY + (Resources.getSystem().getDisplayMetrics().heightPixels - params.messageHeight - params.posY) + EXTRA_PX_DISMISS;
      params.dismissingYVelocity = OSViewUtils.dpToPx(3_000);

      if (params.dragDirection == Params.DRAGGABLE_DIRECTION_UP) {
         params.offScreenYPos = -params.messageHeight - MARGIN_PX_SIZE;
         params.dismissingYVelocity = -params.dismissingYVelocity;
         params.dismissingYPos = params.offScreenYPos / 3;
      }
      else
         params.dismissingYPos = (params.messageHeight / 3) + (params.maxYPos * 2);
   }

   private void createDragHelper() {
      mDragHelper = ViewDragHelper.create(this, 1.0f, new ViewDragHelper.Callback() {
         private int lastYPos;

         @Override
         public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return true;
         }

         @Override
         public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            lastYPos = top;
            if (params.dragDirection == Params.DRAGGABLE_DIRECTION_DOWN) {
               // Dragging down
               // If the top of the message is past the dragThresholdY trigger the onDragStart() callback
               if (top >= params.dragThresholdY && mListener != null)
                  mListener.onDragStart();

               if (top < params.maxYPos)
                  return params.maxYPos;
            }
            else {
               // Dragging up
               // If the top of the message is past the dragThresholdY trigger the onDragStart() callback
               if (top <= params.dragThresholdY && mListener != null)
                  mListener.onDragStart();

               if (top > params.maxYPos)
                  return params.maxYPos;
            }
            return top;
         }

         @Override
         public int clampViewPositionHorizontal(@NonNull View child, int right, int dy) {
            return params.maxXPos;
         }

         // Base on position and scroll speed decide if we need to dismiss
         @Override
         public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int settleDestY = params.maxYPos;
            if (!dismissing) {
               if (params.dragDirection == Params.DRAGGABLE_DIRECTION_DOWN) {
                  if (lastYPos > params.dismissingYPos || yvel > params.dismissingYVelocity) {
                     settleDestY = params.offScreenYPos;
                     dismissing = true;

                     if (mListener != null)
                        mListener.onDismiss();
                  }
               }
               else {
                  if (lastYPos < params.dismissingYPos || yvel < params.dismissingYVelocity) {
                     settleDestY = params.offScreenYPos;
                     dismissing = true;

                     if (mListener != null)
                        mListener.onDismiss();
                  }
               }
            }

            if (DraggableRelativeLayout.this.mDragHelper.settleCapturedViewAt(params.maxXPos, settleDestY))
               ViewCompat.postInvalidateOnAnimation(DraggableRelativeLayout.this);
         }
      });
   }

   @Override
   public boolean onInterceptTouchEvent(MotionEvent event) {
      // Prevent any possible extra clicks or stopping the dismissing animation
      if (dismissing)
         return true;

      // Override our own touch event actions on the view
      switch (event.getAction()) {
         case MotionEvent.ACTION_DOWN:
         case MotionEvent.ACTION_POINTER_DOWN:
            // On touch downs we want to trigger the onDragEnd() callback

            // This seems confusing, but because the JS action within the WebView will trigger on
            // touch up we need to stay in the onDragStart() state

            // The fix for this is using on touch down to go to onDragEnd() state since we can
            // confirm that the view will be at its origin position

            // Therefore, the logic inside of onDragEnd() would be executed before touch up
            // again and our JS action will execute properly
            if (mListener != null)
               mListener.onDragEnd();
            break;
      }

      mDragHelper.processTouchEvent(event);

      // Let child views get all touch events;
      return false;
   }

   @Override
   public void computeScroll() {
      super.computeScroll();
      // Required for settleCapturedViewAt
      boolean settling = mDragHelper.continueSettling(true);

      if (settling)
         ViewCompat.postInvalidateOnAnimation(this);
   }

   public void dismiss() {
      dismissing = true;
      mDragHelper.smoothSlideViewTo(this, getLeft(), params.offScreenYPos);
      ViewCompat.postInvalidateOnAnimation(this);
   }
}
