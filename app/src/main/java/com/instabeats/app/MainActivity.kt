package com.instabeats.app

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.instabeats.app.ui.screens.AboutScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    // Base permissions required to scan workouts and read Heart Rate
    private val basePermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    // Full set requested from Health Connect
    private val allRequestedPermissions = basePermissions + setOf(
        HealthPermission.getReadPermission(SpeedRecord::class)
    )

    // Strict UTC ISO-8601 formatter without nanoseconds (Required by Insta360 Local Files parser)
    private val gpxTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdkStatus = HealthConnectClient.getSdkStatus(this)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Health Connect is not available or needs an update from the Play Store.",
                                color = Color.White
                            )
                        }
                    }
                }
            }
            return
        }

        val healthConnectClient = HealthConnectClient.getOrCreate(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    AppTabs(healthConnectClient)
                }
            }
        }
    }

    @Composable
    fun AppTabs(client: HealthConnectClient) {
        var tab by rememberSaveable { mutableStateOf(0) }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (tab == 0) InstaBeatsScreen(client) else AboutScreen()
            }
            NavigationBar(containerColor = Color.Black) {
                listOf("Workouts", "About").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {},
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Color(0xFFFFC93C),
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    fun InstaBeatsScreen(client: HealthConnectClient) {
        val scope = rememberCoroutineScope()
        var workouts by remember { mutableStateOf<List<ExerciseSessionRecord>>(emptyList()) }
        var statusText by remember { mutableStateOf("Tap '1. Grant & Scan Workouts' to begin.") }
        var selectedSessionForConsent by remember { mutableStateOf<ExerciseSessionRecord?>(null) }

        // Per-session GPS Route Consent Popup
        val routeRequestLauncher = rememberLauncherForActivityResult(
            contract = ExerciseRouteRequestContract()
        ) { exerciseRoute ->
            val session = selectedSessionForConsent
            if (session != null) {
                scope.launch {
                    if (exerciseRoute != null && exerciseRoute.route.isNotEmpty()) {
                        exportMergedGpx(client, session, exerciseRoute.route, false) { msg ->
                            statusText = msg
                        }
                    } else {
                        statusText = "No GPS route returned. Exporting Heart Rate-only GPX..."
                        val fallbackPts = buildSyntheticLocations(session)
                        exportMergedGpx(client, session, fallbackPts, true) { msg ->
                            statusText = msg
                        }
                    }
                }
            }
        }

        // Health Connect Permission Launcher
        val permissionLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            scope.launch {
                if (granted.containsAll(basePermissions)) {
                    workouts = loadRecentWorkouts(client)
                    statusText = "Found ${workouts.size} workouts from the last 14 days."
                } else {
                    statusText = "⚠️ Please grant Exercise and Heart Rate permissions in Health Connect settings."
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "InstaBeats",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300)
            )
            Text(
                text = "Universal Watch HR + GPS Exporter for Insta360",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val granted = client.permissionController.getGrantedPermissions()
                            if (granted.containsAll(basePermissions)) {
                                workouts = loadRecentWorkouts(client)
                                statusText = "Found ${workouts.size} workouts from the last 14 days."
                            } else {
                                permissionLauncher.launch(allRequestedPermissions)
                            }
                        } catch (e: Exception) {
                            statusText = "Error checking permissions: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
            ) {
                Text("1. Grant & Scan Workouts", color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF81C784),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(workouts) { session ->
                    WorkoutCard(session = session) {
                        scope.launch {
                            statusText = "Extracting Heart Rate & GPS points..."
                            when (val routeResult = session.exerciseRouteResult) {
                                is ExerciseRouteResult.Data -> {
                                    val pts = routeResult.exerciseRoute.route
                                    if (pts.isNotEmpty()) {
                                        exportMergedGpx(client, session, pts, false) { msg ->
                                            statusText = msg
                                        }
                                    } else {
                                        val fallbackPts = buildSyntheticLocations(session)
                                        exportMergedGpx(client, session, fallbackPts, true) { msg ->
                                            statusText = msg
                                        }
                                    }
                                }
                                is ExerciseRouteResult.ConsentRequired -> {
                                    selectedSessionForConsent = session
                                    routeRequestLauncher.launch(session.metadata.id)
                                }
                                is ExerciseRouteResult.NoData -> {
                                    statusText = "No GPS route in workout. Exporting Heart Rate-only GPX..."
                                    val fallbackPts = buildSyntheticLocations(session)
                                    exportMergedGpx(client, session, fallbackPts, true) { msg ->
                                        statusText = msg
                                    }
                                }
                                else -> {
                                    statusText = "Unexpected route state. Exporting Heart Rate-only GPX..."
                                    val fallbackPts = buildSyntheticLocations(session)
                                    exportMergedGpx(client, session, fallbackPts, true) { msg ->
                                        statusText = msg
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WorkoutCard(session: ExerciseSessionRecord, onExportClick: () -> Unit) {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.US)
            .withZone(ZoneId.systemDefault())
        val startStr = formatter.format(session.startTime)
        val durationMin = ChronoUnit.MINUTES.between(session.startTime, session.endTime)

        val typeName = when (session.exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "🚴 Outdoor Bike"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "🚲 Indoor Bike"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "🏃 Running"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "🏃 Treadmill"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "🥾 Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "🚶 Walking"
            else -> "⚡ Workout (${session.title ?: "Type ${session.exerciseType}"})"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(typeName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(startStr, fontSize = 13.sp, color = Color.LightGray)
                    Text("Duration: $durationMin mins", fontSize = 12.sp, color = Color.Gray)
                }
                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Export GPX", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Fallback generator for Indoor Bike or generic non-GPS bands so Insta360 still renders Heart Rate
    private fun buildSyntheticLocations(session: ExerciseSessionRecord): List<ExerciseRoute.Location> {
        val list = mutableListOf<ExerciseRoute.Location>()
        var t = session.startTime
        var step = 0
        while (!t.isAfter(session.endTime) && step < 14400) { // Cap at 4 hours max
            val microOffset = step * 0.000005
            list.add(
                ExerciseRoute.Location(
                    time = t,
                    latitude = 30.7046 + microOffset,
                    longitude = 76.7179 + microOffset
                )
            )
            t = t.plusSeconds(1)
            step++
        }
        return list
    }

    private suspend fun loadRecentWorkouts(client: HealthConnectClient): List<ExerciseSessionRecord> {
        return withContext(Dispatchers.IO) {
            val now = Instant.now()
            val start = now.minus(14, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )
            response.records.sortedByDescending { it.startTime }
        }
    }

    private suspend fun exportMergedGpx(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
        locations: List<ExerciseRoute.Location>,
        isSyntheticGps: Boolean,
        onResult: (String) -> Unit
    ) {
        if (locations.isEmpty()) {
            onResult("⚠️ No timestamps found for this workout.")
            return
        }

        val fileName = "InstaBeats_${session.startTime.epochSecond}.gpx"

        val resultMessage = withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Heart Rate samples recorded during the session window
                val hrRecords = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
                    )
                ).records.flatMap { it.samples }

                // 2. Build Insta360-compatible GPX 1.1 with Garmin TrackPointExtension v1
                val sb = StringBuilder()
                val startTimeIso = gpxTimeFormatter.format(session.startTime)
                sb.append(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <gpx creator="InstaBeats Exporter" version="1.1"
                      xmlns="http://www.topografix.com/GPX/1/1"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
                      <metadata>
                        <time>$startTimeIso</time>
                      </metadata>
                      <trk>
                        <name>InstaBeats Workout</name>
                        <type>cycling</type>
                        <trkseg>
                    """.trimIndent()
                ).append("\n")

                var lastValidHr = hrRecords.firstOrNull()?.beatsPerMinute ?: 85L
                for (loc in locations) {
                    val closestHr = hrRecords.minByOrNull {
                        abs(it.time.epochSecond - loc.time.epochSecond)
                    }?.beatsPerMinute ?: lastValidHr
                    lastValidHr = closestHr

                    val ele = loc.altitude?.inMeters ?: 0.0
                    val ptTimeIso = gpxTimeFormatter.format(loc.time)
                    val latStr = String.format(Locale.US, "%.7f", loc.latitude)
                    val lonStr = String.format(Locale.US, "%.7f", loc.longitude)
                    val eleStr = String.format(Locale.US, "%.2f", ele)

                    sb.append(
                        """
                          <trkpt lat="$latStr" lon="$lonStr">
                            <ele>$eleStr</ele>
                            <time>$ptTimeIso</time>
                            <extensions>
                              <gpxtpx:TrackPointExtension>
                                <gpxtpx:hr>$closestHr</gpxtpx:hr>
                              </gpxtpx:TrackPointExtension>
                            </extensions>
                          </trkpt>
                        """.trimIndent()
                    ).append("\n")
                }

                sb.append("    </trkseg>\n  </trk>\n</gpx>")
                val gpxBytes = sb.toString().toByteArray(Charsets.UTF_8)

                // 3. Save to /Download/InstaBeats/ safely via MediaStore
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/InstaBeats")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("MediaStore failed to create file URI")

                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(gpxBytes)
                        os.flush()
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "InstaBeats"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(File(dir, fileName)).use { os ->
                        os.write(gpxBytes)
                        os.flush()
                    }
                }

                val modeLabel = if (isSyntheticGps) "HR-Only (Synthetic GPS)" else "${locations.size} Real GPS pts"
                "✅ Saved: Downloads/InstaBeats/$fileName\n($modeLabel + ${hrRecords.size} Heart Rate samples merged!)"
            } catch (e: Exception) {
                "❌ Error saving GPX: ${e.message}"
            }
        }

        onResult(resultMessage)
        if (resultMessage.startsWith("✅")) {
            Toast.makeText(this@MainActivity, "Saved $fileName", Toast.LENGTH_LONG).show()
        }
    }
}
