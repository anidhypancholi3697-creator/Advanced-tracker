package com.focuscoach

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

val CAT_COLORS = intArrayOf(
    Color.parseColor("#2E7D32"), // Screen
    Color.parseColor("#1565C0"), // Notes
    Color.parseColor("#EF6C00"), // Time-pass
    Color.parseColor("#757575")  // Away
)

class BarChartView(c: Context) : View(c) {
    private var v = LongArray(4)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f }

    fun set(values: LongArray) { v = values; invalidate() }

    override fun onDraw(cv: Canvas) {
        val max = (v.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val rowH = height / 4f
        val left = 220f
        val barMax = width - left - 220f
        for (i in 0 until 4) {
            val y = i * rowH
            p.color = Color.DKGRAY
            cv.drawText(Store.NAMES[i], 8f, y + rowH * 0.65f, p)
            p.color = CAT_COLORS[i]
            cv.drawRect(left, y + rowH * 0.2f, left + barMax * v[i] / max, y + rowH * 0.8f, p)
            p.color = Color.DKGRAY
            val m = v[i] / 60
            cv.drawText("${m / 60}h ${m % 60}m", left + barMax + 12f, y + rowH * 0.65f, p)
        }
    }
}

class TimelineView(c: Context) : View(c) {
    private var tl = ""
    private val p = Paint()

    fun set(s: String) { tl = s; invalidate() }

    override fun onDraw(cv: Canvas) {
        val cell = width.toFloat() / maxOf(tl.length, 60)
        for ((i, ch) in tl.withIndex()) {
            p.color = CAT_COLORS[(ch - '0').coerceIn(0, 3)]
            cv.drawRect(i * cell, 0f, (i + 1) * cell, height.toFloat(), p)
        }
    }
}
