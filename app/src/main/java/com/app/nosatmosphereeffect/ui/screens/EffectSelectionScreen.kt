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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.ui.components.AtmoCard
import com.app.nosatmosphereeffect.ui.components.AtmoChip
import com.app.nosatmosphereeffect.ui.components.AtmoTopBar
import com.app.nosatmosphereeffect.ui.theme.AtmoOnSurfaceVariant
import com.app.nosatmosphereeffect.ui.theme.AtmoPurple

/** Effect model used by both the selection screen and the hosting activity. */
data class EffectItem(
    val id: String,
    val title: String,
    val transition: String,
    val description: String
)

@Composable
fun EffectSelectionScreen(
    title: String,
    effects: List<EffectItem>,
    onEffectClick: (EffectItem) -> Unit,
    onBack: () -> Unit
) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Pick how your wallpaper transforms when you wake the screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AtmoOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            items(effects, key = { it.id }) { effect ->
                EffectCard(effect = effect, onClick = { onEffectClick(effect) })
            }
        }
    }
}

@Composable
private fun EffectCard(effect: EffectItem, onClick: () -> Unit) {
    AtmoCard(
        modifier = Modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    effect.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AtmoPurple
                )
                Spacer(Modifier.height(8.dp))
                AtmoChip(text = effect.transition)
                Spacer(Modifier.height(10.dp))
                Text(
                    effect.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AtmoOnSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AtmoPurple.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = AtmoPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
