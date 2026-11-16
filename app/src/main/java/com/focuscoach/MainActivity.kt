package com.focuscoach

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
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
    private lateinit var status: TextView
    private lateinit var bars: BarChartView
    private lateinit var tl: TimelineView
    private var pendingStart = false

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f }
        col.addView(TextView(this).apply { text = "Focus Coach"; textSize = 28f })

        col.addView(label("Aaj ka goal (ghante)"))
        goal = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("15")
        }
        col.addView(goal)

        col.addView(label("Distract hone par call kitne minute baad"))
        limit = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText("3") }
        col.addView(limit)

        front = CheckBox(this).apply { text = "Front camera (band = back camera)"; isChecked = true }
        col.addView(front)

        col.addView(Button(this).apply {
            text = "Start"
            setOnClickListener { tryStart() }
        })
        col.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startService(Intent(this@MainActivity, FocusService::class.java)
                    .setAction(FocusService.ACTION_STOP))
            }
        })
        col.addView(Button(this).apply {
            text = "Coach ko call karo (break ke liye)"
            setOnClickListener { startActivity(Intent(this@MainActivity, CallActivity::class.java)) }
        })

        status = TextView(this).apply { textSize = 18f; setPadding(0, dp(16), 0, dp(8)) }
        col.addView(status)

        bars = BarChartView(this)
        col.addView(bars, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)))
        col.addView(label("Timeline (1 block = 1 minute)").apply { setPadding(0, dp(16), 0, dp(4)) })
        tl = TimelineView(this)
        col.addView(tl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        setContentView(ScrollView(this).apply { addView(col) })
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
            val t = Store.totals(this@MainActivity)
            bars.set(t)
            tl.set(Store.timeline(this@MainActivity))
            val f = Store.focusSec(this@MainActivity) / 60
            val g = Store.goalSec(this@MainActivity) / 3600f
            val bu = Store.breakUntil(this@MainActivity)
            val state = when {
                !Store.running(this@MainActivity) -> "Band hai"
                bu > 0 -> "Break: ${maxOf(0L, (bu - System.currentTimeMillis()) / 60000)} min baaki"
                else -> "Abhi: ${Store.NAMES[Store.cur(this@MainActivity)]}"
            }
            status.text = "$state\nFocus: ${f / 60}h ${f % 60}m / $g h"
            h.postDelayed(this, 1000)
        }
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
}
