package dev.capsule.island.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.capsule.island.CapsuleApp
import dev.capsule.island.core.Metrics
import dev.capsule.island.data.HostMode
import dev.capsule.island.data.IdleMode
import dev.capsule.island.data.Module
import dev.capsule.island.data.Prefs
import dev.capsule.island.data.TapAction
import dev.capsule.island.engine.IslandEngine
import dev.capsule.island.engine.Presentation
import dev.capsule.island.system.Demo
import dev.capsule.island.system.Demos
import dev.capsule.island.system.Hosts
import dev.capsule.island.ui.island.IslandFrame
import dev.capsule.island.ui.island.IslandGestures
import dev.capsule.island.ui.island.IslandSurface
import dev.capsule.island.engine.IslandActivity
import kotlinx.coroutines.launch

// ---- Shared pieces --------------------------------------------------------------------------

@Composable
private fun prefsState(): Prefs = CapsuleApp.instance.prefs.collectAsState().value

@Composable
private fun updatePrefs(): ((Prefs) -> Prefs) -> Unit {
    val scope = rememberCoroutineScope()
    return { f -> scope.launch { CapsuleApp.instance.settings.update(f) } }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Ink.Primary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.Tertiary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Surface)
            .border(BorderStroke(1.dp, Ink.Hairline), RoundedCornerShape(18.dp)),
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Ink.Hairline))

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Ink.Green, checkedBorderColor = Ink.Green,
                uncheckedThumbColor = Ink.Secondary, uncheckedTrackColor = Ink.Raised, uncheckedBorderColor = Ink.Hairline,
            ),
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink.Background).padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) Ink.Raised else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) Ink.Primary else Ink.Secondary) }
        }
    }
}

// ---- Stage ----------------------------------------------------------------------------------

private val previewGestures = object : IslandGestures {
    override fun tap(activity: IslandActivity?, onBubble: Boolean) {
        activity ?: return
        if (IslandEngine.state.value.presentation is Presentation.Expanded) IslandEngine.collapse() else IslandEngine.expand(activity.id)
    }
    override fun longPress(activity: IslandActivity?, onBubble: Boolean) { if (activity != null) IslandEngine.expand(activity.id, fromPress = true) }
    override fun swipeUp() = IslandEngine.collapse()
    override fun swipeSideways(activity: IslandActivity?, dx: Float) {
        if (IslandEngine.state.value.presentation is Presentation.Split) IslandEngine.swapSplit() else activity?.let { IslandEngine.end(it.id) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageScreen() {
    val prefs = prefsState()
    val update = updatePrefs()
    val state by IslandEngine.state.collectAsState()
    val running = state.activities.map { it.id }.toSet()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Capsule", "The island, live. Tap a component to run it here and on screen; hold the island to expand it.") }
        item {
            // A phone's top edge at the reference width, drawing the same surface as the overlay.
            BoxWithConstraints(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(232.dp)
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xFF1C1C21))
                    .border(BorderStroke(1.dp, Ink.Hairline), RoundedCornerShape(28.dp)),
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val ptPx = widthPx / Metrics.SCREEN_WIDTH
                IslandSurface(
                    state = state,
                    frame = IslandFrame(ptPx, Metrics.SCREEN_WIDTH, Metrics.SCREEN_WIDTH / 2, Metrics.TOP),
                    gestures = previewGestures,
                    reducedMotion = prefs.reducedMotion,
                    keyline = true,
                )
                Text(
                    if (state.activities.isEmpty()) "Idle" else "${state.activities.size} running \u00B7 ${presentationName(state.presentation)}",
                    style = MaterialTheme.typography.labelMedium, color = Ink.Secondary,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
                if (state.activities.isNotEmpty()) {
                    Text(
                        "Clear", style = MaterialTheme.typography.labelLarge, color = Ink.Primary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Ink.Raised)
                            .clickable { state.activities.map { it.id }.forEach(IslandEngine::end) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Panel { SwitchRow("Island on screen", hostSummary(), prefs.enabled) { v -> update { it.copy(enabled = v) } } }
        }
        val groups = Demo.entries.groupBy { it.group }
        groups.forEach { (group, demos) ->
            item { SectionLabel(group) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    demos.forEach { d -> DemoChip(d, d.id in running) { Demos.toggle(d) } }
                }
            }
        }
    }
}

private fun presentationName(p: Presentation) = when (p) {
    Presentation.Idle -> "idle"
    is Presentation.Compact -> "compact"
    is Presentation.Split -> "split"
    is Presentation.Expanded -> "expanded"
}

@Composable
private fun hostSummary(): String {
    val a11y by Hosts.accessibilityActive.collectAsState()
    val overlay by Hosts.overlayActive.collectAsState()
    return when {
        a11y -> "Drawing above the status bar and on the lock screen"
        overlay -> "Drawing as an app overlay"
        else -> "Not drawing yet. Open System to grant access"
    }
}

@Composable
private fun DemoChip(d: Demo, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink.Raised else Ink.Surface)
            .border(BorderStroke(1.dp, if (on) Ink.Green.copy(alpha = 0.5f) else Ink.Hairline), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Ink.Green))
            Spacer(Modifier.width(8.dp))
        }
        Text(d.title, style = MaterialTheme.typography.labelLarge, color = if (on) Ink.Primary else Ink.Secondary)
    }
}

// ---- Modules --------------------------------------------------------------------------------

@Composable
fun ModulesScreen() {
    val prefs = prefsState()
    val update = updatePrefs()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Modules", "Each source of activities. Off means its listener is unregistered, so it costs nothing.") }
        item {
            Panel {
                Module.entries.forEachIndexed { i, m ->
                    if (i > 0) Divider()
                    SwitchRow(m.title, m.detail, m in prefs.modules) { v ->
                        update { p -> p.copy(modules = if (v) p.modules + m else p.modules - m) }
                    }
                }
            }
        }
    }
}

// ---- Geometry -------------------------------------------------------------------------------

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, readout: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary, modifier = Modifier.weight(1f))
            Text(readout, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium), color = Ink.Secondary)
        }
        Slider(
            value, onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Ink.Primary, inactiveTrackColor = Ink.Hairline),
        )
    }
}

@Composable
fun GeometryScreen() {
    val prefs = prefsState()
    val update = updatePrefs()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Geometry", "The idle pill centres on your camera cutout. Nudge it until the hole sits in the middle.") }
        item {
            Panel {
                SliderRow("Horizontal", prefs.offsetX, -30f..30f, "%+.1f pt".format(prefs.offsetX)) { v -> update { it.copy(offsetX = v) } }
                Divider()
                SliderRow("Vertical", prefs.offsetY, -20f..20f, "%+.1f pt".format(prefs.offsetY)) { v -> update { it.copy(offsetY = v) } }
                Divider()
                SliderRow("Size", prefs.scale, 0.8f..1.2f, "%.0f%%".format(prefs.scale * 100)) { v -> update { it.copy(scale = v) } }
                Divider()
                Text(
                    "Reset", style = MaterialTheme.typography.labelLarge, color = Ink.Primary,
                    modifier = Modifier.fillMaxWidth().clickable { update { it.copy(offsetX = 0f, offsetY = 0f, scale = 1f) } }.padding(16.dp),
                )
            }
        }
        item { SectionLabel("When nothing is running") }
        item {
            Panel {
                Segmented(listOf("Stay visible", "Auto-hide"), prefs.idleMode.ordinal) { i -> update { it.copy(idleMode = IdleMode.entries[i]) } }
                if (prefs.idleMode == IdleMode.AutoHide) {
                    val delays = listOf(3, 5, 10, 30, 60)
                    Segmented(delays.map { "${it}s" }, delays.indexOf(prefs.autoHideSeconds).coerceAtLeast(0)) { i -> update { it.copy(autoHideSeconds = delays[i]) } }
                }
            }
        }
        item { SectionLabel("Gestures") }
        item {
            Panel {
                Segmented(listOf("Tap opens app", "Tap expands"), prefs.tapAction.ordinal) { i -> update { it.copy(tapAction = TapAction.entries[i]) } }
                Text(
                    if (prefs.tapAction == TapAction.OpenApp) "As on iOS: tap opens the app, hold expands. Swipe up to close; swipe sideways to dismiss or swap the split."
                    else "Tap expands, hold opens the app. Swipe up to close; swipe sideways to dismiss or swap the split.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
        item { SectionLabel("Look and motion") }
        item {
            Panel {
                SwitchRow("Reduce motion", "Short eases instead of springs, no blur", prefs.reducedMotion) { v -> update { it.copy(reducedMotion = v) } }
                Divider()
                SwitchRow("Keyline", "The thin grey edge iOS draws on dark backgrounds", prefs.keyline) { v -> update { it.copy(keyline = v) } }
                Divider()
                SwitchRow("Hide in landscape", null, prefs.hideInLandscape) { v -> update { it.copy(hideInLandscape = v) } }
            }
        }
    }
}

// ---- System ---------------------------------------------------------------------------------

private data class Check(val title: String, val detail: String, val ok: Boolean, val action: () -> Unit)

private fun Context.open(action: String, pkgUri: Boolean = false) {
    val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (pkgUri) i.data = Uri.parse("package:$packageName")
    runCatching { startActivity(i) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
fun SystemScreen() {
    val context = LocalContext.current
    val prefs = prefsState()
    val update = updatePrefs()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick
    val pm = context.getSystemService(PowerManager::class.java)
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    val checks = listOf(
        Check(
            "Accessibility host", "Recommended. Draws above the status bar and on the lock screen. Reads no screen content.",
            Hosts.accessibilityEnabled(context),
        ) { context.open(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        Check("Display over other apps", "Fallback host when accessibility is off.", Settings.canDrawOverlays(context)) {
            context.open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri = true)
        },
        Check(
            "Notification access", "Music, calls, timers, navigation, transfers and recordings.",
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        ) { context.open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
        Check("Unrestricted battery", "Keeps OriginOS from closing the island.", pm.isIgnoringBatteryOptimizations(context.packageName)) {
            context.open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri = true)
        },
        Check(
            "Bluetooth and notifications", "Earbud names and battery; the fallback host's notification.",
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
        ) {
            val wanted = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.launch(wanted)
        },
        Check("Do Not Disturb access", "Lets Unmute switch the ringer back on.", nm.isNotificationPolicyAccessGranted) {
            context.open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        },
    )

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("System", "Access the island needs, and how to keep OriginOS from closing it.") }
        item {
            Panel {
                checks.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().clickable(onClick = c.action).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (c.ok) Ink.Green else Ink.Amber))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
                            Text(c.detail, style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(if (c.ok) "On" else "Set up", style = MaterialTheme.typography.labelLarge, color = if (c.ok) Ink.Secondary else Ink.Primary)
                    }
                }
            }
        }
        item { SectionLabel("Host") }
        item {
            Panel {
                Segmented(listOf("Auto", "Accessibility", "Overlay"), prefs.host.ordinal) { i -> update { it.copy(host = HostMode.entries[i]) } }
                Text(
                    "Auto uses the accessibility host when it is on, and the app overlay otherwise. The overlay sits under the status bar, so touches at the very top can reach the notification shade instead.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }
        }
        item { SectionLabel("iQOO Neo 10R \u00B7 OriginOS") }
        item {
            Panel {
                Guide(
                    "1", "Allow restricted settings",
                    "Android 13+ greys out Accessibility and Notification access for apps installed from a file. Open App info \u2192 \u22EE \u2192 Allow restricted settings, then come back.",
                ) { context.open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri = true) }
                Divider()
                Guide("2", "Background power", "Settings \u2192 Battery \u2192 Background power consumption management \u2192 Capsule \u2192 Allow high background power consumption.") {
                    context.open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri = true)
                }
                Divider()
                Guide("3", "Autostart", "Settings \u2192 Apps \u2192 Autostart (or i Manager \u2192 App manager \u2192 Autostart) \u2192 turn on Capsule, so it comes back after a restart.") {
                    context.open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri = true)
                }
                Divider()
                Guide("4", "Lock in recents", "Open recent apps, pull Capsule's card down (or tap the lock) so clearing recents leaves it running.") { }
                Divider()
                Guide("5", "Turn off Atomic Island", "OriginOS draws its own island. Turn it off under Settings \u2192 Status bar and notifications so the two don't overlap. Menu names vary by version.") {
                    context.open(Settings.ACTION_SETTINGS)
                }
            }
        }
        item { SectionLabel("Automation") }
        item {
            Panel {
                Text(
                    "Tasker, MacroDroid or adb can show any component: broadcast dev.capsule.island.action.SHOW to dev.capsule.island/.system.AutomationReceiver with extra type (for example timer, shortcut, findmy) and optional title, subtitle, value. END with the same type removes it.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Guide(step: String, title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Ink.Raised), contentAlignment = Alignment.Center) {
            Text(step, style = MaterialTheme.typography.labelMedium, color = Ink.Primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink.Primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
        }
    }
}
