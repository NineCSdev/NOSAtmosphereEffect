package com.app.nosatmosphereeffect.renderer.vulkan

internal data class VulkanApiVersion(
    val encoded: Int,
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    override fun toString(): String {
        return if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
    }

    companion object {
        private const val VARIANT_SHIFT = 29
        private const val MAJOR_SHIFT = 22
        private const val MINOR_SHIFT = 12
        private const val VARIANT_MASK = 0x7
        private const val MAJOR_MASK = 0x7f
        private const val MINOR_MASK = 0x3ff
        private const val PATCH_MASK = 0xfff

        fun fromEncoded(encoded: Int): VulkanApiVersion? {
            if (encoded <= 0) return null
            val variant = (encoded ushr VARIANT_SHIFT) and VARIANT_MASK
            val major = (encoded ushr MAJOR_SHIFT) and MAJOR_MASK
            val minor = (encoded ushr MINOR_SHIFT) and MINOR_MASK
            val patch = encoded and PATCH_MASK
            if (variant != 0 || major != 1 || minor !in 1..4) return null
            return VulkanApiVersion(
                encoded = encoded,
                major = major,
                minor = minor,
                patch = patch
            )
        }
    }
}
