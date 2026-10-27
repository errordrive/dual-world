package com.example.dualworld.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dualworld.R

/**
 * Custom row component for catalog selection featuring an icon, title, subtitle/version, and checkbox.
 */
class AppListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val titleView = TextView(context).apply {
        setTextColor(Color.parseColor("#F8FAFC"))
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    val subtitleView = TextView(context).apply {
        setTextColor(Color.parseColor("#94A3B8"))
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    val checkBox = CheckBox(context).apply {
        isClickable = false
        isFocusable = false
    }

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_card_glass)
        isClickable = true
        isFocusable = true

        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val padV = (12 * dp).toInt()
        setPadding(padH, padV, padH, padV)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconSize = (44 * dp).toInt()
        root.addView(iconView, LinearLayout.LayoutParams(iconSize, iconSize))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * dp).toInt()
                marginEnd = (8 * dp).toInt()
            }
            layoutParams = lp
        }
        textCol.addView(titleView)
        val subLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (2 * dp).toInt()
        }
        textCol.addView(subtitleView, subLp)
        root.addView(textCol)

        root.addView(checkBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(title: String, subtitle: String, icon: Drawable?, isChecked: Boolean) {
        titleView.text = title
        subtitleView.text = subtitle
        iconView.setImageDrawable(icon)
        checkBox.isChecked = isChecked
    }
}
