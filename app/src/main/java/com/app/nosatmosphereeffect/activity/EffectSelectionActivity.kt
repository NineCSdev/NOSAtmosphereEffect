package com.app.nosatmosphereeffect.activity

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.service.AtmosphereService
import com.app.nosatmosphereeffect.service.BlurToSharpService
import com.app.nosatmosphereeffect.service.ColorFillReverseService
import com.app.nosatmosphereeffect.service.ColorFillService
import com.app.nosatmosphereeffect.service.FrostedReverseService
import com.app.nosatmosphereeffect.service.FrostedService
import com.app.nosatmosphereeffect.service.HalftoneReverseService
import com.app.nosatmosphereeffect.service.HalftoneService
import com.app.nosatmosphereeffect.service.NeonReverseService
import com.app.nosatmosphereeffect.service.NeonService
import com.app.nosatmosphereeffect.ui.components.AtmoDialogRow
import com.app.nosatmosphereeffect.ui.screens.EffectItem
import com.app.nosatmosphereeffect.ui.screens.EffectSelectionScreen
import com.app.nosatmosphereeffect.ui.theme.AtmoEngineTheme

class EffectSelectionActivity : ComponentActivity() {

    private var selectedEffectId: String = "ORIGINAL"

    // Image(s) handed to us by another app via the system Share sheet (null for the
    // normal in-app flow, where the user picks images after choosing an effect).
    private var sharedUris: ArrayList<Uri>? = null

    private val effectsList = listOf(
        EffectItem("ORIGINAL", "Original Atmosphere", "Sharp → Blur",
            "Signature style. Drifting ambient atmospheric clouds."),
        EffectItem("REVERSE", "Reverse Atmosphere", "Blur → Sharp",
            "Mysterious reveal. Ambient clouds fade to a clear view."),
        EffectItem("FROSTED", "Simple Frosted", "Sharp → Blur",
            "Modern minimalism. A clean, uniform frosted glass layer."),
        EffectItem("FROSTED_REVERSE", "Simple Frosted (Reverse)", "Blur → Sharp",
            "Elegant clarity. Heavy frost dissolves to crystal clear."),
        EffectItem("HALFTONE", "Halftone Print", "Sharp → Halftone",
            "Retro aesthetic. Sharp view dissolves into comic-book CMYK dots."),
        EffectItem("HALFTONE_REVERSE", "Halftone Print (Reverse)", "Halftone → Sharp",
            "Retro aesthetic. CMYK dots seamlessly expand into continuous color."),
        EffectItem("COLORFILL", "Color Fill", "B&W → Color",
            "Liquid awakening. Colors flow outward from your fingerprint."),
        EffectItem("COLORFILL_REVERSE", "Color Fill (Reverse)", "Color → B&W",
            "Fluid drain. Colors wash away into grayscale."),
        EffectItem("NEON", "Canvas AOD", "Sketch → Image",
            "Canvas-style lockscreen. Thin line art fades smoothly into your wallpaper."),
        EffectItem("NEON_REVERSE", "Canvas AOD (Reverse)", "Image → Sketch",
            "Reverse Canvas transition. The wallpaper settles back into clean line art.")
    )

    private val pickSingleImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { launchCropActivity(it) }
        }

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) launchMultiCropActivity(ArrayList(uris))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isUpdateOnly = intent.getBooleanExtra("UPDATE_EFFECT_ONLY", false)

        // Launched from another app's Share sheet? Capture the image(s) up front.
        // A share never carries UPDATE_EFFECT_ONLY, so the share and picker paths
        // below are mutually exclusive.
        sharedUris = extractSharedUris()
        val isShare = sharedUris != null

        setContent {
            AtmoEngineTheme {
                var pendingMode by remember { mutableStateOf(false) }

                EffectSelectionScreen(
                    title = if (isUpdateOnly && !isShare) "Change Effect" else "Choose Effect",
                    effects = effectsList,
                    onEffectClick = { item ->
                        selectedEffectId = item.id
                        when {
                            // Shared image(s): straight to crop/playlist. The number of
                            // images shared decides single vs. playlist, so there is no
                            // mode dialog and no picker step.
                            isShare -> {
                                val uris = sharedUris!!
                                if (uris.size == 1) launchCropActivity(uris[0])
                                else launchMultiCropActivity(uris)
                            }
                            isUpdateOnly -> applyEffectDirectly(item.id)
                            else -> pendingMode = true
                        }
                    },
                    onBack = { finish() }
                )

                if (pendingMode) {
                    ModeChoiceDialog(
                        onDismiss = { pendingMode = false },
                        onSingle = { pendingMode = false; pickSingleImage.launch("image/*") },
                        onMultiple = { pendingMode = false; pickMultipleImages.launch("image/*") }
                    )
                }
            }
        }
    }

    /**
     * Returns the image URI(s) if this activity was opened from the system Share
     * sheet (ACTION_SEND / ACTION_SEND_MULTIPLE with an image MIME), otherwise null.
     * The read grant that came with the share intent is re-granted to the crop /
     * playlist activity via FLAG_GRANT_READ_URI_PERMISSION when we forward it.
     */
    private fun extractSharedUris(): ArrayList<Uri>? {
        val type = intent.type
        if (type == null || !type.startsWith("image/")) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) arrayListOf(uri) else null
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) ArrayList(uris) else null
            }
            else -> null
        }
    }

    private fun launchCropActivity(uri: Uri) {
        val intent = if (selectedEffectId.contains("REVERSE")) {
            Intent(this, BlurToSharpCropActivity::class.java)
        } else {
            Intent(this, CropActivity::class.java)
        }
        intent.data = uri
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra("EFFECT_ID", selectedEffectId)
        startActivity(intent)
        finish()
    }

    private fun launchMultiCropActivity(uris: ArrayList<Uri>) {
        val intent = Intent(this, PlaylistEditorActivity::class.java)
        intent.data = uris[0]
        val clipData = ClipData.newUri(contentResolver, "Images", uris[0])
        for (i in 1 until uris.size) clipData.addItem(ClipData.Item(uris[i]))
        intent.clipData = clipData
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putParcelableArrayListExtra("IMAGE_URIS", uris)
        intent.putExtra("EFFECT_ID", selectedEffectId)
        startActivity(intent)
        finish()
    }

    private fun applyEffectDirectly(effectId: String) {
        val serviceClass = when (effectId) {
            "ORIGINAL" -> AtmosphereService::class.java
            "REVERSE" -> BlurToSharpService::class.java
            "FROSTED" -> FrostedService::class.java
            "FROSTED_REVERSE" -> FrostedReverseService::class.java
            "HALFTONE" -> HalftoneService::class.java
            "HALFTONE_REVERSE" -> HalftoneReverseService::class.java
            "COLORFILL" -> ColorFillService::class.java
            "COLORFILL_REVERSE" -> ColorFillReverseService::class.java
            "NEON" -> NeonService::class.java
            "NEON_REVERSE" -> NeonReverseService::class.java
            else -> AtmosphereService::class.java
        }
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        intent.putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, serviceClass)
        )
        startActivity(intent)
        finish()
    }
}

@Composable
private fun ModeChoiceDialog(
    onDismiss: () -> Unit,
    onSingle: () -> Unit,
    onMultiple: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Select Wallpaper Mode", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                AtmoDialogRow("Single Image", "One wallpaper", onSingle)
                AtmoDialogRow(
                    "Multiple Images (Playlist)",
                    "Rotate through several wallpapers",
                    onMultiple
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
