package com.instabeats.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.instabeats.app.ui.theme.Studio
import com.instabeats.app.ui.theme.body

enum class Tab(val label: String) { HOME("Home"), WORKOUTS("Workouts"), EXPORTS("Exports"), ABOUT("About") }

@Composable
fun GlassTabBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Glass(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                Text(
                    tab.label,
                    style = body(12f, if (on) FontWeight.ExtraBold else FontWeight.Normal, if (on) Studio.Yellow else Studio.Text),
                    modifier = Modifier
                        .alpha(if (on) 1f else 0.6f)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
