package com.pulsestamp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulsestamp.app.ui.theme.Studio
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/** Set by screens that draw a photo behind glass; null means plain black behind. */
val LocalHaze = compositionLocalOf<HazeState?> { null }

/**
 * Frosted glass panel: real blur of what's behind on Android 12+ (Haze), a tinted panel on 9–11.
 * [tint] is the panel colour laid over the blur.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    tint: Color = Color(0x66121214),
    blur: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val haze = LocalHaze.current
    val surface = if (haze != null) {
        Modifier.hazeChild(haze, shape, HazeStyle(tint = tint, blurRadius = blur, noiseFactor = 0f))
    } else {
        Modifier.background(tint.copy(alpha = maxOf(tint.alpha, 0.5f)), shape)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(surface)
            .border(1.dp, Studio.GlassBorder, shape),
        content = content,
    )
}
