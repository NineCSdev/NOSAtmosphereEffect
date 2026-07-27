package com.app.nosatmosphereeffect.storage

internal object WallpaperStorageCoordinator {
    private val lock = Any()

    fun <T> runExclusive(operation: () -> T): T {
        return synchronized(lock, operation)
    }
}
