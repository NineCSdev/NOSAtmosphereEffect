package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.SubjectMaskCoordinator
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.AtmosphereBlobFrame
import com.app.nosatmosphereeffect.renderer.AtmosphereRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageHost

internal class VulkanAtmosphereHost(
    context: Context,
    private val reverse: Boolean,
    initialState: AtmosphereRenderState,
    onFatalFailure: (VulkanAtmosphereHost, String) -> Unit,
    onVulkanActive: (VulkanAtmosphereHost, Int) -> Unit
) : VulkanSingleImageHost<AtmosphereRenderState>(
    context = context,
    threadName = if (reverse) {
        "AtmoVulkanReverseAtmosphere"
    } else {
        "AtmoVulkanAtmosphere"
    },
    initialState = initialState.sanitized(),
    bridge = VulkanAtmosphereBridge(reverse),
    onFatalFailure = { host: WallpaperRenderHost, reason: String ->
        onFatalFailure(host as VulkanAtmosphereHost, reason)
    },
    onVulkanActive = { host: WallpaperRenderHost, version: Int ->
        onVulkanActive(host as VulkanAtmosphereHost, version)
    }
) {
    private val blobPlanner = AtmosphereBlobPlanner()
    private val subjectMasks = SubjectMaskCoordinator(appContext) {
        requestRender()
    }

    init {
        subjectMasks.configure(
            initialState.glassEnabled && initialState.glassBackgroundOnly
        )
        startNativeEngine()
    }

    fun updateState(state: AtmosphereRenderState) {
        val safe = state.sanitized()
        val current = currentEffectState()
        if (safe.progress == 0f && current.progress != 0f) {
            blobPlanner.rerollTargets()
        }
        val isolationEnabled = safe.glassEnabled && safe.glassBackgroundOnly
        val isolationChanged = subjectMasks.configure(isolationEnabled)
        updateEffectState {
            safe.copy(
                hasSubject = if (isolationEnabled) current.hasSubject else false,
                blobs = blobPlanner.frame(safe.progress)
            )
        }
        if (isolationChanged && isolationEnabled) {
            reloadTexture()
        }
    }

    override fun onWallpaperUploadedOnWorker(
        handle: Long,
        bitmap: Bitmap,
        textureGeneration: Long
    ): Boolean {
        updateEffectState {
            it.copy(
                hasSubject = false,
                blobs = AtmosphereBlobFrame()
            )
        }
        if (!VulkanAtmosphereNative.nativeClearMask(handle)) {
            return false
        }

        val blurred = try {
            AtmosphereImageProcessor.createBlurredBitmap(bitmap)
        } catch (failure: RuntimeException) {
            Log.e(TAG, "Unable to preblur the Vulkan Atmosphere wallpaper", failure)
            return false
        } catch (failure: OutOfMemoryError) {
            Log.e(TAG, "Not enough memory to preblur the Atmosphere wallpaper", failure)
            return false
        }
        try {
            if (!VulkanAtmosphereNative.nativeUploadBlurred(handle, blurred)) {
                return false
            }
            blobPlanner.replaceImage(blurred)
            updateEffectState {
                it.copy(blobs = blobPlanner.frame(it.progress))
            }
        } finally {
            blurred.recycleSafely()
        }

        if (subjectMasks.enabled) {
            runCatching {
                subjectMasks.request(bitmap, textureGeneration)
            }.onFailure { failure ->
                Log.w(TAG, "Unable to request a Vulkan Atmosphere subject mask", failure)
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
            val uploaded = VulkanAtmosphereNative.nativeUploadMask(
                handle,
                pending.bitmap
            )
            updateEffectState { it.copy(hasSubject = uploaded) }
            return uploaded
        } finally {
            pending.bitmap.recycleSafely()
        }
    }

    override fun onSurfaceResetOnWorker() {
        subjectMasks.discardPending()
        updateEffectState {
            it.copy(
                hasSubject = false,
                blobs = AtmosphereBlobFrame()
            )
        }
    }

    override fun onEffectResourcesReleased() {
        subjectMasks.close()
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private companion object {
        const val TAG = "VulkanAtmosphereHost"
    }
}
