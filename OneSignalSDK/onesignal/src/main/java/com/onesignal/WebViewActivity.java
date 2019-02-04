package com.onesignal;

import android.app.Activity;
import android.content.Context;
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

   boolean dismissing;
   WebView webView;

   class DraggableConstraintLayout extends ConstraintLayout {

      ViewDragHelper mDragHelper;

      public DraggableConstraintLayout(Context context) {
         super(context);
      }

      @Override
      public boolean onInterceptTouchEvent(MotionEvent event) {
         if (mDragHelper.shouldInterceptTouchEvent(event))
            return true;
         return true;
//       return super.onInterceptTouchEvent(event);
      }

      @Override
      public boolean onTouchEvent(MotionEvent event) {
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

      getWindow().getDecorView().setBackgroundColor(Color.parseColor("#77000000"));

      webView = WebViewManager.showWebViewForPage("");

      addLayoutAndViewFromAllCode();
   }

   @Override
   protected void onPause() {
      super.onPause();
      overridePendingTransition(0, 0);
   }

   void addLayoutAndViewFromAllCode() {
      final DraggableConstraintLayout constraintLayout = new DraggableConstraintLayout(this);

      ConstraintLayout.LayoutParams rlp = new ConstraintLayout.LayoutParams(
         ConstraintLayout.LayoutParams.MATCH_PARENT,
         ConstraintLayout.LayoutParams.MATCH_PARENT
      );
      setContentView(constraintLayout, rlp);

      ConstraintLayout.LayoutParams relativeParams = new ConstraintLayout.LayoutParams(
         ConstraintLayout.LayoutParams.MATCH_PARENT,
         ConstraintLayout.LayoutParams.MATCH_PARENT
      );
      final int marginPxSize = OSUtils.dpToPx(24);
      relativeParams.setMargins(marginPxSize , marginPxSize, marginPxSize, marginPxSize);
      webView.setLayoutParams(relativeParams);

      constraintLayout.mDragHelper = ViewDragHelper.create(constraintLayout, 1.0f, new ViewDragHelper.Callback() {
         private int lastYPos;

         @Override
         public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return true;
         }

         @Override
         public int clampViewPositionVertical(View child, int top, int dy) {
            lastYPos = top;
            if (top < marginPxSize)
               return marginPxSize;
            return top;
         }

         @Override
         public int clampViewPositionHorizontal(View child, int right, int dy) {
            return marginPxSize;
         }

         @Override
         public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int settleDestY;
            if (lastYPos < 1_700 && yvel < 1_000)
               settleDestY = marginPxSize;
            else {
               settleDestY = 3_000;
               dismissing = true;
            }

            if (constraintLayout.mDragHelper.settleCapturedViewAt(marginPxSize, settleDestY))
               ViewCompat.postInvalidateOnAnimation(constraintLayout);
         }
      });

      constraintLayout.addView(webView);
   }
}
