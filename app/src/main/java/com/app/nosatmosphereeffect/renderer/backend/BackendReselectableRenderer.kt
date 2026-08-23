package com.app.nosatmosphereeffect.renderer.backend

internal interface BackendReselectableRenderer {
    fun reselectBackend()
}

internal enum class BackendReselectionAction {
    NONE,
    REFRESH_ACTIVE_SESSION,
    SWAP_BACKEND
}

internal object BackendReselectionPolicy {
    fun decide(
        appliedPreference: GraphicsBackendPreference,
        requestedPreference: GraphicsBackendPreference,
        activeBackend: GraphicsBackend,
        resolvedBackend: GraphicsBackend
    ): BackendReselectionAction {
        return when {
            appliedPreference == requestedPreference ->
                BackendReselectionAction.NONE
            activeBackend == resolvedBackend ->
                BackendReselectionAction.REFRESH_ACTIVE_SESSION
            else -> BackendReselectionAction.SWAP_BACKEND
        }
    }
}
