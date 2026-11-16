package com.focuscoach

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class CallActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var pending: Pair<String, String>? = null
    private var msg = ""
    private lateinit var status: TextView
    private lateinit var answerBtn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        msg = intent.getStringExtra("msg")
            ?: "Boliye, kya chahiye? Break chahiye to bolo kitne minute ka."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#102027"))
            setPadding(48, 48, 48, 48)
        }
        fun tv(t: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = sp; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 24, 0, 24)
        }
        root.addView(tv("Focus Coach", 30f, true))
        root.addView(tv("incoming call...", 16f))
        root.addView(tv(msg, 20f))
        status = tv("", 16f)
        root.addView(status)

        answerBtn = Button(this).apply {
            text = "Answer"; setBackgroundColor(Color.parseColor("#2E7D32")); setTextColor(Color.WHITE)
            setOnClickListener { answer() }
        }
        val decline = Button(this).apply {
            text = "Decline"; setBackgroundColor(Color.parseColor("#C62828")); setTextColor(Color.WHITE)
            setOnClickListener { cancelRing(); finish() }
        }
        root.addView(answerBtn)
        root.addView(decline)
        setContentView(root)

        tts = TextToSpeech(this, this)
    }

    override fun onInit(s: Int) {
        val t = tts ?: return
        val r = t.setLanguage(Locale("hi", "IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.language = Locale.getDefault()
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
        if (id == "q") listen() else finish()
    }

    private fun say(text: String, id: String) {
        status.text = text
        if (!ttsReady) { pending = Pair(text, id); return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun cancelRing() {
        getSystemService(NotificationManager::class.java).cancel(FocusService.CALL_ID)
    }

    private fun answer() {
        cancelRing()
        answerBtn.visibility = android.view.View.GONE
        say(msg, "q")
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            say("Mic nahi chal raha. Wapas kaam pe lago.", "end"); return
        }
        status.text = "Sun raha hoon... boliye"
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onError(e: Int) { say("Awaaz nahi aayi. Wapas kaam pe lago.", "end") }
                override fun onResults(res: Bundle?) {
                    val text = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    handle(text)
                }
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            )
        }
    }

    private fun handle(text: String) {
        val t = text.lowercase()
        status.text = "Tumne kaha: $text"
        if (!Store.running(this)) { say("Session chalu nahi hai.", "end"); return }
        val breakWords = listOf("break", "ब्रेक", "gap", "गैप", "rest", "रेस्ट", "आराम", "aaram")
        if (breakWords.any { t.contains(it) }) {
            val m = parseMin(t)
            startService(
                Intent(this, FocusService::class.java)
                    .setAction(FocusService.ACTION_BREAK).putExtra("min", m)
            )
            say("Theek hai, $m minute ka break. Khatam hone par main call karunga.", "end")
        } else {
            val left = maxOf(0L, (Store.goalSec(this) - Store.focusSec(this)) / 60)
            say(
                "Samjha. Ab doosri cheez band karo aur kaam pe lago. " +
                    "Goal me ${left / 60} ghante ${left % 60} minute baaki hain.", "end"
            )
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
        sr?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
