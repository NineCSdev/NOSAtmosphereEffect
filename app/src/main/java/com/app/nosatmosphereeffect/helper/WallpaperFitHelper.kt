package com.app.nosatmosphereeffect.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Central helper for fitting wallpaper images to the *actual* surface they are
 * rendered on.
 *
 * Solves two problems:
 *  1. User-selectable image fit ("Screen Fill", "Fit Image", "Stretch",
 *     "Rotate to Fit") with configurable empty-space fill (black bars,
 *     repeating pattern, mirrored pattern).
 *  2. Foldables / multi-display devices: the surface size changes between the
 *     cover and inner screens. Renderers re-fit the image for the current
 *     surface instead of stretching a bitmap that was prepared for a
 *     different screen.
 *
 * File layout in [Context.getFilesDir]:
 *  - wallpaper.jpg          the active, user-cropped image (legacy, unchanged)
 *  - wallpaper_src.jpg      the un-cropped source of the active image
 *  - next_wallpaper.jpg     cropped image queued for playlist rotation (legacy)
 *  - next_wallpaper_src.jpg un-cropped source queued for playlist rotation
 *
 * Display settings live in their own SharedPreferences file ("display_prefs")
 * on purpose: the apply flows wipe "app_prefs" and "wallpaper_prefs" on every
 * new wallpaper, but the user's fit preference should survive that.
 */
object WallpaperFitHelper {

    const val PREFS_NAME = "display_prefs"

    // Per-slot display modes. A "slot" is one of the two materialized wallpaper
    // image sets on disk: the ACTIVE one (wallpaper.jpg / wallpaper_src.jpg) and
    // the NEXT one queued for playlist rotation (next_wallpaper.jpg /
    // next_wallpaper_src.jpg). Storing the fit per slot lets every playlist image
    // keep its own fit mode (e.g. image 1 = Screen Fill, image 2 = Stretch).
    const val KEY_ACTIVE_FIT = "active_fit_mode"
    const val KEY_ACTIVE_FILL = "active_fill_mode"
    const val KEY_NEXT_FIT = "next_fit_mode"
    const val KEY_NEXT_FILL = "next_fill_mode"

    // Horizontal wallpaper scrolling (home-screen page parallax). Lives in
    // display_prefs alongside the fit modes so it survives the app_prefs /
    // wallpaper_prefs wipe that happens on every new wallpaper. Global (not
    // per-slot): it is a device/launcher behaviour, not an image property.
    const val KEY_SCROLL_ENABLED = "wallpaper_scroll_enabled"

    // Cap the scrollable wallpaper width at this multiple of the screen width.
    // Keeps texture memory bounded (a 4:3 photo on a tall phone would otherwise
    // be ~3x screen width) while still giving a generous, stock-like pan range.
    private const val MAX_SCROLL_WIDTH_FACTOR = 2.0f

    // Fit modes
    const val MODE_FILL = "FILL"             // Center-crop, fills the screen (default, original behavior)
    const val MODE_FIT = "FIT"               // Whole image visible, bars filled per fill mode
    const val MODE_STRETCH = "STRETCH"       // Distort to fill the screen exactly
    const val MODE_ROTATE_FIT = "ROTATE_FIT" // Rotate 90° on orientation mismatch, then fit

    // Empty-space fill modes (used by FIT / ROTATE_FIT)
    const val FILL_BLACK = "BLACK"
    const val FILL_REPEAT = "REPEAT"
    const val FILL_MIRROR = "MIRROR"

    const val ACTIVE_WALLPAPER_FILE = "wallpaper.jpg"
    const val NEXT_WALLPAPER_FILE = "next_wallpaper.jpg"
    const val ACTIVE_SOURCE_FILE = "wallpaper_src.jpg"
    const val NEXT_SOURCE_FILE = "next_wallpaper_src.jpg"

    private const val PLAYLIST_ORIGINALS_DIR = "playlist_originals"
    private const val MAX_DECODE_DIM = 4096

    // ------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveFitMode(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_FIT, MODE_FILL) ?: MODE_FILL

    fun getActiveFillMode(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_FILL, FILL_BLACK) ?: FILL_BLACK

    fun getNextFitMode(context: Context): String =
        prefs(context).getString(KEY_NEXT_FIT, MODE_FILL) ?: MODE_FILL

    fun getNextFillMode(context: Context): String =
        prefs(context).getString(KEY_NEXT_FILL, FILL_BLACK) ?: FILL_BLACK

    /** Sets the display mode for the active wallpaper (single-image crop, and the first playlist image). */
    fun setActiveModes(context: Context, fitMode: String, fillMode: String) {
        prefs(context).edit()
            .putString(KEY_ACTIVE_FIT, fitMode)
            .putString(KEY_ACTIVE_FILL, fillMode)
            .apply()
    }

    /** Sets the display mode for the queued next wallpaper (the next playlist image to rotate in). */
    fun setNextModes(context: Context, fitMode: String, fillMode: String) {
        prefs(context).edit()
            .putString(KEY_NEXT_FIT, fitMode)
            .putString(KEY_NEXT_FILL, fillMode)
            .apply()
    }

    /**
     * Promotes the queued next mode to the active slot. Mirrors [promoteNextSource]
     * and MUST run before the renderer is handed the next bitmap, so the incoming
     * image is fitted with the correct (now active) mode.
     */
    fun promoteNextMode(context: Context) {
        val p = prefs(context)
        val fit = p.getString(KEY_NEXT_FIT, MODE_FILL) ?: MODE_FILL
        val fill = p.getString(KEY_NEXT_FILL, FILL_BLACK) ?: FILL_BLACK
        p.edit()
            .putString(KEY_ACTIVE_FIT, fit)
            .putString(KEY_ACTIVE_FILL, fill)
            .apply()
    }

    /**
     * Sets the NEXT slot's render mode. With WYSIWYG baking, each playlist image's
     * chosen fit mode is already baked into its wallpaper_N.jpg, so the renderer
     * always displays it as-is (Fill). The per-image mode still lives in
     * metadata.json for restoring the editor's chooser.
     */
    fun stageNextModeFromPlaylist(context: Context, playlistFileName: String) {
        setNextModes(context, MODE_FILL, FILL_BLACK)
    }

    /** Modes other than plain screen-fill want the un-cropped source image. */
    fun needsSourceImage(mode: String): Boolean = mode != MODE_FILL

    // ------------------------------------------------------------------
    // Wallpaper scrolling (home-screen parallax)
    // ------------------------------------------------------------------

    fun isScrollEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCROLL_ENABLED, false)

    fun setScrollEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCROLL_ENABLED, enabled).apply()
    }

    /**
     * A bitmap prepared for the GL renderers together with the fraction of its
     * width that is visible on screen at any one time ([windowX], in (0, 1]).
     *
     * When scrolling is off, or the image is not wider than the screen, the
     * whole width is visible and [windowX] is 1.0 (the renderer then draws
     * exactly as it always did). When scrolling is on and the image overflows
     * horizontally, [windowX] < 1.0 and the renderer pans a screen-width window
     * across the texture in response to launcher offsets.
     */
    class RenderImage(val bitmap: Bitmap, val windowX: Float)

    /**
     * Loads the active wallpaper for rendering. Identical to [loadDisplayBitmap]
     * when scrolling is disabled. When enabled, returns a "fill-height, keep
     * width" bitmap (capped to [MAX_SCROLL_WIDTH_FACTOR]x the screen width) so
     * there is horizontal content to pan across.
     */
    fun loadForRender(
        context: Context,
        surfaceW: Int,
        surfaceH: Int,
        previewSource: (() -> Bitmap?)? = null
    ): RenderImage {
        if (previewSource != null) {
            val source = previewSource()
            if (source != null) {
                return RenderImage(
                    fitBitmap(source, surfaceW, surfaceH, MODE_FILL, FILL_BLACK),
                    1.0f
                )
            }
        }
        if (!isScrollEnabled(context) || surfaceW <= 0 || surfaceH <= 0) {
            return RenderImage(loadDisplayBitmap(context, surfaceW, surfaceH), 1.0f)
        }

        // Scrolling wants the full, un-cropped image so the parts the crop would
        // have trimmed are available to pan to. Fall back to the cropped image
        // (old installs / no source) — it simply will not have any pan slack.
        val filesDir = context.filesDir
        var source: Bitmap? = null
        val srcFile = File(filesDir, ACTIVE_SOURCE_FILE)
        if (srcFile.exists()) source = decodeFileSampled(srcFile, MAX_DECODE_DIM)
        if (source == null) {
            val file = File(filesDir, ACTIVE_WALLPAPER_FILE)
            if (file.exists()) source = BitmapFactory.decodeFile(file.absolutePath)
        }
        if (source == null) {
            val placeholder = Bitmap.createBitmap(surfaceW, surfaceH, Bitmap.Config.ARGB_8888)
            placeholder.eraseColor(Color.BLUE)
            return RenderImage(placeholder, 1.0f)
        }
        return makeScrollBitmap(source, surfaceW, surfaceH)
    }

    /**
     * Scroll-aware variant of [fitToSurface] for queued playlist transitions.
     * Mirrors [loadForRender]: wide image kept wide when scrolling is on,
     * otherwise the legacy screen-fit.
     */
    fun fitForRender(context: Context, source: Bitmap, surfaceW: Int, surfaceH: Int): RenderImage {
        if (!isScrollEnabled(context) || surfaceW <= 0 || surfaceH <= 0) {
            return RenderImage(fitToSurface(context, source, surfaceW, surfaceH), 1.0f)
        }
        return makeScrollBitmap(source, surfaceW, surfaceH)
    }

    /**
     * Produces a bitmap scaled to exactly fill the surface HEIGHT, preserving
     * the source aspect ratio, then horizontally centre-cropped to at most
     * [MAX_SCROLL_WIDTH_FACTOR]x the surface width. Returns it with the visible
     * width fraction. Consumes [source] (recycled if a new bitmap is made).
     *
     * If the height-filled image is not actually wider than the screen (a tall
     * / narrow image), there is nothing to scroll, so it falls back to a normal
     * screen-fill and reports windowX = 1.0.
     */
    private fun makeScrollBitmap(source: Bitmap, surfaceW: Int, surfaceH: Int): RenderImage {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()
        if (sw <= 0f || sh <= 0f) {
            return RenderImage(fitBitmap(source, surfaceW, surfaceH, MODE_FILL, FILL_BLACK), 1.0f)
        }

        // Scale so the image exactly fills the screen height.
        val scale = surfaceH / sh
        val scaledW = sw * scale

        // Not wider than the screen -> no horizontal slack; behave like Fill.
        if (scaledW <= surfaceW + 0.5f) {
            return RenderImage(fitBitmap(source, surfaceW, surfaceH, MODE_FILL, FILL_BLACK), 1.0f)
        }

        // Clamp the canvas width to keep texture memory bounded.
        val maxW = surfaceW * MAX_SCROLL_WIDTH_FACTOR
        val canvasW = min(scaledW, maxW)
        val outW = canvasW.toInt().coerceAtLeast(surfaceW)
        val outH = surfaceH

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Centre the (possibly clamped) image horizontally; top-aligned vertically
        // since it already fills the height exactly.
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((outW - scaledW) / 2f, 0f)
        canvas.drawBitmap(source, matrix, paint)
        source.recycle()

        val windowX = (surfaceW.toFloat() / outW.toFloat()).coerceIn(0.05f, 1.0f)
        return RenderImage(output, windowX)
    }

    // ------------------------------------------------------------------
    // Loading + fitting (used by the renderers)
    // ------------------------------------------------------------------

    /**
     * Loads the active wallpaper and fits it to the given surface size using
     * the user's display settings. Always returns a non-null bitmap (falls
     * back to a solid color if no wallpaper exists yet).
     *
     * If the surface size is not known yet (0 x 0) the image is returned
     * unfitted, exactly as the legacy code did.
     */
    fun loadDisplayBitmap(context: Context, surfaceW: Int, surfaceH: Int): Bitmap {
        val mode = getActiveFitMode(context)
        val fill = getActiveFillMode(context)
        val filesDir = context.filesDir

        var source: Bitmap? = null

        // Modes that show the whole image want the un-cropped source. Fall
        // back to the cropped wallpaper if no source exists (old installs).
        if (needsSourceImage(mode)) {
            val srcFile = File(filesDir, ACTIVE_SOURCE_FILE)
            if (srcFile.exists()) {
                source = decodeFileSampled(srcFile, MAX_DECODE_DIM)
            }
        }

        if (source == null) {
            val file = File(filesDir, ACTIVE_WALLPAPER_FILE)
            if (file.exists()) {
                source = BitmapFactory.decodeFile(file.absolutePath)
            }
        }

        if (source == null) {
            source = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.BLUE)
        }

        return fitBitmap(source, surfaceW, surfaceH, mode, fill)
    }

    /**
     * Fits an already decoded bitmap (e.g. a queued playlist transition) to
     * the surface using the user's current display settings.
     */
    fun fitToSurface(context: Context, source: Bitmap, surfaceW: Int, surfaceH: Int): Bitmap {
        return fitBitmap(source, surfaceW, surfaceH, getActiveFitMode(context), getActiveFillMode(context))
    }

    /**
     * Pure geometry: produces a bitmap of exactly [targetW] x [targetH] from
     * [source] according to [mode] and [fillMode].
     *
     * NOTE: consumes [source] — if a new bitmap is created, the source is
     * recycled. Callers must only use (and recycle) the returned bitmap.
     */
    fun fitBitmap(source: Bitmap, targetW: Int, targetH: Int, mode: String, fillMode: String): Bitmap {
        // Surface size unknown: nothing sensible to do, keep legacy behavior.
        if (targetW <= 0 || targetH <= 0) return source

        // Fast path: the common case on regular phones. The crop already
        // matches the screen exactly, so avoid any re-encode quality loss.
        if (mode == MODE_FILL && source.width == targetW && source.height == targetH) {
            return source
        }

        // ROTATE_FIT: rotate the image 90° if its orientation does not match
        // the screen (e.g. a landscape photo on a portrait screen), then fit.
        var working = source
        var rotatedCopy = false
        if (mode == MODE_ROTATE_FIT) {
            val sourceIsLandscape = source.width > source.height
            val targetIsLandscape = targetW > targetH
            if (sourceIsLandscape != targetIsLandscape) {
                val rotate = Matrix().apply { postRotate(90f) }
                val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotate, true)
                if (rotated != source) {
                    working = rotated
                    rotatedCopy = true
                }
            }
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val bw = working.width.toFloat()
        val bh = working.height.toFloat()
        val tw = targetW.toFloat()
        val th = targetH.toFloat()

        val matrix = Matrix()
        when (mode) {
            MODE_STRETCH -> {
                matrix.setScale(tw / bw, th / bh)
            }
            MODE_FILL -> {
                val scale = max(tw / bw, th / bh)
                matrix.setScale(scale, scale)
                matrix.postTranslate((tw - bw * scale) / 2f, (th - bh * scale) / 2f)
            }
            else -> { // MODE_FIT and MODE_ROTATE_FIT: whole image visible, centered
                val scale = min(tw / bw, th / bh)
                matrix.setScale(scale, scale)
                matrix.postTranslate((tw - bw * scale) / 2f, (th - bh * scale) / 2f)
            }
        }

        val letterboxed = (mode == MODE_FIT || mode == MODE_ROTATE_FIT)
        if (letterboxed && fillMode != FILL_BLACK) {
            // Fill the bars by tiling the image outward from its fitted
            // position. MIRROR gives the "reverse-repeat" pattern.
            val tile = if (fillMode == FILL_MIRROR) Shader.TileMode.MIRROR else Shader.TileMode.REPEAT
            val shader = BitmapShader(working, tile, tile)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawRect(0f, 0f, tw, th, paint)
        } else {
            canvas.drawBitmap(working, matrix, paint)
        }

        if (rotatedCopy) working.recycle()
        if (output != source) source.recycle()
        return output
    }

    // ------------------------------------------------------------------
    // Source-image bookkeeping (used by activities and services)
    // ------------------------------------------------------------------

    /**
     * Saves the un-cropped source of the active wallpaper. Passing null
     * removes any stale source so fit modes fall back to the crop.
     */
    fun saveActiveSource(context: Context, bitmap: Bitmap?) {
        try {
            val file = File(context.filesDir, ACTIVE_SOURCE_FILE)
            if (bitmap == null) {
                if (file.exists()) file.delete()
                return
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteNextSource(filesDir: File) {
        try {
            val file = File(filesDir, NEXT_SOURCE_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stages the un-cropped original belonging to a playlist entry
     * (e.g. "wallpaper_3.jpg" -> playlist_originals/original_3.jpg) as the
     * next-wallpaper source. If no original exists, any stale staged source
     * is removed so the rotation falls back to the cropped image.
     */
    fun stageNextSource(filesDir: File, playlistFileName: String) {
        stageNextSource(filesDir, playlistFileName, PLAYLIST_ORIGINALS_DIR)
    }

    fun stageNextSource(
        filesDir: File,
        playlistFileName: String,
        originalsDirectoryName: String
    ) {
        copyPlaylistOriginalTo(
            filesDir,
            playlistFileName,
            NEXT_SOURCE_FILE,
            originalsDirectoryName
        )
    }

    /** Same as [stageNextSource] but for the active wallpaper source. */
    fun stageActiveSourceFromPlaylist(filesDir: File, playlistFileName: String) {
        stageActiveSourceFromPlaylist(filesDir, playlistFileName, PLAYLIST_ORIGINALS_DIR)
    }

    fun stageActiveSourceFromPlaylist(
        filesDir: File,
        playlistFileName: String,
        originalsDirectoryName: String
    ) {
        copyPlaylistOriginalTo(
            filesDir,
            playlistFileName,
            ACTIVE_SOURCE_FILE,
            originalsDirectoryName
        )
    }

    private fun copyPlaylistOriginalTo(
        filesDir: File,
        playlistFileName: String,
        destName: String,
        originalsDirectoryName: String
    ) {
        try {
            val dest = File(filesDir, destName)
            val original = findPlaylistOriginal(filesDir, playlistFileName, originalsDirectoryName)
            if (original != null) {
                original.copyTo(dest, overwrite = true)
            } else if (dest.exists()) {
                dest.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findPlaylistOriginal(
        filesDir: File,
        playlistFileName: String,
        originalsDirectoryName: String
    ): File? {
        // Playlist crops are named "wallpaper_<n>.jpg", originals "original_<n>.jpg"
        val index = playlistFileName
            .removePrefix("wallpaper_")
            .removeSuffix(".jpg")
            .toIntOrNull() ?: return null
        val file = File(File(filesDir, originalsDirectoryName), "original_$index.jpg")
        return if (file.exists()) file else null
    }

    /**
     * Promotes the staged next-wallpaper source to the active source. Called
     * by the rotation logic right after next_wallpaper.jpg is renamed to
     * wallpaper.jpg so both files stay in sync.
     */
    fun promoteNextSource(filesDir: File) {
        try {
            val next = File(filesDir, NEXT_SOURCE_FILE)
            val active = File(filesDir, ACTIVE_SOURCE_FILE)
            if (active.exists()) active.delete()
            if (next.exists()) next.renameTo(active)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Decodes the queued next wallpaper for a playlist transition, choosing
     * the un-cropped source when the current fit mode wants it. Used by the
     * wallpaper services in place of a plain decode of next_wallpaper.jpg.
     */
    fun decodeNextForDisplay(context: Context): Bitmap? {
        val filesDir = context.filesDir
        // Scrolling needs the full-width source so the queued image has pan slack.
        if (isScrollEnabled(context) || needsSourceImage(getNextFitMode(context))) {
            val srcFile = File(filesDir, NEXT_SOURCE_FILE)
            if (srcFile.exists()) {
                val bitmap = decodeFileSampled(srcFile, MAX_DECODE_DIM)
                if (bitmap != null) return bitmap
            }
        }
        val nextFile = File(filesDir, NEXT_WALLPAPER_FILE)
        if (!nextFile.exists()) return null
        return BitmapFactory.decodeFile(nextFile.absolutePath)
    }

    // ------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------

    /**
     * Memory-safe decode of a file: downsamples to [maxDim] and applies EXIF
     * rotation. Playlist originals are raw copies of the picked images, so
     * they can be huge and carry EXIF orientation.
     */
    fun decodeFileSampled(file: File, maxDim: Int = MAX_DECODE_DIM): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, maxDim)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            return applyExifRotation(file, raw)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, maxDim: Int): Int {
        val largest = max(options.outWidth, options.outHeight)
        var inSampleSize = 1
        if (largest > maxDim) {
            val factor = largest.toFloat() / maxDim.toFloat()
            while (inSampleSize < factor) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) return bitmap

            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            return rotated
        } catch (e: Exception) {
            return bitmap
        }
    }
}
