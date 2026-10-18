package com.instabeats.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val GITHUB_URL = "https://github.com/akshaykumarsaha25-maker/InstaBeats"

// Studio palette; moves to ui/theme once the theme package lands
private val Yellow = Color(0xFFFFC93C)
private val Glass = Color(0x1FFFFFFF)
private val Muted = Color(0xB3FFFFFF)

private val STORY = listOf(
    "Every ride, my watch knew exactly how hard my heart was pounding and exactly where I was. My Insta360 footage showed none of it.",
    "Getting that data out was a mess. It was buried, the steps were confusing, and every app wanted me to sync my health data to some cloud first. My heartbeat and my routes on someone else's server, just to put a number on a video.",
)
private const val STORY_BOLD = "No thanks. I wanted full control over what I post."
private val STORY_END = listOf(
    "So I built InstaBeats. It takes the heart rate and GPS straight from your watch and turns them into a file Insta360 can use, right on your phone. No account. No cloud. No internet at all. You decide what gets shared.",
    "I built it for me. I hope it helps you too.",
)

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    var linkError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "My heart was racing. My video didn't show it.",
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp,
        )
        STORY.forEach { Paragraph(it) }
        Paragraph(STORY_BOLD, bold = true)
        STORY_END.forEach { Paragraph(it) }
        Text("— Akshay Kumar Saha", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Glass, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Made by Akshay Kumar Saha", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Built with help from Claude by Anthropic", color = Muted, fontSize = 14.sp)
            Text("Version $version", color = Muted, fontSize = 14.sp)
        }

        Button(
            onClick = { linkError = !openGitHub(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow),
        ) {
            Text("View on GitHub", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (linkError) Text("No browser found", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Paragraph(text: String, bold: Boolean = false) {
    Text(
        text,
        color = if (bold) Color.White else Muted,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

private fun appVersion(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

/** Hands the link to the browser; the app itself has no internet access. */
private fun openGitHub(context: Context): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
