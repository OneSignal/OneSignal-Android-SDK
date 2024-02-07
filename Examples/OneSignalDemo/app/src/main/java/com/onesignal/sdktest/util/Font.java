package com.onesignal.example.util;

import android.content.Context;
import android.graphics.Typeface;
import com.google.android.material.textfield.TextInputLayout;
import android.view.View;
import android.widget.TextView;

public class Font {

    public Typeface saralaRegular;
    public Typeface saralaBold;


    public Font(Context context) {
        saralaRegular = Typeface.createFromAsset(context.getAssets(), "fonts/Sarala-Regular.ttf");
        saralaBold = Typeface.createFromAsset(context.getAssets(), "fonts/Sarala-Bold.ttf");
    }

    public void applyFont(View view, Typeface typeface) {
        if (view instanceof TextView) {
            ((TextView) view).setTypeface(typeface);
        } else if (view instanceof TextInputLayout) {
            ((TextInputLayout) view).setTypeface(typeface);
        }
    }

}