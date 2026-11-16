package com.focuscoach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs

class FocusService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "stop"
        const val ACTION_BREAK = "break"
        const val ACTION_DECLINE = "decline"
        const val SVC_CH = "svc"
        const val CALL_CH = "call"
        const val CALL_ID = 1001
    }

    private val h = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    @Volatile private var cat = Store.AWAY
    @Volatile private var lastResult = 0L
    private var lastFrame = 0L
    private var distractedSince = 0L
    private var lastCall = 0L
    private var secInMin = 0
    private val minuteCount = IntArray(4)
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            h.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> finishSession()
            ACTION_DECLINE -> {
                Ringer.stop()
                getSystemService(NotificationManager::class.java).cancel(CALL_ID)
            }
            ACTION_BREAK -> {
                val m = intent.getIntExtra("min", 5)
                Store.setBreakUntil(this, System.currentTimeMillis() + m * 60000L)
                distractedSince = 0
            }
            else -> if (Store.running(this)) start() else stopSelf()
        }
        return START_STICKY
    }

    private fun start() {
        if (started) return
        started = true
        createChannels()
        val n = NotificationCompat.Builder(this, SVC_CH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Focus Coach chal raha hai")
            .setContentText("Camera se focus track ho raha hai")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        lastResult = System.currentTimeMillis()
        bindCamera()
        h.postDelayed(tick, 1000)
    }

    private fun bindCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val provider = f.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(exec) { proxy ->
                val now = System.currentTimeMillis()
                val img = proxy.image
                if (img == null || now - lastFrame < 1000 || Store.breakUntil(this) > 0) {
                    proxy.close()
                    return@setAnalyzer
                }
                lastFrame = now
                detector.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { faces -> classify(faces) }
                    .addOnCompleteListener { proxy.close() }
            }
            val sel = if (Store.front(this)) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, sel, analysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Heuristic: face dikhe + aankhen khuli + sidha = Screen; neeche jhuka = Notes;
    // side mudi ya aankhen band = Time-pass; face nahi = Away.
    private var cand = Store.AWAY
    private var streak = 0

    // Neeche dekhna = Notes (pehle check, kyunki neeche dekhte waqt aankhen band jaisi dikhti hain).
    // 3 baar lagatar same aaye tabhi badalta hai, taaki flicker na ho.
    private fun classify(faces: List<Face>) {
        lastResult = System.currentTimeMillis()
        val f = faces.maxByOrNull { it.boundingBox.width() }
        val raw = if (f == null) Store.AWAY else {
            val eyes = ((f.leftEyeOpenProbability ?: 1f) + (f.rightEyeOpenProbability ?: 1f)) / 2
            val pitch = f.headEulerAngleX
            val yaw = abs(f.headEulerAngleY)
            when {
                pitch < -10f && yaw < 35f -> Store.NOTES
                yaw > 30f -> Store.DISTRACTED
                eyes < 0.25f -> Store.DISTRACTED
                else -> Store.SCREEN
            }
        }
        if (raw == cand) streak++ else { cand = raw; streak = 1 }
        if (streak >= 3) cat = cand
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        val bu = Store.breakUntil(this)
        if (bu > 0) {
            if (now >= bu) {
                Store.setBreakUntil(this, 0)
                lastResult = now
                distractedSince = 0
                ring("Break khatam ho gaya. Wapas kaam pe aao.")
            }
            return
        }
        val c = if (now - lastResult > 8000) Store.AWAY else cat
        Store.setCur(this, c)
        Store.add(this, c, 1)
        minuteCount[c]++
        if (++secInMin >= 60) {
            Store.addTimeline(this, minuteCount.indices.maxByOrNull { minuteCount[it] } ?: 0)
            minuteCount.fill(0)
            secInMin = 0
        }
        if (c >= Store.DISTRACTED) {
            if (distractedSince == 0L) distractedSince = now
            if (now - distractedSince >= Store.limitMs(this) && now - lastCall > 300000) {
                lastCall = now
                distractedSince = 0
                ring("${Store.limitMs(this) / 60000} minute se tum distract ho. Kya kar rahe ho? Batao.")
            }
        } else distractedSince = 0

        if (Store.focusSec(this) >= Store.goalSec(this)) {
            ring("Shabash! Aaj ka goal poora ho gaya.")
            finishSession()
        }
    }

    private fun ring(msg: String) {
        val i = Intent(this, CallActivity::class.java)
            .putExtra("msg", msg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CALL_CH)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Focus Coach calling")
            .setContentText(msg)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setOngoing(true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    androidx.core.app.Person.Builder().setName("Focus Coach").setImportant(true).build(),
                    PendingIntent.getService(
                        this, 4, Intent(this, FocusService::class.java).setAction(ACTION_DECLINE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ),
                    PendingIntent.getActivity(
                        this, 3,
                        Intent(this, CallActivity::class.java).putExtra("msg", msg).putExtra("answer", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            )
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_ID, n)
        Ringer.start(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SVC_CH, "Focus tracking", NotificationManager.IMPORTANCE_LOW)
        )
        val call = NotificationChannel(CALL_CH, "Coach call", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(call)
    }

    private fun finishSession() {
        Store.setRunning(this, false)
        h.removeCallbacks(tick)
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        h.removeCallbacks(tick)
        exec.shutdown()
        super.onDestroy()
    }
}

// Asli ringtone: alarm stream par bajta hai, isliye silent mode me bhi sunai deta hai.
object Ringer {
    private var r: android.media.Ringtone? = null
    private var vib: android.os.Vibrator? = null
    private val h = Handler(Looper.getMainLooper())

    fun start(c: android.content.Context) {
        stop()
        try {
            val ctx = c.applicationContext
            val rt = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            rt.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            rt.isLooping = true
            rt.play()
            r = rt
            vib = ctx.getSystemService(android.os.Vibrator::class.java)
            vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
            h.postDelayed({ stop() }, 45000)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        h.removeCallbacksAndMessages(null)
        try { r?.stop() } catch (_: Exception) {}
        try { vib?.cancel() } catch (_: Exception) {}
        r = null
    }
}
