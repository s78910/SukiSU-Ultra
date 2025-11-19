package com.sukisu.ultra.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.core.net.toUri
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KernelFlashScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.sukisu.ultra.R
import com.sukisu.ultra.getKernelVersion
import com.sukisu.ultra.ui.component.ChooseKmiDialog
import com.sukisu.ultra.ui.component.SuperDropdown
import com.sukisu.ultra.ui.component.rememberConfirmDialog
import com.sukisu.ultra.ui.kernelFlash.component.SlotSelectionDialog
import com.sukisu.ultra.ui.util.LkmSelection
import com.sukisu.ultra.ui.util.getAvailablePartitions
import com.sukisu.ultra.ui.util.getCurrentKmi
import com.sukisu.ultra.ui.util.getDefaultPartition
import com.sukisu.ultra.ui.util.getSlotSuffix
import com.sukisu.ultra.ui.util.isAbDevice
import com.sukisu.ultra.ui.util.rootAvailable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperCheckbox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Move
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2024/3/12.
 */
enum class KpmPatchOption {
    FOLLOW_KERNEL,
    PATCH_KPM,
    UNDO_PATCH_KPM
}

@Composable
@Destination<RootGraph>
fun InstallScreen(
    navigator: DestinationsNavigator,
    preselectedKernelUri: String? = null
) {
    val context = LocalContext.current
    var installMethod by remember {
        mutableStateOf<InstallMethod?>(null)
    }

    var lkmSelection by remember {
        mutableStateOf<LkmSelection>(LkmSelection.KmiNone)
    }

    var kpmPatchOption by remember { mutableStateOf(KpmPatchOption.FOLLOW_KERNEL) }
    var showSlotSelectionDialog by remember { mutableStateOf(false) }
    var showKpmPatchDialog by remember { mutableStateOf(false) }
    var tempKernelUri by remember { mutableStateOf<Uri?>(null) }

    val kernelVersion = getKernelVersion()
    val isGKI = kernelVersion.isGKI()
    val isAbDevice = produceState(initialValue = false) {
        value = isAbDevice()
    }.value

    var partitionSelectionIndex by remember { mutableIntStateOf(0) }
    var partitionsState by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasCustomSelected by remember { mutableStateOf(false) }
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)

    // 处理预选的内核文件
    LaunchedEffect(preselectedKernelUri) {
        preselectedKernelUri?.let { uriString ->
            try {
                val preselectedUri = uriString.toUri()
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = preselectedUri,
                    summary = horizonKernelSummary
                )
                installMethod = horizonMethod
                tempKernelUri = preselectedUri
                if (isAbDevice) {
                    showSlotSelectionDialog = true
                } else {
                    showKpmPatchDialog = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val onInstall = {
        installMethod?.let { method ->
            when (method) {
                is InstallMethod.HorizonKernel -> {
                    method.uri?.let { uri ->
                        navigator.navigate(
                            KernelFlashScreenDestination(
                                kernelUri = uri,
                                selectedSlot = method.slot,
                                kpmPatchEnabled = kpmPatchOption == KpmPatchOption.PATCH_KPM,
                                kpmUndoPatch = kpmPatchOption == KpmPatchOption.UNDO_PATCH_KPM
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                }
                else -> {
                    val isOta = method is InstallMethod.DirectInstallToInactiveSlot
                    val partitionSelection = partitionsState.getOrNull(partitionSelectionIndex)
                    val flashIt = FlashIt.FlashBoot(
                        boot = if (method is InstallMethod.SelectFile) method.uri else null,
                        lkm = lkmSelection,
                        ota = isOta,
                        partition = partitionSelection
                    )
                    navigator.navigate(FlashScreenDestination(flashIt)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // 槽位选择对话框
    if (showSlotSelectionDialog && isAbDevice) {
        SlotSelectionDialog(
            show = true,
            onDismiss = { showSlotSelectionDialog = false },
            onSlotSelected = { slot ->
                showSlotSelectionDialog = false
                val horizonMethod = InstallMethod.HorizonKernel(
                    uri = tempKernelUri,
                    slot = slot,
                    summary = horizonKernelSummary
                )
                installMethod = horizonMethod
                // 槽位选择后，显示 KPM 补丁选择对话框
                showKpmPatchDialog = true
            }
        )
    }

    // KPM补丁选择对话框
    if (showKpmPatchDialog) {
        KpmPatchSelectionDialog(
            show = true,
            currentOption = kpmPatchOption,
            onDismiss = { showKpmPatchDialog = false },
            onOptionSelected = { option ->
                kpmPatchOption = option
                showKpmPatchDialog = false
            }
        )
    }

    val currentKmi by produceState(initialValue = "") { value = getCurrentKmi() }

    val showChooseKmiDialog = rememberSaveable { mutableStateOf(false) }
    val chooseKmiDialog = ChooseKmiDialog(showChooseKmiDialog) { kmi ->
        kmi?.let {
            lkmSelection = LkmSelection.KmiString(it)
            onInstall()
        }
    }

    val onClickNext = {
        if (isGKI && lkmSelection == LkmSelection.KmiNone && currentKmi.isBlank() && installMethod !is InstallMethod.HorizonKernel) {
            // no lkm file selected and cannot get current kmi
            showChooseKmiDialog.value = true
            chooseKmiDialog
        } else {
            onInstall()
        }
    }

    val selectLkmLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri ->
                    val isKo = isKoFile(context, uri)
                    if (isKo) {
                        lkmSelection = LkmSelection.LkmUri(uri)
                    } else {
                        lkmSelection = LkmSelection.KmiNone
                        Toast.makeText(
                            context,
                            context.getString(R.string.install_only_support_ko_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    val onLkmUpload = {
        selectLkmLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/octet-stream"
        })
    }

    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = colorScheme.background,
        tint = HazeTint(colorScheme.background.copy(0.8f))
    )

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
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
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    SelectInstallMethod(
                        onSelected = { method ->
                            if (method is InstallMethod.HorizonKernel && method.uri != null) {
                                if (isAbDevice) {
                                    tempKernelUri = method.uri
                                    showSlotSelectionDialog = true
                                } else {
                                    installMethod = method
                                    showKpmPatchDialog = true
                                }
                            } else {
                                installMethod = method
                            }
                        },
                        isAbDevice = isAbDevice
                    )
                }
                AnimatedVisibility(
                    visible = installMethod is InstallMethod.DirectInstall || installMethod is InstallMethod.DirectInstallToInactiveSlot,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        val isOta = installMethod is InstallMethod.DirectInstallToInactiveSlot
                        val suffix = produceState(initialValue = "", isOta) {
                            value = getSlotSuffix(isOta)
                        }.value
                        val partitions = produceState(initialValue = emptyList()) {
                            value = getAvailablePartitions()
                        }.value
                        val defaultPartition = produceState(initialValue = "") {
                            value = getDefaultPartition()
                        }.value
                        partitionsState = partitions
                        val displayPartitions = partitions.map { name ->
                            if (defaultPartition == name) "$name (default)" else name
                        }
                        val defaultIndex = partitions.indexOf(defaultPartition).takeIf { it >= 0 } ?: 0
                        if (!hasCustomSelected) partitionSelectionIndex = defaultIndex
                        SuperDropdown(
                            items = displayPartitions,
                            selectedIndex = partitionSelectionIndex,
                            title = "${stringResource(R.string.install_select_partition)} (${suffix})",
                            onSelectedIndexChange = { index ->
                                hasCustomSelected = true
                                partitionSelectionIndex = index
                            },
                            leftAction = {
                                Icon(
                                    MiuixIcons.Useful.Edit,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                // LKM 上传选项（仅 GKI）
                if (isGKI) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        SuperArrow(
                            title = stringResource(id = R.string.install_upload_lkm_file),
                            summary = (lkmSelection as? LkmSelection.LkmUri)?.let {
                                stringResource(
                                    id = R.string.selected_lkm,
                                    it.uri.lastPathSegment ?: "(file)"
                                )
                            },
                            onClick = onLkmUpload,
                            leftAction = {
                                Icon(
                                    MiuixIcons.Useful.Move,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 16.dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                
                // AnyKernel3 相关信息显示
                (installMethod as? InstallMethod.HorizonKernel)?.let { method ->
                    if (method.slot != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            SuperArrow(
                                title = stringResource(
                                    id = R.string.selected_slot,
                                    if (method.slot == "a") stringResource(id = R.string.slot_a)
                                    else stringResource(id = R.string.slot_b)
                                ),
                                onClick = {},
                                leftAction = {
                                    Icon(
                                        Icons.Filled.SdStorage,
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.padding(end = 16.dp),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                    
                    // KPM 状态显示
                    if (kpmPatchOption != KpmPatchOption.FOLLOW_KERNEL) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            SuperArrow(
                                title = when (kpmPatchOption) {
                                    KpmPatchOption.PATCH_KPM -> stringResource(R.string.kpm_patch_enabled)
                                    KpmPatchOption.UNDO_PATCH_KPM -> stringResource(R.string.kpm_undo_patch_enabled)
                                    else -> ""
                                },
                                onClick = {},
                                leftAction = {
                                    Icon(
                                        Icons.Filled.Security,
                                        tint = if (kpmPatchOption == KpmPatchOption.PATCH_KPM) 
                                            colorScheme.primary 
                                        else 
                                            colorScheme.secondary,
                                        modifier = Modifier.padding(end = 16.dp),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = installMethod != null,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = { onClickNext() }
                ) {
                    Text(
                        stringResource(id = R.string.install_next),
                        color = colorScheme.onPrimary,
                        fontSize = MiuixTheme.textStyles.body1.fontSize
                    )
                }
                Spacer(
                    Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
}

sealed class InstallMethod {
    data class SelectFile(
        val uri: Uri? = null,
        @get:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @get:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int
    open val summary: String? = null
}

@Composable
private fun SelectInstallMethod(
    onSelected: (InstallMethod) -> Unit = {},
    isAbDevice: Boolean = false
) {
    val rootAvailable = rootAvailable()
    val defaultPartitionName = produceState(initialValue = "boot") {
        value = getDefaultPartition()
    }.value
    val horizonKernelSummary = stringResource(R.string.horizon_kernel_summary)
    val selectFileTip = stringResource(
        id = R.string.select_file_tip, defaultPartitionName
    )
    val radioOptions = mutableListOf<InstallMethod>(InstallMethod.SelectFile(summary = selectFileTip))
    if (rootAvailable) {
        radioOptions.add(InstallMethod.DirectInstall)

        if (isAbDevice) {
            radioOptions.add(InstallMethod.DirectInstallToInactiveSlot)
        }
        radioOptions.add(InstallMethod.HorizonKernel(summary = horizonKernelSummary))
    }

    var selectedOption by remember { mutableStateOf<InstallMethod?>(null) }
    var currentSelectingMethod by remember { mutableStateOf<InstallMethod?>(null) }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val option = when (currentSelectingMethod) {
                    is InstallMethod.SelectFile -> InstallMethod.SelectFile(uri, summary = selectFileTip)
                    is InstallMethod.HorizonKernel -> InstallMethod.HorizonKernel(uri, summary = horizonKernelSummary)
                    else -> null
                }
                option?.let { opt ->
                    selectedOption = opt
                    onSelected(opt)
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            selectedOption = InstallMethod.DirectInstallToInactiveSlot
            onSelected(InstallMethod.DirectInstallToInactiveSlot)
        }
    )
    val dialogTitle = stringResource(id = android.R.string.dialog_alert_title)
    val dialogContent = stringResource(id = R.string.install_inactive_slot_warning)

    val onClick = { option: InstallMethod ->
        currentSelectingMethod = option
        when (option) {
            is InstallMethod.SelectFile, is InstallMethod.HorizonKernel -> {
                selectImageLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/zip"))
                })
            }

            is InstallMethod.DirectInstall -> {
                selectedOption = option
                onSelected(option)
            }

            is InstallMethod.DirectInstallToInactiveSlot -> {
                confirmDialog.showConfirm(dialogTitle, dialogContent)
            }
        }
    }

    Column {
        radioOptions.forEach { option ->
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = option.javaClass == selectedOption?.javaClass,
                        onValueChange = {
                            onClick(option)
                        },
                        role = Role.RadioButton,
                        indication = LocalIndication.current,
                        interactionSource = interactionSource
                    )
            ) {
                SuperCheckbox(
                    title = stringResource(id = option.label),
                    summary = option.summary,
                    checked = option.javaClass == selectedOption?.javaClass,
                    onCheckedChange = {
                        onClick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: ScrollBehavior,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    TopAppBar(
        modifier = Modifier.hazeEffect(hazeState) {
            style = hazeStyle
            blurRadius = 30.dp
            noiseFactor = 0f
        },
        color = Color.Transparent,
        title = stringResource(R.string.install),
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(start = 16.dp),
                onClick = onBack
            ) {
                Icon(
                    MiuixIcons.Useful.Back,
                    tint = colorScheme.onSurface,
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun KpmPatchSelectionDialog(
    show: Boolean,
    currentOption: KpmPatchOption,
    onDismiss: () -> Unit,
    onOptionSelected: (KpmPatchOption) -> Unit
) {
    var selectedOption by remember { mutableStateOf(currentOption) }
    val showDialog = remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        showDialog.value = show
        if (show) {
            selectedOption = currentOption
        }
    }

    SuperDialog(
        show = showDialog,
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = {
            showDialog.value = false
            onDismiss()
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                // 标题
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    text = stringResource(id = R.string.kpm_patch_options),
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface
                )

                // 描述
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    text = stringResource(id = R.string.kpm_patch_description),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 选项列表
                val options = listOf(
                    KpmPatchOption.FOLLOW_KERNEL to stringResource(R.string.kpm_follow_kernel_file),
                    KpmPatchOption.PATCH_KPM to stringResource(R.string.enable_kpm_patch),
                    KpmPatchOption.UNDO_PATCH_KPM to stringResource(R.string.enable_kpm_undo_patch)
                )

                options.forEach { (option, title) ->
                    SuperArrow(
                        title = title,
                        onClick = {
                            selectedOption = option
                        },
                        leftAction = {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (selectedOption == option) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariantSummary
                                }
                            )
                        },
                        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 按钮行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            showDialog.value = false
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = {
                            onOptionSelected(selectedOption)
                            showDialog.value = false
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

private fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}
