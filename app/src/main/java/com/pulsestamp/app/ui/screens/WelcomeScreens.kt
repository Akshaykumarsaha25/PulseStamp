package com.pulsestamp.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pulsestamp.app.R
import com.pulsestamp.app.ui.components.Glass
import com.pulsestamp.app.ui.components.InstaIcons
import com.pulsestamp.app.ui.components.LocalHaze
import com.pulsestamp.app.ui.components.YellowButton
import com.pulsestamp.app.ui.theme.Studio
import com.pulsestamp.app.ui.theme.body
import com.pulsestamp.app.ui.theme.display
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/** The stacked "Pulse / Stamp" wordmark with the tagline. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Pulse\nStamp", style = display(50).copy(shadow = TextShadow))
        Spacer(Modifier.height(10.dp))
        Text("Stamp your beats on every frame.", style = body(15f, FontWeight.SemiBold).copy(shadow = TextShadow))
    }
}

private val TextShadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 8f)

/** Drift-night photo with the Welcome gradient; glass panels on top blur it. */
@Composable
fun PhotoBackdrop(dim: Float = 0f, content: @Composable () -> Unit) {
    val haze = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.hero_drift_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .haze(haze)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.30f to Color.Black.copy(alpha = 0.10f),
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            0.72f to Color.Black.copy(alpha = 0.92f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        )
                    )
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                },
        )
        CompositionLocalProvider(LocalHaze provides haze) { content() }
    }
}

@Composable
fun PrivacyList() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        PrivacyRow(InstaIcons.NoInternet, "No internet access.", " The app can't upload anything, ever.")
        PrivacyRow(InstaIcons.NoAccount, "No account, no cloud, no syncing", " between devices.")
        PrivacyRow(InstaIcons.FileSaved, "Files save to Downloads/PulseStamp", " and go nowhere unless you share them.")
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, bold: String, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Image(rememberVectorPainter(icon), null, Modifier.size(16.dp).padding(top = 1.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(bold) }
                append(rest)
            },
            style = body(13f),
        )
    }
}

@Composable
private fun BottomCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp), contentAlignment = Alignment.BottomCenter) {
        Glass(Modifier.fillMaxWidth(), tint = Color(0x8C101012)) {
            Column(Modifier.padding(20.dp)) { content() }
        }
    }
}

@Composable
fun WelcomeScreen(onConnect: () -> Unit) {
    PhotoBackdrop {
        Wordmark(Modifier.statusBarsPadding().padding(start = 22.dp, top = 36.dp))
        BottomCard {
            Text("Your heart rate stays on your phone.", style = body(17f, FontWeight.ExtraBold))
            Spacer(Modifier.height(4.dp))
            Text(
                "PulseStamp turns your watch workouts into a file Insta360 can put on your video, right here on this device.",
                style = body(13f, color = Studio.Muted),
            )
            PrivacyList()
            YellowButton("Connect Health Connect", onClick = onConnect)
        }
    }
}

@Composable
fun AccessDeniedScreen(onOpenSettings: () -> Unit, onTryAgain: () -> Unit) {
    PhotoBackdrop(dim = 0.55f) {
        BottomCard {
            Text("PulseStamp needs workout and heart-rate access to work", style = body(17f, FontWeight.ExtraBold))
            Spacer(Modifier.height(6.dp))
            Text(
                "Turn on Exercise and Heart rate for PulseStamp in Health Connect, then come back.",
                style = body(13f, color = Studio.Muted),
            )
            Spacer(Modifier.height(16.dp))
            YellowButton("Open Health Connect settings", onClick = onOpenSettings)
            Spacer(Modifier.height(8.dp))
            com.pulsestamp.app.ui.components.GhostButton("Ask again", Modifier.fillMaxWidth(), onClick = onTryAgain)
        }
    }
}

/** Health Connect missing or too old: say so and link to the Play Store. */
@Composable
fun HealthConnectMissingScreen(needsUpdate: Boolean, onOpenStore: () -> Unit) {
    PhotoBackdrop(dim = 0.55f) {
        Wordmark(Modifier.statusBarsPadding().padding(start = 22.dp, top = 36.dp))
        BottomCard {
            Text(
                if (needsUpdate) "Health Connect needs an update" else "PulseStamp needs Health Connect",
                style = body(17f, FontWeight.ExtraBold),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your watch saves workouts to Health Connect, and PulseStamp reads them from there.",
                style = body(13f, color = Studio.Muted),
            )
            Spacer(Modifier.height(16.dp))
            YellowButton(if (needsUpdate) "Update Health Connect" else "Install Health Connect", onClick = onOpenStore)
        }
    }
}
