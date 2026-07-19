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
import com.app.nosatmosphereeffect.helper.PlaylistRotationController
import com.app.nosatmosphereeffect.renderer.FrostedRenderer
import android.os.PowerManager

class FrostedReverseService : GLWallpaperService() {

    private val activeEngines = mutableSetOf<FrostedEngine>()

    override fun onCreateEngine(): Engine {
        val engine = FrostedEngine()
        activeEngines.add(engine)
        return engine
    }

    override fun getRenderer(): GLSurfaceView.Renderer {
        return FrostedRenderer(applicationContext)
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

    inner class FrostedEngine : GLEngine() {
        private var pollInterval: Long = 50L
        private var lockDelay: Long = 0L
        private var animDuration: Long = 500L

        private var myRenderer: FrostedRenderer? = null
        private var blurAnimator: ValueAnimator? = null
        private var isLocked: Boolean = true
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())

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
                        rotateWallpaper()
//                        handler.postDelayed(rotationRunnable, lockDelay)
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
            if (r is FrostedRenderer) {
                myRenderer = r
                myRenderer?.blurStrength = 1.0f // Default Locked state is Blurred
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
            try { unregisterReceiver(systemEventReceiver) } catch (e: Exception) { }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (!visible) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isInteractive) {
                    // Screen off (device sleeping): prep for the next unlock.
                    myRenderer?.blurStrength = 0.0f
                } else {
                    // Screen still on but wallpaper left view -> app drawer / recents /
                    // another app on top. Flip on the frosted drawer blur.
                    myRenderer?.setDrawerBlurred(true)
                }
            }
            super.onVisibilityChanged(visible)
            if (visible) {
                // Back in view -> clear the drawer blur before drawing home/lock state.
                myRenderer?.setDrawerBlurred(false)
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!km.isKeyguardLocked) isLocked = false

                if (isLocked) {
                    myRenderer?.blurStrength = 1.0f // Locked = Blurred
                    requestRender()
                } else {
                    snapToHomeState()
                }
            }
        }

        private fun playUnlockAnimation() {
            val targetRenderer = myRenderer ?: return
            blurAnimator?.cancel()
            targetRenderer.blurStrength = 1.0f
            requestRender()

            // Reverse: 1.0 (Blur) -> 0.0 (Sharp)
            blurAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
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
            myRenderer?.blurStrength = 0.0f // Unlocked = Sharp
            requestRender()
        }

        private fun prepareForNextUnlock() {
            myRenderer?.blurStrength = 1.0f // Reset to Blurred
            requestRender()
        }

        private fun updateRendererConfig() {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            myRenderer?.dimLevel = prefs.getFloat("dim_level", 0.2f)
            myRenderer?.enableNoise = prefs.getBoolean("enable_noise", false)
            myRenderer?.noiseScale = prefs.getFloat("noise_scale", 2000.0f)
            myRenderer?.noiseStrength = prefs.getFloat("noise_strength", 0.06f)

            val savedRadius = prefs.getFloat("frosted_blur_radius", 200f)
            if (myRenderer?.blurRadius != savedRadius) {
                myRenderer?.blurRadius = savedRadius
                myRenderer?.reloadTexture()
            }

            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            pollInterval = prefs.getLong("poll_interval", if (isSamsung) 30000L else 50L)
            lockDelay = prefs.getLong("lock_delay", if (isSamsung) 0L else 800L)
            animDuration = prefs.getLong("anim_duration", 500L)
        }
    }
}
