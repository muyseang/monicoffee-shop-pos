package com.monicoffee.pos.ui.widget

import android.content.Context
import android.view.ViewGroup

/** Lays its children out left to right, wrapping onto a new line when the next one does not fit. */
class FlowRow(context: Context, private val gap: Int, private val lineGap: Int) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x > 0 && x + child.measuredWidth > width) { x = 0; y += line + lineGap; line = 0 }
            x += child.measuredWidth + gap; line = maxOf(line, child.measuredHeight)
        }
        setMeasuredDimension(width, y + line)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > width) { x = 0; y += line + lineGap; line = 0 }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap; line = maxOf(line, child.measuredHeight)
        }
    }
}
