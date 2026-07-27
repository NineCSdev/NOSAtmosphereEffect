package com.app.nosatmosphereeffect.ui.model

import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimePhase
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatus
import com.app.nosatmosphereeffect.renderer.vulkan.VulkanApiVersion

enum class RendererBackendUi {
    VULKAN,
    OPENGL_ES
}

data class RendererStatusUiModel(
    val backend: RendererBackendUi,
    val vulkanVersion: String?
) {
    init {
        require(
            when (backend) {
                RendererBackendUi.VULKAN -> !vulkanVersion.isNullOrBlank()
                RendererBackendUi.OPENGL_ES -> vulkanVersion == null
            }
        ) { "The renderer backend and Vulkan version must describe the same active state" }
    }

    val isVulkanActive: Boolean
        get() = backend == RendererBackendUi.VULKAN

    companion object {
        fun vulkanActive(version: String?): RendererStatusUiModel {
            val normalizedVersion = version?.trim().orEmpty()
            return if (normalizedVersion.isEmpty()) {
                openGlFallback()
            } else {
                RendererStatusUiModel(
                    backend = RendererBackendUi.VULKAN,
                    vulkanVersion = normalizedVersion
                )
            }
        }

        fun openGlFallback(): RendererStatusUiModel = RendererStatusUiModel(
            backend = RendererBackendUi.OPENGL_ES,
            vulkanVersion = null
        )
    }
}

fun rendererStatusUiModel(
    wallpaperActive: Boolean,
    activeEffectId: String?,
    runtimeStatus: RendererRuntimeStatus
): RendererStatusUiModel? {
    if (!wallpaperActive || activeEffectId == null) return null
    if (activeEffectId !in VULKAN_EFFECT_IDS) {
        return RendererStatusUiModel.openGlFallback()
    }
    if (runtimeStatus.effectId != activeEffectId) return null

    if (runtimeStatus.isVulkanActive) {
        val version = runtimeStatus.activeVulkanApiVersion
            ?.let(VulkanApiVersion::fromEncoded)
            ?: return null
        return RendererStatusUiModel.vulkanActive(version.toString())
    }

    return if (
        runtimeStatus.phase == RendererRuntimePhase.ACTIVE &&
        runtimeStatus.activeBackend == GraphicsBackend.OPENGL_ES
    ) {
        RendererStatusUiModel.openGlFallback()
    } else {
        null
    }
}

private val VULKAN_EFFECT_IDS = setOf("COLORFILL", "COLORFILL_REVERSE")
