package com.onesignal;

import android.content.Context;
import android.webkit.WebView;

// Custom WebView to lock scrolling
public class OSWebView extends WebView {

   public OSWebView(Context context) {
      super(context);
   }

   // The method overrides below; overScrollBy, scrollTo, and computeScroll prevent page scrolling
   @Override
   public boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
                               int scrollRangeX, int scrollRangeY, int maxOverScrollX,
                               int maxOverScrollY, boolean isTouchEvent) {
      return false;
   }

   @Override
   public void scrollTo(int x, int y) {
      // Do nothing
   }

   @Override
   public void computeScroll() {
      // Do nothing
   }
}
