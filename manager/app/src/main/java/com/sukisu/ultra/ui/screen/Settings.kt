package com.sukisu.ultra.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppProfileTemplateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.LogViewerDestination
import com.ramcosta.composedestinations.generated.destinations.PersonalizationDestination
import com.ramcosta.composedestinations.generated.destinations.UmountManagerDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.ConfirmResult
import com.sukisu.ultra.ui.component.DynamicManagerCard
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.SendLogDialog
import com.sukisu.ultra.ui.component.UninstallDialog
import com.sukisu.ultra.ui.component.rememberConfirmDialog
import com.sukisu.ultra.ui.component.rememberLoadingDialog
import com.sukisu.ultra.ui.util.cleanRuntimeEnvironment
import com.sukisu.ultra.ui.util.execKsud
import com.sukisu.ultra.ui.util.getUidMultiUserScan
import com.sukisu.ultra.ui.util.readUidScannerFile
import com.sukisu.ultra.ui.util.setUidAutoScan
import com.sukisu.ultra.ui.util.setUidMultiUserScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
@Destination<RootGraph>
fun SettingPager(
    navigator: DestinationsNavigator,
    bottomInnerPadding: Dp
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = colorScheme.surface,
        tint = HazeTint(colorScheme.surface.copy(0.8f))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 30.dp
                    noiseFactor = 0f
                },
                color = Color.Transparent,
                title = stringResource(R.string.settings),
                scrollBehavior = scrollBehavior
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val loadingDialog = rememberLoadingDialog()

        val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
        val uninstallDialog = UninstallDialog(showUninstallDialog, navigator)
        val showSendLogDialog = rememberSaveable { mutableStateOf(false) }
        val sendLogDialog = SendLogDialog(showSendLogDialog, loadingDialog)
        var isSuLogEnabled by remember { mutableStateOf(Natives.isSuLogEnabled()) }

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
                val context = LocalContext.current
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                var checkUpdate by rememberSaveable {
                    mutableStateOf(prefs.getBoolean("check_update", true))
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val personalization = stringResource(id = R.string.personalization)
                    SuperArrow(
                        title = personalization,
                        summary = stringResource(id = R.string.personalization_summary),
                        leftAction = {
                            Icon(
                                Icons.Rounded.Palette,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = personalization,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            navigator.navigate(PersonalizationDestination) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SuperSwitch(
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        leftAction = {
                            Icon(
                                Icons.Rounded.Update,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = stringResource(id = R.string.settings_check_update),
                                tint = colorScheme.onBackground
                            )
                        },
                        checked = checkUpdate,
                        onCheckedChange = {
                            prefs.edit {
                                putBoolean("check_update", it)
                            }
                            checkUpdate = it
                        }
                    )
                    KsuIsValid {
                        var checkModuleUpdate by rememberSaveable {
                            mutableStateOf(prefs.getBoolean("module_check_update", true))
                        }
                        SuperSwitch(
                            title = stringResource(id = R.string.settings_module_check_update),
                            summary = stringResource(id = R.string.settings_check_update_summary),
                            leftAction = {
                                Icon(
                                    Icons.Rounded.UploadFile,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_check_update),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = checkModuleUpdate,
                            onCheckedChange = {
                                prefs.edit {
                                    putBoolean("module_check_update", it)
                                }
                                checkModuleUpdate = it
                            }
                        )
                    }
                }

                KsuIsValid {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val profileTemplate = stringResource(id = R.string.settings_profile_template)
                        SuperArrow(
                            title = profileTemplate,
                            summary = stringResource(id = R.string.settings_profile_template_summary),
                            leftAction = {
                                Icon(
                                    Icons.Rounded.Fence,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = profileTemplate,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                navigator.navigate(AppProfileTemplateScreenDestination) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }

                KsuIsValid {

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val modeItems = listOf(
                            stringResource(id = R.string.settings_mode_default),
                            stringResource(id = R.string.settings_mode_temp_enable),
                            stringResource(id = R.string.settings_mode_always_enable),
                        )
                        var enhancedSecurityMode by rememberSaveable {
                            mutableIntStateOf(
                                run {
                                    val currentEnabled = Natives.isEnhancedSecurityEnabled()
                                    val savedPersist = prefs.getInt("enhanced_security_mode", 0)
                                    if (savedPersist == 2) 2 else if (currentEnabled) 1 else 0
                                }
                            )
                        }
                        SuperDropdown(
                            title = stringResource(id = R.string.settings_enable_enhanced_security),
                            summary = stringResource(id = R.string.settings_enable_enhanced_security_summary),
                            items = modeItems,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.EnhancedEncryption,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_enable_enhanced_security),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = enhancedSecurityMode,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    // Default: disable and save to persist
                                    0 -> if (Natives.setEnhancedSecurityEnabled(false)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("enhanced_security_mode", 0) }
                                        enhancedSecurityMode = 0
                                    }

                                    // Temporarily enable: save disabled state first, then enable
                                    1 -> if (Natives.setEnhancedSecurityEnabled(false)) {
                                        execKsud("feature save", true)
                                        if (Natives.setEnhancedSecurityEnabled(true)) {
                                            prefs.edit { putInt("enhanced_security_mode", 0) }
                                            enhancedSecurityMode = 1
                                        }
                                    }

                                    // Permanently enable: enable and save
                                    2 -> if (Natives.setEnhancedSecurityEnabled(true)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("enhanced_security_mode", 2) }
                                        enhancedSecurityMode = 2
                                    }
                                }
                            }
                        )

                        var suCompatMode by rememberSaveable {
                            mutableIntStateOf(
                                run {
                                    val currentEnabled = Natives.isSuEnabled()
                                    val savedPersist = prefs.getInt("su_compat_mode", 0)
                                    if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                }
                            )
                        }
                        SuperDropdown(
                            title = stringResource(id = R.string.settings_disable_su),
                            summary = stringResource(id = R.string.settings_disable_su_summary),
                            items = modeItems,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.RemoveModerator,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_disable_su),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = suCompatMode,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    // Default: enable and save to persist
                                    0 -> if (Natives.setSuEnabled(true)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("su_compat_mode", 0) }
                                        suCompatMode = 0
                                    }

                                    // Temporarily disable: save enabled state first, then disable
                                    1 -> if (Natives.setSuEnabled(true)) {
                                        execKsud("feature save", true)
                                        if (Natives.setSuEnabled(false)) {
                                            prefs.edit { putInt("su_compat_mode", 0) }
                                            suCompatMode = 1
                                        }
                                    }

                                    // Permanently disable: disable and save
                                    2 -> if (Natives.setSuEnabled(false)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("su_compat_mode", 2) }
                                        suCompatMode = 2
                                    }
                                }
                            }
                        )

                        var kernelUmountMode by rememberSaveable {
                            mutableIntStateOf(
                                run {
                                    val currentEnabled = Natives.isKernelUmountEnabled()
                                    val savedPersist = prefs.getInt("kernel_umount_mode", 0)
                                    if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                }
                            )
                        }
                        SuperDropdown(
                            title = stringResource(id = R.string.settings_disable_kernel_umount),
                            summary = stringResource(id = R.string.settings_disable_kernel_umount_summary),
                            items = modeItems,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.RemoveCircle,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_disable_kernel_umount),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = kernelUmountMode,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    // Default: enable and save to persist
                                    0 -> if (Natives.setKernelUmountEnabled(true)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("kernel_umount_mode", 0) }
                                        kernelUmountMode = 0
                                    }

                                    // Temporarily disable: save enabled state first, then disable
                                    1 -> if (Natives.setKernelUmountEnabled(true)) {
                                        execKsud("feature save", true)
                                        if (Natives.setKernelUmountEnabled(false)) {
                                            prefs.edit { putInt("kernel_umount_mode", 0) }
                                            kernelUmountMode = 1
                                        }
                                    }

                                    // Permanently disable: disable and save
                                    2 -> if (Natives.setKernelUmountEnabled(false)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("kernel_umount_mode", 2) }
                                        kernelUmountMode = 2
                                    }
                                }
                            }
                        )

                        var SuLogMode by rememberSaveable {
                            mutableIntStateOf(
                                run {
                                    val currentEnabled = Natives.isSuLogEnabled()
                                    val savedPersist = prefs.getInt("sulog_mode", 0)
                                    if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                }
                            )
                        }
                        SuperDropdown(
                            title = stringResource(id = R.string.settings_disable_sulog),
                            summary = stringResource(id = R.string.settings_disable_sulog_summary),
                            items = modeItems,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.RemoveCircle,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_disable_sulog),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = SuLogMode,
                            onSelectedIndexChange = { index ->
                                when (index) {
                                    // Default: enable and save to persist
                                    0 -> if (Natives.setSuLogEnabled(true)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("sulog_mode", 0) }
                                        SuLogMode = 0
                                        isSuLogEnabled = true
                                    }

                                    // Temporarily disable: save enabled state first, then disable
                                    1 -> if (Natives.setSuLogEnabled(true)) {
                                        execKsud("feature save", true)
                                        if (Natives.setSuLogEnabled(false)) {
                                            prefs.edit { putInt("sulog_mode", 0) }
                                            SuLogMode = 1
                                            isSuLogEnabled = false
                                        }
                                    }

                                    // Permanently disable: disable and save
                                    2 -> if (Natives.setSuLogEnabled(false)) {
                                        execKsud("feature save", true)
                                        prefs.edit { putInt("sulog_mode", 2) }
                                        SuLogMode = 2
                                        isSuLogEnabled = false
                                    }
                                }
                            }
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        var umountChecked by rememberSaveable { mutableStateOf(Natives.isDefaultUmountModules()) }
                        SuperSwitch(
                            title = stringResource(id = R.string.settings_umount_modules_default),
                            summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                            leftAction = {
                                Icon(
                                    Icons.Rounded.FolderDelete,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.settings_umount_modules_default),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = umountChecked,
                            onCheckedChange = {
                                if (Natives.setDefaultUmountModules(it)) {
                                    umountChecked = it
                                }
                            }
                        )

                        var enableWebDebugging by rememberSaveable {
                            mutableStateOf(prefs.getBoolean("enable_web_debugging", false))
                        }
                        SuperSwitch(
                            title = stringResource(id = R.string.enable_web_debugging),
                            summary = stringResource(id = R.string.enable_web_debugging_summary),
                            leftAction = {
                                Icon(
                                    Icons.Rounded.DeveloperMode,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = stringResource(id = R.string.enable_web_debugging),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enableWebDebugging,
                            onCheckedChange = {
                                prefs.edit { putBoolean("enable_web_debugging", it) }
                                enableWebDebugging = it
                            }
                        )
                    }
                }

                KsuIsValid {
                    DynamicManagerCard()
                }

                KsuIsValid {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        UidScannerSection(prefs = prefs, scope = scope, context = context)
                    }
                }

                KsuIsValid {
                    val lkmMode = Natives.isLkmMode
                    if (lkmMode) {
                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            val uninstall = stringResource(id = R.string.settings_uninstall)
                            SuperArrow(
                                title = uninstall,
                                leftAction = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        modifier = Modifier.padding(end = 16.dp),
                                        contentDescription = uninstall,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = {
                                    showUninstallDialog.value = true
                                    uninstallDialog
                                }
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                ) {
                    if (isSuLogEnabled) {
                        val sulog = stringResource(id = R.string.log_viewer_view_logs)
                        SuperArrow(
                            title = sulog,
                            leftAction = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = sulog,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                navigator.navigate(LogViewerDestination) {
                                }
                            }
                        )
                    }
                    KsuIsValid {
                        val lkmMode = Natives.isLkmMode
                        if (lkmMode) {
                            val umontManager = stringResource(id = R.string.umount_path_manager)
                            SuperArrow(
                                title = umontManager,
                                leftAction = {
                                    Icon(
                                        Icons.Rounded.FolderDelete,
                                        modifier = Modifier.padding(end = 16.dp),
                                        contentDescription = umontManager,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = {
                                    navigator.navigate(UmountManagerDestination) {
                                    }
                                }
                            )
                        }
                    }

                    SuperArrow(
                        title = stringResource(id = R.string.send_log),
                        leftAction = {
                            Icon(
                                Icons.Rounded.BugReport,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = stringResource(id = R.string.send_log),
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            showSendLogDialog.value = true
                            sendLogDialog
                        },
                    )
                    val about = stringResource(id = R.string.about)
                    SuperArrow(
                        title = about,
                        leftAction = {
                            Icon(
                                Icons.Rounded.ContactPage,
                                modifier = Modifier.padding(end = 16.dp),
                                contentDescription = about,
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            navigator.navigate(AboutScreenDestination) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }
}

enum class UninstallType(val icon: ImageVector, val title: Int, val message: Int) {
    TEMPORARY(
        Icons.Rounded.RemoveModerator,
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message
    ),
    PERMANENT(
        Icons.Rounded.DeleteForever,
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message
    ),
    RESTORE_STOCK_IMAGE(
        Icons.Rounded.RestartAlt,
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message
    ),
    NONE(Icons.Rounded.Adb, 0, 0)
}

@Composable
fun UidScannerSection(
    prefs: SharedPreferences,
    scope: CoroutineScope,
    context: Context
) {
    val realAuto = Natives.isUidScannerEnabled()
    val realMulti = getUidMultiUserScan()

    var autoOn by remember { mutableStateOf(realAuto) }
    var multiOn by remember { mutableStateOf(realMulti) }

    LaunchedEffect(Unit) {
        autoOn = realAuto
        multiOn = realMulti
        prefs.edit {
            putBoolean("uid_auto_scan", autoOn)
            putBoolean("uid_multi_user_scan", multiOn)
        }
    }

    SuperSwitch(
        title = stringResource(R.string.uid_auto_scan_title),
        summary = stringResource(R.string.uid_auto_scan_summary),
        leftAction = {
            Icon(
                Icons.Filled.Scanner,
                modifier = Modifier.padding(end = 16.dp),
                contentDescription = stringResource(R.string.uid_auto_scan_title),
                tint = colorScheme.onBackground
            )
        },
        checked = autoOn,
        onCheckedChange = { target ->
            autoOn = target
            if (!target) multiOn = false

            scope.launch(Dispatchers.IO) {
                setUidAutoScan(target)
                val actual = Natives.isUidScannerEnabled() || readUidScannerFile()
                withContext(Dispatchers.Main) {
                    autoOn = actual
                    if (!actual) multiOn = false
                    prefs.edit {
                        putBoolean("uid_auto_scan", actual)
                        putBoolean("uid_multi_user_scan", multiOn)
                    }
                    if (actual != target) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.uid_scanner_setting_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    )

    AnimatedVisibility(
        visible = autoOn,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        SuperSwitch(
            title = stringResource(R.string.uid_multi_user_scan_title),
            summary = stringResource(R.string.uid_multi_user_scan_summary),
            leftAction = {
                Icon(
                    Icons.Filled.Groups,
                    modifier = Modifier.padding(end = 16.dp),
                    contentDescription = stringResource(R.string.uid_multi_user_scan_title),
                    tint = colorScheme.onBackground
                )
            },
            checked = multiOn,
            onCheckedChange = { target ->
                scope.launch(Dispatchers.IO) {
                    val ok = setUidMultiUserScan(target)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            multiOn = target
                            prefs.edit { putBoolean("uid_multi_user_scan", target) }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.uid_scanner_setting_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }

    AnimatedVisibility(
        visible = autoOn,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val confirmDialog = rememberConfirmDialog()
        SuperArrow(
            title = stringResource(R.string.clean_runtime_environment),
            summary = stringResource(R.string.clean_runtime_environment_summary),
            leftAction = {
                Icon(
                    Icons.Filled.CleaningServices,
                    modifier = Modifier.padding(end = 16.dp),
                    contentDescription = stringResource(R.string.clean_runtime_environment),
                    tint = colorScheme.onBackground
                )
            },
            onClick = {
                scope.launch {
                    if (confirmDialog.awaitConfirm(
                            title = context.getString(R.string.clean_runtime_environment),
                            content = context.getString(R.string.clean_runtime_environment_confirm)
                        ) == ConfirmResult.Confirmed
                    ) {
                        if (cleanRuntimeEnvironment()) {
                            autoOn = false
                            multiOn = false
                            prefs.edit {
                                putBoolean("uid_auto_scan", false)
                                putBoolean("uid_multi_user_scan", false)
                            }
                            Natives.setUidScannerEnabled(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.clean_runtime_environment_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.clean_runtime_environment_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }
}
