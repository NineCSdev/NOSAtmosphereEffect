package com.app.nosatmosphereeffect.service

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.animation.LinearInterpolator
import com.app.nosatmosphereeffect.helper.GLWallpaperService
import com.app.nosatmosphereeffect.helper.CanvasSubjectSettings
import com.app.nosatmosphereeffect.helper.PlaylistRotationController
import com.app.nosatmosphereeffect.renderer.NeonRenderer
import android.os.PowerManager

class NeonService : GLWallpaperService() {

    private val activeEngines = mutableSetOf<NeonEngine>()

    override fun onCreateEngine(): Engine {
        val engine = NeonEngine()
        activeEngines.add(engine)
        return engine
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return NeonRenderer(applicationContext, isReverse = false)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val uiMode = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ||
            uiMode == android.content.res.Configuration.UI_MODE_NIGHT_NO) {
            val isNightMode = (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            activeEngines.forEach { engine -> engine.handleThemeChange(isNightMode) }
        }
    }

    inner class NeonEngine : GLEngine() {
        private var pollInterval: Long = 50L
        private var lockDelay: Long = 0L
        private var animDuration: Long = 1000L

        private var myRenderer: NeonRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

        private val resetRunnable = Runnable {
            prepareForNextUnlock()
        }

//        private val rotationRunnable = Runnable {
//            rotateWallpaper()
//        }

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
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!keyguardManager.isKeyguardLocked) {
                    isLocked = false
                    playUnlockAnimation()
                    handler.removeCallbacks(this)
                } else {
                    handler.postDelayed(this, pollInterval)
                }
            }
        }

        private val systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isLocked = true
                        handler.removeCallbacks(unlockChecker)
//                        handler.removeCallbacks(rotationRunnable)
                        handler.post(unlockChecker)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(unlockChecker)
                        isLocked = true
                        handler.postDelayed(resetRunnable, lockDelay)
//                        handler.postDelayed(rotationRunnable, lockDelay)
                        rotateWallpaper()
                    }
                    Intent.ACTION_USER_PRESENT -> {
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

        override fun onCreate(surfaceHolder: android.view.SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val r = getRenderer()
            if (r is NeonRenderer) {
                myRenderer = r
                r.onSketchUpdated = { requestRender() }
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
            registerReceiver(systemEventReceiver, filter, Context.RECEIVER_EXPORTED)
        }

        override fun onDestroy() {
            super.onDestroy()
            activeEngines.remove(this)
            myRenderer?.release()
            myRenderer = null
            try { unregisterReceiver(systemEventReceiver) } catch (e: Exception) { }
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
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!km.isKeyguardLocked) isLocked = false

                if (isLocked) {
                    myRenderer?.blurStrength = 0.0f // Locked
                    requestRender()
                } else {
                    snapToHomeState()
                }
            }
        }

        // blurStrength is 0.0 for whatever the lock screen shows and 1.0 for
        // whatever the home screen shows; uReverse decides which one is sketch.
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
            myRenderer?.blurStrength = 1.0f // Unlocked
            requestRender()
        }

        private fun prepareForNextUnlock() {
            myRenderer?.blurStrength = 0.0f // Reset to the lock state
            requestRender()
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            myRenderer?.dimLevel = prefs.getFloat("dim_level", 0.0f)
            myRenderer?.lineWidth = prefs.getFloat("neon_line_width", 1.5f)
            myRenderer?.sensitivity = prefs.getFloat("neon_sensitivity", 0.5f)
            myRenderer?.configureSubjectSegmentation(
                prefs.getBoolean(CanvasSubjectSettings.ENABLED_KEY, false)
            )
            // Line sensitivity changes which contours make it into the sketch,
            // so it has to be re-baked when settings are applied.
            myRenderer?.rebuildSketch()

            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            pollInterval = prefs.getLong("poll_interval", if (isSamsung) 30000L else 50L)
            lockDelay = prefs.getLong("lock_delay", if (isSamsung) 0L else 800L)
            animDuration = prefs.getLong("anim_duration", 1000L)
        }
    }
}
