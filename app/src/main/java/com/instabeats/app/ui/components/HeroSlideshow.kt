package com.instabeats.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.instabeats.app.R
import kotlin.math.floor
import kotlin.math.pow

/** One hero photo with its measured mean luma in the title zone and the card zone. */
data class Hero(@DrawableRes val res: Int, val titleLuma: Float, val cardLuma: Float)

val HEROES = listOf(
    Hero(R.drawable.hero_mtb_air, 78f, 33f),
    Hero(R.drawable.hero_drift_night, 89f, 40f),
    Hero(R.drawable.hero_supermoto, 75f, 94f),
    Hero(R.drawable.hero_planet_circuit, 131f, 56f),
    Hero(R.drawable.hero_helmet_pov, 17f, 72f),
)

private const val HOLD = 4200L
private const val FADE = 2200L
private const val CYCLE = HOLD + FADE

private fun scrimAlpha(luma: Float, floor: Float, cap: Float) = (1f - 60f / luma).coerceIn(floor, cap)

/** Scrim strength over the title for slide position [p] (e.g. 1.4 = 40% into the fade from slide 1 to 2). */
fun titleScrim(p: Float): Float = lerpScrim(p) { scrimAlpha(it.titleLuma, 0.40f, 0.78f) }

/** Glass tint alpha for the Home card at slide position [p]. */
fun cardScrim(p: Float): Float = lerpScrim(p) { scrimAlpha(it.cardLuma, 0.38f, 0.70f) }

private inline fun lerpScrim(p: Float, of: (Hero) -> Float): Float {
    val n = HEROES.size
    val i = floor(p).toInt().mod(n)
    val j = (i + 1) % n
    val f = p - floor(p)
    return of(HEROES[i]) * (1 - f) + of(HEROES[j]) * f
}

private fun ease(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/**
 * Full-bleed fade-only slideshow: about 4 s hold, 2.2 s crossfade, one slow drift/zoom per photo,
 * with a top-and-bottom scrim sized from each photo's brightness. [onPosition] reports the
 * slide position every frame so the Home card can follow it.
 */
@Composable
fun HeroSlideshow(modifier: Modifier = Modifier, reduceMotion: Boolean = false, onPosition: (Float) -> Unit = {}) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reduceMotion) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                elapsed = now - start
                onPosition(position(elapsed))
            }
        }
    }
    // Recompose only when the pair of visible photos changes, not every frame
    val visible by remember { derivedStateOf { slots(elapsed).let { it.a to it.b } } }
    Box(modifier.fillMaxSize()) {
        HEROES.forEachIndexed { index, hero ->
            // Only the current and next photo are composed, so at most two bitmaps are in memory
            if (index != visible.first && index != visible.second) return@forEachIndexed
            key(index) {
            Image(
                painter = painterResource(hero.res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val (ca, cb, cf) = slots(elapsed)
                        alpha = when (index) { ca -> 1 - cf; cb -> cf; else -> 0f }
                        if (!reduceMotion && alpha > 0f) {
                            val local = (elapsed % CYCLE).toFloat()
                            val life = if (index == ca) (CYCLE + local) / (CYCLE + FADE) else local / (CYCLE + FADE)
                            val zoom = 1.12f - 0.08f * life
                            scaleX = zoom
                            scaleY = zoom
                            translationX = size.width * ((if (index % 2 == 1) -1 else 1) * (1.6f - 3.2f * life)) / 100f
                        }
                    },
            )
            }
        }
        // Scrim: darker at the top for the wordmark, clear in the middle, dark at the bottom for the card
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val top = titleScrim(position(elapsed))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = top),
                            0.30f to Color.Black.copy(alpha = top * 0.55f),
                            0.48f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                            start = Offset.Zero,
                            end = Offset(0f, size.height),
                        )
                    )
                },
        )
    }
}

private data class Slots(val a: Int, val b: Int, val f: Float)

private fun slots(elapsed: Long): Slots {
    val n = HEROES.size
    val k = elapsed / CYCLE
    val local = elapsed % CYCLE
    val f = if (local < HOLD) 0f else ease((local - HOLD).toFloat() / FADE)
    return Slots((k % n).toInt(), ((k + 1) % n).toInt(), f)
}

private fun position(elapsed: Long): Float {
    val s = slots(elapsed)
    return s.a + s.f
}
