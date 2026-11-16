package com.focuscoach

import android.content.Context

object Store {
    const val SCREEN = 0
    const val NOTES = 1
    const val DISTRACTED = 2
    const val AWAY = 3
    val NAMES = arrayOf("Screen", "Notes", "Time-pass", "Away")

    private fun p(c: Context) = c.getSharedPreferences("fc", Context.MODE_PRIVATE)

    fun reset(c: Context, goalH: Float, limitMin: Int, front: Boolean) {
        p(c).edit()
            .putFloat("goalH", goalH).putInt("limitMin", limitMin).putBoolean("front", front)
            .putString("tot", "0,0,0,0").putString("tl", "").putLong("breakUntil", 0)
            .putInt("cur", AWAY).putBoolean("running", true).apply()
    }

    fun goalSec(c: Context) = (p(c).getFloat("goalH", 8f) * 3600).toLong()
    fun limitMs(c: Context) = p(c).getInt("limitMin", 3) * 60000L
    fun front(c: Context) = p(c).getBoolean("front", true)
    fun running(c: Context) = p(c).getBoolean("running", false)
    fun setRunning(c: Context, v: Boolean) = p(c).edit().putBoolean("running", v).apply()
    fun cur(c: Context) = p(c).getInt("cur", AWAY)
    fun setCur(c: Context, v: Int) = p(c).edit().putInt("cur", v).apply()
    fun breakUntil(c: Context) = p(c).getLong("breakUntil", 0)
    fun setBreakUntil(c: Context, v: Long) = p(c).edit().putLong("breakUntil", v).apply()

    fun totals(c: Context): LongArray {
        val l = (p(c).getString("tot", "0,0,0,0") ?: "0,0,0,0").split(",").map { it.toLongOrNull() ?: 0L }
        return LongArray(4) { l.getOrElse(it) { 0L } }
    }

    fun add(c: Context, cat: Int, sec: Long) {
        val t = totals(c)
        t[cat] += sec
        p(c).edit().putString("tot", t.joinToString(",")).apply()
    }

    fun focusSec(c: Context) = totals(c).let { it[SCREEN] + it[NOTES] }

    fun timeline(c: Context): String = p(c).getString("tl", "") ?: ""
    fun addTimeline(c: Context, cat: Int) = p(c).edit().putString("tl", timeline(c) + cat).apply()
}
