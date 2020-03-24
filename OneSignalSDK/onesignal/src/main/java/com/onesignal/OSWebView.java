package com.onesignal;

import android.content.Context;
import android.webkit.WebView;

// Custom WebView to lock scrolling
public class OSWebView extends WebView {

   public OSWebView(Context context) {
      super(getFixedContext(context));
   }

   public OSWebView(Context context, AttributeSet attrs) {
      super(getFixedContext(context), attrs);
   }

   public OSWebView(Context context, AttributeSet attrs, int defStyleAttr) {
      super(getFixedContext(context), attrs, defStyleAttr);
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   public OSWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
      super(getFixedContext(context), attrs, defStyleAttr, defStyleRes);
   }

   public OSWebView(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
      super(getFixedContext(context), attrs, defStyleAttr, privateBrowsing);
   }

   public static Context getFixedContext(Context context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
         return context.createConfigurationContext(new Configuration());
      } else return context;
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
