package com.sukisu.ultra.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun KernelSUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val themeMode = prefs.getInt("theme_mode", 0)

    val useDarkTheme = when (themeMode) {
        1 -> false // 浅色
        2 -> true  // 深色
        else -> darkTheme // 跟随系统
    }

    val colorScheme = when {
        useDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MiuixTheme(
        colors = colorScheme,
        content = content
    )
}
