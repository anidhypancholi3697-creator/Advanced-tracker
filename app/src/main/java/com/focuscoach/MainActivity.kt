package com.focuscoach

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private lateinit var goal: EditText
    private lateinit var limit: EditText
    private lateinit var front: CheckBox
    private lateinit var state: TextView
    private lateinit var dot: View
    private lateinit var ring: RingView
    private lateinit var donut: DonutView
    private lateinit var tl: TimelineView
    private var pendingStart = false

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun label(t: String, sp: Float = 14f, col: Int = 0xFF94A3B8.toInt(), bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = sp
            setTextColor(col)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(0xFF1E293B.toInt(), 20)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return c
    }

    private fun field(v: String, decimal: Boolean) = EditText(this).apply {
        setText(v)
        setTextColor(Color.WHITE)
        textSize = 18f
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        else InputType.TYPE_CLASS_NUMBER
        background = bg(0xFF0F172A.toInt(), 12)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    private fun btn(t: String, col: Int, f: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        isAllCaps = false
        textSize = 16f
        background = bg(col, 14)
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = 0xFF0F172A.toInt()
        window.navigationBarColor = 0xFF0F172A.toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        // Header
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(label("Focus Coach", 28f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY) }
        }
        head.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) })
        state = label("Band hai", 15f, Color.WHITE)
        head.addView(state)
        col.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // Goal ring
        val c1 = card(col)
        ring = RingView(this)
        c1.addView(ring, LinearLayout.LayoutParams(-1, dp(230)))

        // Settings
        val c2 = card(col)
        c2.addView(label("Aaj ka goal (ghante)"))
        goal = field("15", true)
        c2.addView(goal, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(12) })
        c2.addView(label("Distract hone par call kitne minute baad"))
        limit = field("3", false)
        c2.addView(limit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(8) })
        front = CheckBox(this).apply {
            text = "Front camera (band = back camera)"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        c2.addView(front)

        // Buttons
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            btn("Start", 0xFF16A34A.toInt()) { tryStart() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) }
        )
        row.addView(
            btn("Stop", 0xFFDC2626.toInt()) {
                startService(Intent(this, FocusService::class.java).setAction(FocusService.ACTION_STOP))
            },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) }
        )
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        col.addView(
            btn("Coach ko call karo (break ke liye)", 0xFF2563EB.toInt()) {
                startActivity(Intent(this, CallActivity::class.java))
            },
            LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(14) }
        )

        // Charts
        val c3 = card(col)
        c3.addView(label("Time kahan gaya", 16f, Color.WHITE, true))
        donut = DonutView(this)
        c3.addView(donut, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(8) })

        val c4 = card(col)
        c4.addView(label("Pichhle 2 ghante (1 block = 1 minute)", 14f))
        tl = TimelineView(this)
        c4.addView(tl, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(10) })

        // Entrance animation
        for (i in 0 until col.childCount) {
            col.getChildAt(i).apply {
                alpha = 0f
                translationY = dp(24).toFloat()
                animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * 70L).setDuration(450)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
            addView(col)
        })
    }

    private fun tryStart() {
        val need = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) start() else {
            pendingStart = true
            requestPermissions(missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (pendingStart && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pendingStart = false
            start()
        }
    }

    private fun start() {
        val g = goal.text.toString().toFloatOrNull() ?: 8f
        val l = limit.text.toString().toIntOrNull() ?: 3
        Store.reset(this, g, l, front.isChecked)
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        } catch (_: Exception) {
        }
        startForegroundService(Intent(this, FocusService::class.java))
    }

    private val refresh = object : Runnable {
        override fun run() {
            val c = this@MainActivity
            val f = Store.focusSec(c)
            val gs = Store.goalSec(c).coerceAtLeast(1L)
            val frac = f.toFloat() / gs
            val fm = f / 60
            ring.set(frac, "${(frac * 100).toInt()}%", "${fm / 60}h ${fm % 60}m / ${"%.1f".format(gs / 3600f)}h")
            donut.set(Store.totals(c))
            tl.set(Store.timeline(c))

            val run = Store.running(c)
            val bu = Store.breakUntil(c)
            val txt: String
            val colr: Int
            if (!run) {
                txt = "Band hai"; colr = Color.GRAY
            } else if (bu > 0) {
                txt = "Break: ${maxOf(0L, (bu - System.currentTimeMillis()) / 60000)} min"; colr = 0xFFF59E0B.toInt()
            } else {
                val k = Store.cur(c)
                txt = Store.NAMES[k]; colr = CAT_COLORS[k]
            }
            state.text = txt
            (dot.background as GradientDrawable).setColor(colr)
            h.postDelayed(this, 1000)
        }
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
}
