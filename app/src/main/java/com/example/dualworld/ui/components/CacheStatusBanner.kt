package com.example.dualworld.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dualworld.R

/**
 * Dismissible top banner indicating cache synchronization, DEX optimization, and engine readiness.
 */
class CacheStatusBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView = TextView(context).apply {
        setTextColor(Color.parseColor("#F8FAFC"))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        text = "Virtual Engine Active"
    }

    private val subtitleView = TextView(context).apply {
        setTextColor(Color.parseColor("#94A3B8"))
        textSize = 11f
        text = "4 Agent worker processes initialized"
    }

    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        visibility = GONE
    }

    private val closeButton = ImageView(context).apply {
        setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_close))
        isClickable = true
        isFocusable = true
        val dp = resources.displayMetrics.density
        val size = (20 * dp).toInt()
        layoutParams = LayoutParams(size, size, Gravity.CENTER_VERTICAL or Gravity.END)
    }

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_card_glass)
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val padV = (10 * dp).toInt()
        setPadding(padH, padV, padH, padV)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_sparkle))
            val size = (20 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (12 * dp).toInt()
            }
        }
        row.addView(icon)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(titleView)
        textCol.addView(subtitleView)
        row.addView(textCol)

        row.addView(progressBar)

        closeButton.setOnClickListener {
            animate().alpha(0f).translationY(-20f).setDuration(200).withEndAction {
                visibility = GONE
            }.start()
        }
        row.addView(closeButton)

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setStatus(title: String, subtitle: String, isBusy: Boolean) {
        titleView.text = title
        subtitleView.text = subtitle
        progressBar.visibility = if (isBusy) VISIBLE else GONE
    }
}
