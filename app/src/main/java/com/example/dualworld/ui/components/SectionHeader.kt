package com.example.dualworld.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Standard section divider header displaying a title and optional clickable action link.
 */
class SectionHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView = TextView(context).apply {
        setTextColor(Color.parseColor("#F8FAFC"))
        textSize = 17f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val actionView = TextView(context).apply {
        setTextColor(Color.parseColor("#818CF8"))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        visibility = GONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padV = (10 * resources.displayMetrics.density).toInt()
        val padH = (4 * resources.displayMetrics.density).toInt()
        setPadding(padH, padV, padH, padV)

        val titleLp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        addView(titleView, titleLp)

        val actionLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        addView(actionView, actionLp)
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setAction(label: CharSequence?, onClick: OnClickListener?) {
        if (label != null && onClick != null) {
            actionView.text = label
            actionView.setOnClickListener(onClick)
            actionView.visibility = VISIBLE
        } else {
            actionView.visibility = GONE
        }
    }
}
