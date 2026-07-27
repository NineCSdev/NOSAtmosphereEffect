package com.app.nosatmosphereeffect.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.nosatmosphereeffect.ui.theme.LocalAtmoExpressive
import kotlinx.coroutines.launch

@Composable
fun AtmoAnimatedIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motion: AtmoIconMotion = AtmoIconMotion.PRESS,
    filledTonal: Boolean = false,
    iconTint: Color? = null,
    iconSize: Dp? = null,
    containerColor: Color? = null,
    containerShape: Shape = CircleShape
) {
    val motionEnabled = LocalAtmoExpressive.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = AtmoIconMotionMath.pressTransform(
        motion = motion,
        pressed = pressed,
        motionEnabled = motionEnabled
    )
    val scale by animateFloatAsState(
        targetValue = target.scale,
        animationSpec = spring(stiffness = 480f, dampingRatio = 0.62f),
        label = "atmoIconScale"
    )
    val pressRotation by animateFloatAsState(
        targetValue = target.rotationDegrees,
        animationSpec = spring(stiffness = 440f, dampingRatio = 0.66f),
        label = "atmoIconPressRotation"
    )
    val translationXDp by animateFloatAsState(
        targetValue = target.translationXDp,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.68f),
        label = "atmoIconTranslation"
    )
    val clickRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val buttonModifier = modifier
        .scale(scale)
        .graphicsLayer {
            translationX = with(density) { translationXDp.dp.toPx() }
        }
    val iconModifier = Modifier.graphicsLayer {
        rotationZ = pressRotation + clickRotation.value
    }
    val handleClick = {
        val clickDegrees = AtmoIconMotionMath.clickRotationDegrees(
            motion = motion,
            motionEnabled = motionEnabled
        )
        if (clickDegrees != 0f) {
            scope.launch {
                val targetRotation = clickRotation.value + clickDegrees
                clickRotation.animateTo(
                    targetValue = targetRotation,
                    animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
                )
                clickRotation.snapTo(targetRotation % 360f)
            }
        }
        if (motionEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
        onClick()
    }
    val iconContent: @Composable () -> Unit = {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint ?: LocalContentColor.current,
            modifier = iconModifier.then(
                if (iconSize != null) Modifier.size(iconSize) else Modifier
            )
        )
    }

    if (filledTonal) {
        FilledTonalIconButton(
            onClick = handleClick,
            shape = if (motionEnabled) CircleShape else RoundedCornerShape(16.dp),
            interactionSource = interaction,
            modifier = buttonModifier
        ) {
            iconContent()
        }
    } else if (containerColor != null) {
        Surface(
            shape = containerShape,
            color = containerColor,
            modifier = buttonModifier
        ) {
            IconButton(
                onClick = handleClick,
                interactionSource = interaction
            ) {
                iconContent()
            }
        }
    } else {
        IconButton(
            onClick = handleClick,
            interactionSource = interaction,
            modifier = buttonModifier
        ) {
            iconContent()
        }
    }
}

@Composable
fun AtmoAnimatedIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motion: AtmoIconMotion = AtmoIconMotion.PRESS,
    filledTonal: Boolean = false,
    iconTint: Color? = null,
    iconSize: Dp? = null,
    containerColor: Color? = null,
    containerShape: Shape = CircleShape
) {
    AtmoAnimatedIconButton(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        motion = motion,
        filledTonal = filledTonal,
        iconTint = iconTint,
        iconSize = iconSize,
        containerColor = containerColor,
        containerShape = containerShape
    )
}
