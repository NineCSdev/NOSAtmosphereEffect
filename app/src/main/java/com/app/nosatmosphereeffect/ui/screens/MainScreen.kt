package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoReveal
import com.app.nosatmosphereeffect.ui.components.AtmoSegmentedControl
import com.app.nosatmosphereeffect.ui.components.AtmoTonalButton
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow
import com.app.nosatmosphereeffect.ui.components.WallpaperTransitionPreview
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import com.app.nosatmosphereeffect.ui.theme.AppThemeMode
import com.app.nosatmosphereeffect.ui.theme.LocalAtmoExpressive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    wallpaperActive: Boolean,
    statusText: String,
    activeEffectId: String?,
    previewBitmap: ImageBitmap?,
    isPlaylistMode: Boolean,
    syncColors: Boolean,
    onSyncColorsChange: (Boolean) -> Unit,
    expressiveThemeEnabled: Boolean,
    onExpressiveThemeChange: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    pitchBlackEnabled: Boolean,
    onPitchBlackChange: (Boolean) -> Unit,
    onSetupWallpaper: () -> Unit,
    onChangeEffect: () -> Unit,
    onPickSingleImage: () -> Unit,
    onPickMultipleImages: () -> Unit,
    onEditExistingPlaylist: () -> Unit,
    onAdvancedSettings: () -> Unit
) {
    var showImageSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Atmo Engine", style = MaterialTheme.typography.titleLarge)
                        AnimatedContent(
                            targetState = wallpaperActive,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "wallpaperStatusLabel"
                        ) { active ->
                            Text(
                                if (active) "Wallpaper active" else "Wallpaper studio",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    AliveIconButton(
                        icon = Icons.Rounded.Settings,
                        description = "Appearance settings",
                        onClick = { showSettingsSheet = true }
                    )
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 42.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                item {
                    AtmoReveal {
                        if (wallpaperActive) {
                            ActiveWallpaperPanel(
                                effectId = activeEffectId ?: "ORIGINAL",
                                previewBitmap = previewBitmap,
                                isPlaylistMode = isPlaylistMode,
                                onChangeEffect = onChangeEffect,
                                onChangeImage = { showImageSheet = true }
                            )
                        } else {
                            EmptyWallpaperPanel(
                                statusText = statusText,
                                onSetupWallpaper = onSetupWallpaper
                            )
                        }
                    }
                }

                if (wallpaperActive) {
                    item {
                        AtmoReveal(delayMillis = 70) {
                            UnframedSettingsSection(title = "Wallpaper behavior") {
                                SettingSwitchRow(
                                    title = "Sync system colors",
                                    subtitle = if (isPlaylistMode) {
                                        "Updates the system palette with every playlist image"
                                    } else {
                                        "Updates the system palette from this wallpaper"
                                    },
                                    checked = syncColors,
                                    onCheckedChange = onSyncColorsChange
                                )
                                Spacer(Modifier.height(4.dp))
                                AtmoOutlinedButton(
                                    text = "Fine tune",
                                    onClick = onAdvancedSettings,
                                    accent = true,
                                    icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Tune),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImageSheet) {
        WallpaperModeSheet(
            title = "Wallpaper image",
            isPlaylistMode = isPlaylistMode,
            onDismiss = { showImageSheet = false },
            onPickSingle = { showImageSheet = false; onPickSingleImage() },
            onPickMultiple = { showImageSheet = false; onPickMultipleImages() },
            onEditExisting = { showImageSheet = false; onEditExistingPlaylist() }
        )
    }

    if (showSettingsSheet) {
        AppearanceSettingsSheet(
            expressive = expressiveThemeEnabled,
            themeMode = themeMode,
            pitchBlack = pitchBlackEnabled,
            onExpressiveChange = onExpressiveThemeChange,
            onThemeModeChange = onThemeModeChange,
            onPitchBlackChange = onPitchBlackChange,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun ActiveWallpaperPanel(
    effectId: String,
    previewBitmap: ImageBitmap?,
    isPlaylistMode: Boolean,
    onChangeEffect: () -> Unit,
    onChangeImage: () -> Unit
) {
    val effect = EffectCatalog.find(effectId)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(4.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                if (isPlaylistMode) "Active playlist" else "Active wallpaper",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            AtmoChip(if (isPlaylistMode) "Playlist" else "Single image")
        }

        WallpaperTransitionPreview(
            effectId = effectId,
            wallpaper = previewBitmap,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
        )

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                effect.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                effect.transition,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AtmoTonalButton(
                text = "Effect",
                onClick = onChangeEffect,
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Palette),
                modifier = Modifier.weight(1f)
            )
            AtmoTonalButton(
                text = "Image",
                onClick = onChangeImage,
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Wallpaper),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyWallpaperPanel(
    statusText: String,
    onSetupWallpaper: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        WallpaperTransitionPreview(
            effectId = "ORIGINAL",
            wallpaper = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
        )
        Text(
            "Create a wallpaper",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AtmoPrimaryButton(
            text = "Choose effect and image",
            onClick = onSetupWallpaper,
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Wallpaper),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UnframedSettingsSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsSheet(
    expressive: Boolean,
    themeMode: AppThemeMode,
    pitchBlack: Boolean,
    onExpressiveChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onPitchBlackChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val darkThemeActive = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text("Appearance", style = MaterialTheme.typography.headlineSmall)
            }

            SettingSwitchRow(
                title = "Material Expressive",
                checked = expressive,
                onCheckedChange = onExpressiveChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                AtmoSegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = themeMode.ordinal,
                    onSelected = { onThemeModeChange(AppThemeMode.entries[it]) }
                )
            }

            AnimatedVisibility(visible = darkThemeActive) {
                SettingSwitchRow(
                    title = "Pitch-black background",
                    subtitle = "Use pure black instead of the system dark surface.",
                    checked = pitchBlack,
                    onCheckedChange = onPitchBlackChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperModeSheet(
    title: String = "Wallpaper mode",
    isPlaylistMode: Boolean = false,
    onDismiss: () -> Unit,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    onEditExisting: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            ModeOption(
                title = "Single image",
                subtitle = "Use one wallpaper",
                icon = Icons.Rounded.Image,
                onClick = onPickSingle
            )
            ModeOption(
                title = if (isPlaylistMode) "New playlist" else "Playlist",
                subtitle = "Use several rotating images",
                icon = Icons.Rounded.Collections,
                onClick = onPickMultiple
            )
            if (isPlaylistMode && onEditExisting != null) {
                ModeOption(
                    title = "Edit current playlist",
                    subtitle = "Change its images and crops",
                    icon = Icons.Rounded.Edit,
                    onClick = onEditExisting
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val expressive = LocalAtmoExpressive.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.72f),
        label = "modeOptionScale"
    )
    val container by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "modeOptionColor"
    )

    Surface(
        color = container,
        shape = RoundedCornerShape(if (expressive) 28.dp else 20.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = {
                        if (expressive) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        onClick()
                    }
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AliveIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val expressive = LocalAtmoExpressive.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && expressive) 0.9f else 1f,
        animationSpec = spring(stiffness = 460f, dampingRatio = 0.6f),
        label = "iconButtonScale"
    )
    FilledTonalIconButton(
        onClick = {
            if (expressive) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shape = if (expressive) CircleShape else RoundedCornerShape(16.dp),
        interactionSource = interaction,
        modifier = Modifier.size(48.dp).scale(scale)
    ) {
        Icon(icon, contentDescription = description)
    }
}
