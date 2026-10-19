package com.pulsestamp.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.LapBars
import com.pulsestamp.app.ui.theme.Studio
import com.pulsestamp.app.ui.theme.display

/** Big coral BPM that pulses at the workout's own rate (one beat = one in-and-out). */
@Composable
fun PulsingBpm(bpm: Long, size: Int = 50, reduceMotion: Boolean = false) {
    val halfBeatMs = (30_000 / bpm.coerceIn(30, 240)).toInt()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val k by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(halfBeatMs, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )
    // Count up to the number when it changes
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(bpm) { target = bpm.toFloat() }
    val shown by animateFloatAsState(target, tween(1000, easing = FastOutSlowInEasing), label = "count")
    Text(
        "${shown.toInt()}",
        style = display(size, Studio.Coral),
        modifier = Modifier.graphicsLayer {
            if (!reduceMotion) {
                val s = 0.965f + 0.035f * k
                scaleX = s
                scaleY = s
                alpha = 0.62f + 0.38f * k
            }
        },
    )
}

/** Lap bars: height = lap's average HR, best lap in yellow; effort slices in soft coral. */
@Composable
fun LapBarsView(bars: LapBars, modifier: Modifier = Modifier, height: Dp = 26.dp) {
    val max = bars.bars.mapNotNull { it.avgBpm }.maxOrNull()?.toFloat() ?: 1f
    val min = bars.bars.mapNotNull { it.avgBpm }.minOrNull()?.toFloat() ?: 0f
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.bars.forEach { bar ->
            // Stretch the range a little so small differences still read
            val v = bar.avgBpm?.toFloat()
            val frac = if (v == null) 0.08f else if (max == min) 1f else 0.45f + 0.55f * (v - min) / (max - min)
            val grown by animateFloatAsState(frac, tween(900, easing = FastOutSlowInEasing), label = "bar")
            val color = when {
                bar.best -> Studio.Yellow
                bars.effortOverTime -> Studio.Coral.copy(alpha = 0.55f)
                else -> Studio.Text.copy(alpha = 0.3f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(grown)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * Heart-rate line with zones shaded relative to the workout's own peak:
 * top band (90–100% of peak) coral, next band (80–90%) yellow.
 */
@Composable
fun HrChart(samples: List<HrSample>, peak: Long, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(74.dp)) {
        if (samples.size < 2 || peak <= 0) return@Canvas
        val lo = (samples.minOf { it.bpm }.toFloat() - 5f).coerceAtLeast(0f)
        val hi = peak.toFloat()
        fun y(bpm: Float) = size.height * (1f - ((bpm - lo) / (hi - lo)).coerceIn(0f, 1f))
        drawRect(Studio.Coral.copy(alpha = 0.12f), Offset(0f, 0f), Size(size.width, y(hi * 0.9f)))
        drawRect(Studio.Yellow.copy(alpha = 0.08f), Offset(0f, y(hi * 0.9f)), Size(size.width, y(hi * 0.8f) - y(hi * 0.9f)))

        // Downsample to about one point per 2 px
        val t0 = samples.first().time.toEpochMilli()
        val span = (samples.last().time.toEpochMilli() - t0).coerceAtLeast(1)
        val step = (samples.size / (size.width / 2f)).toInt().coerceAtLeast(1)
        val path = Path()
        samples.filterIndexed { i, _ -> i % step == 0 }.forEachIndexed { i, s ->
            val x = size.width * (s.time.toEpochMilli() - t0) / span
            if (i == 0) path.moveTo(x, y(s.bpm.toFloat())) else path.lineTo(x, y(s.bpm.toFloat()))
        }
        drawPath(path, Studio.Coral, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
