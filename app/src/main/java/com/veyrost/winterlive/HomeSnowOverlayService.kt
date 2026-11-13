package com.veyrost.winterlive

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.random.Random

/**
 * Transparent, non-interactive snow layer. It is shown only while a recognized
 * launcher is foreground; it never consumes taps intended for app icons.
 */
class HomeSnowOverlayService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private var snowView: SnowOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundCheck = 0L
    private var lastForegroundPackage: String? = null
    private val launcherPackages = setOf(
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.miui.home",
        "com.oppo.launcher",
        "com OnePlus.launcher"
    )

    private val monitor = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastForegroundCheck >= 700L) {
                lastForegroundCheck = now
                lastForegroundPackage = foregroundPackage()
                val onHome = lastForegroundPackage in launcherPackages
                if (onHome) attachOverlay() else detachOverlay()
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !hasUsageAccess()) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_STICKY
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun foregroundPackage(): String? {
        val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usage.queryEvents(end - 5000L, end)
        val event = UsageEvents.Event()
        var latest: String? = null
        var latestTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTime) {
                latestTime = event.timeStamp
                latest = event.packageName
            }
        }
        return latest
    }

    private fun attachOverlay() {
        if (snowView != null) return
        val view = SnowOverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager.addView(view, params)
            snowView = view
        } catch (_: RuntimeException) {
            snowView = null
        }
    }

    private fun detachOverlay() {
        val view = snowView ?: return
        try { windowManager.removeView(view) } catch (_: RuntimeException) { }
        snowView = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            snowView?.setTilt((-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        sensorManager.unregisterListener(this)
        detachOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private class SnowOverlayView(context: Context) : View(context) {
    private data class Flake(var x: Float, var y: Float, var speed: Float, var radius: Float)
    private val flakes = Array(110) {
        Flake(Random.nextFloat(), Random.nextFloat(), 0.12f + Random.nextFloat() * 0.35f, 0.8f + Random.nextFloat() * 2.2f)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tilt = 0f
    private var lastFrame = System.nanoTime()
    private var accumulation = 0f

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun setTilt(value: Float) { tilt = tilt * 0.78f + value * 0.22f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
        lastFrame = now
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        // Home-screen snowfall. The view is NOT touchable, so launcher icons remain usable.
        paint.color = 0xEAF8FFFF.toInt()
        paint.style = Paint.Style.FILL
        for (f in flakes) {
            f.y += f.speed * dt * h * 0.72f
            f.x += tilt * dt * 0.11f
            if (f.y > h) { f.y = -Random.nextFloat() * h * 0.15f; f.x = Random.nextFloat() }
            if (f.x < 0f) f.x += 1f
            if (f.x > 1f) f.x -= 1f
            canvas.drawCircle(f.x * w, f.y, f.radius, paint)
        }

        // Calibrated snow ledges over Samsung's common 5-column home-grid and dock.
        // The user can fine-tune grid alignment in a later calibration screen.
        paint.color = 0xDDF7FCFF.toInt()
        val rows = floatArrayOf(0.205f, 0.345f, 0.485f, 0.625f, 0.865f)
        val columns = 5
        val cell = w / columns
        for (row in rows) {
            for (col in 0 until columns) {
                val center = cell * (col + 0.5f) + tilt * 7f
                val y = h * row
                val widthCap = cell * 0.29f
                val depth = (2.0f + accumulation * 0.8f).coerceAtMost(12f)
                val path = Path().apply {
                    moveTo(center - widthCap, y)
                    cubicTo(center - widthCap * 0.35f, y - depth, center + widthCap * 0.45f, y - depth, center + widthCap, y)
                    quadTo(center + widthCap * 0.78f, y + depth * 0.7f, center + widthCap * 0.45f, y + depth * 0.55f)
                    quadTo(center, y + depth * 0.95f, center - widthCap * 0.45f, y + depth * 0.55f)
                    quadTo(center - widthCap * 0.8f, y + depth * 0.55f, center - widthCap, y)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
        accumulation = (accumulation + dt * 0.22f).coerceAtMost(7f)
        postInvalidateOnAnimation()
    }
}
