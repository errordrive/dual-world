package com.example.dualworld.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Animated status indicator dot with a soft radial pulsing glow for active virtual containers.
 */
class StatusDot @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var pulseRadius = 0f
    private var pulseAlpha = 0
    private var isRunningState = false
    private var animator: ValueAnimator? = null

    init {
        setRunning(false)
    }

    fun setRunning(running: Boolean) {
        animator?.cancel()
        animator = null
        this.isRunningState = running
        corePaint.color = if (running)
            Color.parseColor("#10B981")
        else Color.parseColor("#64748B")
        pulsePaint.color = if (running)
            Color.parseColor("#10B981")
        else Color.TRANSPARENT
        pulseRadius = 0f
        pulseAlpha = 0
        invalidate()  // flush stale frame immediately
        if (running) startPulseAnimation()
    }

    private fun startPulseAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                val maxRadius = width.coerceAtLeast(height) / 2f
                val coreRadius = maxRadius * 0.5f
                pulseRadius = coreRadius + (maxRadius - coreRadius) * progress
                pulseAlpha = ((1f - progress) * 140).toInt()
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = width.coerceAtLeast(height) / 2f
        val coreRadius = maxRadius * 0.5f

        if (isRunningState && pulseAlpha > 0) {
            pulsePaint.alpha = pulseAlpha
            canvas.drawCircle(cx, cy, pulseRadius.coerceAtLeast(coreRadius), pulsePaint)
        }

        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
