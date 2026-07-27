package com.app.nosatmosphereeffect.renderer.status

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.nosatmosphereeffect.renderer.backend.GraphicsBackend
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

enum class VulkanDeviceCapability {
    UNKNOWN,
    UNSUPPORTED,
    SUPPORTED
}

enum class RendererRuntimePhase {
    IDLE,
    SELECTED,
    INITIALIZING,
    ACTIVE,
    AWAITING_CONFIRMATION,
    RELEASED
}

data class RendererRuntimeStatus(
    val phase: RendererRuntimePhase,
    val effectId: String?,
    val vulkanCapability: VulkanDeviceCapability,
    val probedVulkanApiVersion: Int?,
    val selectedBackend: GraphicsBackend?,
    val activeBackend: GraphicsBackend?,
    val activeVulkanApiVersion: Int?,
    val fallbackReason: String?,
    val updatedAtEpochMillis: Long
) {
    init {
        require(effectId == null || effectId.isNotBlank())
        require(probedVulkanApiVersion == null || probedVulkanApiVersion > 0)
        require(activeVulkanApiVersion == null || activeVulkanApiVersion > 0)
        require(fallbackReason == null || fallbackReason.isNotBlank())
        require(phase == RendererRuntimePhase.IDLE || effectId != null)
        require(
            (phase == RendererRuntimePhase.ACTIVE) == (activeBackend != null)
        ) { "Only an active renderer phase may expose an active backend" }
        require(
            if (activeBackend == GraphicsBackend.VULKAN) {
                activeVulkanApiVersion != null
            } else {
                activeVulkanApiVersion == null
            }
        ) { "An active Vulkan version requires an active Vulkan backend" }
    }

    val isVulkanActive: Boolean
        get() = phase == RendererRuntimePhase.ACTIVE &&
            activeBackend == GraphicsBackend.VULKAN &&
            activeVulkanApiVersion != null

    companion object {
        fun idle(updatedAtEpochMillis: Long = 0L): RendererRuntimeStatus {
            return RendererRuntimeStatus(
                phase = RendererRuntimePhase.IDLE,
                effectId = null,
                vulkanCapability = VulkanDeviceCapability.UNKNOWN,
                probedVulkanApiVersion = null,
                selectedBackend = null,
                activeBackend = null,
                activeVulkanApiVersion = null,
                fallbackReason = null,
                updatedAtEpochMillis = updatedAtEpochMillis
            )
        }
    }
}

fun interface RendererRuntimeStatusListener {
    fun onRendererRuntimeStatusChanged(status: RendererRuntimeStatus)
}

class RendererRuntimeSession internal constructor(
    internal val id: String
)

object RendererRuntimeStatusRepository {
    private const val TAG = "RendererRuntimeStatus"
    private const val PREFS_NAME = "renderer_runtime_status"

    private val processSessionId = UUID.randomUUID().toString()
    private val creationLock = Any()

    @Volatile
    private var tracker: RendererRuntimeStatusTracker? = null

    fun recordSelection(
        context: Context,
        effectId: String,
        selectedBackend: GraphicsBackend,
        vulkanCapability: VulkanDeviceCapability,
        probedVulkanApiVersion: Int?,
        fallbackReason: String? = null
    ): RendererRuntimeSession {
        return tracker(context).recordSelection(
            effectId = effectId,
            selectedBackend = selectedBackend,
            vulkanCapability = vulkanCapability,
            probedVulkanApiVersion = probedVulkanApiVersion,
            fallbackReason = fallbackReason
        )
    }

    fun recordVulkanInitializing(
        context: Context,
        session: RendererRuntimeSession
    ): RendererRuntimeStatus {
        return tracker(context).recordVulkanInitializing(session)
    }

    fun recordVulkanActive(
        context: Context,
        session: RendererRuntimeSession,
        packedVersion: Int
    ): RendererRuntimeStatus {
        return tracker(context).recordVulkanActive(session, packedVersion)
    }

    fun recordOpenGlActive(
        context: Context,
        session: RendererRuntimeSession,
        reason: String? = null
    ): RendererRuntimeStatus {
        return tracker(context).recordOpenGlActive(session, reason)
    }

    fun recordReleased(
        context: Context,
        session: RendererRuntimeSession
    ): RendererRuntimeStatus {
        return tracker(context).recordReleased(session)
    }

    fun currentStatus(context: Context): RendererRuntimeStatus {
        return tracker(context).currentStatus()
    }

    fun addListener(
        context: Context,
        listener: RendererRuntimeStatusListener
    ) {
        tracker(context).addListener(listener)
    }

    fun removeListener(listener: RendererRuntimeStatusListener) {
        tracker?.removeListener(listener)
    }

    private fun tracker(context: Context): RendererRuntimeStatusTracker {
        tracker?.let { return it }
        return synchronized(creationLock) {
            tracker ?: RendererRuntimeStatusTracker(
                store = SharedPreferencesRendererRuntimeStatusStore(
                    context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                ),
                processSessionId = processSessionId,
                clock = System::currentTimeMillis,
                onError = { failure ->
                    Log.w(TAG, "Unable to persist renderer runtime status", failure)
                }
            ).also { tracker = it }
        }
    }
}

internal data class StoredRendererRuntimeStatus(
    val processSessionId: String,
    val status: RendererRuntimeStatus
)

internal interface RendererRuntimeStatusStore {
    fun load(): StoredRendererRuntimeStatus?
    fun save(storedStatus: StoredRendererRuntimeStatus)
}

internal class RendererRuntimeStatusTracker(
    private val store: RendererRuntimeStatusStore,
    private val processSessionId: String,
    private val clock: () -> Long,
    private val rendererSessionId: () -> String = { UUID.randomUUID().toString() },
    private val onError: (Throwable) -> Unit = {}
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<RendererRuntimeStatusListener>()
    private val liveSessions = LinkedHashMap<RendererRuntimeSession, LiveRendererSession>()
    private var selectionOrder = 0L
    private var status: RendererRuntimeStatus

    init {
        val restored = runCatching(store::load)
            .onFailure(onError)
            .getOrNull()
        status = restored?.status ?: RendererRuntimeStatus.idle(clock())
        if (
            restored != null &&
            restored.processSessionId != processSessionId &&
            restored.status.phase.requiresLiveProcessConfirmation()
        ) {
            status = restored.status.copy(
                phase = RendererRuntimePhase.AWAITING_CONFIRMATION,
                activeBackend = null,
                activeVulkanApiVersion = null,
                updatedAtEpochMillis = clock()
            )
            save(status)
        }
    }

    fun currentStatus(): RendererRuntimeStatus = synchronized(lock) { status }

    fun addListener(listener: RendererRuntimeStatusListener) {
        listeners += listener
        notifyListener(listener, currentStatus())
    }

    fun removeListener(listener: RendererRuntimeStatusListener) {
        listeners -= listener
    }

    fun recordSelection(
        effectId: String,
        selectedBackend: GraphicsBackend,
        vulkanCapability: VulkanDeviceCapability,
        probedVulkanApiVersion: Int?,
        fallbackReason: String? = null
    ): RendererRuntimeSession {
        val normalizedEffectId = effectId.normalizedEffectId()
        val normalizedProbe = probedVulkanApiVersion.validVersionOrNull()
        val normalizedReason = fallbackReason.normalizedReason()
        require(
            selectedBackend != GraphicsBackend.VULKAN ||
                (
                    vulkanCapability == VulkanDeviceCapability.SUPPORTED &&
                        normalizedProbe != null &&
                        normalizedReason == null
                    )
        ) {
            "Selecting Vulkan requires a supported device, a successful probe, and no fallback reason"
        }
        val session = RendererRuntimeSession(
            id = "$processSessionId:${rendererSessionId()}"
        )
        mutateSessions { now ->
            selectionOrder += 1L
            liveSessions[session] = LiveRendererSession(
                selectionOrder = selectionOrder,
                status = RendererRuntimeStatus(
                    phase = RendererRuntimePhase.SELECTED,
                    effectId = normalizedEffectId,
                    vulkanCapability = vulkanCapability,
                    probedVulkanApiVersion = normalizedProbe,
                    selectedBackend = selectedBackend,
                    activeBackend = null,
                    activeVulkanApiVersion = null,
                    fallbackReason = normalizedReason,
                    updatedAtEpochMillis = now
                )
            )
            SessionMutation.changed()
        }
        return session
    }

    fun recordVulkanInitializing(
        session: RendererRuntimeSession
    ): RendererRuntimeStatus {
        return updateSession(session) { current, now ->
            if (current.selectedBackend == GraphicsBackend.OPENGL_ES) {
                return@updateSession null
            }
            current.copy(
                phase = RendererRuntimePhase.INITIALIZING,
                vulkanCapability = VulkanDeviceCapability.SUPPORTED,
                selectedBackend = GraphicsBackend.VULKAN,
                activeBackend = null,
                activeVulkanApiVersion = null,
                fallbackReason = null,
                updatedAtEpochMillis = now
            )
        }
    }

    fun recordVulkanActive(
        session: RendererRuntimeSession,
        packedVersion: Int
    ): RendererRuntimeStatus {
        require(packedVersion > 0) { "A reported Vulkan API version must be positive" }
        return updateSession(session) { current, now ->
            if (current.selectedBackend == GraphicsBackend.OPENGL_ES) {
                return@updateSession null
            }
            current.copy(
                phase = RendererRuntimePhase.ACTIVE,
                vulkanCapability = VulkanDeviceCapability.SUPPORTED,
                probedVulkanApiVersion =
                    current.probedVulkanApiVersion ?: packedVersion,
                selectedBackend = GraphicsBackend.VULKAN,
                activeBackend = GraphicsBackend.VULKAN,
                activeVulkanApiVersion = packedVersion,
                fallbackReason = null,
                updatedAtEpochMillis = now
            )
        }
    }

    fun recordOpenGlActive(
        session: RendererRuntimeSession,
        reason: String? = null
    ): RendererRuntimeStatus {
        val normalizedReason = reason.normalizedReason()
        return updateSession(session) { current, now ->
            current.copy(
                phase = RendererRuntimePhase.ACTIVE,
                selectedBackend = current.selectedBackend ?: GraphicsBackend.OPENGL_ES,
                activeBackend = GraphicsBackend.OPENGL_ES,
                activeVulkanApiVersion = null,
                fallbackReason = normalizedReason ?: current.fallbackReason,
                updatedAtEpochMillis = now
            )
        }
    }

    fun recordReleased(
        session: RendererRuntimeSession
    ): RendererRuntimeStatus {
        return mutateSessions { now ->
            val removed = liveSessions.remove(session)
                ?: return@mutateSessions SessionMutation.unchanged()
            SessionMutation.changed(
                emptyStatus = removed.status.copy(
                    phase = RendererRuntimePhase.RELEASED,
                    activeBackend = null,
                    activeVulkanApiVersion = null,
                    updatedAtEpochMillis = now
                )
            )
        }
    }

    private fun updateSession(
        session: RendererRuntimeSession,
        transform: (RendererRuntimeStatus, Long) -> RendererRuntimeStatus?
    ): RendererRuntimeStatus {
        return mutateSessions { now ->
            val current = liveSessions[session]
                ?: return@mutateSessions SessionMutation.unchanged()
            val transformed = transform(current.status, now)
                ?: return@mutateSessions SessionMutation.unchanged()
            if (transformed == current.status) {
                return@mutateSessions SessionMutation.unchanged()
            }
            liveSessions[session] = current.copy(status = transformed)
            SessionMutation.changed()
        }
    }

    private fun mutateSessions(
        mutation: (Long) -> SessionMutation
    ): RendererRuntimeStatus {
        val next: RendererRuntimeStatus
        val shouldNotify: Boolean
        synchronized(lock) {
            val now = clock()
            val result = mutation(now)
            if (!result.changed) {
                next = status
                shouldNotify = false
            } else {
                val candidate = aggregateLiveStatus(now)
                    ?: result.emptyStatus
                    ?: RendererRuntimeStatus.idle(now)
                if (candidate.sameDisplayStateAs(status)) {
                    next = status
                    shouldNotify = false
                } else {
                    status = candidate
                    next = candidate
                    shouldNotify = true
                    save(candidate)
                }
            }
        }
        if (shouldNotify) {
            listeners.forEach { listener -> notifyListener(listener, next) }
        }
        return next
    }

    private fun aggregateLiveStatus(now: Long): RendererRuntimeStatus? {
        val latestEffect = liveSessions.values
            .maxByOrNull(LiveRendererSession::selectionOrder)
            ?.status
            ?.effectId
            ?: return null
        val candidates = liveSessions.values
            .filter { it.status.effectId == latestEffect }
        val activeVulkan = candidates
            .filter { it.status.isVulkanActive }
            .maxByOrNull { it.status.updatedAtEpochMillis }
        if (activeVulkan != null) {
            return activeVulkan.status.copy(updatedAtEpochMillis = now)
        }

        val pendingVulkan = candidates
            .filter {
                it.status.selectedBackend == GraphicsBackend.VULKAN &&
                    it.status.activeBackend == null
            }
            .maxWithOrNull(
                compareBy<LiveRendererSession> {
                    if (it.status.phase == RendererRuntimePhase.INITIALIZING) 1 else 0
                }.thenBy { it.status.updatedAtEpochMillis }
            )
        if (pendingVulkan != null) {
            return pendingVulkan.status.copy(updatedAtEpochMillis = now)
        }

        val activeOpenGl = candidates
            .filter { it.status.activeBackend == GraphicsBackend.OPENGL_ES }
            .maxByOrNull { it.status.updatedAtEpochMillis }
        if (activeOpenGl != null) {
            return activeOpenGl.status.copy(updatedAtEpochMillis = now)
        }

        return candidates
            .maxByOrNull(LiveRendererSession::selectionOrder)
            ?.status
            ?.copy(updatedAtEpochMillis = now)
    }

    private fun save(status: RendererRuntimeStatus) {
        runCatching {
            store.save(
                StoredRendererRuntimeStatus(
                    processSessionId = processSessionId,
                    status = status
                )
            )
        }.onFailure(onError)
    }

    private fun notifyListener(
        listener: RendererRuntimeStatusListener,
        status: RendererRuntimeStatus
    ) {
        runCatching { listener.onRendererRuntimeStatusChanged(status) }
            .onFailure(onError)
    }

    private data class LiveRendererSession(
        val selectionOrder: Long,
        val status: RendererRuntimeStatus
    )

    private data class SessionMutation(
        val changed: Boolean,
        val emptyStatus: RendererRuntimeStatus?
    ) {
        companion object {
            fun unchanged(): SessionMutation = SessionMutation(
                changed = false,
                emptyStatus = null
            )

            fun changed(
                emptyStatus: RendererRuntimeStatus? = null
            ): SessionMutation = SessionMutation(
                changed = true,
                emptyStatus = emptyStatus
            )
        }
    }
}

private class SharedPreferencesRendererRuntimeStatusStore(
    private val preferences: SharedPreferences
) : RendererRuntimeStatusStore {
    override fun load(): StoredRendererRuntimeStatus? {
        if (!preferences.contains(KEY_PHASE)) return null
        return runCatching {
            val phase = enumValueOf<RendererRuntimePhase>(
                preferences.getString(KEY_PHASE, null).orEmpty()
            )
            val status = RendererRuntimeStatus(
                phase = phase,
                effectId = preferences.getString(KEY_EFFECT_ID, null),
                vulkanCapability = enumValueOf(
                    preferences.getString(KEY_CAPABILITY, null).orEmpty()
                ),
                probedVulkanApiVersion = preferences.optionalPositiveInt(
                    KEY_PROBED_API_VERSION
                ),
                selectedBackend = preferences.optionalEnum(KEY_SELECTED_BACKEND),
                activeBackend = preferences.optionalEnum(KEY_ACTIVE_BACKEND),
                activeVulkanApiVersion = preferences.optionalPositiveInt(
                    KEY_ACTIVE_API_VERSION
                ),
                fallbackReason = preferences.getString(KEY_FALLBACK_REASON, null)
                    .normalizedReason(),
                updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L)
            )
            StoredRendererRuntimeStatus(
                processSessionId = preferences.getString(KEY_PROCESS_SESSION, null)
                    .orEmpty(),
                status = status
            )
        }.getOrNull()
    }

    override fun save(storedStatus: StoredRendererRuntimeStatus) {
        val status = storedStatus.status
        val editor = preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_PROCESS_SESSION, storedStatus.processSessionId)
            .putString(KEY_PHASE, status.phase.name)
            .putString(KEY_EFFECT_ID, status.effectId)
            .putString(KEY_CAPABILITY, status.vulkanCapability.name)
            .putString(KEY_SELECTED_BACKEND, status.selectedBackend?.name)
            .putString(KEY_ACTIVE_BACKEND, status.activeBackend?.name)
            .putString(KEY_FALLBACK_REASON, status.fallbackReason)
            .putLong(KEY_UPDATED_AT, status.updatedAtEpochMillis)
            .putOptionalPositiveInt(
                KEY_PROBED_API_VERSION,
                status.probedVulkanApiVersion
            )
            .putOptionalPositiveInt(
                KEY_ACTIVE_API_VERSION,
                status.activeVulkanApiVersion
            )
        if (!editor.commit()) {
            throw IOException("SharedPreferences rejected the renderer status update")
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_PROCESS_SESSION = "process_session"
        const val KEY_PHASE = "phase"
        const val KEY_EFFECT_ID = "effect_id"
        const val KEY_CAPABILITY = "vulkan_capability"
        const val KEY_PROBED_API_VERSION = "probed_vulkan_api_version"
        const val KEY_SELECTED_BACKEND = "selected_backend"
        const val KEY_ACTIVE_BACKEND = "active_backend"
        const val KEY_ACTIVE_API_VERSION = "active_vulkan_api_version"
        const val KEY_FALLBACK_REASON = "fallback_reason"
        const val KEY_UPDATED_AT = "updated_at"
    }
}

private fun RendererRuntimePhase.requiresLiveProcessConfirmation(): Boolean {
    return this == RendererRuntimePhase.INITIALIZING ||
        this == RendererRuntimePhase.ACTIVE
}

private fun RendererRuntimeStatus.sameDisplayStateAs(
    other: RendererRuntimeStatus
): Boolean {
    return copy(updatedAtEpochMillis = 0L) ==
        other.copy(updatedAtEpochMillis = 0L)
}

private fun String.normalizedEffectId(): String {
    return trim().also {
        require(it.isNotEmpty()) { "The renderer effect id must not be blank" }
    }
}

private fun String?.normalizedReason(): String? {
    return this?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_REASON_LENGTH)
}

private fun Int?.validVersionOrNull(): Int? = this?.takeIf { it > 0 }

private inline fun <reified T : Enum<T>> SharedPreferences.optionalEnum(
    key: String
): T? {
    val value = getString(key, null) ?: return null
    return enumValueOf(value)
}

private fun SharedPreferences.optionalPositiveInt(key: String): Int? {
    if (!contains(key)) return null
    return getInt(key, 0).takeIf { it > 0 }
}

private fun SharedPreferences.Editor.putOptionalPositiveInt(
    key: String,
    value: Int?
): SharedPreferences.Editor {
    return if (value == null) remove(key) else putInt(key, value)
}

private const val MAX_REASON_LENGTH = 500
