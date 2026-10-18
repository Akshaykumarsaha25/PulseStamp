package com.instabeats.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.instabeats.app.model.RouteState
import com.instabeats.app.ui.ExportStep
import com.instabeats.app.ui.WorkoutDetail
import com.instabeats.app.ui.components.BackButton
import com.instabeats.app.ui.components.GhostButton
import com.instabeats.app.ui.components.HEROES
import com.instabeats.app.ui.components.HrChart
import com.instabeats.app.ui.components.LapBarsView
import com.instabeats.app.ui.components.YellowButton
import com.instabeats.app.ui.metaLine
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body
import com.instabeats.app.ui.theme.display
import kotlin.math.abs

@Composable
fun WorkoutDetailScreen(detail: WorkoutDetail?, onBack: () -> Unit, onExport: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Studio.Black)) {
        if (detail == null) {
            BackButton(onBack, Modifier.statusBarsPadding().padding(18.dp))
            return@Box
        }
        val w = detail.workout
        // GPS workouts get one of the action photos; HR-only ones a coral glow
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            if (w.hasGps) {
                Image(
                    painterResource(HEROES[abs(w.id.hashCode()) % HEROES.size].res),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(Studio.Coral.copy(alpha = 0.35f), Color.Transparent), radius = 700f),
                    ),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Studio.Black))))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        ) {
            BackButton(onBack, Modifier.padding(top = 12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(110.dp))
                Text(w.name, style = display(28))
                Text(metaLine(w), style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(w.avgBpm?.toString() ?: "–", "avg", Studio.Coral)
                    Stat(w.peakBpm?.toString() ?: "–", "peak", Studio.Coral)
                    val n = detail.samples.size
                    Stat(if (n >= 1000) "%.1fk".format(n / 1000f) else "$n", "HR samples", Studio.Text)
                }
                if (w.peakBpm != null && detail.samples.size > 1) {
                    HrChart(detail.samples, w.peakBpm, Modifier.padding(top = 12.dp))
                    Text(
                        "Heart rate with zones from your ${w.peakBpm} bpm peak · " +
                            if (detail.bars.effortOverTime) "effort over time below" else "laps below",
                        style = body(11f, color = Studio.Mu),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                LapBarsView(detail.bars, height = 30.dp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x80141416), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("GPS route", style = body(13f))
                    when (w.routeState) {
                        RouteState.HAS_ROUTE -> Text("Ready ✓", style = body(13f, color = Studio.Green))
                        RouteState.NEEDS_CONSENT -> Text("Needs your OK", style = body(13f, color = Studio.Yellow))
                        RouteState.NO_ROUTE -> Text("HR only", style = body(13f, color = Studio.Mu))
                    }
                }
                if (w.avgBpm == null) {
                    Text(
                        "No heart rate recorded for this workout.",
                        style = body(13f, color = Studio.Mu),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            YellowButton("Export for Insta360", Modifier.padding(bottom = 16.dp), enabled = w.avgBpm != null) { onExport(w.id) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column {
        Text(value, style = display(30, color))
        Text(label, style = body(11f, color = Studio.Mu))
    }
}

/**
 * Everything that happens after "Export for Insta360": the no-GPS question, the working
 * sheet, the done sheet, and errors. Route consent and the Android 9 storage prompt are
 * system dialogs launched by the caller.
 */
@Composable
fun ExportOverlay(
    step: ExportStep,
    insta360Installed: Boolean,
    onExportWithoutGps: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenInsta360: () -> Unit,
    onShare: () -> Unit,
    onGuide: () -> Unit,
) {
    when (step) {
        is ExportStep.AskWithoutGps -> Dialog(onDismissRequest = onDismiss) {
            Column(
                Modifier.fillMaxWidth().background(Studio.Sheet, RoundedCornerShape(22.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Export without GPS?", style = body(17f, FontWeight.ExtraBold))
                Text(
                    "The file keeps your real heart rate on a fixed point. Hide the map gauge in Insta360.",
                    style = body(13f, color = Studio.Mu),
                )
                YellowButton("Export") { onExportWithoutGps(step.workoutId) }
                GhostButton("Cancel", Modifier.fillMaxWidth(), onDismiss)
            }
        }
        is ExportStep.Working -> Sheet(onDismiss = {}) {
            CircularProgressIndicator(color = Studio.Yellow, trackColor = Studio.Yellow.copy(alpha = 0.2f), modifier = Modifier.size(38.dp))
            Spacer(Modifier.height(10.dp))
            Text("Building your GPX", style = body(15f, FontWeight.ExtraBold))
            Text(
                if (step.hrSamples > 0) "Matching ${"%,d".format(step.hrSamples)} heart-rate samples to the track" else "Reading heart rate from Health Connect",
                style = body(12f, color = Studio.Mu),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is ExportStep.Done -> Sheet(onDismiss) {
            Text("✓", style = display(30, Studio.Yellow))
            Text("Saved to Downloads/InstaBeats", style = body(16f, FontWeight.ExtraBold), modifier = Modifier.padding(top = 6.dp))
            Text(
                step.file.name + "\n" +
                    (if (step.hrOnly) "Heart rate only, no map · " else "${"%,d".format(step.gpsPoints)} GPS points · ") +
                    "${"%,d".format(step.hrSamples)} heart-rate samples",
                style = body(12f, color = Studio.Mu),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp, bottom = 16.dp),
            )
            if (insta360Installed) {
                YellowButton("Open Insta360", onClick = onOpenInsta360)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Share", Modifier.weight(1f), onShare)
                GhostButton("How to load it", Modifier.weight(1f), onGuide)
            }
        }
        is ExportStep.Failed -> Sheet(onDismiss) {
            Text(step.message, style = body(15f, FontWeight.ExtraBold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            YellowButton("Retry", onClick = onRetry)
            Spacer(Modifier.height(8.dp))
            GhostButton("Close", Modifier.fillMaxWidth(), onDismiss)
        }
        else -> Unit
    }
}

/** Bottom sheet over a dimmed screen; tapping the dim area closes it. */
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Studio.Sheet, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(interactionSource = noRipple, indication = null) {}
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun GuideScreen(insta360Installed: Boolean, onBack: () -> Unit, onOpenInsta360: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Studio.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        BackButton(onBack, Modifier.padding(top = 12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Put it on your video", style = display(26), modifier = Modifier.padding(top = 18.dp, bottom = 24.dp))
            GuideStep(1, plain("Open the Insta360 app and pick your clip."))
            GuideStep(2, buildAnnotatedString { append("Tap "); bold("Dashboard"); append(", then "); bold("Data source"); append(".") })
            GuideStep(3, buildAnnotatedString { append("Choose "); bold("Local files"); append(" and select your InstaBeats file.") })
            GuideStep(4, plain("Add the heart-rate gauge. It lines up with your footage by time."))
            Text(
                "Clip and workout must overlap in time. Check your camera clock matches your phone. For HR-only files, hide the map gauge.",
                style = body(13f, color = Studio.Mu),
                modifier = Modifier.fillMaxWidth().background(Color(0x80141416), RoundedCornerShape(16.dp)).padding(12.dp),
            )
        }
        if (insta360Installed) YellowButton("Open Insta360", Modifier.padding(bottom = 16.dp), onClick = onOpenInsta360)
    }
}

private fun plain(s: String) = buildAnnotatedString { append(s) }

private fun androidx.compose.ui.text.AnnotatedString.Builder.bold(s: String) =
    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(s) }

@Composable
private fun GuideStep(n: Int, text: androidx.compose.ui.text.AnnotatedString) {
    Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp).background(Studio.Yellow, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = body(12f, FontWeight.ExtraBold, Studio.Black))
        }
        Text(text, style = body(14f), modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Studio.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BackButton(onBack, Modifier.padding(top = 12.dp))
        Text("Privacy", style = display(26), modifier = Modifier.padding(top = 18.dp, bottom = 20.dp))
        Text("Your heart rate stays on your phone.", style = body(17f, FontWeight.ExtraBold))
        PrivacyList()
        Text(
            "InstaBeats only reads from Health Connect and never writes anything back. No analytics or crash reporting. " +
                "Android app backup is off, so app data isn't copied to Google Drive.",
            style = body(13f, color = Studio.Mu),
        )
        Spacer(Modifier.height(24.dp))
    }
}
