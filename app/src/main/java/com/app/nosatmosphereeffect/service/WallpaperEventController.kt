package com.app.nosatmosphereeffect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log

internal data class EffectTiming(
    val pollIntervalMs: Long,
    val lockDelayMs: Long,
    val animationDurationMs: Long
)

internal class WallpaperEventController(
    private val context: Context,
    private val logTag: String,
    private val timing: () -> EffectTiming,
    private val transitionsEnabled: () -> Boolean,
    private val isKeyguardLocked: () -> Boolean,
    private val onUnlock: () -> Unit,
    private val onPrepareForLock: () -> Unit,
    private val onScreenOff: () -> Unit,
    private val onReload: () -> Unit,
    private val onConfigUpdate: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var locked = true
    private var closed = false
    private var systemReceiverRegistered = false
    private var appReceiverRegistered = false

    private val prepareForLock = Runnable {
        runCallback("prepare the next unlock", onPrepareForLock)
    }

    private val unlockChecker = object : Runnable {
        override fun run() {
            if (closed) return
            if (!isKeyguardLocked()) {
                locked = false
                runCallback("play the unlock animation", onUnlock)
                handler.removeCallbacks(this)
            } else {
                handler.postDelayed(this, timing().pollIntervalMs)
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_RELOAD_WALLPAPER -> runCallback("reload the wallpaper", onReload)
                ACTION_UPDATE_CONFIG -> runCallback("update the wallpaper configuration", onConfigUpdate)
            }
        }
    }

    fun start(initiallyLocked: Boolean) {
        if (closed) return
        locked = initiallyLocked
        registerSystemReceiver()
        registerAppReceiver()
    }

    fun setLocked(value: Boolean) {
        locked = value
        if (!value) {
            handler.removeCallbacks(unlockChecker)
        }
    }

    fun onTransitionModeChanged() {
        if (transitionsEnabled()) return
        handler.removeCallbacks(unlockChecker)
        handler.removeCallbacks(prepareForLock)
    }

    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(unlockChecker)
        handler.removeCallbacks(prepareForLock)
        unregisterSystemReceiver()
        unregisterAppReceiver()
    }

    private fun handleScreenOn() {
        if (closed) return
        handler.removeCallbacks(unlockChecker)
        if (transitionsEnabled()) {
            locked = true
            handler.post(unlockChecker)
        } else {
            locked = isKeyguardLocked()
            if (locked) {
                runCallback("show the fixed lock-screen state", onPrepareForLock)
            } else {
                runCallback("show the fixed home-screen state", onUnlock)
            }
        }
    }

    private fun handleScreenOff() {
        if (closed) return
        handler.removeCallbacks(unlockChecker)
        handler.removeCallbacks(prepareForLock)
        locked = true
        if (transitionsEnabled()) {
            handler.postDelayed(prepareForLock, timing().lockDelayMs)
        } else {
            runCallback("show the fixed lock-screen state", onPrepareForLock)
        }
        runCallback("rotate the wallpaper playlist", onScreenOff)
    }

    private fun handleUserPresent() {
        if (closed) return
        handler.removeCallbacks(prepareForLock)
        if (!locked) return
        locked = false
        handler.removeCallbacks(unlockChecker)
        runCallback("play the unlock animation", onUnlock)
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
            systemReceiverRegistered = true
        } catch (failure: RuntimeException) {
            Log.e(logTag, "Unable to register the screen event receiver", failure)
        }
    }

    private fun registerAppReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_RELOAD_WALLPAPER)
            addAction(ACTION_UPDATE_CONFIG)
        }
        try {
            context.registerReceiver(appReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            appReceiverRegistered = true
        } catch (failure: RuntimeException) {
            Log.e(logTag, "Unable to register the wallpaper command receiver", failure)
        }
    }

    private fun unregisterSystemReceiver() {
        if (!systemReceiverRegistered) return
        systemReceiverRegistered = false
        try {
            context.unregisterReceiver(systemReceiver)
        } catch (failure: RuntimeException) {
            Log.w(logTag, "Unable to unregister the screen event receiver", failure)
        }
    }

    private fun unregisterAppReceiver() {
        if (!appReceiverRegistered) return
        appReceiverRegistered = false
        try {
            context.unregisterReceiver(appReceiver)
        } catch (failure: RuntimeException) {
            Log.w(logTag, "Unable to unregister the wallpaper command receiver", failure)
        }
    }

    private fun runCallback(operation: String, callback: () -> Unit) {
        try {
            callback()
        } catch (failure: RuntimeException) {
            Log.e(logTag, "Unable to $operation", failure)
        }
    }

    private companion object {
        const val ACTION_RELOAD_WALLPAPER = "com.app.nosatmosphereeffect.RELOAD_WALLPAPER"
        const val ACTION_UPDATE_CONFIG = "com.app.nosatmosphereeffect.UPDATE_CONFIG"
    }
}
