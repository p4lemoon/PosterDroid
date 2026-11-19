package com.posterdroid.wallpaper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.posterdroid.caml.model.CamlPackage
import com.posterdroid.caml.parser.TendiesExtractor
import com.posterdroid.core.PosterConstants
import com.posterdroid.wallpaper.render.CamlRenderer
import com.posterdroid.wallpaper.settings.PosterPreferences
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import kotlin.math.abs

class PosterWallpaperService : WallpaperService() {

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateEngine(): Engine {
        return PosterEngine()
    }

    inner class PosterEngine : Engine() {
        private var currentProgress: Float = 0f
        private var currentState: String = "LOCKED"
        private var isVisibleState: Boolean = true
        private var hasSurface: Boolean = false

        private var camlPackage: CamlPackage? = null
        private var renderer: CamlRenderer? = null
        private var unlockAnimator: ValueAnimator? = null
        private var currentAnimTarget: Float? = null

        private var isAnimationRunning = false
        private var isEngineDestroyed = false
        private val drawLock = Any()
        private var startNanos = 0L
        private var currentElapsedSeconds = 0f
        private var lastLogTimeMs = 0L
        private var lastFrameTimeNanos = 0L
        private var lastDrawNanos = 0L

        private var sensorManager: SensorManager? = null
        private var rotationSensor: Sensor? = null
        private var accelSensor: Sensor? = null
        private var isSensorRegistered = false

        private var rawTiltX = 0f
        private var rawTiltY = 0f
        private var currentTiltX = 0f
        private var currentTiltY = 0f

        private val rotationMatrix = FloatArray(9)
        private val orientationAngles = FloatArray(3)
        private var baselinePitch = Float.NaN

        private val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isVisibleState || !hasSurface) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val pitch = orientationAngles[1]
                        val roll = orientationAngles[2]

                        if (baselinePitch.isNaN()) {
                            baselinePitch = pitch
                        } else {
                            baselinePitch = baselinePitch * 0.995f + pitch * 0.005f
                        }

                        val deltaPitch = pitch - baselinePitch
                        val deltaRoll = roll

                        rawTiltX = (deltaRoll / 0.52f).coerceIn(-1f, 1f)
                        rawTiltY = (deltaPitch / 0.52f).coerceIn(-1f, 1f)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        rawTiltX = (ax / 5f).coerceIn(-1f, 1f)
                        rawTiltY = ((ay - 5f) / 5f).coerceIn(-1f, 1f)
                    }
                }

                if (!isAnimationRunning && hasSurface && isVisibleState) {
                    if (abs(rawTiltX - currentTiltX) > 0.005f || abs(rawTiltY - currentTiltY) > 0.005f) {
                        startAnimationLoop()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        private fun registerSensors() {
            if (isSensorRegistered) return
            val sm = sensorManager ?: return
            val sensor = rotationSensor ?: accelSensor ?: return
            sm.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
            isSensorRegistered = true
            baselinePitch = Float.NaN
        }

        private fun unregisterSensors() {
            if (!isSensorRegistered) return
            sensorManager?.unregisterListener(sensorListener)
            isSensorRegistered = false
        }

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isAnimationRunning || !hasSurface) {
                    isAnimationRunning = false
                    return
                }

                if (targetFps in 1..143) {
                    val minIntervalNanos = (1_000_000_000L / targetFps) - 2_000_000L
                    if (lastFrameTimeNanos > 0L && frameTimeNanos - lastFrameTimeNanos < minIntervalNanos) {
                        if (isAnimationRunning && hasSurface) {
                            Choreographer.getInstance().postFrameCallback(this)
                        }
                        return
                    }
                    lastFrameTimeNanos = frameTimeNanos
                }

                if (startNanos == 0L) {
                    startNanos = frameTimeNanos
                }
                currentElapsedSeconds = (frameTimeNanos - startNanos) / 1_000_000_000f

                currentTiltX = currentTiltX * 0.82f + rawTiltX * 0.18f
                currentTiltY = currentTiltY * 0.82f + rawTiltY * 0.18f

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastLogTimeMs > 5000L) {
                    lastLogTimeMs = nowMs
                    Log.d(PosterConstants.TAG_WALLPAPER, "doFrame ticking: elapsed=${"%.2f".format(currentElapsedSeconds)}s, progress=${"%.2f".format(currentProgress)}, fps=$targetFps, tilt=(${"%.2f".format(currentTiltX)}, ${"%.2f".format(currentTiltY)})")
                }

                drawFrame(currentElapsedSeconds, force = true)

                if (isAnimationRunning && hasSurface) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        private fun updateAnimationLoopState() {
            if (!hasSurface || !isVisibleState) {
                stopAnimationLoop()
                return
            }

            val hasContinuous = camlPackage?.hasContinuousAnimations == true
            val isInteracting = (currentState == "DRAGGING" ||
                    currentState == "UNLOCKING" ||
                    currentState == "CANCELLED" ||
                    currentAnimTarget != null ||
                    unlockAnimator?.isRunning == true)
            val isTilting = abs(rawTiltX - currentTiltX) > 0.002f || abs(rawTiltY - currentTiltY) > 0.002f

            if (hasContinuous || isInteracting || isTilting) {
                startAnimationLoop()
            } else {
                stopAnimationLoop()
                drawFrame(currentElapsedSeconds, force = true)
            }
        }

        private fun startAnimationLoop() {
            if (!hasSurface || !isVisibleState) {
                return
            }
            if (!isAnimationRunning) {
                isAnimationRunning = true
                Log.d(PosterConstants.TAG_WALLPAPER, "startAnimationLoop: STARTED Choreographer loop")
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }

        private fun stopAnimationLoop() {
            if (isAnimationRunning) {
                isAnimationRunning = false
                Log.d(PosterConstants.TAG_WALLPAPER, "stopAnimationLoop: STOPPED Choreographer loop")
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        }

        private val preferences by lazy { PosterPreferences(this@PosterWallpaperService) }
        private var isEffectEnabled = true
        private var isSmoothingEnabled = true
        private var sensitivity = 1.0f
        private var targetFps = 60

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PosterConstants.PREF_ACTIVE_TENDIES_PATH -> loadActiveTendies()
                PosterConstants.PREF_EFFECT_ENABLED -> isEffectEnabled = preferences.isEffectEnabled
                PosterConstants.PREF_SMOOTHING_ENABLED -> isSmoothingEnabled = preferences.isSmoothingEnabled
                PosterConstants.PREF_SENSITIVITY -> sensitivity = preferences.sensitivity
                PosterConstants.PREF_TARGET_FPS -> {
                    targetFps = preferences.targetFps
                    lastFrameTimeNanos = 0L
                    lastDrawNanos = 0L
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.d(PosterConstants.TAG_WALLPAPER, "PosterEngine created")
            isEffectEnabled = preferences.isEffectEnabled
            isSmoothingEnabled = preferences.isSmoothingEnabled
            sensitivity = preferences.sensitivity
            targetFps = preferences.targetFps
            preferences.registerListener(prefListener)

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            if (rotationSensor == null) {
                accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            }
            if (isVisibleState) {
                registerSensors()
            }

            try {
                val filter = android.content.IntentFilter("com.posterdroid.DEBUG_RENDER")
                androidx.core.content.ContextCompat.registerReceiver(
                    this@PosterWallpaperService,
                    debugReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                )
                isDebugReceiverRegistered = true
            } catch (t: Throwable) {
                Log.w(PosterConstants.TAG_WALLPAPER, "Could not register debug receiver: ${t.message}")
            }

            loadActiveTendies()
        }

        private var isDebugReceiverRegistered = false
        private val debugReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val rend = renderer

                if (intent.hasExtra("fixed_progress")) {
                    val p = intent.getFloatExtra("fixed_progress", -1f)
                    rend?.debugFixedProgress = if (p >= 0f) p else null
                    if (p >= 0f) currentProgress = p
                }
                if (intent.hasExtra("only_layer_index")) {
                    val idx = intent.getIntExtra("only_layer_index", -1)
                    rend?.debugOnlyLayerIndex = if (idx >= 0) idx else null
                }
                if (intent.hasExtra("only_layer_name")) {
                    val name = intent.getStringExtra("only_layer_name")
                    rend?.debugOnlyLayerName = if (name.isNullOrEmpty()) null else name
                }
                if (intent.hasExtra("highlight_bounds")) {
                    rend?.debugHighlightBounds = intent.getBooleanExtra("highlight_bounds", false)
                }

                val capturePath = intent.getStringExtra("capture_png")
                if (!capturePath.isNullOrEmpty() && rend != null) {
                    val w = intent.getIntExtra("capture_width", 238)
                    val h = intent.getIntExtra("capture_height", 526)
                    val p = intent.getFloatExtra("progress", rend.debugFixedProgress ?: currentProgress)
                    try {
                        val bmp = rend.renderToBitmap(w, h, progress = p, timeSeconds = currentElapsedSeconds)
                        java.io.FileOutputStream(File(capturePath)).use { out ->
                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Log.i(PosterConstants.TAG_WALLPAPER, "DEBUG_RENDER: saved frame to $capturePath (${w}x${h}, progress=$p)")
                    } catch (t: Throwable) {
                        Log.e(PosterConstants.TAG_WALLPAPER, "DEBUG_RENDER: failed to save frame: ${t.message}", t)
                    }
                }

                drawFrame(currentElapsedSeconds, force = true)
            }
        }

        private fun loadActiveTendies() {
            bgExecutor.execute {
                var loadedPkg: CamlPackage? = null
                var loadedName = "default.tendies"

                val customPath = preferences.activeTendiesPath
                val customFile = customPath?.let { File(it) }

                if (customFile != null && customFile.exists() && customFile.canRead()) {
                    try {
                        FileInputStream(customFile).use { fis ->
                            val extractor = TendiesExtractor()
                            loadedPkg = extractor.extract(fis, customFile.name)
                            loadedName = customFile.name
                        }
                    } catch (t: Throwable) {
                        Log.e(PosterConstants.TAG_WALLPAPER, "Failed to parse custom file $customPath: ${t.message}", t)
                    }
                }

                if (loadedPkg == null) {
                    try {
                        assets.open("default.tendies").use { inputStream ->
                            val extractor = TendiesExtractor()
                            loadedPkg = extractor.extract(inputStream, "default.tendies")
                            loadedName = "default.tendies"
                        }
                    } catch (t: Throwable) {
                        Log.e(PosterConstants.TAG_WALLPAPER, "Fatal: failed to load default.tendies: ${t.message}", t)
                    }
                }

                val pkg = loadedPkg ?: return@execute
                val newRenderer = CamlRenderer(pkg)

                mainHandler.post {
                    if (isEngineDestroyed) {
                        newRenderer.release()
                        return@post
                    }
                    val oldRenderer = renderer
                    camlPackage = pkg
                    renderer = newRenderer
                    oldRenderer?.release()

                    Log.i(PosterConstants.TAG_WALLPAPER, "Loaded active tendies ($loadedName): ${pkg.totalLayers} layers, continuous=${pkg.hasContinuousAnimations}")
                    updateAnimationLoopState()
                    drawFrame(currentElapsedSeconds, force = true)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(PosterConstants.TAG_WALLPAPER, "onVisibilityChanged: visible=$visible, engine.isVisible=$isVisible")
            isVisibleState = visible
            if (visible) {
                registerSensors()
            } else {
                unregisterSensors()
            }
            updateAnimationLoopState()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            hasSurface = true
            Log.d(PosterConstants.TAG_WALLPAPER, "onSurfaceCreated: holder=$holder")
            updateAnimationLoopState()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            hasSurface = true
            Log.d(PosterConstants.TAG_WALLPAPER, "onSurfaceChanged: ${width}x${height}")
            updateAnimationLoopState()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            hasSurface = false
            Log.d(PosterConstants.TAG_WALLPAPER, "onSurfaceDestroyed: holder=$holder")
            stopAnimationLoop()
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (action == PosterConstants.COMMAND_PROGRESS) {
                val newProgress = extras?.getFloat(PosterConstants.EXTRA_PROGRESS, 0f) ?: 0f
                val newState = extras?.getString(PosterConstants.EXTRA_STATE, "UNKNOWN") ?: "UNKNOWN"
                Log.d(PosterConstants.TAG_WALLPAPER, "onCommand: progress=$newProgress, state=$newState")
                handleProgressEvent(newProgress, newState)
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun handleProgressEvent(targetProgress: Float, state: String) {
            if (!isEffectEnabled) {
                // всегда будет выполняться, потому что захардкожено лол
                currentProgress = 0f
                if (!isAnimationRunning) {
                    drawFrame(currentElapsedSeconds, force = true)
                }
                return
            }

            val effectiveProgress = (targetProgress * sensitivity).coerceIn(0f, 1f)

            when (state) {
                "UNLOCKING" -> {
                    currentState = "UNLOCKING"
                    if (unlockAnimator?.isRunning == true && currentAnimTarget == 1.0f) {
                        return
                    }
                    if (currentProgress < 0.999f) {
                        startTransitionAnimation(currentProgress, 1.0f, 350L)
                    } else {
                        unlockAnimator?.cancel()
                        currentAnimTarget = null
                        currentProgress = 1.0f
                        updateAnimationLoopState()
                        drawFrame(currentElapsedSeconds, force = true)
                    }
                }
                "UNLOCKED" -> {
                    currentState = "UNLOCKED"
                    if (unlockAnimator?.isRunning == true && currentAnimTarget == 1.0f) {
                        return
                    }
                    if (currentProgress < 0.999f) {
                        startTransitionAnimation(currentProgress, 1.0f, 350L)
                    } else {
                        unlockAnimator?.cancel()
                        currentAnimTarget = null
                        currentProgress = 1.0f
                        updateAnimationLoopState()
                        drawFrame(currentElapsedSeconds, force = true)
                    }
                }
                "CANCELLED" -> {
                    if (currentState == "UNLOCKING" || currentState == "UNLOCKED") {
                        return
                    }
                    currentState = "CANCELLED"
                    if (unlockAnimator?.isRunning == true && currentAnimTarget == 0.0f) {
                        return
                    }
                    if (currentProgress > 0.001f) {
                        startTransitionAnimation(currentProgress, 0.0f, 300L)
                    } else {
                        unlockAnimator?.cancel()
                        currentAnimTarget = null
                        currentProgress = 0f
                        updateAnimationLoopState()
                        drawFrame(currentElapsedSeconds, force = true)
                    }
                }
                "DRAGGING" -> {
                    if (currentState == "UNLOCKING" || currentState == "UNLOCKED") {
                        return
                    }
                    currentState = "DRAGGING"
                    currentAnimTarget = null
                    unlockAnimator?.cancel()
                    currentProgress = effectiveProgress
                    updateAnimationLoopState()
                    drawFrame(currentElapsedSeconds, force = false)
                }
                "LOCKED" -> {
                    currentState = "LOCKED"
                    if (unlockAnimator?.isRunning == true && currentAnimTarget == 0.0f) {
                        return
                    }
                    if (currentProgress > 0.001f) {
                        startTransitionAnimation(currentProgress, 0.0f, 350L)
                    } else {
                        unlockAnimator?.cancel()
                        currentAnimTarget = null
                        currentProgress = 0f
                        updateAnimationLoopState()
                        drawFrame(currentElapsedSeconds, force = true)
                    }
                }
                else -> {
                    if (currentState != "UNLOCKING" && currentState != "UNLOCKED") {
                        currentProgress = effectiveProgress
                        drawFrame(currentElapsedSeconds, force = false)
                    }
                }
            }
        }

        private fun startTransitionAnimation(from: Float, to: Float, durationMs: Long) {
            unlockAnimator?.cancel()
            currentAnimTarget = to
            if (!isSmoothingEnabled || durationMs <= 0L) {
                currentProgress = to
                currentAnimTarget = null
                if (to == 0.0f) currentState = "LOCKED" else if (to == 1.0f) currentState = "UNLOCKED"
                updateAnimationLoopState()
                drawFrame(currentElapsedSeconds, force = true)
                return
            }

            val transition = camlPackage?.background?.stateTransitions?.firstOrNull()
                ?: camlPackage?.floating?.stateTransitions?.firstOrNull()
                ?: camlPackage?.foreground?.stateTransitions?.firstOrNull()

            val springElem = transition?.elements?.firstOrNull { it.stiffness != null && it.damping != null }
            val spring = if (springElem != null) {
                com.posterdroid.caml.math.CASpringTiming(
                    mass = springElem.mass ?: 1.0f,
                    stiffness = springElem.stiffness!!,
                    damping = springElem.damping!!,
                    initialVelocity = springElem.velocity ?: 0.0f
                )
            } else {
                com.posterdroid.caml.math.CASpringTiming(
                    mass = 1.0f,
                    stiffness = 150.0f,
                    damping = 18.0f,
                    initialVelocity = 0.0f
                )
            }

            val settleMs = (spring.settlingDuration * 1000).toLong().coerceIn(280L, 550L)
            val interp = android.animation.TimeInterpolator { f -> spring.solveNormalized(f) }

            var isCancelled = false
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = settleMs
                interpolator = interp
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    currentProgress = from + (to - from) * fraction
                    drawFrame(currentElapsedSeconds, force = false)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        isCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (isCancelled) return
                        currentAnimTarget = null
                        currentProgress = to
                        if (to == 0.0f) {
                            currentState = "LOCKED"
                        } else if (to == 1.0f) {
                            currentState = "UNLOCKED"
                        }
                        updateAnimationLoopState()
                        drawFrame(currentElapsedSeconds, force = true)
                    }
                })
            }
            unlockAnimator = animator
            animator.start()
            updateAnimationLoopState()
        }

        private fun drawFrame(timeSeconds: Float = currentElapsedSeconds, force: Boolean = false) {
            val holder = surfaceHolder ?: return
            if (!hasSurface) return

            if (!force && targetFps in 1..143) {
                val now = System.nanoTime()
                val minIntervalNanos = (1_000_000_000L / targetFps) - 2_000_000L
                if (lastDrawNanos > 0L && now - lastDrawNanos < minIntervalNanos) {
                    return
                }
                lastDrawNanos = now
            } else {
                lastDrawNanos = System.nanoTime()
            }
            if (!hasSurface) return
            synchronized(drawLock) {
                var canvas: Canvas? = null
                try {
                    canvas = try {
                        holder.lockHardwareCanvas()
                    } catch (t: Throwable) {
                        holder.lockCanvas()
                    }
                    if (canvas != null) {
                        drawContent(canvas, timeSeconds)
                    }
                } catch (t: Throwable) {
                    Log.e(PosterConstants.TAG_WALLPAPER, "Error drawing frame: ${t.message}")
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (t: Throwable) {
                            // ловлю ловлю
                        }
                    }
                }
            }
        }

        private fun drawContent(canvas: Canvas, timeSeconds: Float) {
            val rend = renderer
            if (rend != null) {
                canvas.drawColor(Color.BLACK)
                rend.render(canvas, currentProgress, timeSeconds, tiltX = currentTiltX, tiltY = currentTiltY)
            } else {
                canvas.drawColor(Color.parseColor("#121212"))
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            isEngineDestroyed = true
            unregisterSensors()
            stopAnimationLoop()
            unlockAnimator?.cancel()
            renderer?.release()
            renderer = null
            camlPackage = null
            preferences.unregisterListener(prefListener)
            if (isDebugReceiverRegistered) {
                try {
                    unregisterReceiver(debugReceiver)
                } catch (t: Throwable) {}
                isDebugReceiverRegistered = false
            }
            Log.d(PosterConstants.TAG_WALLPAPER, "PosterEngine destroyed")
        }
    }
}
