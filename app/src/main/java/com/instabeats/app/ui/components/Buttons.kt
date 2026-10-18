package com.instabeats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body

private val Pill = RoundedCornerShape(99.dp)

/** The one yellow action on a screen. */
@Composable
fun YellowButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Pill)
            .background(Studio.Yellow)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(15f, FontWeight.ExtraBold, Studio.Black))
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(Pill)
            .border(1.dp, Color(0x4DFFFFFF), Pill)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(13f, FontWeight.SemiBold))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Pill)
            .background(if (selected) Studio.Yellow else Studio.Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 12.dp),
    ) {
        Text(
            text,
            style = body(12f, if (selected) FontWeight.ExtraBold else FontWeight.Normal, if (selected) Studio.Black else Studio.Text),
        )
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        "‹ Back",
        style = body(14f, FontWeight.SemiBold),
        modifier = modifier
            .clip(Pill)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    )
}
