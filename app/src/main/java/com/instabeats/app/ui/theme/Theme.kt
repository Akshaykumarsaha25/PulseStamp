package com.instabeats.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.instabeats.app.R

/** "Studio": true black, yellow only for actions, coral only for heart rate. */
object Studio {
    val Black = Color(0xFF000000)
    val Text = Color(0xFFF5F5F2)
    val Muted = Color(0xD6F5F5F2)
    val Mu = Color(0xB3F5F5F2)
    val Yellow = Color(0xFFFFC93C)
    val Coral = Color(0xFFFF6B5E)
    val Green = Color(0xFF7BE08F)
    val Sheet = Color(0xFF141416)
    val Chip = Color(0xFF1C1C1C)
    val GlassBorder = Color(0x29FFFFFF)
    val Hairline = Color(0x1AFFFFFF)
}

val Unbounded = FontFamily(Font(R.font.unbounded_black, FontWeight.Black))

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/** The stacked wordmark and big numbers. */
fun display(size: Int, color: Color = Studio.Text) = TextStyle(
    fontFamily = Unbounded,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    lineHeight = (size * 0.95).sp,
    letterSpacing = (-0.02).em,
    color = color,
)

fun body(size: Float = 14f, weight: FontWeight = FontWeight.Normal, color: Color = Studio.Text) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.45f).sp,
    color = color,
)

@Composable
fun InstaBeatsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Studio.Yellow,
            onPrimary = Studio.Black,
            background = Studio.Black,
            surface = Studio.Black,
            onBackground = Studio.Text,
            onSurface = Studio.Text,
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = body(16f),
            bodyMedium = body(14f),
            bodySmall = body(12f),
        ),
        content = content,
    )
}
