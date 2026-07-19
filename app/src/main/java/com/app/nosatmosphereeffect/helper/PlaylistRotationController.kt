package com.app.nosatmosphereeffect.helper

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import java.io.File

/** A single rotation pipeline used by every effect service. */
object PlaylistRotationController {
    private val rotationLock = Any()

    fun rotateAsync(
        context: Context,
        isThemeChange: Boolean,
        currentNightMode: Boolean,
        queueTransition: (Bitmap) -> Unit,
        requestRender: () -> Unit,
        notifyColorsChanged: () -> Unit
    ) {
        val appContext = context.applicationContext
        Thread {
            synchronized(rotationLock) {
                rotate(
                    context = appContext,
                    isThemeChange = isThemeChange,
                    currentNightMode = currentNightMode,
                    queueTransition = queueTransition,
                    requestRender = requestRender,
                    notifyColorsChanged = notifyColorsChanged
                )
            }
        }.start()
    }

    private fun rotate(
        context: Context,
        isThemeChange: Boolean,
        currentNightMode: Boolean,
        queueTransition: (Bitmap) -> Unit,
        requestRender: () -> Unit,
        notifyColorsChanged: () -> Unit
    ) {
        val mode = PlaylistModeManager.getMode(context)
        if (mode == PlaylistModeManager.MODE_SINGLE) return
        if (isThemeChange && mode != PlaylistModeManager.MODE_THEME) return

        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val isNightMode = if (isThemeChange) currentNightMode
            else PlaylistModeManager.currentNightMode(context)
        val nextThemeState = if (isNightMode) 1 else 0
        val themeChanged = mode == PlaylistModeManager.MODE_THEME &&
            prefs.getInt(PlaylistModeManager.KEY_ACTIVE_THEME, -1) != nextThemeState
        if (isThemeChange && !themeChanged) return

        val playlistDir = PlaylistModeManager.activePlaylistDir(context, isNightMode)
        val playlistFiles = PlaylistModeManager.imageFiles(playlistDir)
        if (playlistFiles.isEmpty()) return
        if (!themeChanged && playlistFiles.size <= 1) return

        if (!themeChanged) {
            val intervalMinutes = prefs.getLong("rotation_interval_minutes", 0L).coerceAtLeast(0L)
            if (intervalMinutes > 0L) {
                val elapsedMinutes = (
                    System.currentTimeMillis() - prefs.getLong("last_rotation_timestamp", 0L)
                    ) / 60_000L
                if (elapsedMinutes < intervalMinutes) return
            }
        }

        val lastKey = PlaylistModeManager.lastImagePreferenceKey(mode, isNightMode)
        val lastUsedName = prefs.getString(lastKey, null)
        val candidates = playlistFiles.filterNot { it.name == lastUsedName }
        val selected = candidates.ifEmpty { playlistFiles }.random()
        if (!stageAndPromote(context, selected, isNightMode, queueTransition)) return

        prefs.edit {
            putString(lastKey, selected.name)
            putLong("last_rotation_timestamp", System.currentTimeMillis())
            if (mode == PlaylistModeManager.MODE_THEME) {
                putInt(PlaylistModeManager.KEY_ACTIVE_THEME, if (isNightMode) 1 else 0)
            }
        }
        requestRender()
        notifyColorsChanged()
    }

    private fun stageAndPromote(
        context: Context,
        selected: File,
        isNightMode: Boolean,
        queueTransition: (Bitmap) -> Unit
    ): Boolean {
        return try {
            val nextFile = File(context.filesDir, WallpaperFitHelper.NEXT_WALLPAPER_FILE)
            selected.copyTo(nextFile, overwrite = true)
            WallpaperFitHelper.stageNextSource(
                filesDir = context.filesDir,
                playlistFileName = selected.name,
                originalsDirectoryName = PlaylistModeManager.activeOriginalsDirName(
                    context,
                    isNightMode
                )
            )
            WallpaperFitHelper.setNextModes(
                context,
                WallpaperFitHelper.MODE_FILL,
                WallpaperFitHelper.FILL_BLACK
            )

            val bitmap = WallpaperFitHelper.decodeNextForDisplay(context) ?: return false
            val activeFile = File(context.filesDir, WallpaperFitHelper.ACTIVE_WALLPAPER_FILE)
            if (activeFile.exists() && !activeFile.delete()) return false
            if (!nextFile.renameTo(activeFile)) {
                nextFile.copyTo(activeFile, overwrite = true)
                nextFile.delete()
            }
            WallpaperFitHelper.promoteNextSource(context.filesDir)
            WallpaperFitHelper.promoteNextMode(context)
            queueTransition(bitmap)
            true
        } catch (failure: Throwable) {
            failure.printStackTrace()
            false
        }
    }
}
