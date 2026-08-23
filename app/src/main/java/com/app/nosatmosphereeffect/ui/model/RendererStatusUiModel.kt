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
    val vulkanVersion: String?,
    val fallbackReason: String? = null
) {
    init {
        require(
            when (backend) {
                RendererBackendUi.VULKAN ->
                    !vulkanVersion.isNullOrBlank() && fallbackReason == null
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
                openGlActive()
            } else {
                RendererStatusUiModel(
                    backend = RendererBackendUi.VULKAN,
                    vulkanVersion = normalizedVersion
                )
            }
        }

        fun openGlActive(fallbackReason: String? = null): RendererStatusUiModel =
            RendererStatusUiModel(
                backend = RendererBackendUi.OPENGL_ES,
                vulkanVersion = null,
                fallbackReason = fallbackReason
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.take(500)
            )
    }
}

fun rendererStatusUiModel(
    wallpaperActive: Boolean,
    activeEffectId: String?,
    runtimeStatus: RendererRuntimeStatus
): RendererStatusUiModel? {
    if (!wallpaperActive || activeEffectId == null) return null
    if (runtimeStatus.effectId != activeEffectId) return null
    if (runtimeStatus.phase != RendererRuntimePhase.ACTIVE) return null

    return when (runtimeStatus.activeBackend) {
        GraphicsBackend.VULKAN -> {
            val version = runtimeStatus.activeVulkanApiVersion
                ?.let(VulkanApiVersion::fromEncoded)
                ?: return null
            RendererStatusUiModel.vulkanActive(version.toString())
        }
        GraphicsBackend.OPENGL_ES -> RendererStatusUiModel.openGlActive(
            runtimeStatus.fallbackReason
        )
        null -> null
    }
}
