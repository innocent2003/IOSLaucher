package com.example.oslaucher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator

internal object PageTransitionAnimator {
    const val PREFERENCES_NAME = "launcher_settings"
    const val EFFECT_KEY = "page_transition_effect"
    const val DEFAULT = 0
    const val ROTATE = 1
    const val FLIP = 2
    const val ZOOM = 3
    const val FADE = 4

    fun animate(
        context: Context,
        outgoing: View,
        incoming: View,
        direction: Int,
        effect: Int,
        duration: Long,
        onEnd: () -> Unit
    ) {
        animateProgress(context, outgoing, incoming, direction, effect, 0f, 1f, duration, onEnd)
    }

    fun animateProgress(
        context: Context,
        outgoing: View,
        incoming: View,
        direction: Int,
        effect: Int,
        startProgress: Float,
        endProgress: Float,
        duration: Long,
        onEnd: () -> Unit
    ) {
        outgoing.animate().cancel()
        incoming.animate().cancel()
        applyProgress(context, outgoing, incoming, direction, effect, startProgress)
        val animator = ValueAnimator.ofFloat(startProgress, endProgress).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener {
                applyProgress(
                    context,
                    outgoing,
                    incoming,
                    direction,
                    effect,
                    it.animatedValue as Float
                )
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    reset(outgoing)
                    reset(incoming)
                    onEnd()
                }
            })
        }
        animator.start()
    }

    fun applyProgress(
        context: Context,
        outgoing: View,
        incoming: View,
        direction: Int,
        effect: Int,
        progress: Float
    ) {
        val amount = progress.coerceIn(-0.04f, 1.04f)
        val width = (outgoing.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels).toFloat()
        reset(outgoing)
        reset(incoming)
        incoming.translationZ = outgoing.translationZ + 1f

        outgoing.translationX = direction * width * amount
        incoming.translationX = -direction * width * (1f - amount)
        when (effect) {
            ROTATE -> {
                setCameraDistance(context, outgoing, incoming)
                outgoing.pivotX = if (direction < 0) 0f else outgoing.width.toFloat()
                incoming.pivotX = if (direction < 0) incoming.width.toFloat() else 0f
                outgoing.rotationY = direction * 8f * amount
                incoming.rotationY = -direction * 8f * (1f - amount)
                outgoing.scaleX = 1f - 0.04f * amount
                incoming.scaleX = 0.96f + 0.04f * amount
            }

            FLIP -> {
                setCameraDistance(context, outgoing, incoming)
                outgoing.rotationY = direction * 16f * amount
                incoming.rotationY = -direction * 16f * (1f - amount)
            }

            ZOOM -> {
                outgoing.scaleX = 1f - 0.06f * amount
                outgoing.scaleY = 1f - 0.06f * amount
                incoming.scaleX = 0.94f + 0.06f * amount
                incoming.scaleY = 0.94f + 0.06f * amount
            }

            FADE -> {
                outgoing.alpha = (1f - 0.18f * amount).coerceIn(0f, 1f)
                incoming.alpha = (0.82f + 0.18f * amount).coerceIn(0f, 1f)
            }
        }
    }

    private fun setCameraDistance(context: Context, outgoing: View, incoming: View) {
        val distance = context.resources.displayMetrics.density * 12000f
        outgoing.cameraDistance = distance
        incoming.cameraDistance = distance
    }

    fun reset(view: View) {
        view.translationX = 0f
        view.translationY = 0f
        view.rotation = 0f
        view.rotationX = 0f
        view.rotationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        view.translationZ = 0f
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
    }

}
