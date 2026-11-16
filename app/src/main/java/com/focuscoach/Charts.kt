package com.focuscoach

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

val CAT_COLORS = intArrayOf(
    0xFF22C55E.toInt(), // Screen
    0xFF38BDF8.toInt(), // Notes
    0xFFF59E0B.toInt(), // Time-pass
    0xFF94A3B8.toInt()  // Away
)

// Goal ring: animated sweep + percent in the middle.
class RingView(c: Context) : View(c) {
    private var cur = 0f
    private var tgt = 0f
    private var l1 = "0%"
    private var l2 = ""
    private var an: ValueAnimator? = null
    private val oval = RectF()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF334155.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt(); textAlign = Paint.Align.CENTER
    }

    fun set(frac: Float, a: String, b: String) {
        l1 = a; l2 = b
        val t = frac.coerceIn(0f, 1f)
        if (abs(t - tgt) > 0.0005f) {
            tgt = t
            an?.cancel()
            an = ValueAnimator.ofFloat(cur, t).apply {
                duration = 700
                interpolator = DecelerateInterpolator()
                addUpdateListener { cur = it.animatedValue as Float; invalidate() }
                start()
            }
        } else invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.09f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        val cx = width / 2f
        val cy = height / 2f
        oval.set(cx - s / 2 + sw, cy - s / 2 + sw, cx + s / 2 - sw, cy + s / 2 - sw)
        cv.drawArc(oval, 0f, 360f, false, track)
        if (cur > 0.001f) {
            arc.shader = SweepGradient(cx, cy, intArrayOf(0xFF38BDF8.toInt(), 0xFF22C55E.toInt()), null)
            cv.save()
            cv.rotate(-90f, cx, cy)
            cv.drawArc(oval, 0f, 360f * cur, false, arc)
            cv.restore()
        }
        big.textSize = s * 0.2f
        small.textSize = s * 0.075f
        cv.drawText(l1, cx, cy + s * 0.04f, big)
        cv.drawText(l2, cx, cy + s * 0.04f + s * 0.12f, small)
    }
}

// Donut + legend: where the time went.
class DonutView(c: Context) : View(c) {
    private val dn = resources.displayMetrics.density
    private val cur = FloatArray(4)
    private var tgt = FloatArray(4)
    private var an: ValueAnimator? = null
    private val oval = RectF()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(v: LongArray) {
        val t = FloatArray(4) { v[it].toFloat() }
        if (t.contentEquals(tgt)) return
        val from = cur.copyOf()
        tgt = t
        an?.cancel()
        an = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val k = a.animatedValue as Float
                for (i in 0..3) cur[i] = from[i] + (tgt[i] - from[i]) * k
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val d = minOf(height.toFloat(), width * 0.42f)
        val sw = d * 0.16f
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        val top = (height - d) / 2f
        oval.set(sw / 2, top + sw / 2, d - sw / 2, top + d - sw / 2)
        p.color = 0xFF334155.toInt()
        cv.drawArc(oval, 0f, 360f, false, p)
        val tot = cur.sum()
        if (tot > 0f) {
            var a = -90f
            for (i in 0..3) {
                val sweep = cur[i] / tot * 360f
                if (sweep <= 0.5f) continue
                p.color = CAT_COLORS[i]
                cv.drawArc(oval, a, maxOf(sweep - 3f, 0.5f), false, p)
                a += sweep
            }
        }
        val tt = tgt.sum()
        val tm = (tt / 60f).toLong()
        tp.color = Color.WHITE
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = d * 0.17f
        cv.drawText("${tm / 60}h ${tm % 60}m", d / 2f, height / 2f + d * 0.06f, tp)

        val x0 = d + 28 * dn
        val rowH = height / 4f
        p.style = Paint.Style.FILL
        tp.textSize = 15 * dn
        for (i in 0..3) {
            val y = i * rowH + rowH / 2f
            p.color = CAT_COLORS[i]
            cv.drawCircle(x0, y, 6 * dn, p)
            tp.typeface = Typeface.DEFAULT
            tp.color = Color.WHITE
            tp.textAlign = Paint.Align.LEFT
            cv.drawText(Store.NAMES[i], x0 + 16 * dn, y + 5 * dn, tp)
            val m = (tgt[i] / 60f).toLong()
            val pc = if (tt > 0f) (tgt[i] / tt * 100).toInt() else 0
            tp.textAlign = Paint.Align.RIGHT
            tp.color = 0xFF94A3B8.toInt()
            cv.drawText("${m / 60}h ${m % 60}m   $pc%", width - 4 * dn, y + 5 * dn, tp)
        }
    }
}

// Last 2 hours, 1 rounded block per minute.
class TimelineView(c: Context) : View(c) {
    private var tl = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun set(s: String) {
        if (s != tl) { tl = s; invalidate() }
    }

    override fun onDraw(cv: Canvas) {
        val n = 120
        val s = if (tl.length > n) tl.takeLast(n) else tl
        val cw = width.toFloat() / n
        val gap = cw * 0.18f
        for (i in 0 until n) {
            p.color = if (i < s.length) CAT_COLORS[(s[i] - '0').coerceIn(0, 3)] else 0xFF334155.toInt()
            r.set(i * cw + gap, 0f, (i + 1) * cw - gap, height.toFloat())
            cv.drawRoundRect(r, cw / 2, cw / 2, p)
        }
    }
}
