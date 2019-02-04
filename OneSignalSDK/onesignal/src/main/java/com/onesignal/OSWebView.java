package com.onesignal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.webkit.WebView;

public class OSWebView extends WebView {

   private int width;
   private int height;
   private int radius;

   public OSWebView(Context context) {
      super(context);
   }

   // This method gets called when the view first loads, and also whenever the
   // view changes. Use this opportunity to save the view's width and height.
   @Override protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
      super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
      width = newWidth;
      height = newHeight;
      radius = OSUtils.dpToPx(7);
   }

   @Override protected void onDraw(Canvas canvas) {
      Path clipPath = new Path();
      clipPath.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
      canvas.clipPath(clipPath);
      super.onDraw(canvas);
   }
}
