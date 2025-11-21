package com.sukisu.ultra.ui.susfs

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.susfs.component.*
import com.sukisu.ultra.ui.theme.wallpaperCardColors
import com.sukisu.ultra.ui.theme.wallpaperContainerColor
import com.sukisu.ultra.ui.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.util.isAbDevice
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 标签页枚举类
 */
enum class SuSFSTab(val displayNameRes: Int) {
    BASIC_SETTINGS(R.string.susfs_tab_basic_settings),
    SUS_PATHS(R.string.susfs_tab_sus_paths),
    SUS_LOOP_PATHS(R.string.susfs_tab_sus_loop_paths),
    SUS_MAPS(R.string.susfs_tab_sus_maps),
    SUS_MOUNTS(R.string.susfs_tab_sus_mounts),
    TRY_UMOUNT(R.string.susfs_tab_try_umount),
    KSTAT_CONFIG(R.string.susfs_tab_kstat_config),
    PATH_SETTINGS(R.string.susfs_tab_path_settings),
    ENABLED_FEATURES(R.string.susfs_tab_enabled_features);

    companion object {
        fun getAllTabs(): List<SuSFSTab> {
            return entries.toList()
        }
    }
}

/**
 * SuSFS配置界面
 */
@SuppressLint("SdCardPath", "AutoboxingStateCreation")
@Destination<RootGraph>
@Composable
fun SuSFSConfigScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = colorScheme.surface,
        tint = HazeTint(colorScheme.surface.copy(0.8f))
    )

    var selectedTab by remember { mutableStateOf(SuSFSTab.BASIC_SETTINGS) }
    var unameValue by remember { mutableStateOf("") }
    var buildTimeValue by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showConfirmReset by remember { mutableStateOf(false) }
    var autoStartEnabled by remember { mutableStateOf(false) }
    var executeInPostFsData by remember { mutableStateOf(false) }
    var enableHideBl by remember { mutableStateOf(true) }
    var enableCleanupResidue by remember { mutableStateOf(false) }
    var enableAvcLogSpoofing by remember { mutableStateOf(false) }

    // 槽位信息相关状态
    var slotInfoList by remember { mutableStateOf(emptyList<SuSFSManager.SlotInfo>()) }
    var currentActiveSlot by remember { mutableStateOf("") }
    var isLoadingSlotInfo by remember { mutableStateOf(false) }
    var showSlotInfoDialog by remember { mutableStateOf(false) }

    // 路径管理相关状态
    var susPaths by remember { mutableStateOf(emptySet<String>()) }
    var susLoopPaths by remember { mutableStateOf(emptySet<String>()) }
    var susMaps by remember { mutableStateOf(emptySet<String>()) }
    var susMounts by remember { mutableStateOf(emptySet<String>()) }
    var tryUmounts by remember { mutableStateOf(emptySet<String>()) }
    var androidDataPath by remember { mutableStateOf("") }
    var sdcardPath by remember { mutableStateOf("") }

    // SUS挂载隐藏控制状态
    var hideSusMountsForAllProcs by remember { mutableStateOf(true) }

    var umountForZygoteIsoService by remember { mutableStateOf(false) }

    // Kstat配置相关状态
    var kstatConfigs by remember { mutableStateOf(emptySet<String>()) }
    var addKstatPaths by remember { mutableStateOf(emptySet<String>()) }

    // 启用功能状态相关
    var enabledFeatures by remember { mutableStateOf(emptyList<SuSFSManager.EnabledFeature>()) }
    var isLoadingFeatures by remember { mutableStateOf(false) }

    // 应用列表相关状态
    var installedApps by remember { mutableStateOf(emptyList<SuSFSManager.AppInfo>()) }

    // 对话框状态
    var showAddPathDialog by remember { mutableStateOf(false) }
    var showAddLoopPathDialog by remember { mutableStateOf(false) }
    var showAddSusMapDialog by remember { mutableStateOf(false) }
    var showAddAppPathDialog by remember { mutableStateOf(false) }
    var showAddMountDialog by remember { mutableStateOf(false) }
    var showAddUmountDialog by remember { mutableStateOf(false) }
    var showAddKstatStaticallyDialog by remember { mutableStateOf(false) }
    var showAddKstatDialog by remember { mutableStateOf(false) }

    // 编辑状态
    var editingPath by remember { mutableStateOf<String?>(null) }
    var editingLoopPath by remember { mutableStateOf<String?>(null) }
    var editingSusMap by remember { mutableStateOf<String?>(null) }
    var editingMount by remember { mutableStateOf<String?>(null) }
    var editingUmount by remember { mutableStateOf<String?>(null) }
    var editingKstatConfig by remember { mutableStateOf<String?>(null) }
    var editingKstatPath by remember { mutableStateOf<String?>(null) }

    // 重置确认对话框状态
    var showResetPathsDialog by remember { mutableStateOf(false) }
    var showResetLoopPathsDialog by remember { mutableStateOf(false) }
    var showResetSusMapsDialog by remember { mutableStateOf(false) }
    var showResetMountsDialog by remember { mutableStateOf(false) }
    var showResetUmountsDialog by remember { mutableStateOf(false) }
    var showResetKstatDialog by remember { mutableStateOf(false) }

    // 备份还原相关状态
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var selectedBackupFile by remember { mutableStateOf<String?>(null) }
    var backupInfo by remember { mutableStateOf<SuSFSManager.BackupData?>(null) }

    var isNavigating by remember { mutableStateOf(false) }

    val allTabs = SuSFSTab.getAllTabs()

    // 实时判断是否可以启用开机自启动
    val canEnableAutoStart by remember {
        derivedStateOf {
            SuSFSManager.hasConfigurationForAutoStart(context)
        }
    }


    // 文件选择器
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            val fileName = SuSFSManager.getDefaultBackupFileName()
            val tempFile = File(context.cacheDir, fileName)
            coroutineScope.launch {
                isLoading = true
                val success = SuSFSManager.createBackup(context, tempFile.absolutePath)
                if (success) {
                    try {
                        context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    tempFile.delete()
                }
                isLoading = false
                showBackupDialog = false
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            coroutineScope.launch {
                try {
                    val tempFile = File(context.cacheDir, "temp_restore.susfs_backup")
                    context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // 验证备份文件
                    val backup = SuSFSManager.validateBackupFile(tempFile.absolutePath)
                    if (backup != null) {
                        selectedBackupFile = tempFile.absolutePath
                        backupInfo = backup
                        showRestoreConfirmDialog = true
                    }
                    tempFile.deleteOnExit()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                showRestoreDialog = false
            }
        }
    }

    // 加载启用功能状态
    fun loadEnabledFeatures() {
        coroutineScope.launch {
            isLoadingFeatures = true
            enabledFeatures = SuSFSManager.getEnabledFeatures(context)
            isLoadingFeatures = false
        }
    }

    // 加载应用列表
    fun loadInstalledApps() {
        coroutineScope.launch {
            installedApps = SuSFSManager.getInstalledApps()
        }
    }

    // 加载槽位信息
    fun loadSlotInfo() {
        coroutineScope.launch {
            isLoadingSlotInfo = true
            slotInfoList = SuSFSManager.getCurrentSlotInfo()
            currentActiveSlot = SuSFSManager.getCurrentActiveSlot()
            isLoadingSlotInfo = false
        }
    }

    // 加载当前配置
    LaunchedEffect(Unit) {
        coroutineScope.launch {

            unameValue = SuSFSManager.getUnameValue(context)
            buildTimeValue = SuSFSManager.getBuildTimeValue(context)
            autoStartEnabled = SuSFSManager.isAutoStartEnabled(context)
            executeInPostFsData = SuSFSManager.getExecuteInPostFsData(context)
            susPaths = SuSFSManager.getSusPaths(context)
            susLoopPaths = SuSFSManager.getSusLoopPaths(context)
            susMaps = SuSFSManager.getSusMaps(context)
            susMounts = SuSFSManager.getSusMounts(context)
            tryUmounts = SuSFSManager.getTryUmounts(context)
            androidDataPath = SuSFSManager.getAndroidDataPath(context)
            sdcardPath = SuSFSManager.getSdcardPath(context)
            kstatConfigs = SuSFSManager.getKstatConfigs(context)
            addKstatPaths = SuSFSManager.getAddKstatPaths(context)
            hideSusMountsForAllProcs = SuSFSManager.getHideSusMountsForAllProcs(context)
            enableHideBl = SuSFSManager.getEnableHideBl(context)
            enableCleanupResidue = SuSFSManager.getEnableCleanupResidue(context)
            umountForZygoteIsoService = SuSFSManager.getUmountForZygoteIsoService(context)
            enableAvcLogSpoofing = SuSFSManager.getEnableAvcLogSpoofing(context)

            loadSlotInfo()
        }
    }

    // 当切换到启用功能状态标签页时加载数据
    LaunchedEffect(selectedTab) {
        if (selectedTab == SuSFSTab.ENABLED_FEATURES) {
            loadEnabledFeatures()
        }
    }

    // 当配置变化时，自动调整开机自启动状态
    LaunchedEffect(canEnableAutoStart) {
        if (!canEnableAutoStart && autoStartEnabled) {
            autoStartEnabled = false
            SuSFSManager.configureAutoStart(context, false)
        }
    }

    // 备份对话框
    val showBackupDialogState = remember { mutableStateOf(showBackupDialog) }
    LaunchedEffect(showBackupDialog) {
        showBackupDialogState.value = showBackupDialog
    }
    if (showBackupDialog) {
        SuperDialog(
            show = showBackupDialogState,
            title = stringResource(R.string.susfs_backup_title),
            onDismissRequest = { showBackupDialog = false },
            content = {
                Text(stringResource(R.string.susfs_backup_description))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showBackupDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                    TextButton(
                        text = stringResource(R.string.susfs_backup_create),
                        onClick = {
                            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val timestamp = dateFormat.format(Date())
                            backupFileLauncher.launch("SuSFS_Config_$timestamp.susfs_backup")
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }

    // 还原对话框
    val showRestoreDialogState = remember { mutableStateOf(showRestoreDialog) }
    LaunchedEffect(showRestoreDialog) {
        showRestoreDialogState.value = showRestoreDialog
    }
    if (showRestoreDialog) {
        SuperDialog(
            show = showRestoreDialogState,
            title = stringResource(R.string.susfs_restore_title),
            onDismissRequest = { showRestoreDialog = false },
            content = {
                Text(stringResource(R.string.susfs_restore_description))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showRestoreDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                    TextButton(
                        text = stringResource(R.string.susfs_restore_select_file),
                        onClick = {
                            restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }

    // 还原确认对话框
    val showRestoreConfirmDialogState = remember { mutableStateOf(showRestoreConfirmDialog && backupInfo != null) }
    LaunchedEffect(showRestoreConfirmDialog, backupInfo) {
        showRestoreConfirmDialogState.value = showRestoreConfirmDialog && backupInfo != null
    }
    if (showRestoreConfirmDialog && backupInfo != null) {
        SuperDialog(
            show = showRestoreConfirmDialogState,
            title = stringResource(R.string.susfs_restore_confirm_title),
            onDismissRequest = {
                showRestoreConfirmDialog = false
                selectedBackupFile = null
                backupInfo = null
            },
            content = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.susfs_restore_confirm_description))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = wallpaperCardColors()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            Text(
                                text = stringResource(R.string.susfs_backup_info_date,
                                    dateFormat.format(Date(backupInfo!!.timestamp))),
                                fontSize = MiuixTheme.textStyles.body2.fontSize
                            )
                            Text(
                                text = stringResource(R.string.susfs_backup_info_device, backupInfo!!.deviceInfo),
                                fontSize = MiuixTheme.textStyles.body2.fontSize
                            )
                            Text(
                                text = stringResource(R.string.susfs_backup_info_version, backupInfo!!.version),
                                fontSize = MiuixTheme.textStyles.body2.fontSize
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            showRestoreConfirmDialog = false
                            selectedBackupFile = null
                            backupInfo = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                    TextButton(
                        text = stringResource(R.string.susfs_restore_confirm),
                        onClick = {
                            selectedBackupFile?.let { filePath ->
                                coroutineScope.launch {
                                    isLoading = true
                                    try {
                                        val success = SuSFSManager.restoreFromBackup(context, filePath)
                                        if (success) {
                                            // 在后台线程读取配置，然后在主线程更新状态
                                            val configs = withContext(Dispatchers.IO) {
                                                mapOf(
                                                    "unameValue" to SuSFSManager.getUnameValue(context),
                                                    "buildTimeValue" to SuSFSManager.getBuildTimeValue(context),
                                                    "autoStartEnabled" to SuSFSManager.isAutoStartEnabled(context),
                                                    "executeInPostFsData" to SuSFSManager.getExecuteInPostFsData(context),
                                                    "susPaths" to SuSFSManager.getSusPaths(context),
                                                    "susLoopPaths" to SuSFSManager.getSusLoopPaths(context),
                                                    "susMaps" to SuSFSManager.getSusMaps(context),
                                                    "susMounts" to SuSFSManager.getSusMounts(context),
                                                    "tryUmounts" to SuSFSManager.getTryUmounts(context),
                                                    "androidDataPath" to SuSFSManager.getAndroidDataPath(context),
                                                    "sdcardPath" to SuSFSManager.getSdcardPath(context),
                                                    "kstatConfigs" to SuSFSManager.getKstatConfigs(context),
                                                    "addKstatPaths" to SuSFSManager.getAddKstatPaths(context),
                                                    "hideSusMountsForAllProcs" to SuSFSManager.getHideSusMountsForAllProcs(context),
                                                    "enableHideBl" to SuSFSManager.getEnableHideBl(context),
                                                    "enableCleanupResidue" to SuSFSManager.getEnableCleanupResidue(context),
                                                    "umountForZygoteIsoService" to SuSFSManager.getUmountForZygoteIsoService(context),
                                                    "enableAvcLogSpoofing" to SuSFSManager.getEnableAvcLogSpoofing(context)
                                                )
                                            }
                                            
                                            // 在主线程更新状态
                                            @Suppress("UNCHECKED_CAST")
                                            unameValue = configs["unameValue"] as String
                                            @Suppress("UNCHECKED_CAST")
                                            buildTimeValue = configs["buildTimeValue"] as String
                                            @Suppress("UNCHECKED_CAST")
                                            autoStartEnabled = configs["autoStartEnabled"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            executeInPostFsData = configs["executeInPostFsData"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            susPaths = configs["susPaths"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            susLoopPaths = configs["susLoopPaths"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            susMaps = configs["susMaps"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            susMounts = configs["susMounts"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            tryUmounts = configs["tryUmounts"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            androidDataPath = configs["androidDataPath"] as String
                                            @Suppress("UNCHECKED_CAST")
                                            sdcardPath = configs["sdcardPath"] as String
                                            @Suppress("UNCHECKED_CAST")
                                            kstatConfigs = configs["kstatConfigs"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            addKstatPaths = configs["addKstatPaths"] as Set<String>
                                            @Suppress("UNCHECKED_CAST")
                                            hideSusMountsForAllProcs = configs["hideSusMountsForAllProcs"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            enableHideBl = configs["enableHideBl"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            enableCleanupResidue = configs["enableCleanupResidue"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            umountForZygoteIsoService = configs["umountForZygoteIsoService"] as Boolean
                                            @Suppress("UNCHECKED_CAST")
                                            enableAvcLogSpoofing = configs["enableAvcLogSpoofing"] as Boolean
                                            
                                            // 延迟关闭对话框，给 UI 时间更新
                                            delay(300)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        // 先关闭对话框，确保在主线程上执行
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            showRestoreConfirmDialog = false
                                        }
                                        // 延迟清空状态，确保对话框完全关闭后再清空
                                        delay(100)
                                        withContext(Dispatchers.Main) {
                                            selectedBackupFile = null
                                            backupInfo = null
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }

    // 槽位信息对话框
    SlotInfoDialog(
        showDialog = showSlotInfoDialog,
        onDismiss = { showSlotInfoDialog = false },
        slotInfoList = slotInfoList,
        currentActiveSlot = currentActiveSlot,
        isLoadingSlotInfo = isLoadingSlotInfo,
        onRefresh = { loadSlotInfo() },
        onUseUname = { uname: String ->
            unameValue = uname
            showSlotInfoDialog = false
        },
        onUseBuildTime = { buildTime: String ->
            buildTimeValue = buildTime
            showSlotInfoDialog = false
        }
    )

    // 各种对话框
    AddPathDialog(
        showDialog = showAddPathDialog,
        onDismiss = {
            showAddPathDialog = false
            editingPath = null
        },
        onConfirm = { path ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingPath != null) {
                    SuSFSManager.editSusPath(context, editingPath!!, path)
                } else {
                    SuSFSManager.addSusPath(context, path)
                }
                if (success) {
                    susPaths = SuSFSManager.getSusPaths(context)
                }
                isLoading = false
                showAddPathDialog = false
                editingPath = null
            }
        },
        isLoading = isLoading,
        titleRes = if (editingPath != null) R.string.susfs_edit_sus_path else R.string.susfs_add_sus_path,
        labelRes = R.string.susfs_path_label,
        initialValue = editingPath ?: ""
    )

    AddPathDialog(
        showDialog = showAddLoopPathDialog,
        onDismiss = {
            showAddLoopPathDialog = false
            editingLoopPath = null
        },
        onConfirm = { path ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingLoopPath != null) {
                    SuSFSManager.editSusLoopPath(context, editingLoopPath!!, path)
                } else {
                    SuSFSManager.addSusLoopPath(context, path)
                }
                if (success) {
                    susLoopPaths = SuSFSManager.getSusLoopPaths(context)
                }
                isLoading = false
                showAddLoopPathDialog = false
                editingLoopPath = null
            }
        },
        isLoading = isLoading,
        titleRes = if (editingLoopPath != null) R.string.susfs_edit_sus_loop_path else R.string.susfs_add_sus_loop_path,
        labelRes = R.string.susfs_loop_path_label,
        initialValue = editingLoopPath ?: ""
    )

    AddPathDialog(
        showDialog = showAddSusMapDialog,
        onDismiss = {
            showAddSusMapDialog = false
            editingSusMap = null
        },
        onConfirm = { path ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingSusMap != null) {
                    SuSFSManager.editSusMap(context, editingSusMap!!, path)
                } else {
                    SuSFSManager.addSusMap(context, path)
                }
                if (success) {
                    susMaps = SuSFSManager.getSusMaps(context)
                }
                isLoading = false
                showAddSusMapDialog = false
                editingSusMap = null
            }
        },
        isLoading = isLoading,
        titleRes = if (editingSusMap != null) R.string.susfs_edit_sus_map else R.string.susfs_add_sus_map,
        labelRes = R.string.susfs_sus_map_label,
        initialValue = editingSusMap ?: ""
    )

    AddAppPathDialog(
        showDialog = showAddAppPathDialog,
        onDismiss = { showAddAppPathDialog = false },
        onConfirm = { packageNames ->
            coroutineScope.launch {
                isLoading = true
                var successCount = 0
                packageNames.forEach { packageName ->
                    if (SuSFSManager.addAppPaths(context, packageName)) {
                        successCount++
                    }
                }
                if (successCount > 0) {
                    susPaths = SuSFSManager.getSusPaths(context)
                }
                isLoading = false
                showAddAppPathDialog = false
            }
        },
        isLoading = isLoading,
        apps = installedApps,
        onLoadApps = { loadInstalledApps() },
        existingSusPaths = susPaths
    )

    AddPathDialog(
        showDialog = showAddMountDialog,
        onDismiss = {
            showAddMountDialog = false
            editingMount = null
        },
        onConfirm = { mount ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingMount != null) {
                    SuSFSManager.editSusMount(context, editingMount!!, mount)
                } else {
                    SuSFSManager.addSusMount(context, mount)
                }
                if (success) {
                    susMounts = SuSFSManager.getSusMounts(context)
                }
                isLoading = false
                showAddMountDialog = false
                editingMount = null
            }
        },
        isLoading = isLoading,
        titleRes = if (editingMount != null) R.string.susfs_edit_sus_mount else R.string.susfs_add_sus_mount,
        labelRes = R.string.susfs_mount_path_label,
        initialValue = editingMount ?: ""
    )

    AddTryUmountDialog(
        showDialog = showAddUmountDialog,
        onDismiss = {
            showAddUmountDialog = false
            editingUmount = null
        },
        onConfirm = { path, mode ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingUmount != null) {
                    SuSFSManager.editTryUmount(context, editingUmount!!, path, mode)
                } else {
                    SuSFSManager.addTryUmount(context, path, mode)
                }
                if (success) {
                    tryUmounts = SuSFSManager.getTryUmounts(context)
                }
                isLoading = false
                showAddUmountDialog = false
                editingUmount = null
            }
        },
        isLoading = isLoading,
        initialPath = editingUmount?.split("|")?.get(0) ?: "",
        initialMode = editingUmount?.split("|")?.get(1)?.toIntOrNull() ?: 0
    )

    AddKstatStaticallyDialog(
        showDialog = showAddKstatStaticallyDialog,
        onDismiss = {
            showAddKstatStaticallyDialog = false
            editingKstatConfig = null
        },
        onConfirm = { path, ino, dev, nlink, size, atime, atimeNsec, mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingKstatConfig != null) {
                    SuSFSManager.editKstatConfig(
                        context,
                        editingKstatConfig!!,
                        path,
                        ino,
                        dev,
                        nlink,
                        size,
                        atime,
                        atimeNsec,
                        mtime,
                        mtimeNsec,
                        ctime,
                        ctimeNsec,
                        blocks,
                        blksize
                    )
                } else {
                    SuSFSManager.addKstatStatically(
                        context, path, ino, dev, nlink, size, atime, atimeNsec,
                        mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize
                    )
                }
                if (success) {
                    kstatConfigs = SuSFSManager.getKstatConfigs(context)
                }
                isLoading = false
                showAddKstatStaticallyDialog = false
                editingKstatConfig = null
            }
        },
        isLoading = isLoading,
        initialConfig = editingKstatConfig ?: ""
    )

    AddPathDialog(
        showDialog = showAddKstatDialog,
        onDismiss = {
            showAddKstatDialog = false
            editingKstatPath = null
        },
        onConfirm = { path ->
            coroutineScope.launch {
                isLoading = true
                val success = if (editingKstatPath != null) {
                    SuSFSManager.editAddKstat(context, editingKstatPath!!, path)
                } else {
                    SuSFSManager.addKstat(context, path)
                }
                if (success) {
                    addKstatPaths = SuSFSManager.getAddKstatPaths(context)
                }
                isLoading = false
                showAddKstatDialog = false
                editingKstatPath = null
            }
        },
        isLoading = isLoading,
        titleRes = if (editingKstatPath != null) R.string.edit_kstat_path_title else R.string.add_kstat_path_title,
        labelRes = R.string.file_or_directory_path_label,
        initialValue = editingKstatPath ?: ""
    )

    // 确认对话框
    ConfirmDialog(
        showDialog = showConfirmReset,
        onDismiss = { showConfirmReset = false },
        onConfirm = {
            showConfirmReset = false
            coroutineScope.launch {
                isLoading = true
                if (SuSFSManager.resetToDefault(context)) {
                    unameValue = "default"
                    buildTimeValue = "default"
                    autoStartEnabled = false
                }
                isLoading = false
            }
        },
        titleRes = R.string.susfs_reset_confirm_title,
        messageRes = R.string.susfs_reset_confirm_title,
        isLoading = isLoading
    )

    // 重置对话框
    ConfirmDialog(
        showDialog = showResetPathsDialog,
        onDismiss = { showResetPathsDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveSusPaths(context, emptySet())
                susPaths = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetPathsDialog = false
            }
        },
        titleRes = R.string.susfs_reset_paths_title,
        messageRes = R.string.susfs_reset_paths_message,
        isLoading = isLoading
    )

    ConfirmDialog(
        showDialog = showResetLoopPathsDialog,
        onDismiss = { showResetLoopPathsDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveSusLoopPaths(context, emptySet())
                susLoopPaths = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetLoopPathsDialog = false
            }
        },
        titleRes = R.string.susfs_reset_loop_paths_title,
        messageRes = R.string.susfs_reset_loop_paths_message,
        isLoading = isLoading
    )

    ConfirmDialog(
        showDialog = showResetSusMapsDialog,
        onDismiss = { showResetSusMapsDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveSusMaps(context, emptySet())
                susMaps = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetSusMapsDialog = false
            }
        },
        titleRes = R.string.susfs_reset_sus_maps_title,
        messageRes = R.string.susfs_reset_sus_maps_message,
        isLoading = isLoading
    )

    ConfirmDialog(
        showDialog = showResetMountsDialog,
        onDismiss = { showResetMountsDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveSusMounts(context, emptySet())
                susMounts = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetMountsDialog = false
            }
        },
        titleRes = R.string.susfs_reset_mounts_title,
        messageRes = R.string.susfs_reset_mounts_message,
        isLoading = isLoading
    )

    ConfirmDialog(
        showDialog = showResetUmountsDialog,
        onDismiss = { showResetUmountsDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveTryUmounts(context, emptySet())
                tryUmounts = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetUmountsDialog = false
            }
        },
        titleRes = R.string.susfs_reset_umounts_title,
        messageRes = R.string.susfs_reset_umounts_message,
        isLoading = isLoading
    )

    ConfirmDialog(
        showDialog = showResetKstatDialog,
        onDismiss = { showResetKstatDialog = false },
        onConfirm = {
            coroutineScope.launch {
                isLoading = true
                SuSFSManager.saveKstatConfigs(context, emptySet())
                SuSFSManager.saveAddKstatPaths(context, emptySet())
                kstatConfigs = emptySet()
                addKstatPaths = emptySet()
                if (SuSFSManager.isAutoStartEnabled(context)) {
                    SuSFSManager.configureAutoStart(context, true)
                }
                isLoading = false
                showResetKstatDialog = false
            }
        },
        titleRes = R.string.reset_kstat_config_title,
        messageRes = R.string.reset_kstat_config_message,
        isLoading = isLoading
    )

    // 主界面布局
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
                title = stringResource(R.string.susfs_config_title),
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isNavigating) {
                            isNavigating = true
                            navigator.popBackStack()
                        }
                    }) {
                        Icon(
                            MiuixIcons.Useful.Back,
                            contentDescription = stringResource(R.string.log_viewer_back),
                            tint = colorScheme.onBackground
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
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
                // 标签页
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(allTabs.size) { index ->
                        val tab = allTabs[index]
                        val isSelected = selectedTab == tab
                        Card(
                            modifier = Modifier
                                .clickable { selectedTab = tab },
                            colors = wallpaperCardColors(
                                background = if (isSelected) {
                                    colorScheme.primaryContainer
                                } else {
                                    colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            ),
                            cornerRadius = 8.dp
                        ) {
                            Text(
                                text = stringResource(tab.displayNameRes),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurfaceVariantSummary
                                }
                            )
                        }
                    }
                }

            }

            item {
                Spacer(modifier = Modifier.height(12.dp))

                // 标签页内容
                when (selectedTab) {
                    SuSFSTab.BASIC_SETTINGS -> {
                        BasicSettingsContent(
                            unameValue = unameValue,
                            onUnameValueChange = { value -> unameValue = value },
                            buildTimeValue = buildTimeValue,
                            onBuildTimeValueChange = { value -> buildTimeValue = value },
                            executeInPostFsData = executeInPostFsData,
                            onExecuteInPostFsDataChange = { value -> executeInPostFsData = value },
                            autoStartEnabled = autoStartEnabled,
                            canEnableAutoStart = canEnableAutoStart,
                            isLoading = isLoading,
                            onAutoStartToggle = { enabled: Boolean ->
                                if (canEnableAutoStart) {
                                    coroutineScope.launch {
                                        isLoading = true
                                        if (SuSFSManager.configureAutoStart(context, enabled)) {
                                            autoStartEnabled = enabled
                                        }
                                        isLoading = false
                                    }
                                }
                            },
                            onShowSlotInfo = { showSlotInfoDialog = true },
                            context = context,
                            onShowBackupDialog = { showBackupDialog = true },
                            onShowRestoreDialog = { showRestoreDialog = true },
                            enableHideBl = enableHideBl,
                            onEnableHideBlChange = { enabled: Boolean ->
                                enableHideBl = enabled
                                SuSFSManager.saveEnableHideBl(context, enabled)
                                if (SuSFSManager.isAutoStartEnabled(context)) {
                                    coroutineScope.launch {
                                        SuSFSManager.configureAutoStart(context, true)
                                    }
                                }
                            },
                            enableCleanupResidue = enableCleanupResidue,
                            onEnableCleanupResidueChange = { enabled: Boolean ->
                                enableCleanupResidue = enabled
                                SuSFSManager.saveEnableCleanupResidue(context, enabled)
                                if (SuSFSManager.isAutoStartEnabled(context)) {
                                    coroutineScope.launch {
                                        SuSFSManager.configureAutoStart(context, true)
                                    }
                                }
                            },
                            enableAvcLogSpoofing = enableAvcLogSpoofing,
                            onEnableAvcLogSpoofingChange = { enabled: Boolean ->
                                coroutineScope.launch {
                                    isLoading = true
                                    val success = SuSFSManager.setEnableAvcLogSpoofing(context, enabled)
                                    if (success) {
                                        enableAvcLogSpoofing = enabled
                                    }
                                    isLoading = false
                                }
                            },
                            onReset = { showConfirmReset = true }
                        )
                    }
                    SuSFSTab.SUS_PATHS -> {
                        SusPathsContent(
                            susPaths = susPaths,
                            isLoading = isLoading,
                            onAddPath = { showAddPathDialog = true },
                            onAddAppPath = { showAddAppPathDialog = true },
                            onRemovePath = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusPath(context, path)) {
                                        susPaths = SuSFSManager.getSusPaths(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditPath = { path ->
                                editingPath = path
                                showAddPathDialog = true
                            },
                            forceRefreshApps = selectedTab == SuSFSTab.SUS_PATHS,
                            onReset = { showResetPathsDialog = true }
                        )
                    }
                    SuSFSTab.SUS_LOOP_PATHS -> {
                        SusLoopPathsContent(
                            susLoopPaths = susLoopPaths,
                            isLoading = isLoading,
                            onAddLoopPath = { showAddLoopPathDialog = true },
                            onRemoveLoopPath = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusLoopPath(context, path)) {
                                        susLoopPaths = SuSFSManager.getSusLoopPaths(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditLoopPath = { path ->
                                editingLoopPath = path
                                showAddLoopPathDialog = true
                            },
                            onReset = { showResetLoopPathsDialog = true }
                        )
                    }
                    SuSFSTab.SUS_MAPS -> {
                        SusMapsContent(
                            susMaps = susMaps,
                            isLoading = isLoading,
                            onAddSusMap = { showAddSusMapDialog = true },
                            onRemoveSusMap = { map ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusMap(context, map)) {
                                        susMaps = SuSFSManager.getSusMaps(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditSusMap = { map ->
                                editingSusMap = map
                                showAddSusMapDialog = true
                            },
                            onReset = { showResetSusMapsDialog = true }
                        )
                    }
                    SuSFSTab.SUS_MOUNTS -> {
                        SusMountsContent(
                            susMounts = susMounts,
                            hideSusMountsForAllProcs = hideSusMountsForAllProcs,
                            isLoading = isLoading,
                            onAddMount = { showAddMountDialog = true },
                            onRemoveMount = { mount ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeSusMount(context, mount)) {
                                        susMounts = SuSFSManager.getSusMounts(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditMount = { mount ->
                                editingMount = mount
                                showAddMountDialog = true
                            },
                            onToggleHideSusMountsForAllProcs = { hideForAll ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.setHideSusMountsForAllProcs(
                                            context,
                                            hideForAll
                                        )
                                    ) {
                                        hideSusMountsForAllProcs = hideForAll
                                    }
                                    isLoading = false
                                }
                            },
                            onReset = { showResetMountsDialog = true }
                        )
                    }

                    SuSFSTab.TRY_UMOUNT -> {
                        TryUmountContent(
                            tryUmounts = tryUmounts,
                            umountForZygoteIsoService = umountForZygoteIsoService,
                            isLoading = isLoading,
                            onAddUmount = { showAddUmountDialog = true },
                            onRemoveUmount = { umountEntry ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeTryUmount(context, umountEntry)) {
                                        tryUmounts = SuSFSManager.getTryUmounts(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditUmount = { umountEntry ->
                                editingUmount = umountEntry
                                showAddUmountDialog = true
                            },
                            onToggleUmountForZygoteIsoService = { enabled ->
                                coroutineScope.launch {
                                    isLoading = true
                                    val success =
                                        SuSFSManager.setUmountForZygoteIsoService(context, enabled)
                                    if (success) {
                                        umountForZygoteIsoService = enabled
                                    }
                                    isLoading = false
                                }
                            },
                            onReset = { showResetUmountsDialog = true }
                        )
                    }

                    SuSFSTab.KSTAT_CONFIG -> {
                        KstatConfigContent(
                            kstatConfigs = kstatConfigs,
                            addKstatPaths = addKstatPaths,
                            isLoading = isLoading,
                            onAddKstatStatically = { showAddKstatStaticallyDialog = true },
                            onAddKstat = { showAddKstatDialog = true },
                            onRemoveKstatConfig = { config ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeKstatConfig(context, config)) {
                                        kstatConfigs = SuSFSManager.getKstatConfigs(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditKstatConfig = { config ->
                                editingKstatConfig = config
                                showAddKstatStaticallyDialog = true
                            },
                            onRemoveAddKstat = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    if (SuSFSManager.removeAddKstat(context, path)) {
                                        addKstatPaths = SuSFSManager.getAddKstatPaths(context)
                                    }
                                    isLoading = false
                                }
                            },
                            onEditAddKstat = { path ->
                                editingKstatPath = path
                                showAddKstatDialog = true
                            },
                            onUpdateKstat = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.updateKstat(context, path)
                                    isLoading = false
                                }
                            },
                            onUpdateKstatFullClone = { path ->
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.updateKstatFullClone(context, path)
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.PATH_SETTINGS -> {
                        PathSettingsContent(
                            androidDataPath = androidDataPath,
                            onAndroidDataPathChange = { androidDataPath = it },
                            sdcardPath = sdcardPath,
                            onSdcardPathChange = { sdcardPath = it },
                            isLoading = isLoading,
                            onSetAndroidDataPath = {
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.setAndroidDataPath(context, androidDataPath.trim())
                                    isLoading = false
                                }
                            },
                            onSetSdcardPath = {
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.setSdcardPath(context, sdcardPath.trim())
                                    isLoading = false
                                }
                            },
                            onReset = {
                                androidDataPath = "/sdcard/Android/data"
                                sdcardPath = "/sdcard"
                                coroutineScope.launch {
                                    isLoading = true
                                    SuSFSManager.setAndroidDataPath(context, androidDataPath)
                                    SuSFSManager.setSdcardPath(context, sdcardPath)
                                    isLoading = false
                                }
                            }
                        )
                    }
                    SuSFSTab.ENABLED_FEATURES -> {
                        EnabledFeaturesContent(
                            enabledFeatures = enabledFeatures,
                            onRefresh = { loadEnabledFeatures() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 基本设置内容组件
 */
@Composable
fun BasicSettingsContent(
    unameValue: String,
    onUnameValueChange: (String) -> Unit,
    buildTimeValue: String,
    onBuildTimeValueChange: (String) -> Unit,
    executeInPostFsData: Boolean,
    onExecuteInPostFsDataChange: (Boolean) -> Unit,
    autoStartEnabled: Boolean,
    canEnableAutoStart: Boolean,
    isLoading: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    onShowSlotInfo: () -> Unit,
    context: Context,
    onShowBackupDialog: () -> Unit,
    onShowRestoreDialog: () -> Unit,
    enableHideBl: Boolean,
    onEnableHideBlChange: (Boolean) -> Unit,
    enableCleanupResidue: Boolean,
    onEnableCleanupResidueChange: (Boolean) -> Unit,
    enableAvcLogSpoofing: Boolean,
    onEnableAvcLogSpoofingChange: (Boolean) -> Unit,
    onReset: (() -> Unit)? = null
) {
    val isAbDevice = produceState(initialValue = false) {
        value = isAbDevice()
    }.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors(
                background = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.susfs_config_description),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.susfs_config_description_text),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
            }
        }

        // Uname输入框
        TextField(
            value = unameValue,
            onValueChange = onUnameValueChange,
            label = stringResource(R.string.susfs_uname_label),
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        // 构建时间伪装输入框
        TextField(
            value = buildTimeValue,
            onValueChange = onBuildTimeValueChange,
            label = stringResource(R.string.susfs_build_time_label),
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        // 执行位置选择
        val locationItems = listOf(
            stringResource(R.string.susfs_execution_location_service),
            stringResource(R.string.susfs_execution_location_post_fs_data)
        )
        SuperDropdown(
            title = stringResource(R.string.susfs_execution_location_label),
            summary = if (executeInPostFsData) {
                stringResource(R.string.susfs_execution_location_post_fs_data)
            } else {
                stringResource(R.string.susfs_execution_location_service)
            },
            items = locationItems,
            selectedIndex = if (executeInPostFsData) 1 else 0,
            onSelectedIndexChange = { index ->
                onExecuteInPostFsDataChange(index == 1)
            },
            enabled = !isLoading
        )

        // 当前值显示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors(
                background = colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.susfs_current_value, SuSFSManager.getUnameValue(context)),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = stringResource(R.string.susfs_current_build_time, SuSFSManager.getBuildTimeValue(context)),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = stringResource(R.string.susfs_current_execution_location, if (SuSFSManager.getExecuteInPostFsData(context)) "Post-FS-Data" else "Service"),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary
                )
            }
        }

        // 开机自启动开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors()
        ) {
            SuperSwitch(
                title = stringResource(R.string.susfs_autostart_title),
                summary = if (canEnableAutoStart) {
                    stringResource(R.string.susfs_autostart_description)
                } else {
                    stringResource(R.string.susfs_autostart_requirement)
                },
                leftAction = {
                    Icon(
                        Icons.Default.AutoMode,
                        modifier = Modifier.padding(end = 16.dp),
                        contentDescription = stringResource(R.string.susfs_autostart_title),
                        tint = if (canEnableAutoStart) colorScheme.onBackground else colorScheme.onSurfaceVariantSummary
                    )
                },
                checked = autoStartEnabled,
                onCheckedChange = onAutoStartToggle,
                enabled = !isLoading && canEnableAutoStart
            )
        }

        // 隐藏BL脚本开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors()
        ) {
            SuperSwitch(
                title = stringResource(R.string.hide_bl_script),
                summary = stringResource(R.string.hide_bl_script_description),
                leftAction = {
                    Icon(
                        Icons.Default.Security,
                        modifier = Modifier.padding(end = 16.dp),
                        contentDescription = stringResource(R.string.hide_bl_script),
                        tint = colorScheme.onBackground
                    )
                },
                checked = enableHideBl,
                onCheckedChange = onEnableHideBlChange,
                enabled = !isLoading
            )
        }

        // 清理残留脚本开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors()
        ) {
            SuperSwitch(
                title = stringResource(R.string.cleanup_residue),
                summary = stringResource(R.string.cleanup_residue_description),
                leftAction = {
                    Icon(
                        Icons.Default.CleaningServices,
                        modifier = Modifier.padding(end = 16.dp),
                        contentDescription = stringResource(R.string.cleanup_residue),
                        tint = colorScheme.onBackground
                    )
                },
                checked = enableCleanupResidue,
                onCheckedChange = onEnableCleanupResidueChange,
                enabled = !isLoading
            )
        }

        // AVC日志欺骗开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = wallpaperCardColors()
        ) {
            SuperSwitch(
                title = stringResource(R.string.avc_log_spoofing),
                summary = stringResource(R.string.avc_log_spoofing_description),
                leftAction = {
                    Icon(
                        Icons.Default.VisibilityOff,
                        modifier = Modifier.padding(end = 16.dp),
                        contentDescription = stringResource(R.string.avc_log_spoofing),
                        tint = colorScheme.onBackground
                    )
                },
                checked = enableAvcLogSpoofing,
                onCheckedChange = onEnableAvcLogSpoofingChange,
                enabled = !isLoading
            )
        }

        // 槽位信息按钮
        if (isAbDevice) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = wallpaperCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.susfs_slot_info_title),
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onBackground
                        )
                    }
                    Text(
                        text = stringResource(R.string.susfs_slot_info_description),
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                        lineHeight = 18.sp
                    )
                    TextButton(
                        text = stringResource(R.string.susfs_slot_info_title),
                        onClick = onShowSlotInfo,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 备份按钮
            TextButton(
                text = stringResource(R.string.susfs_backup_title),
                onClick = onShowBackupDialog,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp)
            )
            // 还原按钮
            TextButton(
                text = stringResource(R.string.susfs_restore_title),
                onClick = onShowRestoreDialog,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp)
            )
        }

        // 重置按钮
        if (onReset != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = wallpaperCardColors(
                    background = colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                cornerRadius = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.susfs_reset_confirm_title),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 槽位信息对话框
 */
@Composable
fun SlotInfoDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    slotInfoList: List<SuSFSManager.SlotInfo>,
    currentActiveSlot: String,
    isLoadingSlotInfo: Boolean,
    onRefresh: () -> Unit,
    onUseUname: (String) -> Unit,
    onUseBuildTime: (String) -> Unit
) {
    val isAbDevice = produceState(initialValue = false) {
        value = isAbDevice()
    }.value

    val showDialogState = remember { mutableStateOf(showDialog && isAbDevice) }
    
    LaunchedEffect(showDialog, isAbDevice) {
        showDialogState.value = showDialog && isAbDevice
    }

    if (showDialogState.value) {
        SuperDialog(
            show = showDialogState,
            title = stringResource(R.string.susfs_slot_info_title),
            onDismissRequest = onDismiss,
            content = {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.susfs_current_active_slot, currentActiveSlot),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary
                    )

                    if (slotInfoList.isNotEmpty()) {
                        slotInfoList.forEach { slotInfo ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = wallpaperCardColors(
                                    background = if (slotInfo.slotName == currentActiveSlot) {
                                        colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        colorScheme.surface.copy(alpha = 0.5f)
                                    }
                                ),
                                cornerRadius = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = if (slotInfo.slotName == currentActiveSlot) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.onSurface
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = slotInfo.slotName,
                                            style = MiuixTheme.textStyles.title2,
                                            fontWeight = FontWeight.Bold,
                                            color = if (slotInfo.slotName == currentActiveSlot) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.onSurface
                                            }
                                        )
                                        if (slotInfo.slotName == currentActiveSlot) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = colorScheme.primary,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.susfs_slot_current_badge),
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.susfs_slot_uname, slotInfo.uname),
                                        style = MiuixTheme.textStyles.body2,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.susfs_slot_build_time, slotInfo.buildTime),
                                        style = MiuixTheme.textStyles.body2,
                                        color = colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onUseUname(slotInfo.uname) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .padding(vertical = 8.dp),
                                            cornerRadius = 8.dp
                                        ) {
                                            Text(
                                                text = stringResource(R.string.susfs_slot_use_uname),
                                                style = MiuixTheme.textStyles.body2,
                                                maxLines = 2
                                            )
                                        }
                                        Button(
                                            onClick = { onUseBuildTime(slotInfo.buildTime) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .padding(vertical = 8.dp),
                                            cornerRadius = 8.dp
                                        ) {
                                            Text(
                                                text = stringResource(R.string.susfs_slot_use_build_time),
                                                style = MiuixTheme.textStyles.body2,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.susfs_slot_info_unavailable),
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = onRefresh,
                        enabled = !isLoadingSlotInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp),
                        cornerRadius = 8.dp
                    ) {
                        Text(
                            text = stringResource(R.string.refresh),
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 2
                        )
                    }
                    
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(vertical = 8.dp)
                    )
                }
            }
        )
    }
}