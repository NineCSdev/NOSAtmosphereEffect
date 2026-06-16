package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.ui.components.AtmoCard
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoTonalButton
import com.app.nosatmosphereeffect.ui.components.LabeledSlider
import com.app.nosatmosphereeffect.ui.components.SectionHeader
import com.app.nosatmosphereeffect.ui.components.SettingSwitchRow
import com.app.nosatmosphereeffect.ui.theme.AtmoOnSurfaceVariant
import com.app.nosatmosphereeffect.ui.theme.AtmoPurple

@Composable
fun MainScreen(
    wallpaperActive: Boolean,
    statusText: String,
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
    onSetupWallpaper: () -> Unit,
    onChangeEffect: () -> Unit,
    onPickSingleImage: () -> Unit,
    onPickMultipleImages: () -> Unit,
    onEditExistingPlaylist: () -> Unit,
    onAdvancedSettings: () -> Unit
) {
    var showImageDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Hero(statusText = statusText)
            Spacer(Modifier.height(36.dp))

            if (!wallpaperActive) {
                AtmoPrimaryButton(
                    text = "Select Effect & Wallpaper",
                    onClick = onSetupWallpaper,
                    icon = painterResource(R.drawable.ic_wallpaper),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Quick actions
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AtmoTonalButton(
                        text = "Change Effect",
                        onClick = onChangeEffect,
                        icon = painterResource(R.drawable.ic_deblur),
                        modifier = Modifier.weight(1f)
                    )
                    AtmoTonalButton(
                        text = "Change Image",
                        onClick = { showImageDialog = true },
                        icon = painterResource(R.drawable.ic_wallpaper),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionHeader("Display", Modifier.align(Alignment.Start))
                Spacer(Modifier.height(10.dp))

                AtmoCard {
                    LabeledSlider(
                        label = "Dimness",
                        value = dimness,
                        onValueChange = onDimnessChange,
                        valueRange = 0f..0.8f,
                        step = 0.05f
                    )
                    Spacer(Modifier.height(16.dp))
                    AtmoPrimaryButton(
                        text = "Update Dimness",
                        onClick = onApplyDimness,
                        enabled = dimnessDirty,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showBlur) {
                    Spacer(Modifier.height(16.dp))
                    AtmoCard {
                        LabeledSlider(
                            label = "Blur Strength",
                            value = blur,
                            onValueChange = onBlurChange,
                            valueRange = 0f..400f,
                            step = 10f
                        )
                        Spacer(Modifier.height(16.dp))
                        AtmoPrimaryButton(
                            text = "Update Blur",
                            onClick = onApplyBlur,
                            enabled = blurDirty,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                AtmoCard {
                    SettingSwitchRow(
                        title = "Sync System Colors",
                        subtitle = "Updates Material You colors on wallpaper change. " +
                            "Disable if laggy, especially in playlist mode.",
                        checked = syncColors,
                        onCheckedChange = onSyncColorsChange
                    )
                }

                Spacer(Modifier.height(24.dp))
                AtmoOutlinedButton(
                    text = "Fine Tune",
                    onClick = onAdvancedSettings,
                    accent = true,
                    icon = painterResource(R.drawable.ic_tune),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showImageDialog) {
        ImageModeDialog(
            isPlaylistMode = isPlaylistMode,
            onDismiss = { showImageDialog = false },
            onPickSingle = { showImageDialog = false; onPickSingleImage() },
            onPickMultiple = { showImageDialog = false; onPickMultipleImages() },
            onEditExisting = { showImageDialog = false; onEditExistingPlaylist() }
        )
    }
}

@Composable
private fun Hero(statusText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Soft radial glow behind the logo for a little depth.
            Box(
                Modifier
                    .size(180.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AtmoPurple.copy(alpha = 0.20f), AtmoPurple.copy(alpha = 0f))
                        )
                    )
            )
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(AtmoPurple.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Atmo Engine logo",
                    tint = AtmoPurple,
                    modifier = Modifier.size(96.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Atmo Engine",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = AtmoOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ImageModeDialog(
    isPlaylistMode: Boolean,
    onDismiss: () -> Unit,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    onEditExisting: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Select Wallpaper Mode", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                DialogOption("Single Image", "One wallpaper", onPickSingle)
                DialogOption(
                    if (isPlaylistMode) "Create New Playlist" else "Multiple Images (Playlist)",
                    "Rotate through several wallpapers",
                    onPickMultiple
                )
                if (isPlaylistMode) {
                    DialogOption("Edit Existing Playlist", "Adjust your current set", onEditExisting)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AtmoPurple) }
        }
    )
}

@Composable
private fun DialogOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AtmoOnSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}
