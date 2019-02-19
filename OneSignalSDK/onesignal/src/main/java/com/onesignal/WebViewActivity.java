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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

// TODO: Fix bug where the WebView disappears after resuming the Activity
// TODO: Fix bug where rotation isn't recalculating the image size correctly
public class WebViewActivity extends Activity {

   static final String PAGE_HEIGHT_INTENT_KEY = "pageHeight";

   private static final int ACTIVITY_BACKGROUND_COLOR = Color.parseColor("#77000000");

   private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;

   private static int DISMISSING_Y_POS;
   private static int OFF_SCREEN_SCROLL_Y_POS;
   private static final int DISMISSING_Y_VEL = OSUtils.dpToPx(333);
   private static final int MARGIN_PX_SIZE = OSUtils.dpToPx(24);

   static WebViewActivity instance;

   boolean dismissing;
   WebView webView;
   DraggableRelativeLayout draggableRelativeLayout;

   class DraggableRelativeLayout extends RelativeLayout {

      ViewDragHelper mDragHelper;

      public DraggableRelativeLayout(Context context) {
         super(context);
      }

      @Override
      public boolean onInterceptTouchEvent(MotionEvent event) {
         mDragHelper.shouldInterceptTouchEvent(event);
         return true;
      }

      @Override
      public boolean onTouchEvent(MotionEvent event) {
         webView.onTouchEvent(event); // Forward touch event to webView so JS's onClick works
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

      getWindow().getDecorView().setBackgroundColor(ACTIVITY_BACKGROUND_COLOR);

      webView = WebViewManager.webView;

      addLayoutAndView();
   }

   // TODO: Test layout with a fullscreen app (without status bar or buttons)
   // TODO: Edge case: Modal in portrait is to tall when using split screen mode
   void addLayoutAndView() {
      draggableRelativeLayout = new DraggableRelativeLayout(this);
      draggableRelativeLayout.setClipChildren(false);

      // Use pageHeight if we have it, otherwise use use full height of the Activity
      Bundle extra = getIntent().getExtras();
      int pageWidth = ConstraintLayout.LayoutParams.MATCH_PARENT;
      int pageHeight = extra.getInt(PAGE_HEIGHT_INTENT_KEY, ConstraintLayout.LayoutParams.MATCH_PARENT);
      // If we have a height constraint; (Modal or Banner)
      //   1. Ensure we don't set a height higher than the screen height.
      //   2. Limit the width to either screen width or the height of the screen.
      //      - This is to make the modal width the same for landscape and portrait modes.
      if (pageHeight != ConstraintLayout.LayoutParams.MATCH_PARENT) {
         pageHeight += (MARGIN_PX_SIZE * 2);
         pageHeight = Math.min(pageHeight, getWebViewYSize() + (MARGIN_PX_SIZE * 2));
         pageWidth = Math.min(getWebViewXSize() + (MARGIN_PX_SIZE * 2), getWebViewYSize() + (MARGIN_PX_SIZE * 3));
      }

      FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(
         pageWidth,
         pageHeight
      );
      frameLayoutParams.gravity = Gravity.CENTER;
      setContentView(draggableRelativeLayout, frameLayoutParams);

      // Set NoClip - For Dialog and Banners when dragging
      ((ViewGroup) draggableRelativeLayout.getParent()).setClipChildren(false);

      RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(
         ConstraintLayout.LayoutParams.MATCH_PARENT,
         ConstraintLayout.LayoutParams.MATCH_PARENT
      );
      relativeLayoutParams.setMargins(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
      relativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
      webView.setLayoutParams(relativeLayoutParams);

      draggableRelativeLayout.mDragHelper = ViewDragHelper.create(draggableRelativeLayout, 1.0f, new ViewDragHelper.Callback() {
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

         // Base on position and scroll speed decide if we need to dismiss
         @Override
         public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int settleDestY;
            if (!dismissing && lastYPos < DISMISSING_Y_POS && yvel < DISMISSING_Y_VEL)
               settleDestY = MARGIN_PX_SIZE;
            else {
               settleDestY = OFF_SCREEN_SCROLL_Y_POS;
               dismissing = true;
            }

            if (draggableRelativeLayout.mDragHelper.settleCapturedViewAt(MARGIN_PX_SIZE, settleDestY))
               ViewCompat.postInvalidateOnAnimation(draggableRelativeLayout);
         }
      });

      draggableRelativeLayout.addView(webView);
   }


// Another possible way to get the size. Should include the status bar...
// getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
   static int getWebViewXSize() {
      return Resources.getSystem().getDisplayMetrics().widthPixels - (MARGIN_PX_SIZE * 2);
   }

   static int getWebViewYSize() {
      // 24dp is a best estimate of the status bar.
      // Getting the size correct will prevent a redraw of the WebView
      return Resources.getSystem().getDisplayMetrics().heightPixels - (MARGIN_PX_SIZE * 2) - OSUtils.dpToPx(24);
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
      draggableRelativeLayout.removeAllViews();
   }

   // TODO: Modal in landscape mode transitions bottom left instead of center bottom
   void dismiss() {
      dismissing = true;
      draggableRelativeLayout.mDragHelper.smoothSlideViewTo(draggableRelativeLayout, 0, OFF_SCREEN_SCROLL_Y_POS);
      ViewCompat.postInvalidateOnAnimation(draggableRelativeLayout);
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
      }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS);
   }
}
