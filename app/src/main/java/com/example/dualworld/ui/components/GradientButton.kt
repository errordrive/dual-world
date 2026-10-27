package com.example.dualworld.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dualworld.R

/**
 * Reusable premium filled gradient call-to-action button with spring press feedback.
 */
class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        isAllCaps = false
    }

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_gradient_button)
        isClickable = true
        isFocusable = true
        elevation = 4f * resources.displayMetrics.density

        val padV = (14 * resources.displayMetrics.density).toInt()
        val padH = (24 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        addView(textView, lp)

        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
    }

    fun setText(text: CharSequence) {
        textView.text = text
    }

    fun getText(): CharSequence {
        return textView.text
    }
}
