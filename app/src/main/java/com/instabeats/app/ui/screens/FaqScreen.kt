package com.instabeats.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.instabeats.app.model.fromSamsung
import com.instabeats.app.ui.AppState
import com.instabeats.app.ui.SamsungGps
import com.instabeats.app.ui.components.GhostButton
import com.instabeats.app.ui.madeLabel
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body
import com.instabeats.app.ui.theme.display
import java.time.Duration
import java.time.Instant

private class Faq(val question: String, val steps: List<String>, val note: String? = null)

private val FAQS = listOf(
    Faq(
        "My latest workouts don't show",
        listOf(
            "Open Samsung Health so your watch syncs to the phone.",
            "Open Settings › Health Connect › App permissions › Samsung Health. Turn Allow all off, then on again.",
            "In Samsung Health, open Settings › Privacy and turn on \"Consent to the processing of health and wellness data\".",
            "Open Health Connect › Data and access › Exercise to check the workout is there. If it is, tap Check again above.",
        ),
        "Workouts recorded while sync was broken may not come back. New workouts sync again once the steps above are done.",
    ),
    Faq(
        "A workout shows but has no GPS",
        listOf(
            "Check the workout was recorded with GPS on the watch or phone.",
            "Samsung Health doesn't share routes with Health Connect, so InstaBeats reads them from Samsung Health directly. When it asks, allow Exercise and Exercise location.",
            "For other watches, when InstaBeats asks to read the route, tap Allow. If you refused it, export again and allow it this time.",
            "If there's no route, you can still export heart rate only. Insta360 gets a still track with your heart rate.",
        ),
    ),
    Faq(
        "Get GPS from a Samsung Galaxy Watch",
        listOf(
            "Open Samsung Health and tap ⋮ › Settings › About Samsung Health.",
            "Tap the version line quickly 10 times or more, then tap \"Developer mode (Samsung Health Data SDK)\" and agree to the notice. Not the one that says \"SDK for Android\".",
            "Turn on the Developer mode switch. Leave App package name and Access code empty.",
            "Back in InstaBeats, open Workouts and tap Allow Samsung Health (or export a ride). Allow Exercise and Location.",
        ),
        "This only lets InstaBeats read your workouts and routes on this phone. InstaBeats has no internet access, so nothing is ever sent anywhere automatically. A file only leaves your phone if you choose to share it.",
    ),
    Faq(
        "Record a ride with GPS",
        listOf(
            "Before you start, pick an outdoor workout on your watch: Bike, Mountain biking, Running, Walking or Hiking.",
            "Indoor types like Bike indoor, Treadmill and Other workout don't record GPS, so they export heart rate only.",
            "Wait for the watch to find GPS before you tap Start. Ride outdoors for a few minutes.",
            "Afterwards, the ride shows under With GPS in InstaBeats.",
        ),
    ),
    Faq(
        "My video starts later than the workout",
        listOf(
            "That's fine. The file covers your whole workout, from start to stop.",
            "Insta360 matches the clip by its time, so a video started 2 or 20 minutes in lines up with the right part of the ride.",
            "Start the workout before you film and stop it after, and keep the camera's clock right.",
        ),
    ),
    Faq(
        "Insta360 doesn't show my heart rate",
        listOf(
            "In the Insta360 app, open your clip and tap Dashboard › Data source › Local files. Pick your InstaBeats file.",
            "Make sure the clip was filmed during the workout. The file only covers the workout's start and end time.",
            "Check the camera's clock is right. A wrong clock puts the clip outside the workout.",
        ),
    ),
    Faq(
        "Where are my files?",
        listOf(
            "Every export is saved in Download › InstaBeats on your phone.",
            "The Exports tab lists them. You can share or delete them from there.",
        ),
    ),
)

@Composable
fun FaqScreen(
    state: AppState,
    samsungHealthInstalled: Boolean,
    onCheckAgain: () -> Unit,
    onAllowSamsung: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    bottomSpace: Dp,
) {
    var open by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Studio.Black).verticalScroll(rememberScrollState())) {
        ScreenHeader("FAQ")
        Column(
            Modifier.padding(horizontal = 14.dp).padding(top = 16.dp).navigationBarsPadding().padding(bottom = bottomSpace + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SyncCheck(state, samsungHealthInstalled, onCheckAgain, onAllowSamsung, onOpenSamsungHealth, onOpenHealthConnect)
            FAQS.forEachIndexed { i, faq ->
                FaqItem(faq, expanded = open == i, onToggle = { open = if (open == i) -1 else i })
            }
        }
    }
}

/** Shows the newest workout Health Connect gave us, so a stalled sync is easy to spot. */
@Composable
private fun SyncCheck(
    state: AppState,
    samsungHealthInstalled: Boolean,
    onCheckAgain: () -> Unit,
    onAllowSamsung: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    val newest = state.workouts.filterNot { it.fromSamsung }.maxByOrNull { it.end }
    val stale = newest == null || Duration.between(newest.end, Instant.now()) > Duration.ofDays(3)
    ListCard {
        Text("Is your watch syncing?", style = display(15))
        Spacer(Modifier.height(6.dp))
        val line = when {
            state.loading -> "Checking Health Connect…"
            newest == null -> "No workouts in Health Connect in the last ${state.rangeDays} days."
            else -> "Newest workout in Health Connect: ${newest.name}, ${madeLabel(newest.end.toEpochMilli())}."
        }
        Text(line, style = body(13f, color = if (stale && !state.loading) Studio.Coral else Studio.Text))
        if (stale && !state.loading) {
            Text(
                "Did you work out after that? Then sync has stopped. Follow the first answer below.",
                style = body(12f, color = Studio.Mu),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val gps = when (state.samsungGps) {
            SamsungGps.UNAVAILABLE -> null
            SamsungGps.NEEDS_ACCESS -> "Samsung Health GPS: not allowed yet."
            SamsungGps.READY -> "Samsung Health GPS: connected. Rides missing from Health Connect are read from Samsung Health."
        }
        gps?.let { Text(it, style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 6.dp)) }
        state.samsungError?.let { Text("Samsung Health said: $it", style = body(12f, color = Studio.Coral), modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        if (state.samsungGps == SamsungGps.NEEDS_ACCESS) {
            GhostButton("Allow Samsung Health", Modifier.fillMaxWidth(), onAllowSamsung)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Check again", onClick = onCheckAgain)
            if (samsungHealthInstalled) GhostButton("Samsung Health", onClick = onOpenSamsungHealth)
            GhostButton("Health Connect", onClick = onOpenHealthConnect)
        }
    }
}

@Composable
private fun FaqItem(faq: Faq, expanded: Boolean, onToggle: () -> Unit) {
    ListCard(Modifier.animateContentSize(), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(faq.question, style = body(14f, FontWeight.ExtraBold), modifier = Modifier.weight(1f))
            Text(if (expanded) "–" else "+", style = display(16).copy(color = Studio.Yellow))
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            faq.steps.forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${i + 1}", style = body(13f, FontWeight.ExtraBold, Studio.Yellow), modifier = Modifier.padding(end = 10.dp))
                    Text(step, style = body(13f, color = Color(0xE0F5F5F2)))
                }
            }
            faq.note?.let {
                Text(it, style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
