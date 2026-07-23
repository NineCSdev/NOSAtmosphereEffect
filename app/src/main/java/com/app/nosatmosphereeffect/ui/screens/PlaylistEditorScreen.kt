package com.app.nosatmosphereeffect.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.image.BitmapDecoder
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoSegmentedControl
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import com.app.nosatmosphereeffect.ui.theme.LocalAtmoExpressive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

data class PlaylistEntry(
    val displayUri: Uri,
    val isEdited: Boolean
)

@Composable
fun PlaylistEditorScreen(
    entries: List<PlaylistEntry>,
    effectId: String,
    title: String = "Edit Playlist",
    playlistTabs: List<String> = emptyList(),
    playlistCounts: List<Int> = emptyList(),
    selectedPlaylist: Int = 0,
    onPlaylistSelected: (Int) -> Unit = {},
    applyLabel: String = "Apply playlist",
    applyEnabled: Boolean = entries.isNotEmpty(),
    onEditItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onAddMore: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { entries.size })
    LaunchedEffect(selectedPlaylist) {
        if (entries.isNotEmpty()) pagerState.scrollToPage(0)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtmoTopBar(
                title = title,
                backIcon = painterResource(R.drawable.ic_arrow_back),
                onBack = onBack
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (playlistTabs.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    AtmoSegmentedControl(
                        options = playlistTabs.mapIndexed { index, label ->
                            "$label ${playlistCounts.getOrElse(index) { 0 }}"
                        },
                        selectedIndex = selectedPlaylist.coerceIn(playlistTabs.indices),
                        onSelected = onPlaylistSelected
                    )
                    Text(
                        if (selectedPlaylist == 0) "Light theme wallpapers" else "Dark theme wallpapers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (entries.isEmpty()) {
                    EmptyPlaylist(
                        label = if (playlistTabs.isEmpty()) {
                            "this playlist"
                        } else if (selectedPlaylist == 0) {
                            "the light playlist"
                        } else {
                            "the dark playlist"
                        }
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        pageSpacing = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        // Pager state can briefly outlive an entry removed during composition.
                        val entry = entries.getOrNull(page) ?: return@HorizontalPager
                        Box(
                            Modifier.graphicsLayer {
                                val pageOffset = (
                                    (pagerState.currentPage - page) +
                                        pagerState.currentPageOffsetFraction
                                    ).absoluteValue.coerceIn(0f, 1f)
                                val emphasis = 1f - pageOffset
                                scaleX = 0.94f + emphasis * 0.06f
                                scaleY = 0.94f + emphasis * 0.06f
                                alpha = 0.72f + emphasis * 0.28f
                            }
                        ) {
                            PlaylistCard(
                                entry = entry,
                                effectId = effectId,
                                isActive = page == pagerState.currentPage,
                                onClick = { onEditItem(page) },
                                onDelete = { onDeleteItem(page) }
                            )
                        }
                    }
                }
            }

            if (entries.isNotEmpty()) {
                PageIndicator(
                    count = entries.size,
                    current = pagerState.currentPage.coerceIn(entries.indices),
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "${pagerState.currentPage + 1} of ${entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AtmoOutlinedButton(
                            text = "Add",
                            onClick = onAddMore,
                            accent = true,
                            icon = painterResource(R.drawable.ic_add),
                            modifier = Modifier.weight(0.38f)
                        )
                        AtmoPrimaryButton(
                            text = applyLabel,
                            onClick = onApply,
                            enabled = applyEnabled,
                            modifier = Modifier.weight(0.62f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    entry: PlaylistEntry,
    effectId: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val expressive = LocalAtmoExpressive.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && expressive) 0.975f else 1f,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.66f),
        label = "playlistCardScale"
    )
    val thumb = rememberThumbnail(context, entry.displayUri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .scale(scale)
            .clip(RoundedCornerShape(if (expressive) 32.dp else 20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = {
                    if (expressive) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
            )
    ) {
        if (thumb != null && isActive) {
            com.app.nosatmosphereeffect.ui.components.WallpaperTransitionPreview(
                effectId = effectId,
                wallpaper = thumb,
                modifier = Modifier.fillMaxSize()
            )
        } else if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }

        if (entry.isEdited) {
            AtmoChip(
                text = "Edited",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f)
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "Remove image",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count.coerceAtMost(9)) { index ->
            val selected = index == current.coerceAtMost(8)
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 7.dp,
                animationSpec = spring(stiffness = 420f, dampingRatio = 0.68f),
                label = "pageIndicatorWidth"
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                label = "pageIndicatorColor"
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width, 7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun EmptyPlaylist(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No images yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap Add to choose photos for $label.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun rememberThumbnail(
    context: Context,
    uri: Uri
): androidx.compose.ui.graphics.ImageBitmap? {
    var image by remember(uri) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { decodeThumbnail(context, uri) }
        image = bmp?.asImageBitmap()
    }
    return image
}

private fun decodeThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        BitmapDecoder.decodeUri(context, uri, maxDimension = 600)
    } catch (error: Exception) {
        Log.w("PlaylistEditorScreen", "Could not create a thumbnail for $uri", error)
        null
    }
}
