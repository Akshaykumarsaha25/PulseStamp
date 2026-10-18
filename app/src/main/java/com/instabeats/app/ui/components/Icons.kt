package com.instabeats.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.instabeats.app.ui.theme.Studio

/** Small line icons from the mockups, drawn on a 16×16 grid. */
object InstaIcons {
    private fun stroke(name: String, color: Color, size: Float, viewport: Float, vararg paths: String) =
        ImageVector.Builder(name, size.dp, size.dp, viewport, viewport).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(color),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val NoInternet = stroke(
        "no_internet", Studio.Yellow, 16f, 16f,
        "M2 2l12 12M5.5 5.6A4 4 0 0 0 4 8.5M8 4a6 6 0 0 1 5 2.5M3 6.5A6 6 0 0 1 5 4.6M10.6 8.6A4 4 0 0 1 12 9.5M8 12.5h.01",
    )
    val NoAccount = stroke(
        "no_account", Studio.Yellow, 16f, 16f,
        "M10.6 5.5a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0",
        "M3 14c.6-2.6 2.6-4 5-4s4.4 1.4 5 4",
        "M2 2l12 12",
    )
    val FileSaved = stroke("file_saved", Studio.Yellow, 16f, 16f, "M3 2.5h7l3 3v8H3z", "M6 9.5l1.5 1.5 3-3")

    val GitHub: ImageVector = ImageVector.Builder("github", 22.dp, 22.dp, 16f, 16f).apply {
        addPath(
            pathData = addPathNodes(
                "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.33c-2.23.48-2.7-1.07-2.7-1.07-.36-.92-.89-1.17-.89-1.17-.73-.5.06-.49.06-.49.8.06 1.23.83 1.23.83.71 1.22 1.87.87 2.33.66.07-.52.28-.87.5-1.07-1.78-.2-3.65-.89-3.65-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.6 7.6 0 0 1 4 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.2c0 .21.15.46.55.38A8 8 0 0 0 16 8c0-4.42-3.58-8-8-8z"
            ),
            fill = SolidColor(Studio.Text),
        )
    }.build()

    /** The Pulse Frame logo, same art as the launcher icon. */
    val Logo: ImageVector = ImageVector.Builder("logo", 60.dp, 60.dp, 108f, 108f).apply {
        addPath(addPathNodes("M24 0h60a24 24 0 0 1 24 24v60a24 24 0 0 1-24 24H24A24 24 0 0 1 0 84V24A24 24 0 0 1 24 0z"), fill = SolidColor(Color(0xFF0A0A0B)))
        listOf(
            "M26 40V28h12M70 28h12v12M82 68v12H70M38 80H26V68" to Studio.Yellow,
            "M20 56h18l6-12 9 26 6-20 4 6h25" to Studio.Coral,
        ).forEach { (d, c) ->
            addPath(addPathNodes(d), stroke = SolidColor(c), strokeLineWidth = 6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()
}
