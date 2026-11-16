package com.focuscoach

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

// Slide: right = Answer, left = Decline (normal phone jaisa).
class SlideView(c: Context, private val onAnswer: () -> Unit, private val onDecline: () -> Unit) : View(c) {
    private val dn = resources.displayMetrics.density
    private var hx = -1f
    private var drag = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15 * dn }
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = h / 2f
        if (hx < 0f) hx = w / 2f
        p.color = 0xFF1E293B.toInt()
        r.set(0f, 0f, w, h)
        cv.drawRoundRect(r, rad, rad, p)
        p.color = 0xFFEF4444.toInt()
        cv.drawCircle(rad, rad, rad * 0.6f, p)
        p.color = 0xFF22C55E.toInt()
        cv.drawCircle(w - rad, rad, rad * 0.6f, p)
        tp.textAlign = Paint.Align.LEFT
        cv.drawText("Decline", rad * 1.8f, rad + 5 * dn, tp)
        tp.textAlign = Paint.Align.RIGHT
        cv.drawText("Answer", w - rad * 1.8f, rad + 5 * dn, tp)
        p.color = when {
            hx > w * 0.65f -> 0xFF86EFAC.toInt()
            hx < w * 0.35f -> 0xFFFCA5A5.toInt()
            else -> Color.WHITE
        }
        cv.drawCircle(hx, rad, rad * 0.85f, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val rad = height / 2f
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                drag = abs(e.x - hx) < rad * 1.3f
                if (drag) parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (drag) {
                hx = e.x.coerceIn(rad, width - rad)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (drag) {
                drag = false
                when {
                    hx > width * 0.8f -> onAnswer()
                    hx < width * 0.2f -> onDecline()
                    else -> ValueAnimator.ofFloat(hx, width / 2f).apply {
                        duration = 200
                        addUpdateListener { hx = it.animatedValue as Float; invalidate() }
                        start()
                    }
                }
            }
        }
        return true
    }
}

class CallActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var pending: Pair<String, String>? = null
    private var retried = false
    private lateinit var msg: String
    private lateinit var root: LinearLayout
    private lateinit var status: TextView

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun tv(t: String, sp: Float, bold: Boolean = false, col: Int = Color.WHITE) =
        TextView(this).apply {
            text = t; textSize = sp; setTextColor(col); gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
        }

    private fun btn(t: String, col: Int, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); isAllCaps = false; textSize = 16f
        background = GradientDrawable().apply { setColor(col); cornerRadius = dp(14).toFloat() }
        setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ringing = intent.hasExtra("msg")
        msg = intent.getStringExtra("msg") ?: "Boliye, kya chahiye? Break chahiye to bolo kitne minute ka."
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        setContentView(root)
        tts = TextToSpeech(this, this)
        if (intent.getBooleanExtra("answer", false)) answer()
        else if (ringing) showRinging() else showCall()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        if (i.getBooleanExtra("answer", false)) answer()
    }

    private fun showRinging() {
        root.removeAllViews()
        root.addView(tv("Focus Coach", 32f, true))
        root.addView(tv("Incoming call...", 16f, false, 0xFF94A3B8.toInt()))
        root.addView(tv(msg, 20f))
        root.addView(View(this), LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(SlideView(this, { answer() }, { Ringer.stop(); cancelRing(); finish() }),
            LinearLayout.LayoutParams(-1, dp(72)))
    }

    private fun showCall() {
        root.removeAllViews()
        root.addView(tv("Focus Coach", 28f, true))
        status = tv(msg, 18f)
        root.addView(status)
        val lp = { LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) } }
        val r1 = LinearLayout(this)
        for (m in listOf(5, 10, 15)) r1.addView(btn("$m min break", 0xFF2563EB.toInt()) { doBreak(m) }, lp())
        root.addView(r1, LinearLayout.LayoutParams(-1, -2))
        val r2 = LinearLayout(this)
        r2.addView(btn("30 min break", 0xFF2563EB.toInt()) { doBreak(30) }, lp())
        r2.addView(btn("Kaam pe hoon", 0xFF16A34A.toInt()) { say("Theek hai. Lage raho.", "end") }, lp())
        root.addView(r2, LinearLayout.LayoutParams(-1, -2))
        val r3 = LinearLayout(this)
        r3.addView(btn("Bolo (mic)", 0xFF7C3AED.toInt()) { retried = false; listen() }, lp())
        r3.addView(btn("Call katt karo", 0xFFDC2626.toInt()) { finish() }, lp())
        root.addView(r3, LinearLayout.LayoutParams(-1, -2))
    }

    override fun onInit(s: Int) {
        val t = tts ?: return
        val r = t.setLanguage(Locale("hi", "IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.language = Locale.getDefault()
        t.setSpeechRate(0.92f)
        t.setPitch(1.0f)
        t.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        try {
            t.voices?.filter { it.locale.language == "hi" }?.maxByOrNull { it.quality }?.let { t.voice = it }
        } catch (_: Exception) {
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { runOnUiThread { done(id) } }
            override fun onDone(id: String?) { runOnUiThread { done(id) } }
        })
        ttsReady = true
        pending?.let { say(it.first, it.second); pending = null }
    }

    private fun done(id: String?) {
        if (id == "q") listen() else if (id == "end") finish()
    }

    private fun say(text: String, id: String) {
        if (::status.isInitialized) status.text = text
        if (!ttsReady) { pending = Pair(text, id); return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun cancelRing() {
        getSystemService(NotificationManager::class.java).cancel(FocusService.CALL_ID)
    }

    private fun answer() {
        Ringer.stop()
        cancelRing()
        showCall()
        say(msg, "q")
    }

    private fun doBreak(m: Int) {
        if (!Store.running(this)) { say("Focus session chalu nahi hai.", "end"); return }
        startService(Intent(this, FocusService::class.java).setAction(FocusService.ACTION_BREAK).putExtra("min", m))
        say("$m minute ka break. Khatam hone par main bulaunga.", "end")
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Mic permission do"
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Is tablet par speech recognition nahi hai. Neeche ke button dabao."
            return
        }
        startRec(if (retried) null else "hi-IN")
    }

    private fun startRec(lang: String?) {
        status.text = "Sun raha hoon... boliye"
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                if (!retried) { retried = true; startRec(null); return }
                status.text = "Mic error $e. Neeche ke button dabao ya 'Bolo (mic)' dobara dabao."
            }
            override fun onResults(res: Bundle?) {
                val text = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                handle(text)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (lang != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        r.startListening(i)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == 7 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
    }

    private fun handle(text: String) {
        val t = text.lowercase()
        status.text = "Tumne kaha: $text"
        val words = listOf("break", "ब्रेक", "gap", "गैप", "rest", "रेस्ट", "आराम", "aaram", "minute", "मिनट")
        if (words.any { t.contains(it) }) doBreak(parseMin(t))
        else {
            val left = maxOf(0L, (Store.goalSec(this) - Store.focusSec(this)) / 60)
            say("Samjha. Ab kaam pe lago. Goal me ${left / 60} ghante ${left % 60} minute baaki hain.", "end")
        }
    }

    private fun parseMin(t: String): Int {
        Regex("\\d+").find(t)?.let { return it.value.toInt().coerceIn(1, 240) }
        val words = mapOf(
            "ek" to 1, "एक" to 1, "do" to 2, "दो" to 2, "teen" to 3, "तीन" to 3,
            "char" to 4, "चार" to 4, "paanch" to 5, "panch" to 5, "पांच" to 5, "पाँच" to 5, "five" to 5,
            "das" to 10, "दस" to 10, "ten" to 10, "pandrah" to 15, "पंद्रह" to 15,
            "bees" to 20, "बीस" to 20, "tees" to 30, "तीस" to 30
        )
        for (tok in t.split(Regex("\\s+"))) words[tok]?.let { return it }
        return 5
    }

    override fun onDestroy() {
        Ringer.stop()
        sr?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
