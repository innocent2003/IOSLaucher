package com.example.oslaucher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator

internal object PageTransitionAnimator {
    class TransitionLayerCache internal constructor(
        val outgoingLayerType: Int,
        val incomingLayerType: Int
    )

    const val PREFERENCES_NAME = "launcher_settings"
    const val EFFECT_KEY = "page_transition_effect"
    const val DEFAULT = 0
    const val ROTATE = 1
    const val FLIP = 2
    const val ZOOM = 3
    const val FADE = 4
    const val SCREEN_DURATION = 300L
    private val transitionInterpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)

    fun animate(
        context: Context,
        outgoing: View,
        incoming: View,
        direction: Int,
        effect: Int,
        duration: Long,
        travelWidth: Float? = null,
        onEnd: () -> Unit
    ) {
        animateProgress(
            context,
            outgoing,
            incoming,
            direction,
            effect,
            0f,
            1f,
            duration,
            travelWidth,
            onEnd
        )
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
        travelWidth: Float? = null,
        onEnd: () -> Unit
    ) {
        outgoing.animate().cancel()
        incoming.animate().cancel()
        val layerCache = enableTransitionLayerCache(outgoing, incoming)
        applyProgress(context, outgoing, incoming, direction, effect, startProgress, travelWidth)
        val animator = ValueAnimator.ofFloat(startProgress, endProgress).apply {
            this.duration = duration
            interpolator = transitionInterpolator
            addUpdateListener {
                applyProgress(
                    context,
                    outgoing,
                    incoming,
                    direction,
                    effect,
                    it.animatedValue as Float,
                    travelWidth
                )
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    reset(outgoing)
                    reset(incoming)
                    restoreTransitionLayerCache(outgoing, incoming, layerCache)
                    onEnd()
                }
            })
        }
        animator.start()
    }

    fun enableTransitionLayerCache(
        outgoing: View,
        incoming: View
    ): TransitionLayerCache {
        val cache = TransitionLayerCache(outgoing.layerType, incoming.layerType)
        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        return cache
    }

    fun restoreTransitionLayerCache(
        outgoing: View,
        incoming: View,
        cache: TransitionLayerCache
    ) {
        outgoing.setLayerType(cache.outgoingLayerType, null)
        incoming.setLayerType(cache.incomingLayerType, null)
    }

    fun applyProgress(
        context: Context,
        outgoing: View,
        incoming: View,
        direction: Int,
        effect: Int,
        progress: Float,
        travelWidth: Float? = null
    ) {
        val amount = progress.coerceIn(-0.04f, 1.04f)
        val width = travelWidth?.takeIf { it > 0f }
            ?: (outgoing.width.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels).toFloat()
        reset(outgoing)
        reset(incoming)
        incoming.translationZ = outgoing.translationZ + 1f

        outgoing.translationX = direction * width * amount
        incoming.translationX = -direction * width * (1f - amount)
        when (effect) {
            ROTATE -> {
                setCameraDistance(context, outgoing, incoming)
                setEdgePivots(outgoing, incoming, direction)
                outgoing.rotationY = direction * 90f * amount
                incoming.rotationY = -direction * 90f * (1f - amount)
            }

            FLIP -> {
                setCameraDistance(context, outgoing, incoming)
                val depth = kotlin.math.sin(amount * Math.PI).toFloat().coerceAtLeast(0f)
                outgoing.rotationY = direction * 90f * amount
                incoming.rotationY = -direction * 90f * (1f - amount)
                outgoing.scaleX = 1f - 0.16f * depth
                outgoing.scaleY = 1f - 0.16f * depth
                incoming.scaleX = 1f - 0.16f * depth
                incoming.scaleY = 1f - 0.16f * depth
                outgoing.translationZ = -dp(context, 48f) * depth
                incoming.translationZ = dp(context, 48f) * depth + 1f
            }

            ZOOM -> {
                setCameraDistance(context, outgoing, incoming)
                setEdgePivots(outgoing, incoming, direction)
                val depth = kotlin.math.sin(amount * Math.PI).toFloat().coerceAtLeast(0f)
                outgoing.rotationY = direction * 180f * amount
                incoming.rotationY = -direction * 180f * (1f - amount)
                outgoing.scaleX = 1f - 0.08f * depth
                outgoing.scaleY = 1f - 0.08f * depth
                incoming.scaleX = 1f - 0.08f * depth
                incoming.scaleY = 1f - 0.08f * depth
            }

            FADE -> {
                setCameraDistance(context, outgoing, incoming)
                outgoing.pivotX = if (direction < 0) outgoing.width.toFloat() else 0f
                incoming.pivotX = if (direction < 0) 0f else incoming.width.toFloat()
                outgoing.rotationY = direction * 30f * amount
                incoming.rotationY = -direction * 30f * (1f - amount)
                outgoing.scaleX = 1f - 0.08f * amount
                outgoing.scaleY = 1f - 0.08f * amount
                incoming.scaleX = 0.92f + 0.08f * amount
                incoming.scaleY = 0.92f + 0.08f * amount
                outgoing.alpha = (1f - 0.18f * amount).coerceIn(0f, 1f)
                incoming.alpha = (0.82f + 0.18f * amount).coerceIn(0f, 1f)
            }
        }
    }

    private fun setEdgePivots(outgoing: View, incoming: View, direction: Int) {
        outgoing.pivotX = if (direction < 0) outgoing.width.toFloat() else 0f
        incoming.pivotX = if (direction < 0) 0f else incoming.width.toFloat()
    }

    private fun setCameraDistance(context: Context, outgoing: View, incoming: View) {
        val maxDimension = maxOf(
            outgoing.width,
            outgoing.height,
            incoming.width,
            incoming.height,
            1
        )
        val distance = maxDimension * 4f
        outgoing.cameraDistance = distance
        incoming.cameraDistance = distance
    }

    private fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

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
