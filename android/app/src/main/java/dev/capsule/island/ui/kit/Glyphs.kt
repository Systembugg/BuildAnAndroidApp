package dev.capsule.island.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import dev.capsule.island.R
import dev.capsule.island.core.pt

/**
 * The kit's SF Symbols, outlined from the Figma file (see tools/svg_to_vector.mjs). [w] and [h]
 * are the outline's size at [size], the type size the kit used it at; [Glyph] scales from there.
 */
enum class Sf(@DrawableRes val res: Int, val w: Float, val h: Float, val size: Float) {
    Waveform(R.drawable.sf_10066b_regular, 16.2f, 18.36f, 18f),
    FaceId(R.drawable.sf_1003bd_regular, 19.12f, 19.11f, 20f),
    Bell(R.drawable.sf_1002da_regular, 14.74f, 16.45f, 16f),
    BellBold(R.drawable.sf_1002da_bold, 17.56f, 19.09f, 18f),
    BellSlash(R.drawable.sf_1002de_regular, 15.24f, 16.45f, 16f),
    Moon(R.drawable.sf_1001ba_bold, 15.36f, 15.43f, 16f),
    Car(R.drawable.sf_100659_bold, 19.46f, 15.4f, 16f),
    Hotspot(R.drawable.sf_100264_semibold, 22.83f, 13.2f, 16f),
    HotspotBold(R.drawable.sf_100264_bold, 25.93f, 15.15f, 18f),
    Mirroring(R.drawable.sf_1003e7_semibold, 19.97f, 16.5f, 16f),
    MirroringBold(R.drawable.sf_1003e7_bold, 22.81f, 19f, 18f),
    Tram(R.drawable.sf_100747_semibold, 14.27f, 20.52f, 16f),
    TramBold(R.drawable.sf_100747_bold, 16.79f, 23.84f, 18f),
    Antenna(R.drawable.sf_100580_semibold, 17.44f, 14.84f, 16f),
    AntennaBold(R.drawable.sf_100580_bold, 20.17f, 16.9f, 18f),
    SharePlay(R.drawable.sf_1010d1_semibold, 23.78f, 13.73f, 16f),
    SharePlayBold(R.drawable.sf_1010d1_bold, 27.07f, 15.69f, 18f),
    SharePlayRegular(R.drawable.sf_1010d1_regular, 33.24f, 19.03f, 22.82f),
    CheckCircle(R.drawable.sf_100062_regular, 19.92f, 19.92f, 20f),
    LockClosed(R.drawable.sf_1003a1_regular, 11.74f, 17.15f, 18f),
    LockOpen(R.drawable.sf_1003a5_regular, 17.96f, 17.15f, 18f),
    Phone(R.drawable.sf_10033f_regular, 16.29f, 16.32f, 18f),
    PhoneSemibold(R.drawable.sf_10033f_semibold, 16.82f, 16.84f, 18f),
    PhoneDown(R.drawable.sf_100347_regular, 26.91f, 10.07f, 23.44f),
    Video(R.drawable.sf_10034a_regular, 16.78f, 11.1f, 14f),
    VideoSemibold(R.drawable.sf_10034a_semibold, 21.99f, 14.62f, 18f),
    Timer(R.drawable.sf_100431_medium, 20.18f, 20.18f, 20f),
    TimerSemibold(R.drawable.sf_100431_semibold, 18.33f, 18.33f, 18f),
    TurnRight(R.drawable.sf_100139_medium, 16.59f, 14.04f, 18f),
    TurnRightSemibold(R.drawable.sf_100139_semibold, 16.67f, 14.19f, 18f),
    TurnRightLarge(R.drawable.sf_100139_regular, 32.99f, 27.67f, 36f),
    TurnLeftLarge(R.drawable.sf_100138_regular, 32.99f, 27.67f, 36f),
    StraightLarge(R.drawable.sf_100128_regular, 26.65f, 33.21f, 36f),
    Forward(R.drawable.sf_10028c_semibold, 34.01f, 19.15f, 24f),
    Backward(R.drawable.sf_10028a_semibold, 28.33f, 15.96f, 20f),
    Play(R.drawable.sf_100284_semibold, 25.02f, 27.97f, 32f),
    Pause(R.drawable.sf_100286_regular, 14f, 18.7f, 23.51f),
    AirPlayAudio(R.drawable.sf_100462_semibold, 20.23f, 19.9f, 20f),
    Back15(R.drawable.sf_100384_semibold, 24.45f, 26.73f, 24f),
    Forward15(R.drawable.sf_100383_semibold, 24.45f, 26.86f, 24f),
    Speaker(R.drawable.sf_1002a8_regular, 29.91f, 21.53f, 22.82f),
    Mic(R.drawable.sf_1002b1_regular, 15.95f, 23.74f, 22.82f),
    Xmark(R.drawable.sf_100184_medium, 17.95f, 17.95f, 22.82f),
    XmarkSemibold(R.drawable.sf_100184_semibold, 18.7f, 18.71f, 23.51f),
    Laptop(R.drawable.sf_1007db_regular, 25.35f, 14.3f, 18f),
    MusicNote(R.drawable.sf_10046a_regular, 10.72f, 17.65f, 18f),
    Message(R.drawable.sf_100325_regular, 20.64f, 18.98f, 18.64f),
    Info(R.drawable.sf_100174_regular, 17.93f, 17.93f, 18f),
    ScreenShare(R.drawable.sf_101140_regular, 30.65f, 20.15f, 22.41f),
    AudioRoute(R.drawable.sf_100ab7_regular, 31.25f, 20.14f, 23.31f),
    ReturnArrow(R.drawable.sf_100c4d_medium, 16.83f, 16.73f, 18f),
    Airplane(R.drawable.sf_100453_regular, 30.82f, 27.84f, 28f),
}

/** A kit glyph at type size [size] (points), tinted [color] or filled with [brush]. */
@Composable
fun Glyph(sf: Sf, size: Float, color: Color, modifier: Modifier = Modifier, brush: Brush? = null) {
    val k = size / sf.size
    val painter = painterResource(sf.res)
    if (brush == null) {
        Image(painter, null, modifier.size((sf.w * k).pt, (sf.h * k).pt), colorFilter = ColorFilter.tint(color))
    } else {
        Image(
            painter, null,
            modifier
                .size((sf.w * k).pt, (sf.h * k).pt)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcIn)
                },
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

/** A glyph centred in a box the size of the kit's text frame, as SF Symbols sit in a line of text. */
@Composable
fun GlyphBox(sf: Sf, size: Float, color: Color, w: Float, h: Float, modifier: Modifier = Modifier, brush: Brush? = null) {
    Box(modifier.size(w.pt, h.pt), contentAlignment = Alignment.Center) { Glyph(sf, size, color, brush = brush) }
}
