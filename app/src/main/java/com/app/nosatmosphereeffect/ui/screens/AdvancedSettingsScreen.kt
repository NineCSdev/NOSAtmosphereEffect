package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.SubjectModelPhase
import com.app.nosatmosphereeffect.helper.SubjectModelState
import com.app.nosatmosphereeffect.ui.components.AtmoCard
import com.app.nosatmosphereeffect.ui.components.AtmoDropdownField
import com.app.nosatmosphereeffect.ui.components.AtmoNumberField
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import com.app.nosatmosphereeffect.ui.components.LabeledSlider
import com.app.nosatmosphereeffect.ui.components.SectionHeader
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow

/** Everything the screen needs to seed its fields + decide which sections show. */
data class AdvancedConfig(
    val showHalftone: Boolean,
    val showColorFill: Boolean,
    val showNeon: Boolean,
    val showNoiseSwitch: Boolean,
    val showBlob: Boolean,
    val isPlaylistMode: Boolean,
    val rotationOptions: List<String>,
    val initialRotationIndex: Int,
    val poll: String,
    val delay: String,
    val duration: String,
    val enableNoise: Boolean,
    val noiseScale: String,
    val noiseStrength: String,
    val dotSize: Float,
    val grayscale: Boolean,
    val originX: Float,
    val originY: Float,
    val saturation: Float,
    val contrast: Float,
    val neonSensitivity: Float,
    val neonLineWidth: Float,
    val subjectSegmentationEnabled: Boolean,
    val scrollEnabled: Boolean
)

/** The field values the activity persists when the user taps Apply. */
data class AdvancedResult(
    val poll: String,
    val delay: String,
    val duration: String,
    val enableNoise: Boolean,
    val noiseScale: String,
    val noiseStrength: String,
    val dotSize: Float,
    val grayscale: Boolean,
    val originX: Float,
    val originY: Float,
    val saturation: Float,
    val contrast: Float,
    val neonSensitivity: Float,
    val neonLineWidth: Float,
    val subjectSegmentationEnabled: Boolean,
    val rotationIndex: Int,
    val scrollEnabled: Boolean
)

@Composable
fun AdvancedSettingsScreen(
    config: AdvancedConfig,
    subjectModelState: SubjectModelState,
    onDownloadSubjectModel: () -> Unit,
    onApply: (AdvancedResult) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    // --- live field state, seeded from config -------------------------------
    var poll by remember { mutableStateOf(config.poll) }
    var delay by remember { mutableStateOf(config.delay) }
    var duration by remember { mutableStateOf(config.duration) }
    var rotationIndex by remember { mutableIntStateOf(config.initialRotationIndex) }

    var dotSize by remember { mutableFloatStateOf(config.dotSize) }
    var grayscale by remember { mutableStateOf(config.grayscale) }

    var originX by remember { mutableFloatStateOf(config.originX) }
    var originY by remember { mutableFloatStateOf(config.originY) }

    var saturation by remember { mutableFloatStateOf(config.saturation) }
    var contrast by remember { mutableFloatStateOf(config.contrast) }

    var neonSensitivity by remember { mutableFloatStateOf(config.neonSensitivity) }
    var neonLineWidth by remember { mutableFloatStateOf(config.neonLineWidth) }
    var subjectSegmentationEnabled by remember {
        mutableStateOf(config.subjectSegmentationEnabled)
    }

    var noiseEnabled by remember { mutableStateOf(config.enableNoise) }
    var noiseScale by remember { mutableStateOf(config.noiseScale) }
    var noiseStrength by remember { mutableStateOf(config.noiseStrength) }

    var scrollEnabled by remember { mutableStateOf(config.scrollEnabled) }

    var infoDialog by remember { mutableStateOf<InfoDialog?>(null) }

    val infoPainter = painterResource(R.drawable.ic_info)
    val downloadPainter = painterResource(R.drawable.ic_download)

    val subjectModelReady = subjectModelState.phase == SubjectModelPhase.READY
    val subjectModelWorking = subjectModelState.phase == SubjectModelPhase.DOWNLOADING ||
        subjectModelState.phase == SubjectModelPhase.INSTALLING ||
        subjectModelState.phase == SubjectModelPhase.PAUSED
    val subjectModelButtonText = when (subjectModelState.phase) {
        SubjectModelPhase.NOT_DOWNLOADED -> "Download Subject Model"
        SubjectModelPhase.DOWNLOADING -> subjectModelState.progressPercent?.let { "Downloading $it%" }
            ?: "Downloading Subject Model"
        SubjectModelPhase.INSTALLING -> "Installing Subject Model"
        SubjectModelPhase.PAUSED -> "Download Paused"
        SubjectModelPhase.READY -> "Subject Model Downloaded"
        SubjectModelPhase.FAILED -> "Retry Subject Model Download"
    }
    val subjectModelStatusText = when (subjectModelState.phase) {
        SubjectModelPhase.NOT_DOWNLOADED ->
            "Optional. Nothing is downloaded until you tap the button."
        SubjectModelPhase.DOWNLOADING ->
            "Downloading once through Google Play services."
        SubjectModelPhase.INSTALLING ->
            "Finishing the on-device model installation."
        SubjectModelPhase.PAUSED ->
            "Download paused until the required connection is available."
        SubjectModelPhase.READY ->
            "Ready. Subject analysis runs on-device and works offline."
        SubjectModelPhase.FAILED ->
            "Download failed. Check the connection and try again."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtmoTopBar(
                title = "Fine Tuning",
                backIcon = painterResource(R.drawable.ic_arrow_back),
                onBack = onBack
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Timing ---------------------------------------------------
            AtmoCard {
                SectionHeader("Timing & Response")
                Spacer(Modifier.height(16.dp))
                AtmoNumberField(
                    label = "Unlock Check Interval (ms)",
                    value = poll,
                    onValueChange = { poll = it.filterDigits() },
                    helper = "Recommended — Others: 50ms · Samsung: 30000ms",
                    infoIcon = infoPainter,
                    onInfoClick = { infoDialog = InfoDialog.Poll }
                )
                Spacer(Modifier.height(16.dp))
                AtmoNumberField(
                    label = "Lock Delay (ms)",
                    value = delay,
                    onValueChange = { delay = it.filterDigits() },
                    helper = "Recommended — Others: 800ms · Samsung: 0ms",
                    infoIcon = infoPainter,
                    onInfoClick = { infoDialog = InfoDialog.Delay }
                )
                Spacer(Modifier.height(16.dp))
                AtmoNumberField(
                    label = "Animation Duration (ms)",
                    value = duration,
                    onValueChange = { duration = it.filterDigits() },
                    helper = "Original: 2500 · Reverse, Color Fill & Canvas Sketch: 1500 · Others: 500"
                )
            }

            // ---- Home screen / scrolling ---------------------------------
            AtmoCard {
                SectionHeader("Home Screen")
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow(
                    title = "Wallpaper Scrolling (Experimental)",
                    checked = scrollEnabled,
                    onCheckedChange = { scrollEnabled = it }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pans wide wallpapers (e.g. 4:3) sideways as you swipe between " +
                        "home-screen pages, like the stock launcher. Uses the full, " +
                        "un-cropped image. No effect on images that already fit the screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Playlist rotation ---------------------------------------
            if (config.isPlaylistMode) {
                AtmoCard {
                    SectionHeader("Playlist Rotation")
                    Spacer(Modifier.height(16.dp))
                    AtmoDropdownField(
                        label = "Rotation Mode",
                        options = config.rotationOptions,
                        selectedIndex = rotationIndex,
                        onSelected = { rotationIndex = it },
                        helper = "Pick an interval, or sync Image 1 (Light) / Image 2 (Dark)."
                    )
                }
            }

            // ---- Halftone -------------------------------------------------
            if (config.showHalftone) {
                AtmoCard {
                    SectionHeader("Halftone")
                    Spacer(Modifier.height(16.dp))
                    LabeledSlider(
                        label = "Halftone Pixel Size",
                        value = dotSize,
                        onValueChange = { dotSize = it },
                        valueRange = 0f..40f,
                        step = 1f
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "Black & White Effect",
                        checked = grayscale,
                        onCheckedChange = { grayscale = it }
                    )
                }
            }

            // ---- Color fill ----------------------------------------------
            if (config.showColorFill) {
                AtmoCard {
                    SectionHeader("Color Fill Origin")
                    Spacer(Modifier.height(16.dp))
                    LabeledSlider(
                        label = "Fingerprint Position (Horizontal)",
                        value = originX,
                        onValueChange = { originX = it },
                        valueRange = 0f..1f,
                        step = 0.01f
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledSlider(
                        label = "Fingerprint Position (Vertical)",
                        value = originY,
                        onValueChange = { originY = it },
                        valueRange = 0f..1f,
                        step = 0.01f
                    )
                }
            }

            // ---- Canvas sketch -------------------------------------------
            if (config.showNeon) {
                AtmoCard {
                    SectionHeader("Canvas Sketch")
                    Spacer(Modifier.height(16.dp))
                    SettingSwitchRow(
                        title = "Subject Segmentation",
                        checked = subjectSegmentationEnabled,
                        onCheckedChange = { subjectSegmentationEnabled = it },
                        enabled = subjectModelReady,
                        subtitle = if (subjectModelReady) {
                            "Isolates a prominent subject using the downloaded model."
                        } else {
                            "Download the optional model to enable subject isolation."
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    AtmoOutlinedButton(
                        text = subjectModelButtonText,
                        onClick = onDownloadSubjectModel,
                        enabled = !subjectModelWorking && !subjectModelReady,
                        accent = true,
                        icon = if (!subjectModelWorking && !subjectModelReady) downloadPainter else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        subjectModelStatusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(16.dp))
                    LabeledSlider(
                        label = "Sketch Detail",
                        value = neonSensitivity,
                        onValueChange = { neonSensitivity = it },
                        valueRange = 0f..1f,
                        step = 0.05f
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledSlider(
                        label = "Line Thickness",
                        value = neonLineWidth,
                        onValueChange = { neonLineWidth = it },
                        valueRange = 0.5f..4f,
                        step = 0.5f
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "With subject segmentation off, Canvas Sketch traces the whole " +
                            "wallpaper. When enabled, it keeps the detected subject's " +
                            "silhouette and broad internal contours.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ---- Atmosphere colour (blob) --------------------------------
            if (config.showBlob) {
                AtmoCard {
                    SectionHeader("Atmosphere Color")
                    Spacer(Modifier.height(16.dp))
                    LabeledSlider(
                        label = "Blob Saturation",
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..3f,
                        step = 0.1f
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledSlider(
                        label = "Blob Contrast",
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 0f..3f,
                        step = 0.1f
                    )
                }
            }

            // ---- Film grain (noise) --------------------------------------
            if (config.showNoiseSwitch) {
                AtmoCard {
                    SectionHeader("Film Grain")
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        title = "Enable Blur Noise (Film Grain)",
                        checked = noiseEnabled,
                        onCheckedChange = { noiseEnabled = it }
                    )
                    if (noiseEnabled) {
                        Spacer(Modifier.height(16.dp))
                        AtmoNumberField(
                            label = "Noise Scale (Grain Size)",
                            value = noiseScale,
                            onValueChange = { noiseScale = it.filterDecimal() },
                            helper = "Lower = larger grains. Recommended: 2000",
                            decimal = true
                        )
                        Spacer(Modifier.height(16.dp))
                        AtmoNumberField(
                            label = "Noise Strength (Intensity)",
                            value = noiseStrength,
                            onValueChange = { noiseStrength = it.filterDecimal() },
                            helper = "0.0 to 1.0. Recommended: 0.06",
                            decimal = true
                        )
                    }
                }
            }

            // ---- Actions --------------------------------------------------
            Spacer(Modifier.height(4.dp))
            AtmoPrimaryButton(
                text = "Save & Apply",
                onClick = {
                    onApply(
                        AdvancedResult(
                            poll = poll,
                            delay = delay,
                            duration = duration,
                            enableNoise = noiseEnabled,
                            noiseScale = noiseScale,
                            noiseStrength = noiseStrength,
                            dotSize = dotSize,
                            grayscale = grayscale,
                            originX = originX,
                            originY = originY,
                            saturation = saturation,
                            contrast = contrast,
                            neonSensitivity = neonSensitivity,
                            neonLineWidth = neonLineWidth,
                            subjectSegmentationEnabled = subjectSegmentationEnabled,
                            rotationIndex = rotationIndex,
                            scrollEnabled = scrollEnabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            AtmoOutlinedButton(
                text = "Reset to Recommended",
                onClick = onReset,
                accent = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- Info dialogs --------------------------------------------------------
    infoDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(dialog.title, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(dialog.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Got it", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

private enum class InfoDialog(val title: String, val message: String) {
    Poll(
        "Unlock Check Interval",
        "Controls how frequently the app checks if the device has been unlocked.\n\n" +
            "• What it solves:\n" +
            "If you unlock your phone and the animation starts after a delay, lower this value.\n\n" +
            "• Recommended:\n" +
            "30000ms for Samsung and most devices (saves battery).\n" +
            "50ms if you experience delayed animation start."
    ),
    Delay(
        "Lock Delay",
        "Adds a pause before the wallpaper resets when you lock the phone.\n\n" +
            "• What it solves:\n" +
            "If you see a glimpse of the wallpaper resetting/snapping back before the screen turns fully black, increase this value.\n\n" +
            "• Recommended:\n" +
            "0ms for Samsung / most devices.\n" +
            "500ms - 800ms if you experience the glitch.\n\n" +
            "⚠️ Note: If this value is too high, unlocking immediately after locking might show the wallpaper in its previous state."
    )
}

private fun String.filterDigits(): String = filter { it.isDigit() }

private fun String.filterDecimal(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot == -1) return cleaned
    // keep only the first dot
    return cleaned.substring(0, firstDot + 1) +
        cleaned.substring(firstDot + 1).replace(".", "")
}
