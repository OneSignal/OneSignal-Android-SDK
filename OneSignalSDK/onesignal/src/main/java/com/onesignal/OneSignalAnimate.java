package com.onesignal;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

class OneSignalAnimate {

    /**
     * A Y-axis translation animation that will start at a delta Y and go to the originally
     * drawn location
     */
    static Animation animateViewByTranslation(View view, float deltaFromY, float deltaToY, int duration, Interpolator interpolator, Animation.AnimationListener animCallback) {
        Animation translateAnimation = new TranslateAnimation(0f, 0f, deltaFromY, deltaToY);
        translateAnimation.setDuration(duration);
        translateAnimation.setInterpolator(interpolator);
        if (animCallback != null)
            translateAnimation.setAnimationListener(animCallback);
        view.setAnimation(translateAnimation);

        return translateAnimation;
    }

    /**
     * A value animator animation that continuously updates the background view color until
     * from a start color to end color
     */
    static ValueAnimator animateViewColor(final View view, int duration, int colorStart, int colorEnd, Animator.AnimatorListener animCallback) {
        ValueAnimator backgroundAnimation = new ValueAnimator();
        backgroundAnimation.setDuration(duration);
        backgroundAnimation.setIntValues(
                colorStart,
                colorEnd);
        backgroundAnimation.setEvaluator(new ArgbEvaluator());
        backgroundAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                view.setBackgroundColor((Integer) valueAnimator.getAnimatedValue());
            }
        });
        if (animCallback != null)
            backgroundAnimation.addListener(animCallback);

        return backgroundAnimation;
    }

    /**
     * A scale animation from 0f (hidden) to 1f (original size)
     */
    static Animation animateViewSmallToLarge(View view, int duration, Interpolator interpolator, Animation.AnimationListener animCallback) {
        Animation scaleAnimation = new ScaleAnimation(
                0f, 1f,
                0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimation.setDuration(duration);
        scaleAnimation.setInterpolator(interpolator);
        if (animCallback != null)
            scaleAnimation.setAnimationListener(animCallback);
        view.setAnimation(scaleAnimation);

        return scaleAnimation;
    }



}
