package dev.capsule.island

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.capsule.island.core.Metrics
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.IslandState
import dev.capsule.island.engine.Img
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Payload.Navigation.Maneuver
import dev.capsule.island.ui.island.IslandFrame
import dev.capsule.island.ui.island.IslandSurface
import org.junit.Rule
import org.junit.Test

/** Renders each kit component in each state, to compare against the Figma file. */
class IslandRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 786, screenHeight = 1000, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH), maxPercentDifference = 0.1)

    private fun a(id: String, p: Payload) = IslandActivity(id, p, 50, startedAt = 0)

    private val samples: List<Pair<String, Payload>> = listOf(
        "Music" to Payload.Music("Heat Waves", "Grass Animals", Img.Res(R.drawable.demo_artwork), Color(0xFFF84BAB), Color(0xFFB4CDFB), false, 241_000, 50_000, 0, 1f, explicit = true),
        "AirPlay" to Payload.Music("Video", "Grass Animals", null, Color(0xFFF84BAB), Color(0xFFB4CDFB), false, 241_000, 50_000, 0, 1f, remote = true, device = "Samsung TV"),
        "AirDrop" to Payload.Transfer("AirDrop from Chi", null, 0.35f, Img.Res(R.drawable.demo_airdrop_photo)),
        "Incoming" to Payload.Call("Tamia Castillo", "Mobile", Img.Res(R.drawable.demo_avatar_large), true, false, null),
        "Ongoing" to Payload.Call("Tamia Castillo", "FaceTime Audio", Img.Res(R.drawable.demo_avatar), false, false, 0, muted = true, speaker = true),
        "FaceTime" to Payload.Call("Tamia Castillo", "FaceTime Audio", Img.Res(R.drawable.demo_avatar), false, true, 0),
        "Charging" to Payload.Battery(75, Payload.Battery.Kind.Charging),
        "Low" to Payload.Battery(1, Payload.Battery.Kind.Low),
        "Critical" to Payload.Battery(1, Payload.Battery.Kind.Critical),
        "Silent" to Payload.Ringer(true),
        "Ring" to Payload.Ringer(false),
        "RingVolume" to Payload.RingVolume(0.55f),
        "DND" to Payload.Focus(Payload.Focus.Kind.DoNotDisturb, true),
        "Driving" to Payload.Focus(Payload.Focus.Kind.Driving, true),
        "Shortcut" to Payload.Shortcut("Shortcut", 1f, true),
        "Voice" to Payload.Recording(false, 0),
        "Screen" to Payload.Recording(true, 0),
        "Timer" to Payload.Timer(0, 215_000, paused = true),
        "Navigation" to Payload.Navigation("0.2", "mi", "North", "San Francisco", Maneuver.Right, null, listOf(Maneuver.Left, Maneuver.Straight, Maneuver.Straight, Maneuver.Right), 3, Img.Res(R.drawable.demo_route_shield)),
        "Lock" to Payload.Lock(false),
        "Unlock" to Payload.Lock(true),
        "FaceID" to Payload.FaceId(),
        "AirPods" to Payload.AirPods("Ladislav\u2019s Air Pods", 75),
        "AirPodsMoved" to Payload.AirPods("AirPods", null, moved = true),
        "Hotspot" to Payload.Hotspot(1),
        "Mirroring" to Payload.Mirroring("MacBook Pro"),
        "Transit" to Payload.Transit("Prague Main Train Station"),
        "NoData" to Payload.NoData,
        "Airplane" to Payload.Airplane,
        "SharePlay" to Payload.SharePlay("Asia Wild Nature", "For me", Img.Res(R.drawable.demo_artwork_video)),
        "Satellite" to Payload.Satellite,
        "FindMy" to Payload.FindMy("Find My iPhone Alert"),
    )

    @Composable
    private fun Cell(label: String, state: IslandState, height: Int) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF3A3A3A))) {
            Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            Box(Modifier.fillMaxWidth().height(height.dp)) {
                val w = with(LocalDensity.current) { 393.dp.toPx() }
                IslandSurface(state, IslandFrame(w / Metrics.SCREEN_WIDTH, Metrics.SCREEN_WIDTH, Metrics.SCREEN_WIDTH / 2, 6f), gestures = null)
            }
        }
    }

    @Test
    fun compactAndMinimal() {
        samples.chunked(8).forEachIndexed { i, chunk ->
            paparazzi.snapshot("compact$i") {
                Column(Modifier.width(393.dp).background(Color(0xFF3A3A3A))) {
                    chunk.forEach { (name, p) -> Cell("$name compact", IslandState(listOf(a("x", p))), 46) }
                }
            }
        }
        paparazzi.snapshot("split") {
            Column(Modifier.width(393.dp).background(Color(0xFF3A3A3A))) {
                Cell("Split: music + timer", IslandState(listOf(a("m", samples[0].second), a("t", Payload.Timer(0, 176_000, true)).copy(priority = 40))), 46)
                Cell("Split: call + charging", IslandState(listOf(a("c", samples[4].second).copy(priority = 75), a("b", samples[6].second))), 46)
                Cell("Split: nav + music", IslandState(listOf(a("n", samples[18].second).copy(priority = 65), a("m", samples[0].second))), 46)
                Cell("Idle", IslandState(), 46)
            }
        }
    }

    @Test
    fun expanded() {
        samples.chunked(2).forEachIndexed { i, chunk ->
            paparazzi.snapshot("expanded$i") {
                Column(Modifier.width(393.dp).background(Color(0xFF3A3A3A))) {
                    chunk.forEach { (name, p) -> Cell("$name expanded", IslandState(listOf(a("x", p)), expandedId = "x"), 205) }
                }
            }
        }
    }
}
