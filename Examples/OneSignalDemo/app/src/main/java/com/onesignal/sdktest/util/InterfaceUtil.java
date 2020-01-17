package com.onesignal.sdktest.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class InterfaceUtil {

    public static float getScreenDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    public static int getScreenWidth(Context context) {
        return ((Activity) context).getWindowManager().getDefaultDisplay().getWidth();
    }

    public static int getScreenHeight(Context context) {
        return ((Activity) context).getWindowManager().getDefaultDisplay().getHeight();
    }

    public static void showKeyboardFrom(Context context) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    public static void hideKeyboardFrom(Context context, View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
