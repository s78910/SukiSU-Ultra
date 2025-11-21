package com.sukisu.ultra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Stable
data class WallpaperState(
    val enabled: Boolean,
    val surfaceAlpha: Float,
    val transitionBlend: Float
)

val LocalWallpaperState = compositionLocalOf {
    WallpaperState(
        enabled = false,
        surfaceAlpha = 1f,
        transitionBlend = 0f
    )
}

@Composable
fun wallpaperContainerColor(): Color {
    val wallpaperState = LocalWallpaperState.current
    if (!wallpaperState.enabled) {
        return MiuixTheme.colorScheme.surface
    }
    val base = MiuixTheme.colorScheme.surface
    val adjustedAlpha = wallpaperState.surfaceAlpha.coerceIn(0f, 1f)
    return base.copy(alpha = adjustedAlpha)
}

@Composable
fun wallpaperSurfaceColor(base: Color = MiuixTheme.colorScheme.surface): Color {
    val wallpaperState = LocalWallpaperState.current
    if (!wallpaperState.enabled) {
        return base
    }
    val alphaMultiplier = wallpaperState.surfaceAlpha.coerceIn(0f, 1f)
    val dimOverlay = Color.Black.copy(alpha = 0.25f)
    val tintedBase = lerp(base, dimOverlay, 0.15f)
    return tintedBase.copy(alpha = tintedBase.alpha * alphaMultiplier)
}

@Composable
fun wallpaperContentColor(): Color {
    return MiuixTheme.colorScheme.onSurface
}

@Composable
fun wallpaperTransitionAlpha(
    enabledAlpha: Float,
    disabledAlpha: Float = 1f
): Float {
    val wallpaperState = LocalWallpaperState.current
    val blend = wallpaperState.transitionBlend.coerceIn(0f, 1f)
    val clampedEnabled = enabledAlpha.coerceIn(0f, 1f)
    val clampedDisabled = disabledAlpha.coerceIn(0f, 1f)
    return (clampedDisabled + (clampedEnabled - clampedDisabled) * blend)
        .coerceIn(0f, 1f)
}

@Composable
fun wallpaperCardColors(
    background: Color = wallpaperSurfaceColor(),
    content: Color = wallpaperContentColor(),
) = CardDefaults.defaultColors(
    color = background,
    contentColor = content,
)
