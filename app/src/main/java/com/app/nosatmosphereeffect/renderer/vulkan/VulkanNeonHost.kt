package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.app.nosatmosphereeffect.helper.SubjectMaskCoordinator
import com.app.nosatmosphereeffect.helper.WallpaperRenderHost
import com.app.nosatmosphereeffect.renderer.NeonRenderState
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageBridge
import com.app.nosatmosphereeffect.renderer.vulkan.common.VulkanSingleImageHost
import kotlin.math.abs

internal class VulkanNeonHost(
    context: Context,
    private val reverse: Boolean,
    initialState: NeonRenderState,
    onFatalFailure: (WallpaperRenderHost, String) -> Unit,
    onVulkanActive: (WallpaperRenderHost, Int) -> Unit,
    previewSource: (() -> Bitmap?)? = null
) : VulkanSingleImageHost<NeonRenderState>(
    context = context,
    threadName = "AtmoVulkanCanvas",
    initialState = initialState.sanitized(),
    bridge = NeonBridge(reverse),
    onFatalFailure = onFatalFailure,
    onVulkanActive = onVulkanActive,
    previewSource = previewSource
) {
    private val subjectMasks = SubjectMaskCoordinator(context, ::requestRender)

    private var retainedSource: Bitmap? = null
    private var retainedSubjectMask: Bitmap? = null
    private var currentTextureGeneration = NO_TEXTURE_GENERATION
    private var requestedMaskGeneration = NO_TEXTURE_GENERATION
    private var subjectMaskRevision = 0L
    private var bakedMaskRevision = NO_MASK_REVISION
    private var bakedTextureGeneration = NO_TEXTURE_GENERATION
    private var bakedSensitivity = Float.NaN
    private var bakedWithSegmentation = false

    init {
        subjectMasks.configure(initialState.subjectSegmentationEnabled)
        startNativeEngine()
    }

    fun updateState(state: NeonRenderState) {
        val sanitized = state.sanitized()
        val previous = currentEffectState()
        val segmentationChanged = subjectMasks.configure(
            sanitized.subjectSegmentationEnabled
        )
        updateEffectState { sanitized }
        if (segmentationChanged && sanitized.subjectSegmentationEnabled) {
            reloadTexture()
        } else if (
            segmentationChanged ||
            abs(previous.sensitivity - sanitized.sensitivity) >
                SENSITIVITY_EPSILON
        ) {
            requestRender()
        }
    }

    override fun onWallpaperUploadedOnWorker(
        handle: Long,
        bitmap: Bitmap,
        textureGeneration: Long
    ): Boolean {
        recycleRetainedImages()
        subjectMasks.discardPending()
        val copied = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return false
        retainedSource = copied
        currentTextureGeneration = textureGeneration
        requestedMaskGeneration = NO_TEXTURE_GENERATION
        subjectMaskRevision = 0L
        bakedMaskRevision = NO_MASK_REVISION
        bakedTextureGeneration = NO_TEXTURE_GENERATION
        bakedSensitivity = Float.NaN
        bakedWithSegmentation = false

        requestSubjectMaskIfNeeded(textureGeneration)
        return bakeAndUploadContour(handle, textureGeneration)
    }

    override fun prepareFrameOnWorker(
        handle: Long,
        textureGeneration: Long
    ): Boolean {
        consumePendingSubjectMask(textureGeneration)

        val state = currentEffectState().sanitized()
        if (!state.subjectSegmentationEnabled && retainedSubjectMask != null) {
            retainedSubjectMask.recycleSafely()
            retainedSubjectMask = null
            subjectMaskRevision++
        }
        requestSubjectMaskIfNeeded(textureGeneration)

        val needsBake =
            bakedTextureGeneration != textureGeneration ||
                abs(bakedSensitivity - state.sensitivity) > SENSITIVITY_EPSILON ||
                bakedWithSegmentation != state.subjectSegmentationEnabled ||
                bakedMaskRevision != subjectMaskRevision
        return !needsBake || bakeAndUploadContour(handle, textureGeneration)
    }

    override fun onSurfaceResetOnWorker() {
        subjectMasks.discardPending()
        recycleRetainedImages()
        currentTextureGeneration = NO_TEXTURE_GENERATION
        requestedMaskGeneration = NO_TEXTURE_GENERATION
        subjectMaskRevision = 0L
        bakedMaskRevision = NO_MASK_REVISION
        bakedTextureGeneration = NO_TEXTURE_GENERATION
        bakedSensitivity = Float.NaN
        bakedWithSegmentation = false
    }

    override fun onEffectResourcesReleased() {
        recycleRetainedImages()
        subjectMasks.close()
    }

    private fun requestSubjectMaskIfNeeded(textureGeneration: Long) {
        val source = retainedSource ?: return
        if (
            !subjectMasks.enabled ||
            textureGeneration != currentTextureGeneration ||
            requestedMaskGeneration == textureGeneration
        ) {
            return
        }
        requestedMaskGeneration = textureGeneration
        subjectMasks.request(source, textureGeneration)
    }

    private fun consumePendingSubjectMask(textureGeneration: Long) {
        val pending = subjectMasks.takePending() ?: return
        if (
            pending.generation != textureGeneration ||
            textureGeneration != currentTextureGeneration ||
            !subjectMasks.enabled
        ) {
            pending.bitmap.recycleSafely()
            return
        }
        retainedSubjectMask.recycleSafely()
        retainedSubjectMask = pending.bitmap
        subjectMaskRevision++
    }

    private fun bakeAndUploadContour(
        handle: Long,
        textureGeneration: Long
    ): Boolean {
        val source = retainedSource ?: return false
        val state = currentEffectState().sanitized()
        return runCatching {
            val workSize = CanvasContourProcessor.workingSize(
                source.width,
                source.height
            )
            val working = if (
                source.width == workSize.width &&
                source.height == workSize.height
            ) {
                source
            } else {
                Bitmap.createScaledBitmap(
                    source,
                    workSize.width,
                    workSize.height,
                    true
                )
            }
            try {
                val sourcePixels = IntArray(workSize.width * workSize.height)
                working.getPixels(
                    sourcePixels,
                    0,
                    workSize.width,
                    0,
                    0,
                    workSize.width,
                    workSize.height
                )
                val subjectMask = if (state.subjectSegmentationEnabled) {
                    retainedSubjectMask?.toProcessorMask()
                } else {
                    null
                }
                val contour = CanvasContourProcessor.process(
                    source = CanvasContourProcessor.PixelImage(
                        workSize.width,
                        workSize.height,
                        sourcePixels
                    ),
                    sensitivity = state.sensitivity,
                    subjectMask = subjectMask
                )
                val bitmap = contour.toBitmap()
                try {
                    if (!VulkanNeonNative.nativeUploadContour(handle, bitmap)) {
                        return@runCatching false
                    }
                } finally {
                    bitmap.recycleSafely()
                }
            } finally {
                if (working !== source) working.recycleSafely()
            }

            bakedTextureGeneration = textureGeneration
            bakedSensitivity = state.sensitivity
            bakedWithSegmentation = state.subjectSegmentationEnabled
            bakedMaskRevision = subjectMaskRevision
            true
        }.getOrElse { failure ->
            Log.e(TAG, "Unable to build the Vulkan Canvas contour map", failure)
            false
        }
    }

    private fun Bitmap.toProcessorMask(): CanvasContourProcessor.SubjectMask {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        val values = ByteArray(pixels.size)
        for (index in pixels.indices) {
            values[index] = ((pixels[index] ushr 16) and 0xFF).toByte()
        }
        return CanvasContourProcessor.SubjectMask(width, height, values)
    }

    private fun CanvasContourProcessor.Result.toBitmap(): Bitmap {
        val pixels = IntArray(normalizedDistance.size)
        for (index in normalizedDistance.indices) {
            val gray = normalizedDistance[index].toInt() and 0xFF
            pixels[index] = 0xFF000000.toInt() or
                (gray shl 16) or
                (gray shl 8) or
                gray
        }
        return Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun recycleRetainedImages() {
        retainedSource.recycleSafely()
        retainedSource = null
        retainedSubjectMask.recycleSafely()
        retainedSubjectMask = null
    }

    private fun Bitmap?.recycleSafely() {
        if (this != null && !isRecycled) recycle()
    }

    private companion object {
        const val TAG = "VulkanCanvasHost"
        const val NO_TEXTURE_GENERATION = -1L
        const val NO_MASK_REVISION = -1L
        const val SENSITIVITY_EPSILON = 0.0001f
    }
}

private class NeonBridge(
    private val reverse: Boolean
) : VulkanSingleImageBridge<NeonRenderState> {
    override val effectLabel = "Canvas Sketch"

    override fun create(assets: android.content.res.AssetManager): Long {
        if (!VulkanNeonNative.libraryLoaded) return 0L
        return VulkanNeonNative.nativeCreate(assets, reverse)
    }

    override fun setSurface(
        handle: Long,
        surface: android.view.Surface,
        width: Int,
        height: Int
    ): Boolean {
        return VulkanNeonNative.nativeSetSurface(
            handle,
            surface,
            width,
            height
        )
    }

    override fun getApiVersion(handle: Long): Int {
        return VulkanNeonNative.nativeGetApiVersion(handle)
    }

    override fun uploadWallpaper(handle: Long, bitmap: Bitmap): Boolean {
        return VulkanNeonNative.nativeUploadWallpaper(handle, bitmap)
    }

    override fun setState(
        handle: Long,
        state: NeonRenderState,
        scrollOffsetX: Float,
        scrollWindowX: Float
    ) {
        val safe = state.sanitized()
        VulkanNeonNative.nativeSetState(
            handle = handle,
            progress = safe.progress,
            dimLevel = safe.dimLevel,
            lineWidth = safe.lineWidth,
            scrollOffsetX = scrollOffsetX,
            scrollWindowX = scrollWindowX
        )
    }

    override fun render(handle: Long): Int {
        return VulkanNeonNative.nativeRender(handle)
    }

    override fun destroySurface(handle: Long) {
        VulkanNeonNative.nativeDestroySurface(handle)
    }

    override fun destroy(handle: Long) {
        VulkanNeonNative.nativeDestroy(handle)
    }
}
