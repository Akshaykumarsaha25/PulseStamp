package com.instabeats.app.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.instabeats.app.ui.AppState
import com.instabeats.app.ui.SamsungGps
import com.instabeats.app.ui.WorkoutFilter
import com.instabeats.app.ui.bestLine
import com.instabeats.app.ui.dayLabel
import com.instabeats.app.ui.duration
import com.instabeats.app.ui.madeLabel
import com.instabeats.app.ui.sizeLabel
import com.instabeats.app.ui.unitsLabel
import com.instabeats.app.ui.components.Chip
import com.instabeats.app.ui.components.Glass
import com.instabeats.app.ui.components.GhostButton
import com.instabeats.app.ui.components.HeroSlideshow
import com.instabeats.app.ui.components.LapBarsView
import com.instabeats.app.ui.components.LocalHaze
import com.instabeats.app.ui.components.PulsingBpm
import com.instabeats.app.ui.components.YellowButton
import com.instabeats.app.ui.components.cardScrim
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body
import com.instabeats.app.ui.theme.display
import com.instabeats.app.model.ExportedFile
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** Space the content leaves free for the floating tab bar. */
val TabBarSpace = 84.dp

@Composable
fun HomeScreen(
    state: AppState,
    reduceMotion: Boolean,
    onOpen: (String) -> Unit,
    onExport: (String) -> Unit,
    onRetry: () -> Unit,
    tabBar: @Composable () -> Unit,
) {
    val haze = remember { HazeState() }
    var position by remember { mutableFloatStateOf(0f) }
    // Card tint follows the photo's brightness; rounded so the card doesn't recompose every frame
    val tint by remember { derivedStateOf { (cardScrim(position) * 50).toInt() / 50f } }

    Box(Modifier.fillMaxSize()) {
        HeroSlideshow(Modifier.haze(haze), reduceMotion) { position = it }
        CompositionLocalProvider(LocalHaze provides haze) {
            Wordmark(Modifier.statusBarsPadding().padding(start = 22.dp, top = 36.dp))
            Box(
                Modifier.fillMaxSize().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = TabBarSpace),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Glass(Modifier.fillMaxWidth(), tint = Color(0xFF0E0E10).copy(alpha = tint)) {
                    Column(Modifier.padding(16.dp)) { HomeCard(state, reduceMotion, onOpen, onExport, onRetry) }
                }
            }
            tabBar()
        }
    }
}

@Composable
private fun HomeCard(
    state: AppState,
    reduceMotion: Boolean,
    onOpen: (String) -> Unit,
    onExport: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val latest = state.latest
    when {
        state.busy -> {
            Text("Health Connect is busy. Try again in a minute.", style = body(15f, FontWeight.ExtraBold))
            Spacer(Modifier.height(12.dp))
            YellowButton("Retry", onClick = onRetry)
        }
        latest == null && state.loading -> Text("Reading your workouts…", style = body(13f, color = Studio.Muted))
        latest == null -> {
            Text("No workouts in the last ${state.rangeDays} days.", style = body(15f, FontWeight.ExtraBold))
            Text("Record one on your watch, then come back.", style = body(13f, color = Studio.Muted))
        }
        else -> {
            val w = latest.workout
            Column(Modifier.clickable { onOpen(w.id) }) {
                Text("${w.name} · ${dayLabel(w.start)} · ${unitsLabel(w)}", style = body(12f, color = Studio.Muted))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    if (w.avgBpm != null) {
                        PulsingBpm(w.avgBpm, 50, reduceMotion)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text("avg bpm · peak ${w.peakBpm ?: w.avgBpm}", style = body(12f, color = Studio.Muted), modifier = Modifier.padding(bottom = 8.dp))
                    } else {
                        Text("No heart rate", style = display(28, Studio.Mu))
                    }
                }
                LapBarsView(latest.bars)
                Text(bestLine(latest.bars), style = body(11f, color = Studio.Muted), modifier = Modifier.padding(top = 5.dp))
            }
            Spacer(Modifier.height(12.dp))
            YellowButton("Export for Insta360", enabled = w.avgBpm != null) { onExport(w.id) }
        }
    }
}

@Composable
fun ScreenHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 14.dp, top = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = display(26))
        trailing()
    }
}

@Composable
fun ListCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Glass(modifier.fillMaxWidth(), radius = 16.dp, tint = Color(0x80141416)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) { content() }
    }
}

@Composable
fun WorkoutsScreen(
    state: AppState,
    onFilter: (WorkoutFilter) -> Unit,
    onCycleRange: () -> Unit,
    onShow30: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onAllowSamsung: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Studio.Black)) {
        ScreenHeader("Workouts")
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WorkoutFilter.entries.forEach { f -> Chip(f.label, state.filter == f) { onFilter(f) } }
            Chip("Last ${state.rangeDays} days ▾", false, onCycleRange)
        }
        val list = state.visibleWorkouts
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = TabBarSpace + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (state.samsungGps == SamsungGps.NEEDS_ACCESS) item { SamsungGpsCard(state.samsungError, onAllowSamsung) }
            when {
                state.busy -> item {
                    ListCard {
                        Text("Health Connect is busy. Try again in a minute.", style = body(14f, FontWeight.ExtraBold))
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Retry", Modifier.fillMaxWidth(), onRetry)
                    }
                }
                state.loading && list.isEmpty() -> item { Text("Reading your workouts…", style = body(13f, color = Studio.Mu)) }
                list.isEmpty() && state.workouts.isNotEmpty() -> item {
                    ListCard {
                        val what = if (state.filter == WorkoutFilter.WITH_GPS) "with GPS" else "without GPS"
                        Text("No workouts $what in the last ${state.rangeDays} days.", style = body(14f, FontWeight.ExtraBold))
                        val others = "You have ${state.workouts.size} other workout${if (state.workouts.size == 1) "" else "s"}."
                        Text(
                            if (state.filter == WorkoutFilter.WITH_GPS) {
                                "$others For GPS, start an outdoor Bike, Mountain biking, Run, Walk or Hike workout on your watch."
                            } else {
                                others
                            },
                            style = body(12f, color = Studio.Mu),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        )
                        GhostButton("Show all", Modifier.fillMaxWidth()) { onFilter(WorkoutFilter.ALL) }
                    }
                }
                list.isEmpty() -> item {
                    ListCard {
                        Text("No workouts in the last ${state.rangeDays} days.", style = body(14f, FontWeight.ExtraBold))
                        Text("Record one on your watch, then come back.", style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                        if (state.rangeDays < 30) GhostButton("Show 30 days", Modifier.fillMaxWidth(), onShow30)
                    }
                }
                else -> items(list, key = { it.id }) { w ->
                    val exported = state.marks.containsKey(w.id)
                    ListCard(Modifier.alpha(if (exported) 0.6f else 1f), onClick = { onOpen(w.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(w.name, style = body(15f, FontWeight.ExtraBold))
                            Text(if (w.hasGps) "GPS · ${unitsLabel(w)}" else "HR only", style = body(11f, color = Studio.Mu))
                        }
                        Text(
                            "${dayLabel(w.start)} · ${duration(w)}" + if (exported) " · Exported ✓" else "",
                            style = body(12f, color = Studio.Mu),
                        )
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                            Text(w.avgBpm?.toString() ?: "–", style = display(22, Studio.Coral))
                            Text(" avg bpm", style = body(11f, color = Studio.Mu), modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Galaxy Watch routes stay in Samsung Health until the user lets us read them. */
@Composable
private fun SamsungGpsCard(error: String?, onAllow: () -> Unit) {
    ListCard {
        Text("Get GPS from Samsung Health", style = body(14f, FontWeight.ExtraBold))
        Text(
            "Your Galaxy Watch routes are kept in Samsung Health. Allow InstaBeats to read your exercise and its location.",
            style = body(12f, color = Studio.Mu),
            modifier = Modifier.padding(top = 6.dp),
        )
        error?.let { Text("Samsung Health said: $it", style = body(12f, color = Studio.Coral), modifier = Modifier.padding(top = 6.dp)) }
        Text(
            "Nothing happens? Turn on Developer mode first. See FAQ › Get GPS from a Samsung Galaxy Watch.",
            style = body(12f, color = Studio.Mu),
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        YellowButton("Allow Samsung Health", onClick = onAllow)
    }
}

@Composable
fun ExportsScreen(
    state: AppState,
    onShare: (ExportedFile) -> Unit,
    onDelete: (ExportedFile) -> Unit,
    onGuide: () -> Unit,
    onPrivacy: () -> Unit,
    onGoWorkouts: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Studio.Black)) {
        ScreenHeader("Exports") { GhostButton("How to use", onClick = onGuide) }
        Text(
            "Files in Downloads/InstaBeats",
            style = body(12f, color = Studio.Mu),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 10.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = TabBarSpace + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (state.exports.isEmpty()) {
                item {
                    ListCard {
                        Text("Your exported files show up here.", style = body(14f, FontWeight.ExtraBold))
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Go to Workouts", Modifier.fillMaxWidth(), onGoWorkouts)
                    }
                }
            }
            items(state.exports, key = { it.uri }) { f ->
                ListCard {
                    Text(f.name, style = body(13f, FontWeight.ExtraBold))
                    Text("${madeLabel(f.createdMillis)} · ${sizeLabel(f.sizeBytes)}", style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GhostButton("Share", Modifier.weight(1f)) { onShare(f) }
                        GhostButton("Delete", Modifier.weight(1f)) { onDelete(f) }
                    }
                }
            }
            item {
                Text(
                    "Privacy",
                    style = body(12f, color = Studio.Mu).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onPrivacy).padding(12.dp),
                )
            }
        }
    }
}
