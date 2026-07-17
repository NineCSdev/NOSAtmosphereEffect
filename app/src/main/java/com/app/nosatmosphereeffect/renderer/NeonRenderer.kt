package com.app.nosatmosphereeffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.app.nosatmosphereeffect.helper.WallpaperFitHelper
import com.app.nosatmosphereeffect.helper.WallpaperScrollRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Neon Blueprint. The lock screen is pitch black except for the outlines of the
 * wallpaper, drawn as glowing neon tubes; on unlock the tubes pulse and colour
 * bleeds out of them into the black until the untouched wallpaper is back.
 * [isReverse] swaps which end of that animation is the lock screen.
 *
 * The bleed needs to know, for every pixel, how far it sits from the nearest
 * outline - otherwise "bleed outward from the lines" degenerates into a
 * cross-fade. Computing that per frame is hopeless, so it is baked once per
 * wallpaper into a small distance field (see [buildField] and the neon_edt
 * shader) and the animation is just a threshold sweeping across it. Sampling
 * that field is also what gives the tubes their halo for free, with an exact
 * exponential falloff instead of a blur pass.
 */
class NeonRenderer(
    private val context: Context,
    private val isReverse: Boolean = false
) : GLSurfaceView.Renderer, WallpaperScrollRenderer {

    private companion object {
        // The field runs at 1/8 of the wallpaper. A distance field is smooth and
        // ~1-Lipschitz, so bilinear upsampling costs nothing the eye can catch,
        // while the two O(radius) sweeps that build it get 64x cheaper.
        const val FIELD_DIV = 8

        // Sweep window for the line distance, in wallpaper texels. Only the tube
        // half-width plus its antialiasing is ever read back, so a handful of
        // texels is plenty and the two full-res sweeps stay cheap.
        const val LINE_MAX_DIST = 6.0f

        // How far certainty is allowed to walk along a contour. Three passes
        // carry a strong crest across a 3-texel dropout, which covers the dips
        // that break outlines in practice.
        const val HYST_PASSES = 3

        // Crests below uThreshold * this are discarded outright; between the two
        // they have to prove themselves by joining a strong one.
        const val WEAK_RATIO = 0.4f

        // Sweep window, in field texels: how far from an outline the field can
        // still measure. 128 * 8 is a bit over 1000 screen pixels; past that the
        // field saturates and the shader's noise dissolves the remainder.
        const val FIELD_MAX_DIST = 128.0f
    }

    // --- Wallpaper scrolling (home-screen parallax) ---
    @Volatile private var scrollOffsetX: Float = 0.5f
    private var currentWindowX: Float = 1f   // visible width fraction of current texture
    private var nextWindowX: Float = 1f      // ...of the queued (transition) texture

    override fun setWallpaperOffset(xOffset: Float) {
        scrollOffsetX = xOffset.coerceIn(0f, 1f)
    }
    // ---------------------------------------------------

    // --- RAM Optimized Ring Buffer Logic ---
    private class TextureSet {
        var sharpId = 0
        var fieldId = 0
        var width = 0
        var height = 0
        var fieldWidth = 0
        var fieldHeight = 0
        var lineId = 0         // full-res R8: texels to the nearest outline, over LINE_MAX_DIST
        var rankId = 0         // 256x1 R8: distance -> fraction of screen nearer than it
        fun isValid() = sharpId != 0 && fieldId != 0 && lineId != 0 && rankId != 0
        fun reset() {
            sharpId = 0; fieldId = 0; lineId = 0; rankId = 0; width = 0; height = 0
            fieldWidth = 0; fieldHeight = 0
        }
    }

    private var currentSet = TextureSet()
    private var nextSet = TextureSet()

    @Volatile private var pendingPlaylistBitmap: Bitmap? = null

    var blurStrength: Float = 0.0f
    @Volatile var dimLevel: Float = 0.0f
    @Volatile private var needsReload: Boolean = false
    @Volatile private var needsFieldRebuild: Boolean = false

    // --- User settings (Fine Tuning) ---
    @Volatile var lineWidth: Float = 1.5f       // Sobel tap spread, in screen pixels
    @Volatile var sensitivity: Float = 0.5f     // 0 = only hard outlines, 1 = every scrap of detail
    @Volatile var glowRadius: Float = 26.0f     // halo reach, in screen pixels

    private var programId: Int = 0
    private var edgeProgramId: Int = 0
    private var hystProgramId: Int = 0
    private var seedProgramId: Int = 0
    private var edtProgramId: Int = 0
    private var fboId: Int = 0
    private var aspectRatio: Float = 1.0f

    // Scratch targets for the two field passes, shared by both texture sets.
    private var seedTexId: Int = 0
    private var rowTexId: Int = 0
    private var scratchWidth: Int = 0
    private var scratchHeight: Int = 0

    // Ping-pong for the full-resolution line passes. Freed the moment the bake
    // ends: these are wallpaper-sized, and a live wallpaper that sits on avoidable
    // megabytes between bakes is a live wallpaper that gets killed behind a game.
    private var edgeAId: Int = 0
    private var edgeBId: Int = 0

    // --- DISPLAY FIT: actual surface size + the size the textures were fitted for ---
    @Volatile private var surfaceWidth: Int = 0
    @Volatile private var surfaceHeight: Int = 0
    private var fittedForWidth: Int = -1
    private var fittedForHeight: Int = -1
    // --------------------------------------------------------------------------------

    private val vertices = floatArrayOf(
        -1f, -1f,  0f, 1f,
        1f, -1f,  1f, 1f,
        -1f,  1f,  0f, 0f,
        1f,  1f,  1f, 0f
    )
    private lateinit var vertexBuffer: FloatBuffer

    fun queuePlaylistTransition(bitmap: Bitmap) {
        pendingPlaylistBitmap = bitmap
    }

    fun reloadTexture() {
        needsReload = true
    }

    /**
     * Line sensitivity feeds the baked field, not just the on-screen Sobel, so a
     * settings change has to re-bake it. Cheap - a few ms of GPU work on a 1/8
     * scale target, and no bitmap decode - so the service fires it blind on any
     * config update rather than tracking which slider moved.
     */
    fun rebuildField() {
        needsFieldRebuild = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        val vertexCode = loadShaderFromAssets("shaders/neon/neon.vert")
        // One fragment shader for both directions: uReverse decides which end of
        // the animation is the lock screen. The two directions are the same 150
        // lines of edge detection, field sampling and noise, so a second copy
        // would only be a copy to keep in sync.
        programId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon.frag"))
        edgeProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_edges.frag"))
        hystProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_hyst.frag"))
        seedProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_seed.frag"))
        edtProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_edt.frag"))

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        fboId = fbo[0]

        // GL context is fresh: any previously held texture handles are invalid.
        currentSet.reset()
        nextSet.reset()
        seedTexId = 0
        rowTexId = 0
        scratchWidth = 0
        scratchHeight = 0
        needsReload = true
    }

    private fun loadAndApplyTextures() {
        // Not isValid(): a set that failed halfway through a previous build still
        // holds a sharp texture worth reclaiming.
        if (currentSet.sharpId != 0) GLES30.glDeleteTextures(1, intArrayOf(currentSet.sharpId), 0)
        if (currentSet.fieldId != 0) GLES30.glDeleteTextures(1, intArrayOf(currentSet.fieldId), 0)
        if (currentSet.lineId != 0) GLES30.glDeleteTextures(1, intArrayOf(currentSet.lineId), 0)
        if (currentSet.rankId != 0) GLES30.glDeleteTextures(1, intArrayOf(currentSet.rankId), 0)
        currentSet.reset()
        fittedForWidth = surfaceWidth
        fittedForHeight = surfaceHeight
        val render = WallpaperFitHelper.loadForRender(context, surfaceWidth, surfaceHeight)
        val sharpBitmap = render.bitmap
        currentWindowX = render.windowX

        currentSet.width = sharpBitmap.width
        currentSet.height = sharpBitmap.height

        currentSet.sharpId = uploadTexture(sharpBitmap)
        sharpBitmap.recycle()

        buildField(currentSet)
        needsFieldRebuild = false
    }

    private fun processPlaylistTransition() {
        val raw = pendingPlaylistBitmap ?: return
        // Fit the incoming image to the current surface (display settings + foldables + scroll)
        val render = WallpaperFitHelper.fitForRender(context, raw, surfaceWidth, surfaceHeight)
        val bitmap = render.bitmap
        nextWindowX = render.windowX
        fittedForWidth = surfaceWidth
        fittedForHeight = surfaceHeight

        // RAM FIX & PIXEL BUG FIX: Overwrite the existing nextSet.sharpId using texSubImage2D
        nextSet.sharpId = uploadTexture(bitmap, nextSet.sharpId, nextSet.width, nextSet.height)

        nextSet.width = bitmap.width
        nextSet.height = bitmap.height

        bitmap.recycle()

        buildField(nextSet)

        // SWAP! Old current becomes next
        val temp = currentSet
        currentSet = nextSet
        nextSet = temp
        val tmpWin = currentWindowX
        currentWindowX = nextWindowX
        nextWindowX = tmpWin
        pendingPlaylistBitmap = null
    }

    /**
     * Bakes everything about [set] that only depends on its pixels, which is
     * everything except the animation: where the outlines are, how wide they are,
     * how far each pixel sits from one, and how those distances are distributed.
     *
     * All of it is baked because none of it can change between frames, and the
     * gap in what that affords is enormous - the line finding here would be
     * absurd at 60fps (see neon_edges.frag), while the frames it feeds cost a tap
     * each. The old build did a coarse Sobel here and a second, different Sobel
     * per-frame on screen, so the glow only ever approximately agreed with the
     * lines it was supposedly glowing from. One detector now feeds both.
     *
     * Full resolution: outline crests -> hysteresis -> distance to the nearest
     * outline. Then 1/8 scale: which blocks hold a line -> distance to the nearest
     * one -> the rank lookup built off a readback of that.
     */
    private fun buildField(set: TextureSet) {
        if (set.sharpId == 0 || set.width <= 0 || set.height <= 0) return

        val w = set.width
        val h = set.height
        val fw = max(1, w / FIELD_DIV)
        val fh = max(1, h / FIELD_DIV)

        // Create/resize every target first: these calls rebind GL_TEXTURE_2D, so
        // doing it mid-pass would pull the input out from under the draw.
        // R8, not RGBA: these are wallpaper-sized and every one of them holds a
        // single number.
        edgeAId = createEmptyTexture(w, h, GLES30.GL_NEAREST, edgeAId, 0, 0, GLES30.GL_R8, GLES30.GL_RED)
        edgeBId = createEmptyTexture(w, h, GLES30.GL_NEAREST, edgeBId, 0, 0, GLES30.GL_R8, GLES30.GL_RED)
        set.lineId = createEmptyTexture(
            w, h, GLES30.GL_LINEAR, set.lineId, set.width, set.height, GLES30.GL_R8, GLES30.GL_RED
        )
        seedTexId = createEmptyTexture(fw, fh, GLES30.GL_NEAREST, seedTexId, scratchWidth, scratchHeight)
        rowTexId = createEmptyTexture(fw, fh, GLES30.GL_NEAREST, rowTexId, scratchWidth, scratchHeight)
        scratchWidth = fw
        scratchHeight = fh
        // The field stays RGBA8 so glReadPixels can take the guaranteed
        // RGBA/UNSIGNED_BYTE path off it. It is 1/8 scale, so the three wasted
        // channels are a few hundred KB and buying them avoids having to query
        // the implementation's preferred read format.
        set.fieldId = createEmptyTexture(fw, fh, GLES30.GL_LINEAR, set.fieldId, set.fieldWidth, set.fieldHeight)
        set.fieldWidth = fw
        set.fieldHeight = fh

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        // --- Pass 1: outline crests, at full resolution ---------------------
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(edgeProgramId)
        attach(edgeAId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, set.sharpId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(edgeProgramId, "uTextureSharp"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(edgeProgramId, "uStep"), 1f / w, 1f / h)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edgeProgramId, "uLod"), edgeLod())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edgeProgramId, "uThreshold"), edgeThreshold())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edgeProgramId, "uWeakRatio"), WEAK_RATIO)
        drawQuad(edgeProgramId)

        // --- Pass 2: hysteresis, ping-ponging ------------------------------
        GLES30.glUseProgram(hystProgramId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(hystProgramId, "uTexture"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(hystProgramId, "uStep"), 1f / w, 1f / h)
        var src = edgeAId
        var dst = edgeBId
        for (i in 0 until HYST_PASSES) {
            attach(dst)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src)
            // The last pass drops the unproven and re-encodes as EDT seeds.
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(hystProgramId, "uFinal"),
                if (i == HYST_PASSES - 1) 1f else 0f
            )
            drawQuad(hystProgramId)
            val t = src; src = dst; dst = t
        }
        // src now holds the seeds; dst is free to sweep into.

        // --- Pass 3: how far is the nearest outline, in texels --------------
        GLES30.glUseProgram(edtProgramId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(edtProgramId, "uTexture"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uMaxDist"), LINE_MAX_DIST)
        attach(dst)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(edtProgramId, "uStep"), 1f / w, 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uRadius"), LINE_MAX_DIST)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uPass"), 0f)
        drawQuad(edtProgramId)

        attach(set.lineId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dst)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(edtProgramId, "uStep"), 0f, 1f / h)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uRadius"), LINE_MAX_DIST)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uPass"), 1f)
        drawQuad(edtProgramId)

        // --- Pass 4: which 1/8 blocks hold a line ---------------------------
        GLES30.glViewport(0, 0, fw, fh)
        GLES30.glUseProgram(seedProgramId)
        attach(seedTexId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, set.lineId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(seedProgramId, "uLineDist"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(seedProgramId, "uSrcStep"), 1f / w, 1f / h)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(seedProgramId, "uMaxDist"), LINE_MAX_DIST)
        drawQuad(seedProgramId)

        // --- Pass 5: the glow field, sweep 1 - nearest seed along each row ---
        GLES30.glUseProgram(edtProgramId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(edtProgramId, "uTexture"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uMaxDist"), FIELD_MAX_DIST)
        attach(rowTexId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, seedTexId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(edtProgramId, "uStep"), 1f / fw, 0f)
        // Nothing past the target's own width can be found, so do not sweep for it.
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uRadius"), FIELD_MAX_DIST.coerceAtMost(fw.toFloat()))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uPass"), 0f)
        drawQuad(edtProgramId)

        // --- Pass 6: sweep 2 - fold the rows into a true 2D distance ---------
        attach(set.fieldId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rowTexId)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(edtProgramId, "uStep"), 0f, 1f / fh)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uRadius"), FIELD_MAX_DIST.coerceAtMost(fh.toFloat()))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(edtProgramId, "uPass"), 1f)
        drawQuad(edtProgramId)

        // The field is still attached, so read it back before letting go of it.
        if (set.rankId != 0) GLES30.glDeleteTextures(1, intArrayOf(set.rankId), 0)
        set.rankId = buildRankLut(fw, fh)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // Wallpaper-sized scratch, and the next bake is a wallpaper change away.
        GLES30.glDeleteTextures(2, intArrayOf(edgeAId, edgeBId), 0)
        edgeAId = 0
        edgeBId = 0
    }

    private fun attach(texId: Int) {
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texId, 0
        )
    }

    /**
     * Turn the field we just drew into a lookup that answers "what fraction of the
     * screen is nearer to an outline than this?".
     *
     * The bleed is a threshold sweeping over the field, so the share of screen
     * filled at any moment is the field's cumulative distribution - and that curve
     * is wildly image-dependent. FIELD_MAX_DIST is a worst-case ceiling no photo
     * reaches, and rescaling by the image's own maximum only fixes the axis, not
     * the shape: distances pile up near zero, so a front at constant speed does
     * most of its visible work in the first third and then coasts on a screen that
     * already looks finished. Content decides the pacing, which is exactly wrong.
     *
     * Feeding distance through its own CDF instead turns the field into a rank,
     * and sweeping a front over ranks fills area at a constant rate on any
     * wallpaper by construction - no per-image tuning, no curve that only suits
     * the photo it was fitted to.
     *
     * Built piecewise-linear through the occupied levels rather than as a plain
     * cumsum: the field is bilinearly upsampled on the way to the screen, so real
     * distances land continuously between the stored levels, and a step function
     * would stall the front on a plateau and then jump it.
     *
     * This stalls the GL thread on a readback, but it is a few hundred KB once per
     * wallpaper load, next to a JPEG decode and a full texture upload.
     */
    private fun buildRankLut(fw: Int, fh: Int): Int {
        val total = fw * fh * 4
        val buf = ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, fw, fh, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)

        val bytes = ByteArray(total)
        buf.rewind()
        buf.get(bytes)

        val hist = IntArray(256)
        var i = 0
        while (i < total) {                       // distance lives in R
            hist[bytes[i].toInt() and 0xFF]++
            i += 4
        }

        val n = fw * fh
        val lut = ByteArray(256)
        if (n <= 0) {
            for (b in 0..255) lut[b] = b.toByte()          // identity; nothing to rank
        } else {
            // Anchor points: the centre of each occupied level's mass, plus the two
            // ends, then straight lines between them.
            var prevLevel = 0
            var prevRank = 0f
            var cum = 0
            var b = 0
            while (b < 256) {
                val c = hist[b]
                if (c == 0) { b++; continue }
                val rank = (cum + c * 0.5f) / n
                fillRamp(lut, prevLevel, prevRank, b, rank)
                cum += c
                prevLevel = b
                prevRank = rank
                b++
            }
            fillRamp(lut, prevLevel, prevRank, 255, 1f)
        }

        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        if (t[0] == 0) return 0
        val lutBuf = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder())
        lutBuf.put(lut); lutBuf.rewind()

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, 256, 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, lutBuf
        )
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    /** Straight line from (x0,y0) to (x1,y1), written into lut[x0..x1]. */
    private fun fillRamp(lut: ByteArray, x0: Int, y0: Float, x1: Int, y1: Float) {
        if (x1 < x0) return
        val span = (x1 - x0).coerceAtLeast(1)
        for (x in x0..x1) {
            val v = y0 + (y1 - y0) * ((x - x0).toFloat() / span)
            lut[x] = Math.round(v.coerceIn(0f, 1f) * 255f).toByte()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
        surfaceWidth = width
        surfaceHeight = height
        // The surface size changed (fold/unfold, rotation, different display):
        // re-fit the wallpaper so it is not stretched to the new dimensions.
        if (width != fittedForWidth || height != fittedForHeight) {
            needsReload = true
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (pendingPlaylistBitmap != null) processPlaylistTransition()
        if (needsReload) {
            needsReload = false
            loadAndApplyTextures()
        }
        if (needsFieldRebuild) {
            needsFieldRebuild = false
            buildField(currentSet)
        }

        // The field passes render to a 1/8 scale target; restore the viewport.
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }

        if (!currentSet.isValid()) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(programId)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uAspectRatio"), aspectRatio)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uBlurStrength"), blurStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uDimLevel"), dimLevel)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uReverse"), if (isReverse) 1.0f else 0.0f)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uLineWidth"), lineWidth.coerceAtLeast(0.25f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uLineMax"), LINE_MAX_DIST)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uGlowFalloff"), glowFalloff(currentSet))

        // Horizontal scroll window (identity 0f/1f = no scroll, draws as before).
        // The off-screen field passes never set these, so they default to identity.
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uScrollOffsetX"), scrollOffsetX)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uScrollWindowX"), currentWindowX)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.sharpId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTextureSharp"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.fieldId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uFieldTex"), 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.rankId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uRankTex"), 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.lineId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uLineTex"), 3)

        drawQuad(programId)
    }

    /**
     * Low threshold = more crests survive, so the slider reads as "sensitivity"
     * while the shader wants a cutoff.
     *
     * There used to be two of these - a loose one for the on-screen Sobel and a
     * tight one for the field, because seeding the bleed off every last scrap of
     * detail collapsed it into an everywhere-at-once fade. Ranking the field (see
     * buildRankLut) removed that failure outright: a dense field now fills at the
     * same even rate as a sparse one. So the lines and the glow can finally come
     * from one detector at one setting, and agree.
     */
    private fun edgeThreshold(): Float {
        val s = sensitivity.coerceIn(0f, 1f)
        return 0.30f + (0.03f - 0.30f) * s
    }

    /**
     * Pre-blur for the outline pass, in mip levels. Sensitivity asks for
     * structure at one end and every last detail at the other, and that is as
     * much a question of what to look at as of where to cut off.
     */
    private fun edgeLod(): Float {
        val s = sensitivity.coerceIn(0f, 1f)
        return 1.0f - s
    }

    /**
     * Turns a halo radius in screen pixels into the exp() falloff the shader
     * applies to the normalised field: a pixel [glowRadius] from an outline
     * should come out at 1/e.
     */
    private fun glowFalloff(set: TextureSet): Float {
        val texelsPerFieldTexel =
            if (set.fieldWidth > 0) set.width.toFloat() / set.fieldWidth.toFloat() else FIELD_DIV.toFloat()
        val maxDistInTexels = FIELD_MAX_DIST * texelsPerFieldTexel
        return maxDistInTexels / glowRadius.coerceAtLeast(1f)
    }

    private fun drawQuad(program: Int) {
        val aPosLoc = GLES30.glGetAttribLocation(program, "aPosition")
        val aTexLoc = GLES30.glGetAttribLocation(program, "aTexCoord")

        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES30.glEnableVertexAttribArray(aPosLoc)

        vertexBuffer.position(2)
        GLES30.glVertexAttribPointer(aTexLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)
        GLES30.glEnableVertexAttribArray(aTexLoc)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPosLoc)
        GLES30.glDisableVertexAttribArray(aTexLoc)
    }

    private fun createEmptyTexture(
        width: Int,
        height: Int,
        filter: Int,
        existingTextureId: Int = 0,
        existingWidth: Int = 0,
        existingHeight: Int = 0,
        internalFormat: Int = GLES30.GL_RGBA,
        format: Int = GLES30.GL_RGBA
    ): Int {
        val t = if (existingTextureId != 0) intArrayOf(existingTextureId) else {
            val arr = IntArray(1); GLES30.glGenTextures(1, arr, 0); arr
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Only reallocate when it is a new ID or the dimensions have changed.
        if (existingTextureId == 0 || existingWidth != width || existingHeight != height) {
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                format, GLES30.GL_UNSIGNED_BYTE, null
            )
        }
        return t[0]
    }

    private fun uploadTexture(bitmap: Bitmap, existingTextureId: Int = 0, existingWidth: Int = 0, existingHeight: Int = 0): Int {
        val textureHandle = if (existingTextureId != 0) intArrayOf(existingTextureId) else {
            val arr = IntArray(1); GLES30.glGenTextures(1, arr, 0); arr
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)

        // The outline pass pre-blurs through a fractional mip (see edgeLod), so the
        // chain is not optional.
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        return textureHandle[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun loadShaderFromAssets(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
