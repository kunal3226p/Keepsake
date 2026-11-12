package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracotta,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = BotanicalSage,
    onSecondary = OnBotanicalSage,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = SunlitGold,
    onTertiary = OnSunlitGold,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = LinenBackground,
    onBackground = EspressoInk,
    surface = WarmPaperSurface,
    onSurface = EspressoInk,
    surfaceVariant = WarmParchmentSurface,
    onSurfaceVariant = MutedSepia,
    surfaceContainerLow = WarmParchmentSurface,
    surfaceContainer = WarmStoneContainer,
    surfaceContainerHigh = WarmStoneHigh,
    outline = WarmHairline,
    outlineVariant = WarmHairlineSubtle,
    error = CrimsonError,
    errorContainer = CrimsonContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = DarkOnTerracotta,
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = DarkOnTerracottaContainer,
    secondary = DarkSage,
    onSecondary = DarkOnSage,
    secondaryContainer = DarkSageContainer,
    onSecondaryContainer = DarkOnSageContainer,
    tertiary = DarkGold,
    onTertiary = DarkOnGold,
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = DarkOnGoldContainer,
    background = GalleryDarkBg,
    onBackground = WarmIvoryText,
    surface = GalleryDarkSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = GalleryCardSurface,
    onSurfaceVariant = MutedSand,
    surfaceContainerLow = GalleryDarkSurface,
    surfaceContainer = GalleryCardSurface,
    surfaceContainerHigh = GalleryCardHigh,
    outline = DarkWarmOutline,
    outlineVariant = DarkWarmOutline
)

val KeepsakeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun KeepsakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KeepsakeTypography,
        shapes = KeepsakeShapes,
        content = content
    )
}
