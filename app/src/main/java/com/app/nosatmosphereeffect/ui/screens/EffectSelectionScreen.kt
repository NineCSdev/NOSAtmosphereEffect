package com.app.nosatmosphereeffect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.ui.components.AtmoCard
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import com.app.nosatmosphereeffect.ui.components.WallpaperTransitionPreview
import com.app.nosatmosphereeffect.ui.model.EffectCatalog
import com.app.nosatmosphereeffect.ui.model.EffectItem

@Composable
fun EffectSelectionScreen(
    title: String,
    effects: List<EffectItem>,
    previewBitmap: ImageBitmap?,
    onEffectClick: (EffectItem) -> Unit,
    onBack: () -> Unit
) {
    var selectedId by remember(effects) { mutableStateOf(effects.firstOrNull()?.id.orEmpty()) }
    var autoPlay by remember { mutableStateOf(true) }
    var manualProgress by remember { mutableFloatStateOf(0f) }
    val selected = effects.firstOrNull { it.id == selectedId } ?: effects.first()

    LaunchedEffect(selectedId) { autoPlay = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtmoTopBar(
                title = title,
                backIcon = painterResource(R.drawable.ic_arrow_back),
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                AtmoPrimaryButton(
                    text = "Continue with ${selected.title}",
                    onClick = { onEffectClick(selected) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    WallpaperTransitionPreview(
                        effectId = selected.id,
                        wallpaper = previewBitmap,
                        progress = if (autoPlay) null else manualProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.48f)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier.weight(1f),
                            label = "selectedEffectDetails"
                        ) { effect ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    effect.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    effect.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                AtmoChip(effect.transition)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        PreviewPositionControls(
                            autoPlay = autoPlay,
                            progress = manualProgress,
                            onAutoPlay = { autoPlay = true },
                            onPosition = {
                                autoPlay = false
                                manualProgress = it
                            }
                        )
                    }
                }

                item {
                    Text(
                        "Effects",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(effects.chunked(2), key = { row -> row.joinToString { it.id } }) { rowEffects ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowEffects.forEach { effect ->
                            EffectCard(
                                effect = effect,
                                selected = effect.id == selectedId,
                                onClick = { selectedId = effect.id },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowEffects.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectCard(
    effect: EffectItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AtmoCard(
        modifier = modifier.height(142.dp),
        contentPadding = PaddingValues(14.dp),
        selected = selected,
        onClick = onClick
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = effectIcon(effect.id),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            effect.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            effect.transition,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewPositionControls(
    autoPlay: Boolean,
    progress: Float,
    onAutoPlay: () -> Unit,
    onPosition: (Float) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(3.dp)) {
            PreviewIconButton(
                selected = !autoPlay && progress == 0f,
                icon = Icons.Rounded.Lock,
                description = "Show lock screen",
                onClick = { onPosition(0f) }
            )
            PreviewIconButton(
                selected = autoPlay,
                icon = Icons.Rounded.PlayArrow,
                description = "Play transition",
                onClick = onAutoPlay
            )
            PreviewIconButton(
                selected = !autoPlay && progress == 1f,
                icon = Icons.Rounded.Home,
                description = "Show home screen",
                onClick = { onPosition(1f) }
            )
        }
    }
}

@Composable
private fun PreviewIconButton(
    selected: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun effectIcon(id: String): ImageVector = when (EffectCatalog.family(id)) {
    "FROSTED" -> Icons.Rounded.BlurOn
    "HALFTONE" -> Icons.Rounded.Grain
    "COLORFILL" -> Icons.Rounded.ColorLens
    "CANVAS" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Swipe
}
