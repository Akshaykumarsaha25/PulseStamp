package com.instabeats.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.instabeats.app.ui.components.GlassTabBar
import com.instabeats.app.ui.components.Tab
import com.instabeats.app.ui.screens.AboutScreen
import com.instabeats.app.ui.screens.AccessDeniedScreen
import com.instabeats.app.ui.screens.ExportOverlay
import com.instabeats.app.ui.screens.ExportsScreen
import com.instabeats.app.ui.screens.FaqScreen
import com.instabeats.app.ui.screens.GuideScreen
import com.instabeats.app.ui.screens.HealthConnectMissingScreen
import com.instabeats.app.ui.screens.HomeScreen
import com.instabeats.app.ui.screens.PrivacyScreen
import com.instabeats.app.ui.screens.TabBarSpace
import com.instabeats.app.ui.screens.WelcomeScreen
import com.instabeats.app.ui.screens.WorkoutDetailScreen
import com.instabeats.app.ui.screens.WorkoutsScreen
import com.instabeats.app.ui.theme.Studio

private fun Context.launch(intent: Intent?) {
    if (intent == null) return
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open it with
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Top-level flow: Health Connect checks → Welcome → main tabs, detail, guide, privacy. */
@Composable
fun InstaBeatsApp(vm: AppViewModel, reduceMotion: Boolean, startOnPrivacy: Boolean, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> vm.onPermissionResult(granted) }

    // Health Connect opened us to explain why we ask: show Privacy only
    if (startOnPrivacy) {
        PrivacyScreen(onBack = onClose)
        return
    }

    Box(Modifier.fillMaxSize().background(Studio.Black)) {
        when (state.access) {
            Access.CHECKING -> Unit
            Access.MISSING, Access.NEEDS_UPDATE -> HealthConnectMissingScreen(state.access == Access.NEEDS_UPDATE) {
                context.launch(vm.health.playStoreIntent())
            }
            Access.NO_ACCESS -> WelcomeScreen { permissionLauncher.launch(vm.health.requestedPermissions) }
            Access.DENIED -> AccessDeniedScreen(
                onOpenSettings = { context.launch(vm.health.settingsIntent()) },
                onTryAgain = { permissionLauncher.launch(vm.health.requestedPermissions) },
            )
            Access.READY -> MainNav(vm, state, reduceMotion)
        }
    }
}

@Composable
private fun MainNav(vm: AppViewModel, state: AppState, reduceMotion: Boolean) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    val routeConsent = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route ->
        (vm.state.value.export as? ExportStep.NeedsConsent)?.let { vm.onRouteConsent(it.workoutId, route) }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.onStoragePermission(ok)
    }
    // System prompts the export flow needs
    LaunchedEffect(state.export) {
        when (val step = state.export) {
            is ExportStep.NeedsConsent -> routeConsent.launch(step.workoutId)
            is ExportStep.NeedsStoragePermission -> storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            is ExportStep.NeedsSamsungAccess -> {
                val granted = context.findActivity()?.let { vm.samsung.requestAccess(it) } ?: false
                vm.onSamsungAccess(step.workoutId, granted)
            }
            else -> Unit
        }
    }

    val openDetail: (String) -> Unit = { id ->
        vm.openDetail(id)
        nav.navigate("detail")
    }
    val openInsta360 = { context.launch(vm.store.insta360Intent()) }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "main") {
            composable("main") {
                val tabBar = @Composable {
                    Box(
                        Modifier.fillMaxSize().navigationBarsPadding().padding(14.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        GlassTabBar(tab, onSelect = {
                            tab = it
                            if (it == Tab.EXPORTS) vm.loadExports()
                        })
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        // Home draws the bar itself so it can blur the photo behind it
                        Tab.HOME -> HomeScreen(state, reduceMotion, openDetail, vm::startExport, vm::loadWorkouts, tabBar)
                        Tab.WORKOUTS -> WorkoutsScreen(
                            state,
                            onFilter = vm::setFilter,
                            onCycleRange = vm::cycleRange,
                            onShow30 = { vm.setRange(30) },
                            onOpen = openDetail,
                            onRetry = vm::loadWorkouts,
                        )
                        Tab.EXPORTS -> ExportsScreen(
                            state,
                            onShare = { context.launch(vm.store.shareIntent(it)) },
                            onDelete = vm::delete,
                            onGuide = { nav.navigate("guide") },
                            onPrivacy = { nav.navigate("privacy") },
                            onGoWorkouts = { tab = Tab.WORKOUTS },
                        )
                        Tab.FAQ -> FaqScreen(
                            state,
                            samsungHealthInstalled = vm.health.samsungHealthIntent() != null,
                            onCheckAgain = vm::loadWorkouts,
                            onOpenSamsungHealth = { context.launch(vm.health.samsungHealthIntent()) },
                            onOpenHealthConnect = { context.launch(vm.health.settingsIntent()) },
                            bottomSpace = TabBarSpace,
                        )
                        Tab.ABOUT -> AboutScreen(onPrivacy = { nav.navigate("privacy") }, bottomSpace = TabBarSpace)
                    }
                    if (tab != Tab.HOME) tabBar()
                }
            }
            composable("detail") {
                WorkoutDetailScreen(state.detail, onBack = { nav.popBackStack() }, onExport = vm::startExport)
            }
            composable("guide") {
                GuideScreen(state.insta360Installed, onBack = { nav.popBackStack() }, onOpenInsta360 = openInsta360)
            }
            composable("privacy") {
                PrivacyScreen(onBack = { nav.popBackStack() })
            }
        }
        ExportOverlay(
            step = state.export,
            insta360Installed = state.insta360Installed,
            onExportWithoutGps = vm::exportWithoutGps,
            onDismiss = vm::dismissExport,
            onRetry = vm::retryExport,
            onOpenInsta360 = openInsta360,
            onShare = {
                (state.export as? ExportStep.Done)?.let { context.launch(vm.store.shareIntent(it.file)) }
            },
            onGuide = {
                vm.dismissExport()
                nav.navigate("guide")
            },
        )
    }
}
