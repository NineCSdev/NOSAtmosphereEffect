package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.ui.components.AtmoCard
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoTonalButton
import com.app.nosatmosphereeffect.ui.components.LabeledSlider
import com.app.nosatmosphereeffect.ui.components.SectionHeader
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow
import com.app.nosatmosphereeffect.ui.components.WallpaperTransitionPreview
import com.app.nosatmosphereeffect.ui.model.EffectCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    wallpaperActive: Boolean,
    statusText: String,
    activeEffectId: String?,
    previewBitmap: ImageBitmap?,
    isPlaylistMode: Boolean,
    showBlur: Boolean,
    dimness: Float,
    dimnessDirty: Boolean,
    onDimnessChange: (Float) -> Unit,
    onApplyDimness: () -> Unit,
    blur: Float,
    blurDirty: Boolean,
    onBlurChange: (Float) -> Unit,
    onApplyBlur: () -> Unit,
    syncColors: Boolean,
    onSyncColorsChange: (Boolean) -> Unit,
    expressiveThemeEnabled: Boolean,
    onExpressiveThemeChange: (Boolean) -> Unit,
    onSetupWallpaper: () -> Unit,
    onChangeEffect: () -> Unit,
    onPickSingleImage: () -> Unit,
    onPickMultipleImages: () -> Unit,
    onEditExistingPlaylist: () -> Unit,
    onAdvancedSettings: () -> Unit
) {
    var showImageSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Atmo Engine", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (wallpaperActive) "Wallpaper active" else "Wallpaper studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item {
                    AnimatedContent(
                        targetState = wallpaperActive,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 10 }) togetherWith
                                (fadeOut() + slideOutVertically { -it / 10 })
                        },
                        label = "wallpaperState"
                    ) { active ->
                        if (active) {
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
                        SettingsSection(title = "Display") {
                            LabeledSlider(
                                label = "Dimness",
                                value = dimness,
                                onValueChange = onDimnessChange,
                                valueRange = 0f..0.8f,
                                step = 0.05f,
                                valueText = { "${(it * 100).toInt()}%" }
                            )
                            AnimatedVisibility(
                                visible = dimnessDirty,
                                enter = fadeIn() + slideInVertically { it / 2 },
                                exit = fadeOut() + slideOutVertically { it / 2 }
                            ) {
                                AtmoPrimaryButton(
                                    text = "Apply dimness",
                                    onClick = onApplyDimness,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            AnimatedVisibility(visible = showBlur) {
                                Column {
                                    Spacer(Modifier.height(18.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(18.dp))
                                    LabeledSlider(
                                        label = "Blur strength",
                                        value = blur,
                                        onValueChange = onBlurChange,
                                        valueRange = 0f..400f,
                                        step = 10f
                                    )
                                    AnimatedVisibility(
                                        visible = blurDirty,
                                        enter = fadeIn() + slideInVertically { it / 2 },
                                        exit = fadeOut() + slideOutVertically { it / 2 }
                                    ) {
                                        AtmoPrimaryButton(
                                            text = "Apply blur",
                                            onClick = onApplyBlur,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsSection(title = "Wallpaper behavior") {
                            SettingSwitchRow(
                                title = "Sync system colors",
                                subtitle = if (isPlaylistMode) {
                                    "Off is recommended for rotating playlists."
                                } else {
                                    "Refresh Material You colors with this wallpaper."
                                },
                                checked = syncColors,
                                onCheckedChange = onSyncColorsChange
                            )
                            Spacer(Modifier.height(8.dp))
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

                item {
                    SettingsSection(title = "Appearance") {
                        SettingSwitchRow(
                            title = "Material 3 Expressive",
                            subtitle = "Dynamic color, expressive shapes, and spring motion.",
                            checked = expressiveThemeEnabled,
                            onCheckedChange = onExpressiveThemeChange
                        )
                    }
                }
            }
        }
    }

    if (showImageSheet) {
        ImageModeSheet(
            isPlaylistMode = isPlaylistMode,
            onDismiss = { showImageSheet = false },
            onPickSingle = { showImageSheet = false; onPickSingleImage() },
            onPickMultiple = { showImageSheet = false; onPickMultipleImages() },
            onEditExisting = { showImageSheet = false; onEditExistingPlaylist() }
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
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
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
                .aspectRatio(1.48f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    effect.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    effect.transition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WallpaperTransitionPreview(
            effectId = "ORIGINAL",
            wallpaper = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.48f)
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
private fun SettingsSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title)
        AtmoCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageModeSheet(
    isPlaylistMode: Boolean,
    onDismiss: () -> Unit,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    onEditExisting: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Wallpaper image",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            ModeOption(
                title = "Single image",
                subtitle = "Replace the current wallpaper",
                icon = Icons.Rounded.Image,
                onClick = onPickSingle
            )
            ModeOption(
                title = if (isPlaylistMode) "New playlist" else "Playlist",
                subtitle = "Choose several rotating images",
                icon = Icons.Rounded.Collections,
                onClick = onPickMultiple
            )
            if (isPlaylistMode) {
                ModeOption(
                    title = "Edit current playlist",
                    subtitle = "Crop, add, or remove images",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
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
