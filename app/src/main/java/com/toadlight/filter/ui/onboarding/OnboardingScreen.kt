package com.toadlight.filter.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toadlight.filter.appGraph
import com.toadlight.filter.core.color.ColorTemperature
import com.toadlight.filter.filter.FilterService
import kotlinx.coroutines.launch

/**
 * Three-step first-run experience: what the app does, how it works, and the
 * permissions needed (overlay required, notification & location optional).
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    // Re-check the overlay permission while the user is away in system settings.
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        locationGranted = grants.values.any { it }
        if (locationGranted) {
            scope.launch {
                val loc = com.toadlight.filter.core.util.LocationGrabber.currentLocation(context)
                if (loc != null) {
                    graph.repo.setLocation(loc.first, loc.second)
                }
            }
        }
    }

    fun requestOverlayPermission() {
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun finishOnboarding() {
        scope.launch {
            graph.repo.completeOnboarding()
            FilterService.start(context)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Rounded.Bedtime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "toadLIGHT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Gentle light for late nights",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> HowItWorksPage()
                else -> PermissionsPage(
                    overlayGranted = overlayGranted,
                    notificationsGranted = notificationsGranted,
                    locationGranted = locationGranted,
                    onRequestOverlay = ::requestOverlayPermission,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestLocation = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Page indicator dots.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (index == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape,
                        ),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    finishOnboarding()
                }
            },
            enabled = pagerState.currentPage < 2 || overlayGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    pagerState.currentPage < 2 -> "Next"
                    overlayGranted -> "Start glowing"
                    else -> "Allow overlay to continue"
                },
            )
        }

        if (pagerState.currentPage == 2 && !overlayGranted) {
            Spacer(Modifier.height(8.dp))
            Helper(
                "toadLIGHT needs \"Display over other apps\" to warm your screen. " +
                    "It only draws a translucent tint – never touches your taps.",
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { finishOnboarding() }) {
            Text("Skip for now")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Mini live demo of the filter.
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF2A2A4A),
                            Color(0xFF6B3A6E),
                            Color(0xFFB96A4B),
                        ),
                    ),
                    MaterialTheme.shapes.extraLarge,
                ),
        ) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Aa",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                listOf(Color(0xFF4FC3F7), Color(0xFFFFEB3B), Color(0xFFFFFFFF)).forEach { c ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(c, CircleShape),
                    )
                }
            }
            // Warm overlay preview on the right half.
            val warm = ColorTemperature.rgb(2400)
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .height(160.dp)
                    .background(
                        Color(
                            red = warm[0] / 255f,
                            green = warm[1] / 255f,
                            blue = warm[2] / 255f,
                            alpha = 0.55f,
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Your screen follows the sun",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Helper(
            "After sunset, toadLIGHT gently warms your screen and dims it a little, " +
                "reducing blue light so your eyes can rest and your sleep stays on rhythm. " +
                "At sunrise everything fades back to normal.",
        )
    }
}

@Composable
private fun HowItWorksPage() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HowRow(
            icon = Icons.Rounded.Bedtime,
            title = "A soft amber veil",
            body = "A see-through overlay tints the screen with candle-warm color. " +
                "You choose the temperature, strength and extra dimming – live, with a preview.",
        )
        HowRow(
            icon = Icons.Rounded.WbSunny,
            title = "Sun-synced schedule",
            body = "The filter switches on at your local sunset and off at sunrise, " +
                "fading in gradually. Prefer fixed hours? Set a custom schedule instead.",
        )
        HowRow(
            icon = Icons.Rounded.Apps,
            title = "Smart about your apps",
            body = "Pause the filter inside specific apps (games, galleries) or give " +
                "each app its own settings. Everything is optional.",
        )
    }
}

@Composable
private fun HowRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Helper(body)
        }
    }
}

@Composable
private fun PermissionsPage(
    overlayGranted: Boolean,
    notificationsGranted: Boolean,
    locationGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PermissionRow(
            required = true,
            granted = overlayGranted,
            title = "Display over other apps",
            body = "Required – this is how the warmth filter is drawn.",
            actionLabel = if (overlayGranted) null else "Allow",
            onAction = onRequestOverlay,
        )
        PermissionRow(
            required = false,
            granted = notificationsGranted,
            title = "Notifications",
            body = "Pause, resume and stop right from the notification.",
            actionLabel = if (notificationsGranted) null else "Allow",
            onAction = onRequestNotifications,
        )
        PermissionRow(
            required = false,
            granted = locationGranted,
            title = "Location (optional)",
            body = "Used once to find your sunset and sunrise times. Never tracked.",
            actionLabel = if (locationGranted) null else "Allow",
            onAction = onRequestLocation,
        )
    }
}

@Composable
private fun PermissionRow(
    required: Boolean,
    granted: Boolean,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Bedtime,
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Helper(body + if (required) "" else "")
        }
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun Helper(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
