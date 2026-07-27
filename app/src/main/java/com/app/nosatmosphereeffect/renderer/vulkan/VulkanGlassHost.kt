package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.SubjectMaskCoordinator
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.GlassRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageHost

internal class VulkanGlassHost(
    context: Context,
    initialState: GlassRenderState,
    onFatalFailure: (VulkanGlassHost, String) -> Unit,
    onVulkanActive: (VulkanGlassHost, Int) -> Unit
) : VulkanSingleImageHost<GlassRenderState>(
    context = context,
    threadName = "AtmoVulkanGlass",
    initialState = initialState.sanitized(),
    bridge = VulkanGlassBridge,
    onFatalFailure = { host: WallpaperRenderHost, reason: String ->
        onFatalFailure(host as VulkanGlassHost, reason)
    },
    onVulkanActive = { host: WallpaperRenderHost, version: Int ->
        onVulkanActive(host as VulkanGlassHost, version)
    }
) {
    private val subjectMasks = SubjectMaskCoordinator(appContext) {
        requestRender()
    }

    init {
        subjectMasks.configure(initialState.backgroundOnly)
        startNativeEngine()
    }

    fun updateState(state: GlassRenderState) {
        val safe = state.sanitized()
        val backgroundChanged = subjectMasks.configure(safe.backgroundOnly)
        updateEffectState { current ->
            safe.copy(
                hasSubject = if (safe.backgroundOnly) {
                    current.hasSubject
                } else {
                    false
                }
            )
        }
        if (backgroundChanged && safe.backgroundOnly) {
            reloadTexture()
        }
    }

    override fun onWallpaperUploadedOnWorker(
        handle: Long,
        bitmap: Bitmap,
        textureGeneration: Long
    ): Boolean {
        updateEffectState { it.copy(hasSubject = false) }
        if (!VulkanGlassNative.nativeClearMask(handle)) {
            return false
        }
        if (subjectMasks.enabled) {
            runCatching {
                subjectMasks.request(bitmap, textureGeneration)
            }.onFailure { failure ->
                Log.w(TAG, "Unable to request a Vulkan Glass subject mask", failure)
            }
        }
        return true
    }

    override fun prepareFrameOnWorker(
        handle: Long,
        textureGeneration: Long
    ): Boolean {
        val pending = subjectMasks.takePending() ?: return true
        try {
            if (
                pending.generation != textureGeneration ||
                !subjectMasks.enabled
            ) {
                return true
            }
            val uploaded = VulkanGlassNative.nativeUploadMask(
                handle,
                pending.bitmap
            )
            updateEffectState {
                it.copy(hasSubject = uploaded)
            }
            return uploaded
        } finally {
            pending.bitmap.recycleSafely()
        }
    }

    override fun onSurfaceResetOnWorker() {
        subjectMasks.discardPending()
        updateEffectState { it.copy(hasSubject = false) }
    }

    override fun onEffectResourcesReleased() {
        subjectMasks.close()
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private companion object {
        const val TAG = "VulkanGlassHost"
    }
}
