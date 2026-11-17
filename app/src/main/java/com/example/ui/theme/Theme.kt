package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MeroDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ArcCyanDim,
    onPrimaryContainer = ArcCyanBright,
    secondary = FilmyGold,
    onSecondary = ObsidianVoid,
    secondaryContainer = FilmyGoldDim,
    onSecondaryContainer = FilmyGoldBright,
    tertiary = PulseCrimson,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = IceWhite,
    surface = ObsidianSurface,
    onSurface = IceWhite,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = MistSlate,
    outline = HairlineCyan,
    outlineVariant = HairlineGold,
    error = PulseCrimson,
    onError = ObsidianVoid
)

private val MeroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MeroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MeroDarkColorScheme,
        typography = MeroTypography,
        shapes = MeroShapes,
        content = content
    )
}
