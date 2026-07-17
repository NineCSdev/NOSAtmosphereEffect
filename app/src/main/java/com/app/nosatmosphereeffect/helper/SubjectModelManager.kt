package com.app.nosatmosphereeffect.helper

import android.content.Context
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.Closeable

object CanvasSubjectSettings {
    const val ENABLED_KEY = "canvas_subject_segmentation"
    const val MODEL_READY_KEY = "canvas_subject_model_downloaded"
}

enum class SubjectModelPhase {
    CHECKING,
    NOT_DOWNLOADED,
    DOWNLOADING,
    INSTALLING,
    PAUSED,
    READY,
    FAILED
}

data class SubjectModelState(
    val phase: SubjectModelPhase,
    val progressPercent: Int? = null
)

/** Starts a Google Play services module download only after an explicit tap. */
class SubjectModelManager(context: Context) : Closeable {

    private val moduleClient = ModuleInstall.getClient(context.applicationContext)
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().build()
    )

    private var listener: InstallStatusListener? = null
    @Volatile private var closed = false

    /** Reads the installed state without requesting or downloading anything. */
    fun checkAvailability(onState: (SubjectModelState) -> Unit) {
        if (closed) return
        moduleClient.areModulesAvailable(segmenter)
            .addOnSuccessListener { availability ->
                if (closed) return@addOnSuccessListener
                onState(
                    if (availability.areModulesAvailable()) {
                        SubjectModelState(SubjectModelPhase.READY, 100)
                    } else {
                        SubjectModelState(SubjectModelPhase.NOT_DOWNLOADED)
                    }
                )
            }
            .addOnFailureListener {
                if (!closed) onState(SubjectModelState(SubjectModelPhase.FAILED))
            }
    }

    fun download(onState: (SubjectModelState) -> Unit) {
        if (closed) return
        unregisterListener()
        onState(SubjectModelState(SubjectModelPhase.DOWNLOADING))

        val statusListener = InstallStatusListener { update ->
            if (closed) return@InstallStatusListener
            when (update.installState) {
                ModuleInstallStatusUpdate.InstallState.STATE_PENDING -> {
                    onState(SubjectModelState(SubjectModelPhase.DOWNLOADING))
                }
                ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING -> {
                    onState(
                        SubjectModelState(
                            SubjectModelPhase.DOWNLOADING,
                            update.progressInfo?.let { progress ->
                                val total = progress.totalBytesToDownload
                                if (total > 0L) {
                                    ((progress.bytesDownloaded.toDouble() /
                                        total.toDouble()) * 100.0)
                                        .toInt()
                                        .coerceIn(0, 100)
                                } else {
                                    null
                                }
                            }
                        )
                    )
                }
                ModuleInstallStatusUpdate.InstallState.STATE_INSTALLING -> {
                    onState(SubjectModelState(SubjectModelPhase.INSTALLING))
                }
                ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOAD_PAUSED -> {
                    onState(SubjectModelState(SubjectModelPhase.PAUSED))
                }
                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                    onState(SubjectModelState(SubjectModelPhase.READY, 100))
                    unregisterListener()
                }
                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED,
                ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                    onState(SubjectModelState(SubjectModelPhase.FAILED))
                    unregisterListener()
                }
            }
        }
        listener = statusListener

        val request = ModuleInstallRequest.newBuilder()
            .addApi(segmenter)
            .setListener(statusListener)
            .build()

        moduleClient.installModules(request)
            .addOnSuccessListener { response ->
                if (closed) return@addOnSuccessListener
                if (response.areModulesAlreadyInstalled()) {
                    onState(SubjectModelState(SubjectModelPhase.READY, 100))
                    unregisterListener()
                }
            }
            .addOnFailureListener {
                if (!closed) onState(SubjectModelState(SubjectModelPhase.FAILED))
                unregisterListener()
            }
    }

    override fun close() {
        if (closed) return
        closed = true
        unregisterListener()
        segmenter.close()
    }

    private fun unregisterListener() {
        val activeListener = listener ?: return
        listener = null
        moduleClient.unregisterListener(activeListener)
    }
}
