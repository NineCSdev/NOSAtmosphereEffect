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

/**
 * Canvas-style sketch transition. The stylised state is an OLED-black canvas
 * with thin line art extracted from the wallpaper; the image state is the fitted
 * wallpaper itself. [isReverse] swaps which state belongs to the lock screen.
 *
 * The expensive outline detection is baked once per wallpaper load. Frames then
 * only sample the wallpaper plus a single outline-distance texture, which keeps
 * the lockscreen-to-homescreen transition light enough for a live wallpaper.
 */
class NeonRenderer(
    private val context: Context,
    private val isReverse: Boolean = false
) : GLSurfaceView.Renderer, WallpaperScrollRenderer {

    private companion object {
        // Sweep window for the line distance, in wallpaper texels. The shader
        // only needs enough range to draw/antialias the ink strokes.
        const val LINE_MAX_DIST = 6.0f

        // How far certainty is allowed to walk along a contour. Three passes
        // carry a strong crest across a 3-texel dropout, which covers most
        // broken outlines without promoting isolated texture specks.
        const val HYST_PASSES = 3

        // Crests below uThreshold * this are discarded outright; between the two
        // they have to prove themselves by joining a strong one.
        const val WEAK_RATIO = 0.4f
    }

    // --- Wallpaper scrolling (home-screen parallax) ---
    @Volatile private var scrollOffsetX: Float = 0.5f
    private var currentWindowX: Float = 1f
    private var nextWindowX: Float = 1f

    override fun setWallpaperOffset(xOffset: Float) {
        scrollOffsetX = xOffset.coerceIn(0f, 1f)
    }
    // ---------------------------------------------------

    private class TextureSet {
        var sharpId = 0
        var lineId = 0
        var width = 0
        var height = 0

        fun isValid() = sharpId != 0 && lineId != 0

        fun reset() {
            sharpId = 0
            lineId = 0
            width = 0
            height = 0
        }
    }

    private var currentSet = TextureSet()
    private var nextSet = TextureSet()

    @Volatile private var pendingPlaylistBitmap: Bitmap? = null

    var blurStrength: Float = 0.0f
    @Volatile var dimLevel: Float = 0.0f
    @Volatile private var needsReload: Boolean = false
    @Volatile private var needsSketchRebuild: Boolean = false

    // --- User settings (Fine Tuning) ---
    @Volatile var lineWidth: Float = 1.5f
    @Volatile var sensitivity: Float = 0.5f

    private var programId: Int = 0
    private var edgeProgramId: Int = 0
    private var hystProgramId: Int = 0
    private var edtProgramId: Int = 0
    private var fboId: Int = 0
    private var aspectRatio: Float = 1.0f

    // Ping-pong for the full-resolution line passes. Freed once each bake ends.
    private var edgeAId: Int = 0
    private var edgeBId: Int = 0

    @Volatile private var surfaceWidth: Int = 0
    @Volatile private var surfaceHeight: Int = 0
    private var fittedForWidth: Int = -1
    private var fittedForHeight: Int = -1

    private val vertices = floatArrayOf(
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f,
        -1f, 1f, 0f, 0f,
        1f, 1f, 1f, 0f
    )
    private lateinit var vertexBuffer: FloatBuffer

    fun queuePlaylistTransition(bitmap: Bitmap) {
        pendingPlaylistBitmap = bitmap
    }

    fun reloadTexture() {
        needsReload = true
    }

    fun rebuildSketch() {
        needsSketchRebuild = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        val vertexCode = loadShaderFromAssets("shaders/neon/neon.vert")
        programId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon.frag"))
        edgeProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_edges.frag"))
        hystProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_hyst.frag"))
        edtProgramId = createProgram(vertexCode, loadShaderFromAssets("shaders/neon/neon_edt.frag"))

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        fboId = fbo[0]

        currentSet.reset()
        nextSet.reset()
        needsReload = true
    }

    private fun loadAndApplyTextures() {
        deleteTexture(currentSet.sharpId)
        deleteTexture(currentSet.lineId)
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

        buildSketch(currentSet)
        needsSketchRebuild = false
    }

    private fun processPlaylistTransition() {
        val raw = pendingPlaylistBitmap ?: return
        val render = WallpaperFitHelper.fitForRender(context, raw, surfaceWidth, surfaceHeight)
        val bitmap = render.bitmap
        nextWindowX = render.windowX
        fittedForWidth = surfaceWidth
        fittedForHeight = surfaceHeight

        nextSet.sharpId = uploadTexture(bitmap, nextSet.sharpId)
        nextSet.width = bitmap.width
        nextSet.height = bitmap.height
        bitmap.recycle()

        buildSketch(nextSet)

        val temp = currentSet
        currentSet = nextSet
        nextSet = temp

        val tmpWin = currentWindowX
        currentWindowX = nextWindowX
        nextWindowX = tmpWin

        pendingPlaylistBitmap = null
    }

    /**
     * Bakes a clean outline map for the Canvas sketch. The first pass finds ridge
     * crests, the hysteresis passes keep connected lines and drop stray texture,
     * then the two EDT passes turn those pixels into a short line-distance map.
     */
    private fun buildSketch(set: TextureSet) {
        if (set.sharpId == 0 || set.width <= 0 || set.height <= 0) return

        val w = set.width
        val h = set.height

        edgeAId = createEmptyTexture(w, h, GLES30.GL_NEAREST, edgeAId, 0, 0, GLES30.GL_R8, GLES30.GL_RED)
        edgeBId = createEmptyTexture(w, h, GLES30.GL_NEAREST, edgeBId, 0, 0, GLES30.GL_R8, GLES30.GL_RED)
        set.lineId = createEmptyTexture(
            w, h, GLES30.GL_LINEAR, set.lineId, set.width, set.height, GLES30.GL_R8, GLES30.GL_RED
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

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

        GLES30.glUseProgram(hystProgramId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(hystProgramId, "uTexture"), 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(hystProgramId, "uStep"), 1f / w, 1f / h)

        var src = edgeAId
        var dst = edgeBId
        for (i in 0 until HYST_PASSES) {
            attach(dst)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src)
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(hystProgramId, "uFinal"),
                if (i == HYST_PASSES - 1) 1f else 0f
            )
            drawQuad(hystProgramId)
            val t = src
            src = dst
            dst = t
        }

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

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        GLES30.glDeleteTextures(2, intArrayOf(edgeAId, edgeBId), 0)
        edgeAId = 0
        edgeBId = 0
    }

    private fun attach(texId: Int) {
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texId, 0
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
        surfaceWidth = width
        surfaceHeight = height
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
        if (needsSketchRebuild) {
            needsSketchRebuild = false
            buildSketch(currentSet)
        }

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

        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uScrollOffsetX"), scrollOffsetX)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uScrollWindowX"), currentWindowX)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.sharpId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTextureSharp"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentSet.lineId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uLineTex"), 1)

        drawQuad(programId)
    }

    private fun edgeThreshold(): Float {
        val s = sensitivity.coerceIn(0f, 1f)
        return 0.30f + (0.03f - 0.30f) * s
    }

    private fun edgeLod(): Float {
        val s = sensitivity.coerceIn(0f, 1f)
        return 1.0f - s
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
            val arr = IntArray(1)
            GLES30.glGenTextures(1, arr, 0)
            arr
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        if (existingTextureId == 0 || existingWidth != width || existingHeight != height) {
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                format, GLES30.GL_UNSIGNED_BYTE, null
            )
        }
        return t[0]
    }

    private fun uploadTexture(bitmap: Bitmap, existingTextureId: Int = 0): Int {
        val textureHandle = if (existingTextureId != 0) intArrayOf(existingTextureId) else {
            val arr = IntArray(1)
            GLES30.glGenTextures(1, arr, 0)
            arr
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        return textureHandle[0]
    }

    private fun deleteTexture(textureId: Int) {
        if (textureId != 0) GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
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
