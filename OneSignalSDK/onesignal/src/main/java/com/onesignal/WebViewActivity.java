package com.onesignal;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

// TODO: Fix bug where the WebView disappears after resuming the Activity

// TODO: Image is slightly smaller if the modal is first displayed in landscape then
//         rotated into portrait than displaying in portrait first.
public class WebViewActivity extends Activity {

   static final String DISPLAY_LOCATION_INTENT_KEY = "displayLocation";
   static final String PAGE_HEIGHT_INTENT_KEY = "pageHeight";

   private static final int ACTIVITY_BACKGROUND_COLOR = Color.parseColor("#BB000000");

   private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;

   private static final int MARGIN_PX_SIZE = OSUtils.dpToPx(24);

   static WebViewActivity instance;

   WebView webView;
   DraggableRelativeLayout draggableRelativeLayout;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      instance = this;


      getWindow().getDecorView().setBackgroundColor(ACTIVITY_BACKGROUND_COLOR);

      webView = WebViewManager.webView;

      addLayoutAndView();
   }

   // TODO: Test layout with a fullscreen app (without status bar or buttons)
   // TODO: Edge case: Modal in portrait is to tall when using split screen mode
   void addLayoutAndView() {
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

      FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(pageWidth, pageHeight);

      String displayLocation = extra.getString(DISPLAY_LOCATION_INTENT_KEY);
      if ("top".equals(displayLocation))
         frameLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      else if ("bottom".equals(displayLocation))
         frameLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
      else
         frameLayoutParams.gravity = Gravity.CENTER;


      draggableRelativeLayout = new DraggableRelativeLayout(this);
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
      draggableRelativeLayout.addView(webView);

      setupDraggableLayout(pageHeight, displayLocation);
   }

   void setupDraggableLayout(int pageHeight, String displayLocation) {
      DraggableRelativeLayout.Params draggableParams = new DraggableRelativeLayout.Params();
      draggableParams.maxXPos = MARGIN_PX_SIZE;
      draggableParams.maxYPos = MARGIN_PX_SIZE;
      // TODO: Look into using positions from view's.
      //    Tried getLocationInWindow but it was always returning 0;
      draggableParams.height = pageHeight;
      if (pageHeight == -1)
         draggableParams.height = pageHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

      if ("bottom".equals(displayLocation))
         draggableParams.posY = Resources.getSystem().getDisplayMetrics().heightPixels - pageHeight;
      else if (!"top".equals(displayLocation)) // Center
         draggableParams.posY = (Resources.getSystem().getDisplayMetrics().heightPixels / 2) - (pageHeight / 2);

      draggableParams.dragDirection = "top".equals(displayLocation) ?
         DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP :
         DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN;
      draggableRelativeLayout.setParams(draggableParams);
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

   void dismiss() {
      draggableRelativeLayout.dismiss();
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
