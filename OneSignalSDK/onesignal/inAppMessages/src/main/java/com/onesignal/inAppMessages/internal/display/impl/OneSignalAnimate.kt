package com.onesignal.inAppMessages.internal.display.impl

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation

internal object OneSignalAnimate {
    /**
     * A Y-axis translation animation that will start at a delta Y and go to the originally
     * drawn location
     */
    fun animateViewByTranslation(
        view: View,
        deltaFromY: Float,
        deltaToY: Float,
        duration: Int,
        interpolator: Interpolator?,
        animCallback: Animation.AnimationListener?
    ): Animation {
        val translateAnimation: Animation = TranslateAnimation(0f, 0f, deltaFromY, deltaToY)
        translateAnimation.duration = duration.toLong()
        translateAnimation.interpolator = interpolator
        if (animCallback != null) translateAnimation.setAnimationListener(animCallback)
        view.animation = translateAnimation
        return translateAnimation
    }

    /**
     * A value animator animation that continuously updates the background view color until
     * from a start color to end color
     */
    fun animateViewColor(
        view: View,
        duration: Int,
        colorStart: Int,
        colorEnd: Int,
        animCallback: Animator.AnimatorListener?
    ): ValueAnimator {
        val backgroundAnimation = ValueAnimator()
        backgroundAnimation.duration = duration.toLong()
        backgroundAnimation.setIntValues(
            colorStart,
            colorEnd
        )
        backgroundAnimation.setEvaluator(ArgbEvaluator())
        backgroundAnimation.addUpdateListener { valueAnimator -> view.setBackgroundColor((valueAnimator.animatedValue as Int)) }
        if (animCallback != null) backgroundAnimation.addListener(animCallback)
        return backgroundAnimation
    }

    /**
     * A scale animation from 0f (hidden) to 1f (original size)
     */
    fun animateViewSmallToLarge(
        view: View,
        duration: Int,
        interpolator: Interpolator?,
        animCallback: Animation.AnimationListener?
    ): Animation {
        val scaleAnimation: Animation = ScaleAnimation(
            0f,
            1f,
            0f,
            1f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        scaleAnimation.duration = duration.toLong()
        scaleAnimation.interpolator = interpolator
        if (animCallback != null) scaleAnimation.setAnimationListener(animCallback)
        view.animation = scaleAnimation
        return scaleAnimation
    }
}
