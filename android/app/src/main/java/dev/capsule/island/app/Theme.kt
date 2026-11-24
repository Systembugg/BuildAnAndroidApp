package dev.capsule.island.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.capsule.island.ui.kit.Inter

/** OLED instrument: true black surfaces, hairlines, and colour only where a state is live. */
object Ink {
    val Background = Color(0xFF09090B)
    val Surface = Color(0xFF121216)
    val Raised = Color(0xFF1B1B22)
    val Hairline = Color(0xFF272730)
    val Primary = Color(0xFFF5F5F7)
    val Secondary = Color(0xFF8E8E93)
    val Tertiary = Color(0xFF5C5C66)
    val Green = Color(0xFF30D158)
    val Amber = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Blue = Color(0xFF0A84FF)
}

private val type = Typography(
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.03).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.02).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.01).em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.08.em),
)

@Composable
fun CapsuleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.Background,
            surface = Ink.Surface,
            surfaceVariant = Ink.Raised,
            surfaceContainer = Ink.Surface,
            onBackground = Ink.Primary,
            onSurface = Ink.Primary,
            onSurfaceVariant = Ink.Secondary,
            primary = Ink.Primary,
            onPrimary = Ink.Background,
            secondaryContainer = Ink.Raised,
            onSecondaryContainer = Ink.Primary,
            outline = Ink.Hairline,
            outlineVariant = Ink.Hairline,
        ),
        typography = type,
        content = content,
    )
}
