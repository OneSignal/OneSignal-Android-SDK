package com.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class WebViewActivity extends Activity {

   static int DISMISSING_Y_POS;
   static int OFF_SCREEN_SCROLL_Y_POS;
   static final int DISMISSING_Y_VEL = OSUtils.dpToPx(333);
   static final int MARGIN_PX_SIZE = OSUtils.dpToPx(24);

   static WebViewActivity instance;

   boolean dismissing;
   WebView webView;
   DraggableConstraintLayout constraintLayout;

   class DraggableConstraintLayout extends ConstraintLayout {

      ViewDragHelper mDragHelper;

      public DraggableConstraintLayout(Context context) {
         super(context);
      }

      @Override
      public boolean onInterceptTouchEvent(MotionEvent event) {
         mDragHelper.shouldInterceptTouchEvent(event);
         return true;
      }

      @Override
      public boolean onTouchEvent(MotionEvent event) {
         // Forward touch event to WebView so onClick works
         webView.onTouchEvent(event);
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
            finish();
      }
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      instance = this;

      OFF_SCREEN_SCROLL_Y_POS = Resources.getSystem().getDisplayMetrics().heightPixels;
      DISMISSING_Y_POS = OFF_SCREEN_SCROLL_Y_POS / 2;

      getWindow().getDecorView().setBackgroundColor(Color.parseColor("#77000000"));

      webView = WebViewManager.webView;

      addLayoutAndView();
   }

   @Override
   protected void onPause() {
      super.onPause();
      overridePendingTransition(0, 0);
   }

   @Override
   protected void onStop() {
      super.onStop();
      instance = null;
      constraintLayout.removeAllViews();
   }

   void addLayoutAndView() {
      constraintLayout = new DraggableConstraintLayout(this);

      ConstraintLayout.LayoutParams rlp = new ConstraintLayout.LayoutParams(
         ConstraintLayout.LayoutParams.MATCH_PARENT,
         ConstraintLayout.LayoutParams.MATCH_PARENT
      );
      setContentView(constraintLayout, rlp);

      ConstraintLayout.LayoutParams relativeParams = new ConstraintLayout.LayoutParams(
         ConstraintLayout.LayoutParams.MATCH_PARENT,
         ConstraintLayout.LayoutParams.MATCH_PARENT
      );
      relativeParams.setMargins(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
      webView.setLayoutParams(relativeParams);

      constraintLayout.mDragHelper = ViewDragHelper.create(constraintLayout, 1.0f, new ViewDragHelper.Callback() {
         private int lastYPos;

         @Override
         public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return true;
         }

         @Override
         public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            lastYPos = top;
            if (top < MARGIN_PX_SIZE)
               return MARGIN_PX_SIZE;
            return top;
         }

         @Override
         public int clampViewPositionHorizontal(@NonNull View child, int right, int dy) {
            return MARGIN_PX_SIZE;
         }

         @Override
         public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int settleDestY;
            if (!dismissing && lastYPos < DISMISSING_Y_POS && yvel < DISMISSING_Y_VEL)
               settleDestY = MARGIN_PX_SIZE;
            else {
               settleDestY = OFF_SCREEN_SCROLL_Y_POS;
               dismissing = true;
            }

            if (constraintLayout.mDragHelper.settleCapturedViewAt(MARGIN_PX_SIZE, settleDestY))
               ViewCompat.postInvalidateOnAnimation(constraintLayout);
         }
      });

      constraintLayout.addView(webView);
   }

   void dismiss() {
      dismissing = true;
      constraintLayout.mDragHelper.smoothSlideViewTo(constraintLayout, 0, OFF_SCREEN_SCROLL_Y_POS);
      ViewCompat.postInvalidateOnAnimation(constraintLayout);
      finishAfterDelay();
   }

   void finishAfterDelay() {
      // Finishing on a timer as continueSettling does not return false
      //    when using smoothSlideViewTo on Android 4.4
      OSUtils.runOnMainThreadDelayed(new Runnable() {
         @Override
         public void run() {
            finish();
         }
      }, 600);
   }
}
