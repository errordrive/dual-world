package com.example.dualworld.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dualworld.R

/**
 * Premium glassy card component representing an individual active or idle cloned app.
 */
class CloneCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val nameView = TextView(context).apply {
        setTextColor(Color.parseColor("#F8FAFC"))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }

    val statusDot = StatusDot(context)

    val userTagView = TextView(context).apply {
        setTextColor(Color.parseColor("#94A3B8"))
        textSize = 10f
        maxLines = 1
        gravity = Gravity.CENTER
    }

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_card_glass)
        isClickable = true
        isFocusable = true

        val dp = resources.displayMetrics.density
        val cardWidth = (110 * dp).toInt()
        val cardHeight = (130 * dp).toInt()
        val pad = (12 * dp).toInt()

        layoutParams = LayoutParams(cardWidth, cardHeight)
        setPadding(pad, pad, pad, pad)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val iconFrame = FrameLayout(context)
        val iconSize = (52 * dp).toInt()
        val iconLp = LayoutParams(iconSize, iconSize, Gravity.CENTER)
        iconFrame.addView(iconView, iconLp)

        val dotSize = (14 * dp).toInt()
        val dotLp = LayoutParams(dotSize, dotSize, Gravity.TOP or Gravity.END)
        iconFrame.addView(statusDot, dotLp)

        container.addView(iconFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10 * dp).toInt()
        }
        container.addView(nameView, nameLp)

        val userLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (2 * dp).toInt()
        }
        container.addView(userTagView, userLp)

        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun bind(appName: String, icon: Drawable?, isRunning: Boolean, userId: Int) {
        nameView.text = appName
        iconView.setImageDrawable(icon)
        statusDot.setRunning(isRunning)
        userTagView.text = "Account #$userId"
    }
}
