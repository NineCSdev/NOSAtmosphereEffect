package com.app.nosatmosphereeffect.ui.components

enum class AtmoIconMotion {
    PRESS,
    SPIN,
    TILT,
    BACK
}

data class AtmoIconTransform(
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val translationXDp: Float = 0f
)

object AtmoIconMotionMath {
    private val Identity = AtmoIconTransform()

    fun pressTransform(
        motion: AtmoIconMotion,
        pressed: Boolean,
        motionEnabled: Boolean
    ): AtmoIconTransform {
        if (!pressed || !motionEnabled) return Identity

        return when (motion) {
            AtmoIconMotion.PRESS -> AtmoIconTransform(scale = 0.9f)
            AtmoIconMotion.SPIN -> AtmoIconTransform(
                scale = 0.9f,
                rotationDegrees = 22.5f
            )
            AtmoIconMotion.TILT -> AtmoIconTransform(
                scale = 0.92f,
                rotationDegrees = -10f
            )
            AtmoIconMotion.BACK -> AtmoIconTransform(
                scale = 0.86f,
                translationXDp = -3.5f
            )
        }
    }

    fun clickRotationDegrees(
        motion: AtmoIconMotion,
        motionEnabled: Boolean
    ): Float = if (motionEnabled && motion == AtmoIconMotion.SPIN) 360f else 0f
}
