package com.app.nosatmosphereeffect.service

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.animation.LinearInterpolator
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.PlaylistRotationController
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderer
import android.os.PowerManager

class AtmosphereService : GLWallpaperService() {

    private val activeEngines = mutableSetOf<AtmosphereEngine>()

    override fun onCreateEngine(): Engine {
        val engine = AtmosphereEngine()
        activeEngines.add(engine)
        return engine
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return AtmosphereRenderer(applicationContext)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val uiMode = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

        // 3. Only act if explicitly YES or NO. This ignores "UNDEFINED" states during screen rotations that cause false positives!
        if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ||
            uiMode == android.content.res.Configuration.UI_MODE_NIGHT_NO) {

            val isNightMode = (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)

            // Notify all active engines
            activeEngines.forEach { engine ->
                engine.handleThemeChange(isNightMode)
            }
        }
    }

    inner class AtmosphereEngine : GLEngine() {
        private var pollInterval: Long = if (isSamsungDevice()) 30000L else 50L
        private var lockDelay: Long = if (isSamsungDevice()) 0L else 800L
        private var animDuration: Long = 2500L
        private var myRenderer: AtmosphereRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true
        private val handler = Handler(Looper.getMainLooper())

        private val resetRunnable = Runnable {
            prepareForNextUnlock()
        }

//        private val rotationRunnable = Runnable {
//            rotateWallpaper()
//        }

        // Called instantly when the OS configuration changes
        fun handleThemeChange(isNightMode: Boolean) {
            rotateWallpaper(isThemeChange = true, currentNightMode = isNightMode)
        }

        private fun rotateWallpaper(isThemeChange: Boolean = false, currentNightMode: Boolean = false) {
            PlaylistRotationController.rotateAsync(
                context = applicationContext,
                isThemeChange = isThemeChange,
                currentNightMode = currentNightMode,
                queueTransition = { bitmap -> myRenderer?.queuePlaylistTransition(bitmap) },
                requestRender = { requestRender() },
                notifyColorsChanged = { notifySystemColorsChanged() }
            )
        }

        private val unlockChecker = object : Runnable {
            override fun run() {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    // BOOM! Device is unlocked. Trigger animation immediately.
                    isLocked = false
                    playUnlockAnimation()
                    // Stop checking
                    handler.removeCallbacks(this)
                } else {
                    // Still locked, check again in 50ms
                    handler.postDelayed(this, pollInterval)
                }
            }
        }

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        // Screen turned on. Start watching for unlock immediately.
                        isLocked = true
                        handler.removeCallbacks(unlockChecker)
//                        handler.removeCallbacks(rotationRunnable)
                        handler.post(unlockChecker)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        // Screen off. Stop watching (save battery) and reset state.
                        handler.removeCallbacks(unlockChecker)
                        isLocked = true
                        handler.postDelayed(resetRunnable, lockDelay)
                        rotateWallpaper()
//                        handler.postDelayed(rotationRunnable, lockDelay)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Backup: Keep this as a failsafe in case polling misses (rare)
                        handler.removeCallbacks(resetRunnable)
//                        handler.removeCallbacks(rotationRunnable)
                        if (isLocked) {
                            isLocked = false
                            playUnlockAnimation()
                            handler.removeCallbacks(unlockChecker)
                        }
                    }
                    "com.app.nosatmosphereeffect.RELOAD_WALLPAPER" -> {
                        myRenderer?.reloadTexture()
                        requestRender()
                        notifySystemColorsChanged()
                    }
                    "com.app.nosatmosphereeffect.UPDATE_CONFIG" -> {
                        updateRendererConfig()
                        requestRender()
                        notifySystemColorsChanged()
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            isLocked = keyguardManager.isKeyguardLocked

            val r = getRenderer()
            if (r is AtmosphereRenderer) {
                myRenderer = r
                updateRendererConfig()
                setRenderer(myRenderer!!)
            }

            val currentUiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentUiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ||
                currentUiMode == android.content.res.Configuration.UI_MODE_NIGHT_NO) {
                handleThemeChange(currentUiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction("com.app.nosatmosphereeffect.RELOAD_WALLPAPER")
                addAction("com.app.nosatmosphereeffect.UPDATE_CONFIG")
            }

            registerReceiver(systemEventReceiver, filter, RECEIVER_EXPORTED)
        }

        override fun onDestroy() {
            super.onDestroy()
            activeEngines.remove(this)
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (e: Exception) { }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isInteractive) {
                    myRenderer?.blurStrength = 0.0f
                }
            }
            super.onVisibilityChanged(visible)
            if (visible) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    isLocked = false
                }
                if (isLocked) {
                    myRenderer?.blurStrength = 0.0f
                    requestRender()
                } else {
                    snapToHomeState()
                }
            }
        }

        private fun playUnlockAnimation() {
            val targetRenderer = myRenderer ?: return

            blurAnimator?.cancel()
            targetRenderer.blurStrength = 0.0f
            requestRender()

            blurAnimator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                duration = animDuration
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    targetRenderer.blurStrength = value
                    requestRender()
                }
            }
            blurAnimator?.start()
        }

        private fun snapToHomeState() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 1.0f
            requestRender()
        }

        private fun prepareForNextUnlock() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 0.0f
            requestRender()
        }

        private fun isSamsungDevice(): Boolean {
            return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val dim = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.dimLevel = dim

            // New Advanced Settings (Default to -1 to detect "not set")
            val savedPoll = prefs.getLong("poll_interval", -1L)
            val savedDelay = prefs.getLong("lock_delay", -1L)
            val savedDuration = prefs.getLong("anim_duration", -1L)
            val noise = prefs.getBoolean("enable_noise", false)
            val scale = prefs.getFloat("noise_scale", 2000.0f)
            val strength = prefs.getFloat("noise_strength", 0.06f)

            myRenderer?.blobSaturation = prefs.getFloat("blob_saturation", 1.0f)
            myRenderer?.blobContrast = prefs.getFloat("blob_contrast", 1.0f)

            myRenderer?.enableNoise = noise
            myRenderer?.noiseScale = scale
            myRenderer?.noiseStrength = strength
            pollInterval = if (savedPoll != -1L) savedPoll else if (isSamsungDevice()) 30000L else 50L
            lockDelay = if (savedDelay != -1L) savedDelay else if (isSamsungDevice()) 0L else 800L
            animDuration = if (savedDuration != -1L) savedDuration else 2500L
        }
    }
}
