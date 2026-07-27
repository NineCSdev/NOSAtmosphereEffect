package com.app.nosatmosphereeffect.renderer.vulkan

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackendSelector
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeSession
import com.app.nosatmosphereeffect.renderer.status.RendererRuntimeStatusRepository
import com.app.nosatmosphereeffect.renderer.status.VulkanDeviceCapability
import java.util.Locale

internal object VulkanSupport {
    private const val TAG = "VulkanSupport"
    private const val VULKAN_1_1 = 0x00401000

    @Volatile
    private var cachedNativeProbe: Int? = null

    fun selectBackend(context: Context, effectId: String): VulkanBackendSelection {
        val featureQuery = runCatching {
            context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
                VULKAN_1_1
            )
        }
        val hasVulkan11 = featureQuery.getOrElse { failure ->
            Log.w(TAG, "Unable to query Vulkan system features", failure)
            false
        }
        val probedVersion = if (hasVulkan11) probeNativeRuntime() else null
        val blockedAfterFailure = runCatching {
            VulkanFailureStore.isBlocked(context, effectId)
        }.getOrElse { failure ->
            Log.w(TAG, "Unable to read the Vulkan failure state", failure)
            true
        }
        val selectedBackend = GraphicsBackendSelector.select(
            effectId = effectId,
            hasVulkan11 = hasVulkan11,
            nativeProbePassed = probedVersion != null,
            blockedAfterFailure = blockedAfterFailure
        )
        val capability = when {
            featureQuery.isFailure -> VulkanDeviceCapability.UNKNOWN
            !hasVulkan11 -> VulkanDeviceCapability.UNSUPPORTED
            else -> VulkanDeviceCapability.SUPPORTED
        }
        val fallbackReason = if (selectedBackend == GraphicsBackend.VULKAN) {
            null
        } else {
            when {
                featureQuery.isFailure -> "Vulkan capability query failed"
                !hasVulkan11 -> "Vulkan 1.1 is not advertised by this device"
                probedVersion == null -> "No compatible Vulkan runtime was found"
                blockedAfterFailure -> "Vulkan was disabled after a previous driver failure"
                else -> "This effect does not have a Vulkan renderer"
            }
        }
        val runtimeSession = runCatching {
            RendererRuntimeStatusRepository.recordSelection(
                context = context,
                effectId = effectId,
                selectedBackend = selectedBackend,
                vulkanCapability = capability,
                probedVulkanApiVersion = probedVersion?.encoded,
                fallbackReason = fallbackReason
            )
        }.onFailure { failure ->
            Log.w(TAG, "Unable to publish the renderer selection", failure)
        }.getOrNull()
        return VulkanBackendSelection(
            backend = selectedBackend,
            runtimeSession = runtimeSession
        )
    }

    fun recordFailure(context: Context, effectId: String, reason: String) {
        VulkanFailureStore.record(context, effectId, reason)
    }

    fun probedApiVersion(): VulkanApiVersion? {
        return probeNativeRuntime()
    }

    private fun probeNativeRuntime(): VulkanApiVersion? {
        cachedNativeProbe?.let { return VulkanApiVersion.fromEncoded(it) }
        return synchronized(this) {
            val cached = cachedNativeProbe
            if (cached != null) {
                return@synchronized VulkanApiVersion.fromEncoded(cached)
            }
            run {
                val encoded = if (VulkanNative.libraryLoaded) {
                    runCatching {
                        VulkanNative.nativeProbe()
                    }.getOrElse { failure ->
                        Log.w(TAG, "The native Vulkan probe failed", failure)
                        0
                    }
                } else {
                    0
                }
                cachedNativeProbe = encoded
                VulkanApiVersion.fromEncoded(encoded).also { version ->
                    if (encoded != 0 && version == null) {
                        Log.w(TAG, "The native Vulkan probe returned an unsupported API version")
                    }
                }
            }
        }
    }
}

internal data class VulkanBackendSelection(
    val backend: GraphicsBackend,
    val runtimeSession: RendererRuntimeSession?
)

private object VulkanFailureStore {
    private const val PREFS_NAME = "graphics_backend_prefs"
    private const val LEGACY_FAILURE_ID_KEY = "vulkan_failure_id"
    private const val FAILURE_ID_PREFIX = "vulkan_failure_id_"
    private const val FAILURE_REASON_PREFIX = "vulkan_failure_reason_"

    fun isBlocked(context: Context, effectId: String): Boolean {
        val preferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentFailureId = failureId(context)
        return VulkanFailurePolicy.isBlocked(
            effectId = effectId,
            currentFailureId = currentFailureId,
            scopedFailureId = preferences.getString(failureIdKey(effectId), null),
            legacyFailureId = preferences.getString(LEGACY_FAILURE_ID_KEY, null)
        )
    }

    fun record(context: Context, effectId: String, reason: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(failureIdKey(effectId), failureId(context))
            putString(failureReasonKey(effectId), reason.take(500))
        }
    }

    private fun failureIdKey(effectId: String): String {
        return FAILURE_ID_PREFIX + VulkanFailurePolicy.normalizedEffectId(effectId)
    }

    private fun failureReasonKey(effectId: String): String {
        return FAILURE_REASON_PREFIX + VulkanFailurePolicy.normalizedEffectId(effectId)
    }

    private fun failureId(context: Context): String {
        val versionCode = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode
        }.getOrDefault(0L)
        return "${Build.FINGERPRINT}|$versionCode"
    }

}

internal object VulkanFailurePolicy {
    private val legacyColorFillEffects = setOf(
        "COLORFILL",
        "COLORFILL_REVERSE"
    )

    fun isBlocked(
        effectId: String,
        currentFailureId: String,
        scopedFailureId: String?,
        legacyFailureId: String?
    ): Boolean {
        if (scopedFailureId == currentFailureId) return true
        return normalizedEffectId(effectId) in legacyColorFillEffects &&
            legacyFailureId == currentFailureId
    }

    fun normalizedEffectId(effectId: String): String {
        val normalized = effectId.trim()
            .uppercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it == '_' }
        return normalized.ifBlank { "UNKNOWN" }
    }
}
