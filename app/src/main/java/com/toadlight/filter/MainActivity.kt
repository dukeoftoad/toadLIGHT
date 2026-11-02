package com.toadlight.filter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.model.ThemeMode
import com.toadlight.filter.filter.FilterService
import com.toadlight.filter.ui.apps.AppsScreen
import com.toadlight.filter.ui.home.HomeScreen
import com.toadlight.filter.ui.onboarding.OnboardingScreen
import com.toadlight.filter.ui.profiles.ProfilesScreen
import com.toadlight.filter.ui.settings.SettingsScreen
import com.toadlight.filter.ui.theme.toadLIGHTTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch (not on rotation-related recreation, where
        // the same intent would be delivered again).
        if (savedInstanceState == null) handleQuickAction(intent)
        setContent {
            toadLIGHTRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleQuickAction(intent)
    }

    /**
     * Quick actions from launcher shortcuts (long-press on the app icon):
     * toggle the filter or toggle paper mode without opening the app UI.
     * When launched standalone from the launcher, finish right after the
     * action so the user stays where they are.
     */
    private fun handleQuickAction(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_QUICK_ACTION) ?: return
        val graph = appGraph
        when (action) {
            QUICK_TOGGLE_FILTER -> {
                graph.scope.launch {
                    val on = graph.settingsState.value.masterEnabled
                    if (on) {
                        graph.repo.setMasterEnabled(false)
                        graph.repo.endPreview()
                        FilterService.stop(this@MainActivity)
                    } else {
                        graph.repo.setMasterEnabled(true)
                        FilterService.start(this@MainActivity)
                    }
                }
                finishIfStandalone()
            }

            QUICK_TOGGLE_PAPER -> {
                graph.scope.launch {
                    var paperOn = false
                    graph.repo.updateActiveProfile { profile ->
                        paperOn = !profile.paperFinish
                        profile.copy(paperFinish = paperOn)
                    }
                    // Make the change visible even during the day.
                    val s = graph.engine.state.value
                    if (s.masterOn && !s.paused && !s.overlayOn && !s.previewing) {
                        graph.repo.startPreview(2 * 60_000L)
                    }
                    Toast.makeText(
                        applicationContext,
                        if (paperOn) "Paper mode on" else "Paper mode off",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                finishIfStandalone()
            }
        }
    }

    private fun finishIfStandalone() {
        if (isTaskRoot) finish()
    }

    companion object {
        const val EXTRA_QUICK_ACTION = "quick_action"
        const val QUICK_TOGGLE_FILTER = "toggle_filter"
        const val QUICK_TOGGLE_PAPER = "toggle_paper"
    }
}

@Composable
fun toadLIGHTRoot() {
    val graph = androidx.compose.ui.platform.LocalContext.current.appGraph
    val settings by graph.settingsState.collectAsStateWithLifecycle()

    val darkTheme = when (settings.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    toadLIGHTTheme(
        darkTheme = darkTheme,
        amoled = settings.themeMode == ThemeMode.AMOLED,
        dynamicColor = settings.dynamicColors,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!settings.onboardingDone) {
                OnboardingScreen()
            } else {
                MainScaffold()
            }
        }
    }
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = listOf(
        TopDestination("home", "Home", Icons.Rounded.Bedtime),
        TopDestination("profiles", "Profiles", Icons.Rounded.Tune),
        TopDestination("apps", "Apps", Icons.Rounded.Apps),
        TopDestination("settings", "Settings", Icons.Rounded.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") { HomeScreen() }
            composable("profiles") { ProfilesScreen() }
            composable("apps") { AppsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
