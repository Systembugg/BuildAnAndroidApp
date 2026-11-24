package dev.capsule.island.ui.activities

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import dev.capsule.island.R
import dev.capsule.island.core.Metrics
import dev.capsule.island.core.pt
import dev.capsule.island.engine.Action
import dev.capsule.island.engine.IslandActivity
import dev.capsule.island.engine.Payload
import dev.capsule.island.engine.Payload.Navigation.Maneuver
import dev.capsule.island.ui.kit.AirDropRings
import dev.capsule.island.ui.kit.Artwork
import dev.capsule.island.ui.kit.At
import dev.capsule.island.ui.kit.BatteryIndicator
import dev.capsule.island.ui.kit.CallWave
import dev.capsule.island.ui.kit.CapsuleButton
import dev.capsule.island.ui.kit.CircleButton
import dev.capsule.island.ui.kit.Glyph
import dev.capsule.island.ui.kit.GlyphBox
import dev.capsule.island.ui.kit.KitColor
import dev.capsule.island.ui.kit.KitText
import dev.capsule.island.ui.kit.LockGlyph
import dev.capsule.island.ui.kit.MusicBars
import dev.capsule.island.ui.kit.ProgressRing
import dev.capsule.island.ui.kit.SatelliteIcon
import dev.capsule.island.ui.kit.Sf
import dev.capsule.island.ui.kit.StopButton
import dev.capsule.island.ui.kit.StopRing
import dev.capsule.island.ui.kit.VoiceBars
import dev.capsule.island.ui.kit.W
import dev.capsule.island.ui.kit.imageOf
import android.os.SystemClock

/**
 * Every component in the Figma kit ("iOS17 Dynamic Island Components"), per state. Coordinates
 * are the kit's, in points from the component's top-left; nested frames are summed out.
 */
fun ui(a: IslandActivity, tw: TextWidth, unlockProgress: () -> Float = { 1f }): ActivityUi = when (val p = a.payload) {
    is Payload.Music -> music(a, p, tw)
    is Payload.Transfer -> transfer(a, p)
    is Payload.Call -> call(a, p, tw)
    is Payload.Battery -> battery(p, tw)
    is Payload.Ringer -> ringer(a, p, tw)
    is Payload.RingVolume -> ringVolume(p, tw)
    is Payload.Focus -> focus(p, tw)
    is Payload.Shortcut -> shortcut(p)
    is Payload.Recording -> recording(a, p, tw)
    is Payload.Timer -> timer(a, p, tw)
    is Payload.Navigation -> navigation(a, p, tw)
    is Payload.Lock -> lock(p, unlockProgress)
    is Payload.FaceId -> faceId()
    is Payload.AirPods -> airPods(a, p)
    is Payload.Hotspot -> simple(Sf.HotspotBold, Sf.Hotspot, KitColor.Green, 30f) { hotspotExpanded(p) }
    is Payload.Mirroring -> mirroring(a, p)
    is Payload.Transit -> transit(a, p)
    is Payload.NoData -> noData(a)
    is Payload.Airplane -> airplane(a)
    is Payload.SharePlay -> sharePlay(a, p)
    is Payload.Satellite -> satellite()
    is Payload.FindMy -> findMy(p)
}

private fun Modifier.tap(a: IslandActivity, action: Action) = this.then(
    Modifier.clickable(interactionSource = null, indication = null) { a.run(action) },
)

private fun glyphSlot(sf: Sf, size: Float, color: Color, boxW: Float) =
    Slot(boxW, round = boxW <= 26f) { GlyphBox(sf, size, color, boxW, 18f) }

private fun textSlot(text: String, size: Float, weight: W, color: Color, tw: TextWidth, tabular: Boolean = false): Slot {
    val w = minOf(tw.of(text, size, weight, tabular) + 1f, Metrics.COMPACT_MAX_SLOT)
    return Slot(w, round = false) { KitText(text, size, weight, color, w = w, tabular = tabular) }
}

@Composable
private fun MinimalGlyph(sf: Sf, size: Float, color: Color) = Glyph(sf, size, color)

// ---------------------------------------------------------------------------------------------
// Music Control and AirPlay
// ---------------------------------------------------------------------------------------------

private fun music(a: IslandActivity, p: Payload.Music, tw: TextWidth): ActivityUi {
    val brush = if (p.remote) Brush.linearGradient(listOf(Color(0xFFA8A8A8), Color(0xFFD9D9D9)))
    else Brush.linearGradient(listOf(p.accent, p.accentEnd))
    return ActivityUi(
        leading = Slot(24f, round = true) { Artwork(if (p.remote) null else p.art, 24f, 4.2f) },
        trailing = Slot(18f, round = true) { MusicBars(brush, p.playing) },
        minimal = { MusicBars(brush, p.playing) },
        expanded = Expanded(367f, 177f) { MusicExpanded(a, p, brush) },
    )
}

@Composable
private fun MusicExpanded(a: IslandActivity, p: Payload.Music, brush: Brush) {
    val now = rememberNow(if (p.playing) 500L else 60_000L)
    val position = if (p.playing) p.positionMs + ((now - p.positionAt) * p.speed).toLong() else p.positionMs
    val pos = position.coerceIn(0, maxOf(p.durationMs, 1))
    val fraction = if (p.durationMs > 0) pos.toFloat() / p.durationMs else 0f
    Box(Modifier.size(367.pt, 177.pt)) {
        At(24f, 32f) { Artwork(if (p.remote) null else p.art, 53f, 8f) }
        At(97f, 39.5f, 200f, 18f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText(p.title, 16f, W.Semibold, Color.White, modifier = Modifier.weight(1f, fill = false))
                if (p.explicit) {
                    Spacer(Modifier.width(4.pt))
                    Box(Modifier.size(11.pt).clip(RoundedCornerShape(2.pt)).background(KitColor.Secondary), contentAlignment = Alignment.Center) {
                        KitText("E", 8f, W.Bold, Color.Black)
                    }
                }
            }
        }
        At(97f, 59.5f, 200f, 18f) {
            if (p.remote) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Sf.AirPlayAudio, 12f, KitColor.Secondary)
                    Spacer(Modifier.width(4.pt))
                    KitText(p.device ?: p.artist, 15f, W.Regular, KitColor.Secondary)
                }
            } else {
                KitText(p.artist, 14f, W.Regular, KitColor.Secondary)
            }
        }
        At(309f, 49.5f, 26f, 18f, Alignment.Center) { MusicBars(brush, p.playing, size = 22f) }

        At(24f, 94f, 30f, 18f) { KitText(clock(pos), 12f, W.Regular, KitColor.Secondary, tabular = true) }
        At(63f, 99.8f, 240f, 6.5f) {
            Box(Modifier.size(240.pt, 6.5.pt).clip(RoundedCornerShape(8.pt)).background(KitColor.Track.copy(alpha = 0.7f))) {
                Box(Modifier.size((240f * fraction).pt, 6.5.pt).background(Color.White.copy(alpha = 0.8f)))
            }
        }
        At(310f, 94f, 40f, 18f) { KitText("-" + clock(p.durationMs - pos), 12f, W.Regular, KitColor.Secondary, tabular = true) }

        val back = if (p.remote) Sf.Back15 else Sf.Backward
        val fwd = if (p.remote) Sf.Forward15 else Sf.Forward
        val sideSize = if (p.remote) 24f else 20f
        At(107.5f, 121f, 32f, 44f, Alignment.Center) {
            Box(Modifier.size(44.pt).tap(a, Action.Previous), contentAlignment = Alignment.Center) { Glyph(back, sideSize, Color.White) }
        }
        At(168.5f, 121f, 44f, 44f, Alignment.Center) {
            Box(Modifier.size(44.pt).tap(a, Action.PlayPause), contentAlignment = Alignment.Center) {
                if (p.playing) Glyph(Sf.Pause, 30f, Color.White) else Glyph(Sf.Play, 32f, Color.White)
            }
        }
        At(241.5f, 121f, 32f, 44f, Alignment.Center) {
            Box(Modifier.size(44.pt).tap(a, Action.Next), contentAlignment = Alignment.Center) { Glyph(fwd, sideSize, Color.White) }
        }
        At(303f, 121f, 24f, 44f, Alignment.Center) { Glyph(Sf.AirPlayAudio, 20f, Color.White) }
    }
}

// ---------------------------------------------------------------------------------------------
// AirDrop (transfers)
// ---------------------------------------------------------------------------------------------

private fun transfer(a: IslandActivity, p: Payload.Transfer) = ActivityUi(
    leading = Slot(22f, round = true) { AirDropRings(22f, 1.5f) },
    trailing = Slot(20f, round = true) { StopRing(20f, 1.5f, p.progress ?: 0f, 6.7f, 2f) },
    minimal = { AirDropRings(22f, 1.2f) },
    expanded = Expanded(367f, 86f) {
        Box(Modifier.size(367.pt, 86.pt)) {
            At(16f, 15f) { AirDropRings(56f, 3.1f) }
            if (p.photo != null) At(52f, 51f) { Box(Modifier.alpha(0.9f)) { Artwork(p.photo, 20f, 2f, KitColor.Gray6) } }
            At(80f, 28.8f, 205f, 11f) { if (p.subtitle != null) KitText(p.subtitle, 10.5f, W.Regular, KitColor.Gray2) }
            At(80f, 46.2f, 205f, 11f) { KitText(p.title, 16f, W.Regular, Color.White) }
            At(295f, 15f) { Box(Modifier.tap(a, Action.Cancel)) { StopRing(56f, 4.7f, p.progress ?: 0f, 22f, 6.2f) } }
        }
    },
)

// ---------------------------------------------------------------------------------------------
// Calls: Incoming Call, Ongoing Call, FaceTime
// ---------------------------------------------------------------------------------------------

private fun call(a: IslandActivity, p: Payload.Call, tw: TextWidth): ActivityUi {
    val green = KitColor.Green
    val leading: Slot = if (p.video) {
        Slot(24f, round = true) { GlyphBox(Sf.VideoSemibold, 18f, green, 24f, 18f) }
    } else {
        val timeW = tw.of("00:00", 12f, W.Regular, true)
        Slot(20f + timeW, round = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBox(Sf.PhoneSemibold, 18f, green, 20f, 18f)
                if (p.connectedAt != null) {
                    val now = rememberNow()
                    KitText(elapsed(now - p.connectedAt), 12f, W.Regular, green, w = timeW, tabular = true)
                } else {
                    Spacer(Modifier.width(timeW.pt))
                }
            }
        }
    }
    val expanded = when {
        p.incoming -> Expanded(367f, 86f) { IncomingCall(a, p) }
        p.video -> Expanded(367f, 166f, radius = 42f) { CallCard(a, p, facetime = true) }
        else -> Expanded(367f, 168f, radius = 42f) { CallCard(a, p, facetime = false) }
    }
    return ActivityUi(
        leading = leading,
        trailing = Slot(34f, round = false) { CallWave(live = p.connectedAt != null) },
        minimal = { if (p.video) MinimalGlyph(Sf.Video, 14f, green) else MinimalGlyph(Sf.Phone, 18f, green) },
        expanded = expanded,
    )
}

@Composable
private fun IncomingCall(a: IslandActivity, p: Payload.Call) {
    Box(Modifier.size(367.pt, 86.pt)) {
        At(24f, 15f) { Artwork(p.avatar, 56f, 28f, KitColor.Gray6) }
        At(88f, 28.8f, 140f, 11f) { KitText(p.subtitle, 13f, W.Regular, KitColor.Gray2) }
        At(88f, 46.2f, 140f, 11f) { KitText(p.name, 16f, W.Regular, Color.White) }
        At(230.5f, 17f) { CircleButton(52f, KitColor.Red, Modifier.tap(a, Action.Decline)) { Glyph(Sf.PhoneDown, 23.44f, Color.White) } }
        At(290.1f, 17f) { CircleButton(52f, KitColor.Green, Modifier.tap(a, Action.Answer)) { Glyph(if (p.video) Sf.VideoSemibold else Sf.Phone, 23.44f, Color.White) } }
    }
}

@Composable
private fun CallCard(a: IslandActivity, p: Payload.Call, facetime: Boolean) {
    val size = if (facetime) 49.7f else 51.7f
    val xs = if (facetime) floatArrayOf(29f, 93.8f, 158.6f, 223.5f, 288.3f) else floatArrayOf(29f, 93.3f, 157.6f, 222f, 286.3f)
    val glyph = if (facetime) 22.4f else 23.3f
    Box(Modifier.size(367.pt, (if (facetime) 166f else 168f).pt)) {
        At(29f, 34f) { Artwork(p.avatar, 44.3f, 22.15f, KitColor.Gray6) }
        At(81.3f, 41.9f, 220f, 11f) { KitText(p.name, 16f, W.Regular, Color.White) }
        At(81.3f, 59.3f, 220f, 11f) {
            val now = rememberNow()
            val sub = if (p.connectedAt != null) "${p.subtitle} · ${elapsed(now - p.connectedAt)}" else p.subtitle
            KitText(sub, 14f, W.Regular, if (facetime) Color(0xFF898989) else KitColor.Gray, tabular = true)
        }
        At(316f, 45f, 22f, 22f, Alignment.Center) { Glyph(Sf.Info, 18f, if (facetime) Color(0xFF777677) else KitColor.Gray) }

        val y = 92.3f
        // Speaker: white when routed to the speaker.
        At(xs[0], y) {
            val on = p.speaker
            CircleButton(size, if (on) Color.White else KitColor.Button, Modifier.tap(a, Action.Speaker)) {
                if (facetime) Glyph(Sf.Speaker, glyph, if (on) Color.Black else Color.White)
                else Glyph(Sf.AudioRoute, glyph, if (on) Color.Black else Color.White)
            }
        }
        At(xs[1], y) {
            val on = p.muted
            CircleButton(size, if (on) Color.White else KitColor.Button, Modifier.tap(a, Action.Mute)) { Glyph(Sf.Mic, glyph, if (on) Color.Black else Color.White) }
        }
        At(xs[2], y) { CircleButton(size, KitColor.Button, Modifier.tap(a, Action.Video)) { Glyph(Sf.Video, glyph, Color.White) } }
        At(xs[3], y) { CircleButton(size, KitColor.Button, Modifier.tap(a, Action.Open)) { Glyph(Sf.ScreenShare, glyph, Color.White) } }
        At(xs[4], y) { CircleButton(size, KitColor.Red, Modifier.tap(a, Action.HangUp)) { Glyph(Sf.PhoneDown, glyph, Color.White) } }
    }
}

// ---------------------------------------------------------------------------------------------
// Charging, Low Battery, Critical Battery
// ---------------------------------------------------------------------------------------------

private fun battery(p: Payload.Battery, tw: TextWidth): ActivityUi {
    val color = when (p.kind) {
        Payload.Battery.Kind.Charging -> KitColor.Green
        Payload.Battery.Kind.Low -> KitColor.Yellow
        Payload.Battery.Kind.Critical -> KitColor.Red
    }
    val label = if (p.kind == Payload.Battery.Kind.Charging) "Charging" else "Low Battery"
    val pct = "${p.level} %"
    val pctW = tw.of(pct, 14f, W.Semibold, true) + 1f
    val trailing = Slot(pctW + 4f + 34.9f, round = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitText(pct, 14f, W.Semibold, color, w = pctW, tabular = true, align = TextAlign.End)
            Spacer(Modifier.width(4.pt))
            BatteryIndicator(p.level, color)
        }
    }
    val ring: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(24.8f, 2.2f, color, color.copy(alpha = 0.4f), p.level / 100f)
            KitText("${p.level}", 12f, W.Semibold, color, tabular = true)
        }
    }
    return ActivityUi(
        leading = textSlot(label, 14f, W.Regular, Color.White, tw),
        trailing = trailing,
        minimal = ring,
        expanded = Expanded(367f, 86f) {
            Box(Modifier.size(367.pt, 86.pt)) {
                At(24f, 21f, 44f, 44f, Alignment.Center) { Box(Modifier.size(44.pt), contentAlignment = Alignment.Center) { ring() } }
                At(76f, 28.8f, 180f, 11f) { KitText(if (p.kind == Payload.Battery.Kind.Charging) "Charging" else "Battery", 14f, W.Regular, KitColor.Gray2) }
                At(76f, 46.2f, 180f, 11f) { KitText(if (p.kind == Payload.Battery.Kind.Charging) "Connected to power" else "${p.level}% remaining", 14f, W.Semibold, Color.White) }
                At(293.4f, 16.1f, 53f, 53f, Alignment.Center) {
                    ProgressRing(53f, 4.4f, color, color.copy(alpha = 0.25f), p.level / 100f)
                    KitText("${p.level}%", 14f, W.Semibold, color, tabular = true)
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Silent Mode, Ring, Ring Volume, Focus
// ---------------------------------------------------------------------------------------------

private fun ringer(a: IslandActivity, p: Payload.Ringer, tw: TextWidth): ActivityUi {
    if (!p.silent) {
        return ActivityUi(
            leading = glyphSlot(Sf.BellBold, 18f, KitColor.Gray6, 22f),
            trailing = textSlot("Ring", 14f, W.Regular, Color.White, tw),
            minimal = { MinimalGlyph(Sf.Bell, 16f, KitColor.Gray6) },
            expanded = silentExpanded(a, silent = false),
        )
    }
    return ActivityUi(
        leading = Slot(52f, round = false) {
            Box(Modifier.size(52.pt, 23.pt).clip(RoundedCornerShape(50)).background(KitColor.Red), contentAlignment = Alignment.Center) {
                Glyph(Sf.BellSlash, 16f, Color.White)
            }
        },
        trailing = textSlot("Silent", 14f, W.Regular, KitColor.Red, tw),
        minimal = { MinimalGlyph(Sf.BellSlash, 16f, KitColor.Red) },
        expanded = silentExpanded(a, silent = true),
    )
}

private fun silentExpanded(a: IslandActivity, silent: Boolean) = Expanded(317f, 86f, stretch = false) {
    Box(Modifier.size(317.pt, 86.pt)) {
        At(24f, 31.5f, 52f, 23f, Alignment.Center) { Glyph(if (silent) Sf.BellSlash else Sf.BellBold, 24f, Color.White) }
        At(84f, 28.8f, 120f, 11f) { KitText(if (silent) "SilentMode" else "Ring", 14f, W.Regular, KitColor.Gray2) }
        At(84f, 46.2f, 120f, 11f) { KitText(if (silent) "On" else "On", 16f, W.Semibold, Color.White) }
        if (silent) At(214f, 26f) { CapsuleButton("Unmute", KitColor.Fill, Color.White, 79f, 34f, 14f, W.Semibold, Modifier.tap(a, Action.Unmute)) }
    }
}

private fun ringVolume(p: Payload.RingVolume, tw: TextWidth): ActivityUi {
    val ringerW = tw.of("Ringer", 14f, W.Regular) + 1f
    return ActivityUi(
        leading = Slot(26f + ringerW, round = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBox(Sf.BellBold, 18f, KitColor.Gray6, 22f, 18f)
                Spacer(Modifier.width(4.pt))
                KitText("Ringer", 14f, W.Regular, Color.White, w = ringerW)
            }
        },
        trailing = Slot(36f, round = false) {
            Box(Modifier.size(36.pt, 5.pt).clip(RoundedCornerShape(16.pt)).background(Color(0xFF5C5C5C))) {
                Box(Modifier.size((36f * p.fraction.coerceIn(0f, 1f)).pt, 5.pt).clip(RoundedCornerShape(16.pt)).background(Color(0xFFD9D9D9)))
            }
        },
        minimal = { MinimalGlyph(Sf.Bell, 16f, KitColor.Gray6) },
        expanded = Expanded(367f, 86f) {
            Box(Modifier.size(367.pt, 86.pt)) {
                At(24f, 34f, 22f, 18f, Alignment.Center) { Glyph(Sf.BellBold, 22f, KitColor.Gray6) }
                At(60f, 34f, 60f, 18f) { KitText("Ringer", 16f, W.Regular, Color.White) }
                At(130f, 40.5f) {
                    Box(Modifier.size(213.pt, 5.pt).clip(RoundedCornerShape(16.pt)).background(Color(0xFF5C5C5C))) {
                        Box(Modifier.size((213f * p.fraction.coerceIn(0f, 1f)).pt, 5.pt).clip(RoundedCornerShape(16.pt)).background(Color(0xFFD9D9D9)))
                    }
                }
            }
        },
    )
}

private fun focus(p: Payload.Focus, tw: TextWidth): ActivityUi {
    val dnd = p.kind == Payload.Focus.Kind.DoNotDisturb
    val sf = if (dnd) Sf.Moon else Sf.Car
    val on = if (p.on) "On" else "Off"
    return ActivityUi(
        leading = Slot(24f, round = true) { GlyphBox(sf, 18f, KitColor.Indigo, 24f, 18f) },
        trailing = textSlot(on, 16f, W.Regular, KitColor.Indigo, tw),
        minimal = { MinimalGlyph(sf, 16f, KitColor.Indigo) },
        expanded = Expanded(367f, 86f) {
            Box(Modifier.size(367.pt, 86.pt)) {
                At(24f, 21f) { CircleButton(44f, KitColor.Indigo.copy(alpha = 0.2f)) { Glyph(sf, 20f, KitColor.Indigo) } }
                At(76f, 28.8f, 200f, 11f) { KitText(if (dnd) "Do Not Disturb" else "Driving", 14f, W.Regular, KitColor.Gray2) }
                At(76f, 46.2f, 200f, 11f) { KitText(on, 16f, W.Semibold, Color.White) }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Shortcuts
// ---------------------------------------------------------------------------------------------

@Composable
private fun ShortcutIcon(w: Float, h: Float) {
    Image(painterResource(R.drawable.ic_shortcut), null, Modifier.size(w.pt, h.pt))
}

private fun shortcut(p: Payload.Shortcut) = ActivityUi(
    leading = Slot(23.2f, round = true) { ShortcutIcon(23.2f, 30f) },
    trailing = glyphSlot(Sf.CheckCircle, 20f, KitColor.Gray6, 24f),
    minimal = { MinimalGlyph(Sf.CheckCircle, 20f, KitColor.Gray6) },
    expanded = Expanded(367f, 85.1f) {
        Box(Modifier.size(367.pt, 85.1.pt)) {
            At(24f, 16f) { ShortcutIcon(41f, 53.1f) }
            At(73f, 28.3f, 190f, 11f) { KitText(p.name, 16f, W.Semibold, Color.White) }
            At(73f, 45.7f, 190f, 11f) { KitText("${(p.progress * 100).toInt()}%", 15f, W.Medium, KitColor.Gray2, tabular = true) }
            At(291.3f, 21.6f, 57f, 42f, Alignment.Center) {
                if (p.done) Glyph(Sf.CheckCircle, 48f, KitColor.Gray6)
                else ProgressRing(44f, 3f, KitColor.Gray6, KitColor.Gray6.copy(alpha = 0.25f), p.progress)
            }
        }
    },
)

// ---------------------------------------------------------------------------------------------
// Voice Recording, Screen Recording
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecordingDot(size: Float) {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size.pt).alpha(a).clip(CircleShape).background(KitColor.Red))
}

private fun recording(a: IslandActivity, p: Payload.Recording, tw: TextWidth): ActivityUi {
    val timeW = tw.of("00:00", 14f, W.Regular, true) + 1f
    val time: @Composable (Float) -> Unit = { size ->
        val now = rememberNow()
        KitText(elapsed(now - p.startedAt).padStart(5, '0'), size, W.Regular, KitColor.Red, tabular = true)
    }
    if (p.screen) {
        return ActivityUi(
            leading = Slot(12f, round = true) { RecordingDot(12f) },
            trailing = Slot(timeW, round = false) { time(14f) },
            minimal = { RecordingDot(12f) },
            expanded = Expanded(367f, 86f) {
                Box(Modifier.size(367.pt, 86.pt)) {
                    At(30f, 26f) { RecordingDot(12f) }
                    At(48f, 23f, 80f, 18f) { time(18f) }
                    At(30f, 45f, 200f, 18f) { KitText("Screen Recording", 18f, W.Regular, Color.White) }
                    At(291f, 16f) { Box(Modifier.tap(a, Action.Cancel)) { StopButton() } }
                }
            },
        )
    }
    return ActivityUi(
        leading = Slot(38.5f, round = false) { VoiceBars(expanded = false, live = true) },
        trailing = Slot(timeW, round = false) { time(14f) },
        minimal = { VoiceBars(expanded = false, live = true) },
        expanded = Expanded(367f, 86f) {
            Box(Modifier.size(367.pt, 86.pt)) {
                At(30f, 29.5f) { VoiceBars(expanded = true, live = true) }
                At(229f, 34f, 60f, 18f) { time(18f) }
                At(291f, 16f) { Box(Modifier.tap(a, Action.Cancel)) { StopButton() } }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------------------------

private fun timer(a: IslandActivity, p: Payload.Timer, tw: TextWidth): ActivityUi {
    val orange = KitColor.Orange
    val remaining: @Composable () -> Long = {
        if (p.paused) p.remainingMs else p.endsAt - rememberNow()
    }
    val sample = clock(if (p.paused) p.remainingMs else p.endsAt - SystemClock.elapsedRealtime())
    val w = tw.of(sample.replace(Regex("[0-9]"), "0"), 14f, W.Regular, true) + 1f
    return ActivityUi(
        leading = Slot(22f, round = true) { GlyphBox(Sf.TimerSemibold, 18f, orange, 22f, 18f) },
        trailing = Slot(w, round = false) { KitText(clock(remaining()), 14f, W.Regular, orange, w = w, tabular = true, align = TextAlign.End) },
        minimal = { MinimalGlyph(Sf.Timer, 20f, orange) },
        expanded = Expanded(367f, 85.7f) {
            Box(Modifier.size(367.pt, 85.7.pt)) {
                At(16.8f, 17.1f) {
                    CircleButton(52.2f, orange.copy(alpha = 0.45f), Modifier.tap(a, Action.PauseResume)) {
                        if (p.paused) Glyph(Sf.Play, 22f, orange) else Glyph(Sf.Pause, 23.51f, orange)
                    }
                }
                At(82.4f, 17.2f) { CircleButton(52.2f, KitColor.Gray.copy(alpha = 0.7f), Modifier.tap(a, Action.Cancel)) { Glyph(Sf.XmarkSemibold, 23.51f, Color.White) } }
                At(150f, 28.9f, 201f, 28f, Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        KitText(p.label, 16f, W.Regular, orange, modifier = Modifier.alignByBaseline())
                        Spacer(Modifier.width(6.pt))
                        KitText(clock(remaining()), 32f, W.Semibold, orange, tabular = true, modifier = Modifier.alignByBaseline())
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Navigation, Transport Directions
// ---------------------------------------------------------------------------------------------

private fun maneuverGlyph(m: Maneuver, large: Boolean): Sf = when (m) {
    Maneuver.Left -> if (large) Sf.TurnLeftLarge else Sf.TurnRightSemibold
    Maneuver.Right -> if (large) Sf.TurnRightLarge else Sf.TurnRightSemibold
    Maneuver.Straight -> Sf.StraightLarge
    Maneuver.UTurn -> Sf.TurnLeftLarge
}

@Composable
private fun ManeuverIcon(p: Payload.Navigation, size: Float, color: Color) {
    val bitmap = imageOf(p.arrow)
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size((size * 1.1f).pt), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
    } else {
        // Left turns mirror the right-turn arrow at compact size, as the kit only has the right one there.
        val sf = maneuverGlyph(p.maneuver, size > 24f)
        val mirror = p.maneuver == Maneuver.Left && size <= 24f
        Glyph(sf, size, color, if (mirror) Modifier.graphicsLayer { scaleX = -1f } else Modifier)
    }
}


private fun navigation(a: IslandActivity, p: Payload.Navigation, tw: TextWidth): ActivityUi {
    val blue = KitColor.NavBlue
    val distW = tw.of(p.distance, 16f, W.Regular, true)
    val unitW = tw.of(p.unit.uppercase(), 8f, W.Regular)
    return ActivityUi(
        leading = Slot(22f, round = true) { Box(Modifier.size(22.pt, 18.pt), contentAlignment = Alignment.Center) { ManeuverIcon(p, 18f, blue) } },
        trailing = Slot(distW + 1f + unitW, round = false) {
            Row(verticalAlignment = Alignment.Top) {
                KitText(p.distance, 16f, W.Regular, blue, tabular = true)
                Spacer(Modifier.width(1.pt))
                KitText(p.unit.uppercase(), 8f, W.Regular, blue)
            }
        },
        minimal = { ManeuverIcon(p, 18f, blue) },
        expanded = Expanded(372f, 185.4f, radius = 42f) {
            Box(Modifier.size(372.pt, 185.4.pt).tap(a, Action.Open)) {
                if (p.lanes.isNotEmpty()) {
                    val xs = floatArrayOf(17f, 115.9f, 214.8f, 313.6f)
                    p.lanes.take(4).forEachIndexed { i, lane ->
                        At(xs[i], 42f, 41.4f, 41.4f, Alignment.Center) {
                            Glyph(maneuverGlyph(lane, true), 36f, if (i == p.activeLane) Color.White else KitColor.Gray)
                        }
                    }
                } else {
                    At(17f, 42f, 41.4f, 41.4f, Alignment.Center) { ManeuverIcon(p, 36f, Color.White) }
                    At(72f, 52f, 280f, 22f) { KitText(p.instruction, 18f, W.Semibold, Color.White) }
                }
                At(17f, 97.4f, 200f, 11f) { KitText("${p.distance} ${p.unit}", 18f, W.Bold, Color.White, tabular = true) }
                val shield = imageOf(p.shield)
                if (shield != null) At(17f, 122.4f) { Image(shield, null, Modifier.size(51.pt, 39.pt)) }
                val tx = if (shield != null) 76f else 17f
                At(tx, 127.7f, 279f, 11f) { KitText(if (p.lanes.isNotEmpty()) p.instruction else p.detail.ifEmpty { p.instruction }, 18f, W.Semibold, Color.White) }
                At(tx, 145.1f, 279f, 11f) { if (p.lanes.isNotEmpty()) KitText(p.detail, 13f, W.Medium, KitColor.Gray2) }
            }
        },
    )
}

private fun transit(a: IslandActivity, p: Payload.Transit) = ActivityUi(
    leading = glyphSlot(Sf.TramBold, 18f, KitColor.TransitBlue, 22f),
    trailing = null,
    minimal = { MinimalGlyph(Sf.Tram, 16f, KitColor.TransitBlue) },
    expanded = Expanded(367f, 142f, radius = 42f) {
        Box(Modifier.size(367.pt, 142.pt)) {
            At(32f, 37f, 34f, 18f, Alignment.Center) { Glyph(Sf.TramBold, 28f, Color.White) }
            At(82f, 35f, 253f, 22f) { KitText(p.station, 20f, W.Medium, Color.White) }
            At(16f, 81f) { CapsuleButton("End Route", Color(0xFF1D1011), KitColor.Red, 335f, 43f, modifier = Modifier.tap(a, Action.EndRoute)) }
        }
    },
)

// ---------------------------------------------------------------------------------------------
// Lock / Unlock, Face ID
// ---------------------------------------------------------------------------------------------

private fun lock(p: Payload.Lock, progress: () -> Float) = ActivityUi(
    leading = Slot(18f, round = true) { LockGlyph(if (p.unlocked) progress() else 0f, 20f, KitColor.Gray6) },
    trailing = null,
    minimal = { LockGlyph(if (p.unlocked) progress() else 0f, 18f, KitColor.Gray6) },
    expanded = Expanded(155f, 130f, stretch = false, radius = 42f) {
        Box(Modifier.size(155.pt, 130.pt), contentAlignment = Alignment.Center) { LockGlyph(if (p.unlocked) progress() else 0f, 48f, KitColor.Gray6) }
    },
)

private fun faceId() = ActivityUi(
    leading = Slot(20f, round = true) { Spacer(Modifier.size(20.pt)) },
    trailing = Slot(20f, round = true) { Spacer(Modifier.size(20.pt)) },
    minimal = { MinimalGlyph(Sf.FaceId, 20f, KitColor.Green) },
    expanded = Expanded(155f, 130f, stretch = false, radius = 42f) {
        Box(Modifier.size(155.pt, 130.pt), contentAlignment = Alignment.Center) { Glyph(Sf.FaceId, 64f, KitColor.Green) }
    },
)

// ---------------------------------------------------------------------------------------------
// AirPods Connected, AirPods Moved
// ---------------------------------------------------------------------------------------------

private fun airPods(a: IslandActivity, p: Payload.AirPods): ActivityUi {
    val level = (p.battery ?: 0) / 100f
    val ring: @Composable (Float, Float, Boolean) -> Unit = { size, stroke, number ->
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(size, stroke, KitColor.Green, KitColor.GreenDeep, level)
            if (number && p.battery != null) KitText("${p.battery}", 12f, W.Semibold, KitColor.Green, tabular = true)
        }
    }
    val image: @Composable (Float) -> Unit = { s ->
        Image(painterResource(if (s > 30f) R.drawable.img_airpods else R.drawable.img_airpods_small), null, Modifier.size(s.pt).clip(CircleShape))
    }
    return ActivityUi(
        leading = Slot(29f, round = true) { image(29f) },
        trailing = if (p.battery != null) Slot(21.6f, round = true) { ring(21.6f, 1.8f, false) } else null,
        minimal = { if (p.battery != null) ring(24.8f, 1.9f, true) else image(24f) },
        expanded = Expanded(367f, 86f) {
            Box(Modifier.size(367.pt, 86.pt)) {
                if (p.moved) {
                    At(17f, 21f) { image(44f) }
                    At(69f, 46.2f, 200f, 11f) { KitText("Moved to this phone", 14f, W.Regular, KitColor.Gray2) }
                    At(276f, 26f) {
                        Box(Modifier.size(70.pt, 34.pt).clip(RoundedCornerShape(20.pt)).background(Color(0xFF1E2854)).tap(a, Action.Return), contentAlignment = Alignment.Center) {
                            Glyph(Sf.ReturnArrow, 18f, KitColor.Cyan)
                        }
                    }
                } else {
                    At(24f, 21f) { image(44f) }
                    At(76f, 28.8f, 190f, 11f) { KitText("Connected", 14f, W.Regular, KitColor.Gray2) }
                    At(76f, 46.2f, 190f, 11f) { KitText(p.name, 14f, W.Semibold, Color.White) }
                    if (p.battery != null) At(293.4f, 16.1f, 53f, 53f, Alignment.Center) {
                        ProgressRing(53f, 4.4f, KitColor.Green, KitColor.GreenDeep, level)
                        KitText("${p.battery}%", 14f, W.Semibold, KitColor.Green, tabular = true)
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Personal Hotspot, Screen Mirroring, No Data, Airplane Mode
// ---------------------------------------------------------------------------------------------

private fun simple(compact: Sf, minimal: Sf, color: Color, boxW: Float, expanded: () -> Expanded) = ActivityUi(
    leading = glyphSlot(compact, 18f, color, boxW),
    trailing = null,
    minimal = { MinimalGlyph(minimal, 16f, color) },
    expanded = expanded(),
)

private fun hotspotExpanded(p: Payload.Hotspot) = Expanded(367f, 86f) {
    Box(Modifier.size(367.pt, 86.pt)) {
        At(24f, 21f) { CircleButton(44f, KitColor.Green.copy(alpha = 0.18f)) { Glyph(Sf.HotspotBold, 18f, KitColor.Green) } }
        At(76f, 28.8f, 200f, 11f) { KitText("Personal Hotspot", 14f, W.Regular, KitColor.Gray2) }
        At(76f, 46.2f, 200f, 11f) {
            KitText(if (p.devices == 1) "1 Connection" else if (p.devices > 1) "${p.devices} Connections" else "On", 16f, W.Semibold, Color.White)
        }
    }
}

private fun mirroring(a: IslandActivity, p: Payload.Mirroring) = ActivityUi(
    leading = glyphSlot(Sf.MirroringBold, 18f, KitColor.Cyan, 28f),
    trailing = glyphSlot(Sf.Laptop, 18f, KitColor.Muted, 28f),
    minimal = { MinimalGlyph(Sf.Mirroring, 16f, KitColor.Cyan) },
    expanded = Expanded(367f, 144f, radius = 42f) {
        Box(Modifier.size(367.pt, 144.pt)) {
            At(32f, 38f, 43f, 18f, Alignment.Center) { Glyph(Sf.MirroringBold, 28f, KitColor.Cyan) }
            At(91f, 27f, 198f, 22f) { KitText("Screen Mirroring", 16f, W.Medium, Color.White) }
            At(91f, 49f, 198f, 18f) { KitText(p.device, 16f, W.Medium, Color(0xFF818383)) }
            At(305f, 38f, 30f, 18f, Alignment.Center) { Glyph(Sf.Laptop, 18f, KitColor.Muted) }
            At(16f, 83f) { CapsuleButton("Stop Mirroring", Color(0xFF1A1C2D), KitColor.Cyan, 335f, 43f, modifier = Modifier.tap(a, Action.StopMirroring)) }
        }
    },
)

private fun noData(a: IslandActivity) = ActivityUi(
    leading = glyphSlot(Sf.AntennaBold, 18f, KitColor.Green, 24f),
    trailing = null,
    minimal = { MinimalGlyph(Sf.Antenna, 16f, KitColor.Green) },
    expanded = Expanded(367f, 162f, radius = 42f) {
        Box(Modifier.size(367.pt, 162.pt)) {
            At(32f, 47f, 37f, 18f, Alignment.Center) { Glyph(Sf.AntennaBold, 28f, KitColor.Green) }
            At(85f, 27f, 250f, 22f) { KitText("Mobile Data is Turned Off", 16f, W.Medium, Color.White) }
            At(85f, 49f, 250f, 36f) { KitText("Turn on mobile data or use Wi-Fi to access data.", 16f, W.Medium, Color(0xFF818383), maxLines = 2) }
            At(16f, 101f) { CapsuleButton("OK", KitColor.Fill, Color.White, 161.5f, 43f, modifier = Modifier.tap(a, Action.Dismiss)) }
            At(189.5f, 101f) { CapsuleButton("Settings", Color(0xFF1A1C2D), KitColor.Cyan, 161.5f, 43f, modifier = Modifier.tap(a, Action.OpenSettings)) }
        }
    },
)

private fun airplane(a: IslandActivity) = ActivityUi(
    leading = glyphSlot(Sf.Airplane, 18f, KitColor.Orange, 24f),
    trailing = null,
    minimal = { MinimalGlyph(Sf.Airplane, 18f, KitColor.Orange) },
    expanded = Expanded(367f, 148f, radius = 42f) {
        Box(Modifier.size(367.pt, 148.pt)) {
            At(32f, 33f, 35f, 32f, Alignment.Center) { Glyph(Sf.Airplane, 28f, KitColor.Orange) }
            At(83f, 27f, 252f, 44f) { KitText("Turn Off Airplane Mode or Use Wi-Fi to Access Data", 16f, W.Regular, Color.White, maxLines = 2) }
            At(16f, 87f) { CapsuleButton("Open Settings", KitColor.Fill, Color.White, 335f, 43f, modifier = Modifier.tap(a, Action.OpenSettings)) }
        }
    },
)

// ---------------------------------------------------------------------------------------------
// SharePlay, SOS via Satellite, Find My
// ---------------------------------------------------------------------------------------------

private fun sharePlay(a: IslandActivity, p: Payload.SharePlay) = ActivityUi(
    leading = glyphSlot(Sf.SharePlayBold, 18f, KitColor.Green, 31f),
    trailing = glyphSlot(Sf.MusicNote, 18f, KitColor.Muted, 16f),
    minimal = { MinimalGlyph(Sf.SharePlay, 16f, KitColor.Green) },
    expanded = Expanded(367f, 172.6f, radius = 42f) {
        Box(Modifier.size(367.pt, 172.6.pt)) {
            At(24f, 32f) { Artwork(p.art, 53f, 26.5f) }
            At(97f, 39.5f, 238f, 18f) { KitText(p.title, 16f, W.Semibold, Color.White) }
            At(97f, 59.5f, 238f, 18f) { KitText("${p.subtitle} \u203A", 15f, W.Regular, KitColor.Secondary) }
            val xs = floatArrayOf(24f, 91.1f, 158.2f, 225.3f, 292.4f)
            At(xs[0], 98f) { CircleButton(50.6f, KitColor.Button) { Glyph(Sf.Speaker, 22.82f, Color.White) } }
            At(xs[1], 98f) { CircleButton(50.6f, KitColor.Button) { Glyph(Sf.Mic, 22.82f, Color.White) } }
            At(xs[2], 98f) { CircleButton(50.6f, Color.White) { Glyph(Sf.Video, 22.82f, Color.Black) } }
            At(xs[3], 98f) { CircleButton(50.6f, Color.White) { Glyph(Sf.SharePlayRegular, 22.82f, Color.Black) } }
            At(xs[4], 98f) { CircleButton(50.6f, KitColor.Red, Modifier.tap(a, Action.HangUp)) { Glyph(Sf.Xmark, 22.82f, Color.White) } }
        }
    },
)

private fun satellite() = ActivityUi(
    leading = Slot(24f, round = true) { SatelliteIcon(24f, innerRing = true) },
    trailing = null,
    minimal = { SatelliteIcon(28.165f, innerRing = false) },
    expanded = Expanded(367f, 86f) {
        Box(Modifier.size(367.pt, 86.pt)) {
            At(18f, 15f, 56f, 56f, Alignment.Center) {
                Image(painterResource(R.drawable.img_find_disc), null, Modifier.size(56.pt))
                SatelliteIcon(44f, innerRing = false)
            }
            At(82f, 28.8f, 200f, 11f) { KitText("CONNECTED", 13f, W.Regular, KitColor.Green) }
            At(82f, 46.2f, 200f, 11f) { KitText("Keep pointing at Satellite", 16f, W.Regular, Color.White) }
            At(308f, 22f) { CircleButton(41.4f, Color(0xFF0B2F16)) { Glyph(Sf.Message, 18.64f, KitColor.Green) } }
        }
    },
)

private fun findMy(p: Payload.FindMy) = ActivityUi(
    leading = Slot(24f, round = true) { Image(painterResource(R.drawable.img_find_disc), null, Modifier.size(24.pt)) },
    trailing = null,
    minimal = { Image(painterResource(R.drawable.img_find_disc), null, Modifier.size(24.pt)) },
    expanded = Expanded(367f, 86f) {
        Box(Modifier.size(367.pt, 86.pt)) {
            At(18f, 15f) { Image(painterResource(R.drawable.img_find_disc), null, Modifier.size(56.pt)) }
            At(82f, 37.5f, 260f, 11f) { KitText(p.text, 18f, W.Regular, Color.White) }
        }
    },
)
