package com.app.nosatmosphereeffect.helper

import android.content.Context
import java.io.Closeable

object SubjectModelBuild {
    val delivery = SubjectModelDelivery.BUNDLED_FOSS
}

/** The F-Droid model ships in the APK, so it is always ready without a download. */
class SubjectModelManager(@Suppress("UNUSED_PARAMETER") context: Context) : Closeable {

    fun checkAvailability(onState: (SubjectModelState) -> Unit) {
        onState(SubjectModelState(SubjectModelPhase.READY, 100))
    }

    fun download(onState: (SubjectModelState) -> Unit) {
        onState(SubjectModelState(SubjectModelPhase.READY, 100))
    }

    override fun close() = Unit
}
