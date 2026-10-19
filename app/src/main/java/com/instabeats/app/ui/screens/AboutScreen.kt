package com.instabeats.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.instabeats.app.R
import com.instabeats.app.ui.components.InstaIcons
import com.instabeats.app.ui.components.YellowButton
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body
import com.instabeats.app.ui.theme.display

const val GITHUB_URL = "https://github.com/akshaykumarsaha25-maker/InstaBeats"

private val STORY_START = listOf(
    "Every ride, my watch knew exactly how hard my heart was pounding and exactly where I was. My Insta360 footage showed none of it.",
    "Getting that data out was a mess. It was buried, the steps were confusing, and every app wanted me to sync my health data to some cloud first. My heartbeat and my routes on someone else's server, just to put a number on a video.",
)
private const val STORY_BOLD = "No thanks. I wanted full control over what I post."
private val STORY_END = listOf(
    "So I built InstaBeats. It takes the heart rate and GPS straight from your watch and turns them into a file Insta360 can use, right on your phone. No account. No cloud. No internet at all. You decide what gets shared.",
    "I built it for me. I hope it helps you too.",
)

@Composable
fun AboutScreen(onPrivacy: () -> Unit, bottomSpace: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    var linkError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Studio.Black).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            Image(painterResource(R.drawable.hero_helmet_pov), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.35f to Color.Black.copy(alpha = 0.1f),
                        1f to Studio.Black,
                    ),
                ),
            )
            Text("About", style = display(26), modifier = Modifier.statusBarsPadding().padding(start = 18.dp, top = 22.dp))
        }
        Column(
            Modifier.padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = bottomSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                buildAnnotatedString {
                    append("My heart was racing.\n")
                    withStyle(SpanStyle(color = Studio.Yellow)) { append("My video didn't show it.") }
                },
                style = display(22).copy(lineHeight = display(22).fontSize * 1.15f),
            )
            STORY_START.forEach { Paragraph(it) }
            Text(STORY_BOLD, style = display(16))
            STORY_END.forEach { Paragraph(it) }
            Text("Akshay Kumar Saha", style = body(14f, FontWeight.SemiBold))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(rememberVectorPainter(InstaIcons.Logo), "InstaBeats app icon", Modifier.size(60.dp))
                Column {
                    Text("InstaBeats", style = display(18))
                    Text("Stamp your beats on every frame.", style = body(12f, color = Studio.Mu), modifier = Modifier.padding(top = 3.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Color(0x80141416), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Credit("Made by", "Akshay Kumar Saha")
                HorizontalDivider(color = Studio.Hairline)
                Credit("Built with help from", "Claude by Anthropic")
                HorizontalDivider(color = Studio.Hairline)
                Credit("Galaxy Watch GPS", "Samsung Health Data SDK")
                HorizontalDivider(color = Studio.Hairline)
                Credit("Version", version)
            }

            Text(
                "Samsung and Samsung Health are trademarks of Samsung Electronics Co., Ltd. InstaBeats is an independent app, not made or endorsed by Samsung.",
                style = body(11f, color = Studio.Mu),
            )

            Row(
                Modifier.fillMaxWidth().background(Color(0x80141416), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(rememberVectorPainter(InstaIcons.GitHub), null, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("Source code on GitHub", style = body(13f, FontWeight.ExtraBold))
                    Text("github.com/akshaykumarsaha25-maker/InstaBeats", style = body(11f, color = Studio.Mu))
                }
            }

            YellowButton("View on GitHub") { linkError = !openLink(context, GITHUB_URL) }
            if (linkError) Text("No browser found", style = body(12f, color = Studio.Mu), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Text(
                "Privacy",
                style = body(12f, color = Studio.Mu).copy(textDecoration = TextDecoration.Underline),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onPrivacy).padding(8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = body(14f, color = Color(0xE0F5F5F2)))
}

@Composable
private fun Credit(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = body(13f, color = Studio.Mu))
        Text(value, style = body(13f, FontWeight.ExtraBold))
    }
}

private fun appVersion(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

/** Hands the link to the browser; the app itself has no internet access. */
fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
