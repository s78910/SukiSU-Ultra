package com.sukisu.ultra.ui.screen.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.sukisu.ultra.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import com.sukisu.ultra.ui.theme.wallpaperCardColors
import com.sukisu.ultra.ui.theme.wallpaperContainerColor
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@SuppressLint("ObsoleteSdkInt")
@Composable
@Destination<RootGraph>
fun Personalization(
    navigator: DestinationsNavigator
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = colorScheme.surface,
        tint = HazeTint(colorScheme.surface.copy(0.8f))
    )

    Scaffold(
        containerColor = wallpaperContainerColor(),
        topBar = {
            TopAppBar(
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 30.dp
                    noiseFactor = 0f
                },
                color = Color.Transparent,
                title = stringResource(R.string.personalization),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigator.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        LazyColumn(
            modifier = Modifier
                .height(getWindowSize().height.dp)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .hazeSource(state = hazeState)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    colors = wallpaperCardColors(),
                ) {
                    val themeItems = listOf(
                        stringResource(id = R.string.settings_theme_mode_system),
                        stringResource(id = R.string.settings_theme_mode_light),
                        stringResource(id = R.string.settings_theme_mode_dark),
                        stringResource(id = R.string.settings_theme_mode_monet_system),
                        stringResource(id = R.string.settings_theme_mode_monet_light),
                        stringResource(id = R.string.settings_theme_mode_monet_dark),
                    )
                    var themeMode by rememberSaveable {
                        mutableIntStateOf(prefs.getInt("color_mode", 0))
                    }
                    SuperDropdown(
                        title = stringResource(id = R.string.settings_theme),
                        summary = stringResource(id = R.string.settings_theme_summary),
                        items = themeItems,
                        leftAction = {
                            Icon(
                                Icons.Rounded.Palette,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = stringResource(id = R.string.settings_theme),
                                tint = colorScheme.onBackground
                            )
                        },
                        selectedIndex = themeMode,
                        onSelectedIndexChange = { index ->
                            prefs.edit { putInt("color_mode", index) }
                            themeMode = index
                        }
                    )

                    AnimatedVisibility(
                        visible = themeMode in 3..5
                    ) {
                        val colorItems = listOf(
                            stringResource(id = R.string.settings_key_color_default),
                            stringResource(id = R.string.color_blue),
                            stringResource(id = R.string.color_red),
                            stringResource(id = R.string.color_green),
                            stringResource(id = R.string.color_purple),
                            stringResource(id = R.string.color_orange),
                            stringResource(id = R.string.color_teal),
                            stringResource(id = R.string.color_pink),
                            stringResource(id = R.string.color_brown),
                        )
                        val colorValues = listOf(
                            0,
                            Color(0xFF1A73E8).toArgb(),
                            Color(0xFFEA4335).toArgb(),
                            Color(0xFF34A853).toArgb(),
                            Color(0xFF9333EA).toArgb(),
                            Color(0xFFFB8C00).toArgb(),
                            Color(0xFF009688).toArgb(),
                            Color(0xFFE91E63).toArgb(),
                            Color(0xFF795548).toArgb(),
                        )
                        var keyColorIndex by rememberSaveable {
                            mutableIntStateOf(
                                colorValues.indexOf(prefs.getInt("key_color", 0)).takeIf { it >= 0 } ?: 0
                            )
                        }
                        SuperDropdown(
                            title = stringResource(id = R.string.settings_key_color),
                            summary = stringResource(id = R.string.settings_key_color_summary),
                            items = colorItems,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_key_color),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = keyColorIndex,
                            onSelectedIndexChange = { index ->
                                prefs.edit { putInt("key_color", colorValues[index]) }
                                keyColorIndex = index
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val isSystemDarkMode = isSystemInDarkTheme()
                    val isDarkThemeActive = when (themeMode) {
                        1, 4 -> false
                        2, 5 -> true
                        else -> isSystemDarkMode
                    }
                    val wallpaperAlphaMin = if (isDarkThemeActive) 0.2f else 0f
                    val wallpaperEnabledAlpha = if (isDarkThemeActive) 0.5f else 0.3f
                    val wallpaperPreferenceState = rememberWallpaperPreferenceState(
                        prefs = prefs,
                        wallpaperAlphaMin = wallpaperAlphaMin,
                        wallpaperEnabledAlpha = wallpaperEnabledAlpha
                    )

                    val summaryText = if (wallpaperPreferenceState.isEnabled) {
                        stringResource(id = R.string.settings_wallpaper_summary_selected)
                    } else {
                        stringResource(id = R.string.settings_wallpaper_summary)
                    }

                    val wallpaperPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                try {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                } catch (_: SecurityException) {
                                }
                            }
                            val uriString = uri.toString()
                            wallpaperPreferenceState.setWallpaper(uriString)
                        }
                    }

                    SuperArrow(
                        title = stringResource(id = R.string.settings_wallpaper),
                        summary = summaryText,
                        leftAction = {
                            Icon(
                                Icons.Rounded.Palette,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = stringResource(id = R.string.settings_wallpaper),
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    val wallpaperModes = listOf(
                        stringResource(id = R.string.settings_wallpaper_mode_fill),
                        stringResource(id = R.string.settings_wallpaper_mode_stretch),
                        stringResource(id = R.string.settings_wallpaper_mode_fit)
                    )

                    AnimatedVisibility(visible = wallpaperPreferenceState.isEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(id = R.string.settings_wallpaper_replace),
                                    onClick = {
                                        wallpaperPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(id = R.string.settings_wallpaper_remove),
                                    onClick = {
                                        wallpaperPreferenceState.removeWallpaper()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            SuperDropdown(
                                title = stringResource(id = R.string.settings_wallpaper_mode),
                                summary = stringResource(id = R.string.settings_wallpaper_mode_summary),
                                items = wallpaperModes,
                                leftAction = {
                                    Icon(
                                        Icons.Rounded.Palette,
                                        modifier = Modifier.padding(end = 16.dp),
                                        contentDescription = stringResource(id = R.string.settings_wallpaper_mode),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                selectedIndex = wallpaperPreferenceState.scaleMode,
                                onSelectedIndexChange = { index ->
                                    wallpaperPreferenceState.updateScaleMode(index)
                                }
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        id = R.string.settings_wallpaper_opacity_label,
                                        (wallpaperPreferenceState.sliderValue * 100).roundToInt()
                                    ),
                                    color = colorScheme.onBackground
                                )
                                Slider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    value = wallpaperPreferenceState.sliderValue,
                                    onValueChange = wallpaperPreferenceState::onSliderChange,
                                    valueRange = wallpaperPreferenceState.minAlpha..1f,
                                    steps = 8,
                                    colors = SliderDefaults.sliderColors(
                                        thumbColor = colorScheme.primary,
                                    ),
                                    onValueChangeFinished = {
                                        wallpaperPreferenceState.onSliderChangeFinished()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberWallpaperPreferenceState(
    prefs: SharedPreferences,
    wallpaperAlphaMin: Float,
    wallpaperEnabledAlpha: Float
): WallpaperPreferenceState {
    val state = remember(prefs) {
        WallpaperPreferenceState(
            prefs = prefs,
            initialUri = prefs.getString("wallpaper_uri", null),
            initialScaleMode = prefs.getInt("wallpaper_scale_mode", 0),
            initialAlpha = prefs.getFloat("wallpaper_alpha", wallpaperAlphaMin)
                .coerceAtLeast(wallpaperAlphaMin),
            initialMinAlpha = wallpaperAlphaMin,
            initialEnabledAlpha = wallpaperEnabledAlpha
        )
    }
    LaunchedEffect(wallpaperAlphaMin, wallpaperEnabledAlpha) {
        state.updateBounds(wallpaperAlphaMin, wallpaperEnabledAlpha)
    }
    return state
}

@Stable
private class WallpaperPreferenceState(
    private val prefs: SharedPreferences,
    initialUri: String?,
    initialScaleMode: Int,
    initialAlpha: Float,
    initialMinAlpha: Float,
    initialEnabledAlpha: Float,
) {
    var uri by mutableStateOf(initialUri)
        private set

    val isEnabled: Boolean
        get() = !uri.isNullOrBlank()

    var scaleMode by mutableIntStateOf(initialScaleMode)
        private set

    var sliderValue by mutableFloatStateOf(initialAlpha)
        private set

    var minAlpha by mutableFloatStateOf(initialMinAlpha)
        private set

    var enabledAlpha by mutableFloatStateOf(initialEnabledAlpha)
        private set

    private var persistedAlpha by mutableFloatStateOf(initialAlpha)

    fun onSliderChange(value: Float) {
        sliderValue = value
    }

    fun onSliderChangeFinished() {
        setAlphaInternal(sliderValue)
    }

    fun setWallpaper(uriString: String) {
        uri = uriString
        val targetAlpha = enabledAlpha.coerceAtLeast(minAlpha)
        prefs.edit {
            putString("wallpaper_uri", uriString)
            putFloat("wallpaper_alpha", targetAlpha)
        }
        setAlphaInternal(targetAlpha, skipPersist = true)
    }

    fun removeWallpaper() {
        prefs.edit {
            remove("wallpaper_uri")
            putFloat("wallpaper_alpha", 1f)
        }
        uri = null
        setAlphaInternal(1f, skipPersist = true)
    }

    fun updateScaleMode(index: Int) {
        scaleMode = index
        prefs.edit { putInt("wallpaper_scale_mode", index) }
    }

    fun updateBounds(min: Float, enabled: Float) {
        val coercedMin = min.coerceIn(0f, 1f)
        val coercedEnabled = enabled.coerceIn(coercedMin, 1f)
        val alphaAdjusted = persistedAlpha.coerceAtLeast(coercedMin)
        val needsAlphaUpdate = alphaAdjusted != persistedAlpha || coercedMin != minAlpha
        minAlpha = coercedMin
        enabledAlpha = coercedEnabled
        if (sliderValue < coercedMin) {
            sliderValue = coercedMin
        }
        if (needsAlphaUpdate) {
            setAlphaInternal(alphaAdjusted, skipPersist = false)
        }
    }

    private fun setAlphaInternal(target: Float, skipPersist: Boolean = false) {
        val coerced = target.coerceIn(minAlpha, 1f)
        persistedAlpha = coerced
        sliderValue = coerced
        if (!skipPersist) {
            prefs.edit { putFloat("wallpaper_alpha", coerced) }
        }
    }
}
