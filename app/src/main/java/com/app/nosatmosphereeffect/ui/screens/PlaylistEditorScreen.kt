package com.app.nosatmosphereeffect.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoOutlinedButton
import com.app.nosatmosphereeffect.ui.components.AtmoPrimaryButton
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight view of a playlist entry the screen needs in order to render. */
data class PlaylistEntry(
    val displayUri: Uri,
    val isEdited: Boolean
)

@Composable
fun PlaylistEditorScreen(
    entries: List<PlaylistEntry>,
    effectId: String,
    onEditItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onAddMore: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { entries.size })
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtmoTopBar(
                title = "Edit Playlist",
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (entries.isEmpty()) {
                    EmptyPlaylist()
                } else {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        pageSpacing = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        // guard against transient out-of-range during deletions
                        val entry = entries.getOrNull(page) ?: return@HorizontalPager
                        PlaylistCard(
                            entry = entry,
                            effectId = effectId,
                            onClick = { onEditItem(page) },
                            onDelete = { onDeleteItem(page) }
                        )
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
                        text = "Apply playlist",
                        onClick = onApply,
                        enabled = entries.isNotEmpty(),
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    entry: PlaylistEntry,
    effectId: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val thumb = rememberThumbnail(context, entry.displayUri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
    ) {
        if (thumb != null) {
            com.app.nosatmosphereeffect.ui.components.WallpaperTransitionPreview(
                effectId = effectId,
                wallpaper = thumb,
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

        // Delete button (top-right)
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
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 18.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

@Composable
private fun EmptyPlaylist() {
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
            "Tap \"Add More\" to choose photos for your playlist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Thumbnail loading (off the main thread, EXIF-aware)                        */
/* -------------------------------------------------------------------------- */

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
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.inSampleSize = calculateInSampleSize(options, 400, 600)
        options.inJustDecodeBounds = false

        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        applyExifRotation(context, uri, bmp)
    } catch (e: Exception) {
        null
    }
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    return try {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: return bitmap

        val rotation = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        bitmap
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
