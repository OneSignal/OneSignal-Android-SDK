package com.onesignal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.webkit.WebView;

// Custom WebView to round corners
public class OSWebView extends WebView {

   public static final int RADIUS = OSUtils.dpToPx(8);
   private Path clipPath = new Path();
   private RectF rectF;

   public OSWebView(Context context) {
      super(context);
   }

   // This method gets called when the view first loads, and also whenever the
   // view changes. Use this opportunity to save the view's width and height.
   @Override
   protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
      super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
      rectF = new RectF(0, 0, newWidth, newHeight);
   }

   // Round off the edges of the WebView
   // No effect on Android 4.4 and lower
   @Override
   protected void onDraw(Canvas canvas) {
      clipPath.addRoundRect(rectF, RADIUS, RADIUS, Path.Direction.CW);
      canvas.clipPath(clipPath);
      super.onDraw(canvas);
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
