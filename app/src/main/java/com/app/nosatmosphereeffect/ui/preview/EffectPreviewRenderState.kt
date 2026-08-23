package com.app.nosatmosphereeffect.ui.preview

import com.app.nosatmosphereeffect.renderer.AtmosphereRenderState
import com.app.nosatmosphereeffect.renderer.ColorFillRenderState
import com.app.nosatmosphereeffect.renderer.FrostedRenderState
import com.app.nosatmosphereeffect.renderer.GlassRenderState
import com.app.nosatmosphereeffect.renderer.HalftoneRenderState
import com.app.nosatmosphereeffect.renderer.NeonRenderState

internal sealed interface EffectPreviewRenderState {
    data class Atmosphere(val value: AtmosphereRenderState) : EffectPreviewRenderState

    data class Frosted(val value: FrostedRenderState) : EffectPreviewRenderState

    data class Glass(val value: GlassRenderState) : EffectPreviewRenderState

    data class Halftone(val value: HalftoneRenderState) : EffectPreviewRenderState

    data class ColorFill(val value: ColorFillRenderState) : EffectPreviewRenderState

    data class Neon(val value: NeonRenderState) : EffectPreviewRenderState
}

internal sealed interface EffectPreviewOpenGlFrameUpdate {
    val progress: Float

    data class Atmosphere(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate

    data class Frosted(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate

    data class Glass(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate

    data class Halftone(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate

    data class ColorFill(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate

    data class Neon(override val progress: Float) :
        EffectPreviewOpenGlFrameUpdate
}

internal object EffectPreviewStatePolicy {
    fun withProgress(
        state: EffectPreviewRenderState,
        progress: Float,
        atmosphereGlassEnabled: Boolean,
        atmosphereGlassBackgroundOnly: Boolean
    ): EffectPreviewRenderState {
        return when (state) {
            is EffectPreviewRenderState.Atmosphere -> {
                EffectPreviewRenderState.Atmosphere(
                    state.value.copy(
                        progress = progress,
                        glassEnabled = atmosphereGlassEnabled,
                        glassBackgroundOnly =
                            atmosphereGlassEnabled && atmosphereGlassBackgroundOnly
                    ).sanitized()
                )
            }
            is EffectPreviewRenderState.Frosted -> {
                EffectPreviewRenderState.Frosted(
                    state.value.copy(progress = progress).sanitized()
                )
            }
            is EffectPreviewRenderState.Glass -> {
                EffectPreviewRenderState.Glass(
                    state.value.copy(progress = progress).sanitized()
                )
            }
            is EffectPreviewRenderState.Halftone -> {
                EffectPreviewRenderState.Halftone(
                    state.value.copy(progress = progress).sanitized()
                )
            }
            is EffectPreviewRenderState.ColorFill -> {
                EffectPreviewRenderState.ColorFill(
                    state.value.copy(progress = progress).sanitized()
                )
            }
            is EffectPreviewRenderState.Neon -> {
                EffectPreviewRenderState.Neon(
                    state.value.copy(progress = progress).sanitized()
                )
            }
        }
    }

    fun progress(state: EffectPreviewRenderState): Float {
        return when (state) {
            is EffectPreviewRenderState.Atmosphere -> state.value.progress
            is EffectPreviewRenderState.Frosted -> state.value.progress
            is EffectPreviewRenderState.Glass -> state.value.progress
            is EffectPreviewRenderState.Halftone -> state.value.progress
            is EffectPreviewRenderState.ColorFill -> state.value.progress
            is EffectPreviewRenderState.Neon -> state.value.progress
        }
    }

    fun openGlFrameUpdate(
        state: EffectPreviewRenderState
    ): EffectPreviewOpenGlFrameUpdate {
        return when (state) {
            is EffectPreviewRenderState.Atmosphere ->
                EffectPreviewOpenGlFrameUpdate.Atmosphere(state.value.progress)
            is EffectPreviewRenderState.Frosted ->
                EffectPreviewOpenGlFrameUpdate.Frosted(state.value.progress)
            is EffectPreviewRenderState.Glass ->
                EffectPreviewOpenGlFrameUpdate.Glass(state.value.progress)
            is EffectPreviewRenderState.Halftone ->
                EffectPreviewOpenGlFrameUpdate.Halftone(state.value.progress)
            is EffectPreviewRenderState.ColorFill ->
                EffectPreviewOpenGlFrameUpdate.ColorFill(state.value.progress)
            is EffectPreviewRenderState.Neon ->
                EffectPreviewOpenGlFrameUpdate.Neon(state.value.progress)
        }
    }
}
