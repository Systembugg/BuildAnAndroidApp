package dev.capsule.island.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import dev.capsule.island.R
import dev.capsule.island.core.pt
import dev.capsule.island.core.ptSp
import dev.capsule.island.engine.Img

/** Kit colours (the Figma file's Base and System Gray styles). */
object KitColor {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Gray6 = Color(0xFFF0F0F5)
    val Gray2 = Color(0xFFA4A4A9)
    val Gray = Color(0xFF838388)
    val Secondary = Color(0xFF9A9A9A)
    val Muted = Color(0xFF8F8F8F)
    val Cyan = Color(0xFF37A3DE)
    val Green = Color(0xFF37C058)
    val GreenDeep = Color(0xFF13351F)
    val Indigo = Color(0xFF504BCA)
    val Red = Color(0xFFFA3532)
    val Orange = Color(0xFFFB8B28)
    val Yellow = Color(0xFFFDC633)
    val NavBlue = Color(0xFF90C9F9)
    val TransitBlue = Color(0xFF9AD4F2)
    val Button = Color(0xFF2A292D)
    val Fill = Color(0xFF2C2C2D)
    val Track = Color(0xFF3F3F3F)
    val Dim = Color(0xFF5A5A5A)
}

/** Inter stands in for SF Pro Text, which can't ship outside Apple platforms. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

enum class W(val weight: FontWeight) { Regular(FontWeight.Normal), Medium(FontWeight.Medium), Semibold(FontWeight.SemiBold), Bold(FontWeight.Bold) }

@Composable
fun kitStyle(size: Float, weight: W, color: Color, tabular: Boolean = false, align: TextAlign = TextAlign.Start) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight.weight,
    fontSize = size.ptSp,
    lineHeight = (size * 1.2f).ptSp,
    // SF's tracking at text sizes, on Inter's slightly wider set.
    letterSpacing = if (size >= 12f) (-0.022f).em else 0.em,
    color = color,
    fontFeatureSettings = if (tabular) "tnum" else null,
    textAlign = align,
)

/** One line of kit text, vertically centred on the kit's text frame, ellipsized at [w]. */
@Composable
fun KitText(
    text: String,
    size: Float,
    weight: W,
    color: Color,
    modifier: Modifier = Modifier,
    w: Float? = null,
    tabular: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier.then(if (w != null) Modifier.width(w.pt) else Modifier).wrapContentHeight(unbounded = true),
        style = kitStyle(size, weight, color, tabular, align),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Places [content] at kit coordinates (points from the parent's top-left). */
@Composable
fun At(x: Float, y: Float, w: Float? = null, h: Float? = null, align: Alignment = Alignment.CenterStart, content: @Composable BoxScope.() -> Unit) {
    var m: Modifier = Modifier.offset(x.pt, y.pt)
    if (w != null && h != null) m = m.size(w.pt, h.pt)
    else if (w != null) m = m.width(w.pt)
    Box(m, contentAlignment = align, content = content)
}

@Composable
fun imageOf(img: Img?): ImageBitmap? = when (img) {
    is Img.Res -> ImageBitmap.imageResource(img.id)
    is Img.Bitmap -> img.bitmap
    null -> null
}

/** Artwork, a caller photo or a placeholder fill, clipped to the kit's corner. */
@Composable
fun Artwork(img: Img?, size: Float, radius: Float, placeholder: Color = KitColor.Fill, modifier: Modifier = Modifier) {
    val shape = if (radius >= size / 2) CircleShape else RoundedCornerShape(radius.pt)
    val bitmap = imageOf(img)
    Box(modifier.size(size.pt).clip(shape).background(placeholder)) {
        if (bitmap != null) Image(bitmap, null, Modifier.size(size.pt), contentScale = ContentScale.Crop)
    }
}

/** A round button from the call and SharePlay cards. */
@Composable
fun CircleButton(size: Float, fill: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.size(size.pt).clip(CircleShape).background(fill), contentAlignment = Alignment.Center, content = content)
}

/** A capsule button from the expanded cards (Open Settings, Stop Mirroring, Unmute). */
@Composable
fun CapsuleButton(text: String, fill: Color, ink: Color, w: Float, h: Float, size: Float = 18f, weight: W = W.Medium, modifier: Modifier = Modifier) {
    Box(modifier.size(w.pt, h.pt).clip(RoundedCornerShape(50)).background(fill), contentAlignment = Alignment.Center) {
        KitText(text, size, weight, ink)
    }
}
