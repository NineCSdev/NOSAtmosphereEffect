package com.app.nosatmosphereeffect.helper

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable

internal class SubjectMaskCoordinator(
    context: Context,
    private val onMaskReady: () -> Unit
) : Closeable {

    data class PendingMask(
        val generation: Long,
        val bitmap: Bitmap
    )

    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    var enabled = false
        private set

    private var closed = false
    private var latestRequest = -1L
    private var extractor: SubjectMaskExtractor? = null
    private var pendingMask: PendingMask? = null

    fun configure(enabled: Boolean): Boolean {
        var extractorToClose: SubjectMaskExtractor? = null
        var bitmapToRecycle: Bitmap? = null
        val changed = synchronized(lock) {
            if (closed || this.enabled == enabled) {
                false
            } else {
                this.enabled = enabled
                if (!enabled) {
                    latestRequest = -1L
                    extractorToClose = extractor
                    extractor = null
                    bitmapToRecycle = pendingMask?.bitmap
                    pendingMask = null
                }
                true
            }
        }
        extractorToClose?.close()
        bitmapToRecycle.recycleSafely()
        return changed
    }

    fun request(bitmap: Bitmap, generation: Long) {
        val activeExtractor = synchronized(lock) {
            if (closed || !enabled || bitmap.isRecycled) return
            latestRequest = generation
            extractor ?: SubjectMaskExtractor(
                appContext,
                ::onMaskResult
            ).also { extractor = it }
        }
        activeExtractor.extract(bitmap, generation)
    }

    fun takePending(): PendingMask? = synchronized(lock) {
        pendingMask.also { pendingMask = null }
    }

    fun discardPending() {
        takePending()?.bitmap.recycleSafely()
    }

    private fun onMaskResult(generation: Long, bitmap: Bitmap?) {
        if (bitmap == null) return

        var replaced: Bitmap? = null
        val accepted = synchronized(lock) {
            if (closed || !enabled || generation != latestRequest) {
                false
            } else if (pendingMask?.generation?.let { it > generation } == true) {
                false
            } else {
                replaced = pendingMask?.bitmap
                pendingMask = PendingMask(generation, bitmap)
                true
            }
        }

        if (!accepted) {
            bitmap.recycleSafely()
            return
        }
        replaced.recycleSafely()
        onMaskReady()
    }

    override fun close() {
        var extractorToClose: SubjectMaskExtractor? = null
        var bitmapToRecycle: Bitmap? = null
        synchronized(lock) {
            if (closed) return
            closed = true
            enabled = false
            latestRequest = -1L
            extractorToClose = extractor
            extractor = null
            bitmapToRecycle = pendingMask?.bitmap
            pendingMask = null
        }
        extractorToClose?.close()
        bitmapToRecycle.recycleSafely()
    }

    private fun Bitmap?.recycleSafely() {
        if (this != null && !isRecycled) recycle()
    }
}
