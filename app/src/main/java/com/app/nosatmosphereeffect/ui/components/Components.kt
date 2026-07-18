package com.app.nosatmosphereeffect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.app.nosatmosphereeffect.ui.theme.AtmoOnSurfaceVariant
import com.app.nosatmosphereeffect.ui.theme.AtmoOutline
import com.app.nosatmosphereeffect.ui.theme.AtmoPurple
import kotlin.math.roundToInt

/* -------------------------------------------------------------------------- */
/*  Buttons                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
fun AtmoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AtmoTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = AtmoPurple, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AtmoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    icon: Painter? = null
) {
    val contentColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/* -------------------------------------------------------------------------- */
/*  Cards / sections                                                           */
/* -------------------------------------------------------------------------- */

/** A bordered, slightly-elevated surface used to group related settings. */
@Composable
fun AtmoCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, AtmoOutline)
    ) {
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AtmoOnSurfaceVariant,
        modifier = modifier
    )
}

/* -------------------------------------------------------------------------- */
/*  Sliders                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * Label + live value + a clean (tick-free) slider. [step], when > 0, snaps the
 * emitted value and drives the value read-out formatting.
 */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    valueText: (Float) -> String = { defaultFormat(it, step) }
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                valueText(value),
                style = MaterialTheme.typography.labelLarge,
                color = AtmoPurple
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = { raw -> onValueChange(if (step > 0f) snap(raw, valueRange, step) else raw) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AtmoPurple,
                activeTrackColor = AtmoPurple,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

private fun snap(raw: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    val steps = ((raw - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}

private fun defaultFormat(value: Float, step: Float): String =
    when {
        step >= 1f || step == 0f && value == value.roundToInt().toFloat() -> value.roundToInt().toString()
        step >= 0.1f -> String.format("%.1f", value)
        else -> String.format("%.2f", value)
    }

/* -------------------------------------------------------------------------- */
/*  Switch row                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AtmoOnSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = AtmoPurple,
                uncheckedThumbColor = AtmoOnSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = AtmoOutline
            )
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Dropdown                                                                   */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtmoDropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    Column(modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options.getOrElse(safeIndex) { "" },
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = atmoFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(helper, style = MaterialTheme.typography.bodySmall, color = AtmoOnSurfaceVariant)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Number field (with optional info action)                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun AtmoNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    decimal: Boolean = false,
    infoIcon: Painter? = null,
    onInfoClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            trailingIcon = if (infoIcon != null && onInfoClick != null) {
                {
                    IconButton(onClick = onInfoClick) {
                        Icon(infoIcon, contentDescription = "More info", tint = AtmoPurple)
                    }
                }
            } else null,
            colors = atmoFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(helper, style = MaterialTheme.typography.bodySmall, color = AtmoOnSurfaceVariant)
        }
    }
}

@Composable
private fun atmoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AtmoPurple,
    unfocusedBorderColor = AtmoOutline,
    focusedLabelColor = AtmoPurple,
    unfocusedLabelColor = AtmoOnSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = AtmoPurple,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)

/* -------------------------------------------------------------------------- */
/*  Top app bar                                                                */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtmoTopBar(
    title: String,
    backIcon: Painter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(backIcon, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

/* -------------------------------------------------------------------------- */
/*  Small pill / chip                                                          */
/* -------------------------------------------------------------------------- */

@Composable
fun AtmoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(AtmoPurple.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = AtmoPurple)
    }
}

/* -------------------------------------------------------------------------- */
/*  Dialog selection row                                                       */
/* -------------------------------------------------------------------------- */

/**
 * A tappable option row used inside selection dialogs (e.g. choose single vs
 * playlist). Renders a bold title with an optional supporting line, wrapped in
 * a subtle rounded surface for a clear, touch-friendly target.
 */
@Composable
fun AtmoDialogRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AtmoOnSurfaceVariant
                )
            }
        }
    }
}

/** Reusable no-op interaction source factory to avoid re-allocations in lists. */
@Composable
fun rememberNoRippleInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }
