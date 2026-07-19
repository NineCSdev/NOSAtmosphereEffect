package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.helper.SubjectModelPhase
import com.app.nosatmosphereeffect.helper.SubjectModelState
import com.app.nosatmosphereeffect.ui.components.AtmoDropdownField
import com.app.nosatmosphereeffect.ui.components.AtmoNumberField
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoReveal
import com.app.nosatmosphereeffect.ui.components.AtmoSegmentedControl
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import com.app.nosatmosphereeffect.ui.components.AtmoTextButton
import com.app.nosatmosphereeffect.ui.components.LabeledSlider
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow

data class AdvancedConfig(
    val activeEffectTitle: String,
    val recommendedDurationMs: Long,
    val showHalftone: Boolean,
    val showColorFill: Boolean,
    val showNeon: Boolean,
    val showFrosted: Boolean,
    val showNoiseSwitch: Boolean,
    val showBlob: Boolean,
    val isPlaylistMode: Boolean,
    val rotationOptions: List<String>,
    val initialRotationIndex: Int,
    val poll: String,
    val delay: String,
    val duration: String,
    val dimness: Float,
    val blurStrength: Float,
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

data class AdvancedResult(
    val poll: String,
    val delay: String,
    val duration: String,
    val dimness: Float,
    val blurStrength: Float,
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

private enum class FineTuneTab(val label: String) {
    Effect("Effect"),
    Timing("Timing"),
    Display("Display")
}

@Composable
fun AdvancedSettingsScreen(
    config: AdvancedConfig,
    subjectModelState: SubjectModelState,
    onDownloadSubjectModel: () -> Unit,
    onApply: (AdvancedResult) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(FineTuneTab.Effect) }
    var poll by remember { mutableStateOf(config.poll) }
    var delay by remember { mutableStateOf(config.delay) }
    var duration by remember { mutableStateOf(config.duration) }
    var dimness by remember { mutableFloatStateOf(config.dimness) }
    var blurStrength by remember { mutableFloatStateOf(config.blurStrength) }
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

    val subjectModelReady = subjectModelState.phase == SubjectModelPhase.READY
    val subjectModelWorking = subjectModelState.phase in setOf(
        SubjectModelPhase.CHECKING,
        SubjectModelPhase.DOWNLOADING,
        SubjectModelPhase.INSTALLING,
        SubjectModelPhase.PAUSED
    )
    val subjectModelButtonText = when (subjectModelState.phase) {
        SubjectModelPhase.CHECKING -> "Checking model"
        SubjectModelPhase.NOT_DOWNLOADED -> "Download subject model"
        SubjectModelPhase.DOWNLOADING -> subjectModelState.progressPercent?.let { "Downloading $it%" }
            ?: "Downloading model"
        SubjectModelPhase.INSTALLING -> "Installing model"
        SubjectModelPhase.PAUSED -> "Download paused"
        SubjectModelPhase.READY -> "Subject model downloaded"
        SubjectModelPhase.FAILED -> "Retry model download"
    }
    val subjectModelStatusText = when (subjectModelState.phase) {
        SubjectModelPhase.CHECKING -> "Checking Google Play services."
        SubjectModelPhase.NOT_DOWNLOADED -> "Optional and downloaded only on request."
        SubjectModelPhase.DOWNLOADING -> "Google Play services is downloading the model."
        SubjectModelPhase.INSTALLING -> "Completing on-device installation."
        SubjectModelPhase.PAUSED -> "Waiting for an available connection."
        SubjectModelPhase.READY -> "Ready for offline, on-device segmentation."
        SubjectModelPhase.FAILED -> "Download failed. Try again when connected."
    }

    val result = AdvancedResult(
        poll = poll,
        delay = delay,
        duration = duration,
        dimness = dimness,
        blurStrength = blurStrength,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtmoTopBar(
                title = "Fine tuning",
                backIcon = painterResource(R.drawable.ic_arrow_back),
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AtmoOutlinedButton(
                        text = "Reset",
                        onClick = onReset,
                        modifier = Modifier.weight(0.42f)
                    )
                    AtmoPrimaryButton(
                        text = "Save",
                        onClick = { onApply(result) },
                        modifier = Modifier.weight(0.58f)
                    )
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AtmoReveal {
                AtmoSegmentedControl(
                    options = FineTuneTab.entries.map { it.label },
                    selectedIndex = selectedTab.ordinal,
                    onSelected = { selectedTab = FineTuneTab.entries[it] },
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn() + slideInHorizontally { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut() + slideOutHorizontally { if (forward) -it / 6 else it / 6 })
                },
                modifier = Modifier.fillMaxSize(),
                label = "fineTuneTab"
            ) { tab ->
                when (tab) {
                    FineTuneTab.Effect -> EffectSettings(
                        config = config,
                        dotSize = dotSize,
                        onDotSizeChange = { dotSize = it },
                        grayscale = grayscale,
                        onGrayscaleChange = { grayscale = it },
                        originX = originX,
                        onOriginXChange = { originX = it },
                        originY = originY,
                        onOriginYChange = { originY = it },
                        saturation = saturation,
                        onSaturationChange = { saturation = it },
                        contrast = contrast,
                        onContrastChange = { contrast = it },
                        neonSensitivity = neonSensitivity,
                        onNeonSensitivityChange = { neonSensitivity = it },
                        neonLineWidth = neonLineWidth,
                        onNeonLineWidthChange = { neonLineWidth = it },
                        subjectSegmentationEnabled = subjectSegmentationEnabled,
                        onSubjectSegmentationChange = { subjectSegmentationEnabled = it },
                        subjectModelReady = subjectModelReady,
                        subjectModelWorking = subjectModelWorking,
                        subjectModelButtonText = subjectModelButtonText,
                        subjectModelStatusText = subjectModelStatusText,
                        subjectModelState = subjectModelState,
                        onDownloadSubjectModel = onDownloadSubjectModel,
                        noiseEnabled = noiseEnabled,
                        onNoiseEnabledChange = { noiseEnabled = it },
                        noiseScale = noiseScale,
                        onNoiseScaleChange = { noiseScale = it },
                        noiseStrength = noiseStrength,
                        onNoiseStrengthChange = { noiseStrength = it }
                    )
                    FineTuneTab.Timing -> TimingSettings(
                        activeEffectTitle = config.activeEffectTitle,
                        recommendedDurationMs = config.recommendedDurationMs,
                        poll = poll,
                        onPollChange = { poll = it.filterDigits() },
                        delay = delay,
                        onDelayChange = { delay = it.filterDigits() },
                        duration = duration,
                        onDurationChange = { duration = it.filterDigits() },
                        onPollInfo = { infoDialog = InfoDialog.Poll },
                        onDelayInfo = { infoDialog = InfoDialog.Delay }
                    )
                    FineTuneTab.Display -> DisplaySettings(
                        config = config,
                        dimness = dimness,
                        onDimnessChange = { dimness = it },
                        blurStrength = blurStrength,
                        onBlurStrengthChange = { blurStrength = it },
                        scrollEnabled = scrollEnabled,
                        onScrollEnabledChange = { scrollEnabled = it },
                        rotationIndex = rotationIndex,
                        onRotationSelected = { rotationIndex = it }
                    )
                }
            }
        }
    }

    infoDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(dialog.title) },
            text = { Text(dialog.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                AtmoTextButton(text = "Done", onClick = { infoDialog = null })
            }
        )
    }
}

@Composable
private fun EffectSettings(
    config: AdvancedConfig,
    dotSize: Float,
    onDotSizeChange: (Float) -> Unit,
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    originX: Float,
    onOriginXChange: (Float) -> Unit,
    originY: Float,
    onOriginYChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    neonSensitivity: Float,
    onNeonSensitivityChange: (Float) -> Unit,
    neonLineWidth: Float,
    onNeonLineWidthChange: (Float) -> Unit,
    subjectSegmentationEnabled: Boolean,
    onSubjectSegmentationChange: (Boolean) -> Unit,
    subjectModelReady: Boolean,
    subjectModelWorking: Boolean,
    subjectModelButtonText: String,
    subjectModelStatusText: String,
    subjectModelState: SubjectModelState,
    onDownloadSubjectModel: () -> Unit,
    noiseEnabled: Boolean,
    onNoiseEnabledChange: (Boolean) -> Unit,
    noiseScale: String,
    onNoiseScaleChange: (String) -> Unit,
    noiseStrength: String,
    onNoiseStrengthChange: (String) -> Unit
) {
    SettingsScroll {
        if (config.showHalftone) {
            SettingsGroup("Halftone") {
                LabeledSlider(
                    label = "Dot size",
                    value = dotSize,
                    onValueChange = onDotSizeChange,
                    valueRange = 0f..40f,
                    step = 1f
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "Black and white",
                    checked = grayscale,
                    onCheckedChange = onGrayscaleChange
                )
            }
        }

        if (config.showColorFill) {
            SettingsGroup("Color origin") {
                LabeledSlider(
                    label = "Horizontal position",
                    value = originX,
                    onValueChange = onOriginXChange,
                    valueRange = 0f..1f,
                    step = 0.01f
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    label = "Vertical position",
                    value = originY,
                    onValueChange = onOriginYChange,
                    valueRange = 0f..1f,
                    step = 0.01f
                )
            }
        }

        if (config.showNeon) {
            SettingsGroup("Canvas Sketch") {
                SettingSwitchRow(
                    title = "Subject segmentation",
                    checked = subjectSegmentationEnabled,
                    onCheckedChange = onSubjectSegmentationChange,
                    enabled = subjectModelReady,
                    subtitle = if (subjectModelReady) {
                        "Use the installed model to isolate the foreground."
                    } else {
                        "The full wallpaper is sketched until the model is installed."
                    }
                )
                Spacer(Modifier.height(10.dp))
                AtmoOutlinedButton(
                    text = subjectModelButtonText,
                    onClick = onDownloadSubjectModel,
                    enabled = !subjectModelWorking && !subjectModelReady,
                    accent = true,
                    icon = if (!subjectModelWorking && !subjectModelReady) {
                        painterResource(R.drawable.ic_download)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (subjectModelWorking) {
                    Spacer(Modifier.height(10.dp))
                    val percent = subjectModelState.progressPercent
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    subjectModelStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                LabeledSlider(
                    label = "Sketch detail",
                    value = neonSensitivity,
                    onValueChange = onNeonSensitivityChange,
                    valueRange = 0f..1f,
                    step = 0.05f
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    label = "Line thickness",
                    value = neonLineWidth,
                    onValueChange = onNeonLineWidthChange,
                    valueRange = 0.5f..4f,
                    step = 0.5f
                )
            }
        }

        if (config.showBlob) {
            SettingsGroup("Atmosphere color") {
                LabeledSlider(
                    label = "Saturation",
                    value = saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0f..3f,
                    step = 0.1f
                )
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    label = "Contrast",
                    value = contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0f..3f,
                    step = 0.1f
                )
            }
        }

        if (config.showNoiseSwitch) {
            SettingsGroup("Film grain") {
                SettingSwitchRow(
                    title = "Blur noise",
                    checked = noiseEnabled,
                    onCheckedChange = onNoiseEnabledChange
                )
                AnimatedVisibility(visible = noiseEnabled) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        AtmoNumberField(
                            label = "Grain scale",
                            value = noiseScale,
                            onValueChange = { onNoiseScaleChange(it.filterDecimal()) },
                            helper = "Recommended: 2000",
                            decimal = true
                        )
                        Spacer(Modifier.height(14.dp))
                        AtmoNumberField(
                            label = "Grain strength",
                            value = noiseStrength,
                            onValueChange = { onNoiseStrengthChange(it.filterDecimal()) },
                            helper = "Recommended: 0.06",
                            decimal = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingSettings(
    activeEffectTitle: String,
    recommendedDurationMs: Long,
    poll: String,
    onPollChange: (String) -> Unit,
    delay: String,
    onDelayChange: (String) -> Unit,
    duration: String,
    onDurationChange: (String) -> Unit,
    onPollInfo: () -> Unit,
    onDelayInfo: () -> Unit
) {
    val info = painterResource(R.drawable.ic_info)
    SettingsScroll {
        SettingsGroup("Unlock response") {
            AtmoNumberField(
                label = "Unlock check interval (ms)",
                value = poll,
                onValueChange = onPollChange,
                helper = "Standard: 50 | Samsung: 30000",
                infoIcon = info,
                onInfoClick = onPollInfo
            )
            Spacer(Modifier.height(16.dp))
            AtmoNumberField(
                label = "Lock delay (ms)",
                value = delay,
                onValueChange = onDelayChange,
                helper = "Standard: 800 | Samsung: 0",
                infoIcon = info,
                onInfoClick = onDelayInfo
            )
        }
        SettingsGroup("Animation") {
            AtmoNumberField(
                label = "Duration (ms)",
                value = duration,
                onValueChange = onDurationChange,
                helper = "Recommended for $activeEffectTitle: $recommendedDurationMs ms"
            )
        }
    }
}

@Composable
private fun DisplaySettings(
    config: AdvancedConfig,
    dimness: Float,
    onDimnessChange: (Float) -> Unit,
    blurStrength: Float,
    onBlurStrengthChange: (Float) -> Unit,
    scrollEnabled: Boolean,
    onScrollEnabledChange: (Boolean) -> Unit,
    rotationIndex: Int,
    onRotationSelected: (Int) -> Unit
) {
    SettingsScroll {
        SettingsGroup("Wallpaper appearance") {
            LabeledSlider(
                label = "Dimness",
                value = dimness,
                onValueChange = onDimnessChange,
                valueRange = 0f..0.8f,
                step = 0.05f,
                valueText = { "${(it * 100).toInt()}%" }
            )
            if (config.showFrosted) {
                Spacer(Modifier.height(12.dp))
                LabeledSlider(
                    label = "Blur strength",
                    value = blurStrength,
                    onValueChange = onBlurStrengthChange,
                    valueRange = 0f..400f,
                    step = 10f
                )
            }
        }
        SettingsGroup("Home screen") {
            SettingSwitchRow(
                title = "Wallpaper scrolling",
                checked = scrollEnabled,
                onCheckedChange = onScrollEnabledChange
            )
        }
        if (config.isPlaylistMode) {
            SettingsGroup("Playlist") {
                AtmoDropdownField(
                    label = "Rotation mode",
                    options = config.rotationOptions,
                    selectedIndex = rotationIndex,
                    onSelected = onRotationSelected
                )
            }
        }
    }
}

@Composable
private fun SettingsScroll(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

private enum class InfoDialog(val title: String, val message: String) {
    Poll(
        "Unlock check interval",
        "Lower values react sooner after unlock but check more often. Use 30000 ms on " +
            "Samsung or 50 ms if the animation starts late."
    ),
    Delay(
        "Lock delay",
        "Increase this only if the wallpaper visibly resets before the screen turns off. " +
            "Use 0 ms on Samsung or 500-800 ms when needed."
    )
}

private fun String.filterDigits(): String = filter { it.isDigit() }

private fun String.filterDecimal(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot == -1) return cleaned
    return cleaned.substring(0, firstDot + 1) +
        cleaned.substring(firstDot + 1).replace(".", "")
}
