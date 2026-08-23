package com.app.nosatmosphereeffect.helper

internal object PlaylistFilePolicy {
    private val imageName = Regex("""^wallpaper_(\d+)\.jpg$""")

    fun index(fileName: String): Int? {
        return imageName.matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }
}
