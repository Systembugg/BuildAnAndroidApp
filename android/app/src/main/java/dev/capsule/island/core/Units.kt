package dev.capsule.island.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Island geometry is written in iOS points, as measured on the reference phone (402 pt wide) and
 * in the Figma kit. One point is [LocalPt] dp on this screen. Content inside the expanded island
 * overrides it so the kit's 367 pt canvas fills the expanded width exactly.
 */
val LocalPt = staticCompositionLocalOf { 1f }

val Number.pt: Dp
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalPt.current).dp

/** Type size in points. Ignores the system font scale, as the island does on iOS. */
val Number.ptSp: TextUnit
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) { (toFloat() * LocalPt.current).dp.toSp() }
