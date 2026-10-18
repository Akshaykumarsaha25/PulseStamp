package com.instabeats.app

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.instabeats.app.ui.AppViewModel
import com.instabeats.app.ui.InstaBeatsApp
import com.instabeats.app.ui.theme.InstaBeatsTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Health Connect opens the app to show why it asks for access
        val startOnPrivacy = intent?.action in PRIVACY_ACTIONS
        // Respect the system "remove animations" setting
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        setContent {
            InstaBeatsTheme {
                InstaBeatsApp(vm, reduceMotion, startOnPrivacy, onClose = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
    }

    companion object {
        private val PRIVACY_ACTIONS = setOf(
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
            "android.intent.action.VIEW_PERMISSION_USAGE",
        )
    }
}
