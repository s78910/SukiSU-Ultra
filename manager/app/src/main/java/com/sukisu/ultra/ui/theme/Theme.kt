package com.sukisu.ultra.ui.theme

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun KernelSUTheme(
    colorMode: Int = 0,
    keyColor: Color? = null,
    wallpaperUri: String? = null,
    wallpaperAlpha: Float = 1f,
    wallpaperScaleMode: Int = 0,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val controller = when (colorMode) {
        1 -> ThemeController(ColorSchemeMode.Light)
        2 -> ThemeController(ColorSchemeMode.Dark)
        3 -> ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = keyColor,
            isDark = isDark
        )

        4 -> ThemeController(
            ColorSchemeMode.MonetLight,
            keyColor = keyColor,
        )

        5 -> ThemeController(
            ColorSchemeMode.MonetDark,
            keyColor = keyColor,
        )

        else -> ThemeController(ColorSchemeMode.System)
    }
    return MiuixTheme(
        controller = controller,
    ) {
        val context = LocalContext.current
        val hasWallpaper = !wallpaperUri.isNullOrBlank()
        val surfaceAlpha = if (hasWallpaper) wallpaperAlpha.coerceIn(0f, 1f) else 1f
        val uri = wallpaperUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (hasWallpaper && uri != null) {
                val contentScale = when (wallpaperScaleMode) {
                    1 -> ContentScale.FillBounds
                    2 -> ContentScale.Fit
                    else -> ContentScale.Crop
                }
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    contentScale = contentScale
                )

                val scrimBase = if (isDark) 0.25f else 0.05f
                val scrimScale = if (isDark) 0.75f else 0.4f
                val overlayAlpha = (scrimBase + (1f - surfaceAlpha) * scrimScale).coerceIn(0f, if (isDark) 0.85f else 0.45f)
                if (overlayAlpha > 0f) {
                    val overlayColor = if (isDark) {
                        Color.Black.copy(alpha = overlayAlpha)
                    } else {
                        Color.White.copy(alpha = overlayAlpha * 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(overlayColor)
                    )
                }
            }

            CompositionLocalProvider(
                LocalWallpaperState provides WallpaperState(
                    enabled = hasWallpaper,
                    surfaceAlpha = surfaceAlpha,
                    transitionBlend = if (hasWallpaper) 1f else 0f
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                ) {
                    content()
                }
            }
        }
    }
}
