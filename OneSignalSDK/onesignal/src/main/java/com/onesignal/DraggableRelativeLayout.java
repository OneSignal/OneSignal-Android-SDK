package com.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

class DraggableRelativeLayout extends RelativeLayout {

   ViewDragHelper mDragHelper;
   private boolean dismissing;

   static class Params {
      static final int DRAGGABLE_DIRECTION_UP = 0;
      static final int DRAGGABLE_DIRECTION_DOWN = 1;

      Context context;
      View viewToForwardClicks;
      int maxYPos;
      int maxXPos;
      int height;
      int dragDirection;

      private int dismissingYVelocity;
      private int offScreenYPos;
      private int dismissingYPos;
   }

   private Params params;

   public DraggableRelativeLayout(Params params) {
      super(params.context);
      this.params = params;

      params.offScreenYPos = Resources.getSystem().getDisplayMetrics().heightPixels;
      params.dismissingYVelocity = OSUtils.dpToPx(3_000);

      if (params.dragDirection == Params.DRAGGABLE_DIRECTION_UP) {
         params.offScreenYPos = -params.height;
         params.dismissingYVelocity = -params.dismissingYVelocity;
      }

      params.dismissingYPos = params.offScreenYPos / 2;

      setClipChildren(false);
      createDragHelper();
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
               if (top < params.maxYPos)
                  return params.maxYPos;
            }
            else {
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
                     Log.e("OneSignal", "Dismissing: params.dismissingYPos=" + params.dismissingYPos + ", lastYPos=" + lastYPos + ", yvel=" + yvel + ", params.dismissingYPos=" + params.dismissingYPos);
                  }
               }
               else {
                  if (lastYPos < params.dismissingYPos || yvel < params.dismissingYVelocity) {
                     settleDestY = params.offScreenYPos;
                     dismissing = true;
                     Log.e("OneSignal", "Dismissing: params.dismissingYPos=" + params.dismissingYPos + ", lastYPos=" + lastYPos +", yvel=" + yvel + ", params.dismissingYPos=" + params.dismissingYPos);
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
      mDragHelper.shouldInterceptTouchEvent(event);
      return true;
   }

   @Override
   public boolean onTouchEvent(MotionEvent event) {
      super.onTouchEvent(event);
      // Can prevent WebView panning by NOT forwarding the ACTION_MOVE action.
      //   - Only an issue when content is larger then the view itself.
      // Code on how to filter: if (event.getAction() != MotionEvent.ACTION_MOVE) { ... }
      // NOTE: However this has a side effect of dragging causing JS clicks.
      //       This happen when starting the drag outside of the click area then lifting on the clickable


      // Ignore touch events if in dismissing state
      if (dismissing)
         return true;

      // Forwarding touch event to webView so JS's onClick works
      params.viewToForwardClicks.onTouchEvent(event);
      mDragHelper.processTouchEvent(event);
      return true;
   }

   @Override
   public void computeScroll() {
      super.computeScroll();
      // Required for settleCapturedViewAt
      boolean settling = mDragHelper.continueSettling(true);

      if (settling)
         ViewCompat.postInvalidateOnAnimation(this);
      else if (dismissing)
         finishParentActivity();
   }

   private void finishParentActivity() {
      Activity host = (Activity)getContext();
      host.finish();
   }

   public void dismiss() {
      dismissing = true;
      mDragHelper.smoothSlideViewTo(this, getLeft(), params.offScreenYPos);
      ViewCompat.postInvalidateOnAnimation(this);
   }

}
