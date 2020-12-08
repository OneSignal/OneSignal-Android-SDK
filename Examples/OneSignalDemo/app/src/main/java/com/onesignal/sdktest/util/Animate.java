package com.onesignal.sdktest.util;

import android.view.View;

public class Animate {

    public Animate() {
    }

    public void toggleAnimationView(boolean showAnimation, int visibility, View view, View anim) {
        int viewVis = showAnimation ? visibility : View.VISIBLE;
        int animVis = showAnimation ? View.VISIBLE : visibility;

        view.setVisibility(viewVis);
        anim.setVisibility(animVis);
    }

}
