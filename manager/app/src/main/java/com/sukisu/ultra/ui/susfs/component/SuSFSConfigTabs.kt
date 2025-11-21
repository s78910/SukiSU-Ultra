package com.sukisu.ultra.ui.susfs.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Switch
/**
 * SUS路径内容组件
 */
@Composable
fun SusPathsContent(
    susPaths: Set<String>,
    isLoading: Boolean,
    onAddPath: () -> Unit,
    onAddAppPath: () -> Unit,
    onRemovePath: (String) -> Unit,
    onEditPath: ((String) -> Unit)? = null,
    forceRefreshApps: Boolean = false,
    onReset: (() -> Unit)? = null
) {
    var superUserApps by remember { mutableStateOf(SuperUserViewModel.getAppsSafely()) }

    LaunchedEffect(Unit) {
        snapshotFlow { SuperUserViewModel.apps }
            .distinctUntilChanged()
            .collect { _ ->
                superUserApps = SuperUserViewModel.getAppsSafely()
                if (superUserApps.isNotEmpty()) {
                    try {
                        AppInfoCache.clearCache()
                    } catch (_: Exception) {
                    }
                }
            }
    }

    LaunchedEffect(forceRefreshApps) {
        if (forceRefreshApps) {
            try {
                AppInfoCache.clearCache()
            } catch (_: Exception) {
                // Ignore cache clear errors
            }
        }
    }

    val (appPathGroups, otherPaths) = remember(susPaths, superUserApps) {
        val appPathRegex = Regex(".*/Android/data/([^/]+)/?.*")
        val uidPathRegex = Regex("/sys/fs/cgroup/uid_([0-9]+)")
        val appPathMap = mutableMapOf<String, MutableList<String>>()
        val uidToPackageMap = mutableMapOf<String, String>()
        val others = mutableListOf<String>()

        // 构建UID到包名的映射
        try {
            superUserApps.forEach { app: SuperUserViewModel.AppInfo ->
                try {
                    val uid = app.packageInfo.applicationInfo?.uid
                    if (uid != null) {
                        uidToPackageMap[uid.toString()] = app.packageName
                    }
                } catch (_: Exception) {
                    // Ignore individual app errors
                }
            }
        } catch (_: Exception) {
            // Ignore mapping errors
        }

        susPaths.forEach { path ->
            val appDataMatch = appPathRegex.find(path)
            val uidMatch = uidPathRegex.find(path)

            when {
                appDataMatch != null -> {
                    val packageName = appDataMatch.groupValues[1]
                    appPathMap.getOrPut(packageName) { mutableListOf() }.add(path)
                }
                uidMatch != null -> {
                    val uid = uidMatch.groupValues[1]
                    val packageName = uidToPackageMap[uid]
                    if (packageName != null) {
                        appPathMap.getOrPut(packageName) { mutableListOf() }.add(path)
                    } else {
                        others.add(path)
                    }
                }
                else -> {
                    others.add(path)
                }
            }
        }

        val sortedAppGroups = appPathMap.toList()
            .sortedBy { it.first }
            .map { (packageName, paths) -> packageName to paths.sorted() }

        Pair(sortedAppGroups, others.sorted())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 应用路径分组
        if (appPathGroups.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.app_paths_section),
                subtitle = null,
                icon = Icons.Default.Apps,
                count = appPathGroups.size
            )

            appPathGroups.forEach { (packageName, paths) ->
                AppPathGroupCard(
                    packageName = packageName,
                    paths = paths,
                    onDeleteGroup = {
                        paths.forEach { path -> onRemovePath(path) }
                    },
                    onEditGroup = if (onEditPath != null) {
                        {
                            onEditPath(paths.first())
                        }
                    } else null,
                    isLoading = isLoading
                )
            }
        }

        // 其他路径
        if (otherPaths.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.other_paths_section),
                subtitle = null,
                icon = Icons.Default.Folder,
                count = otherPaths.size
            )

            otherPaths.forEach { path ->
                PathItemCard(
                    path = path,
                    icon = Icons.Default.Folder,
                    onDelete = { onRemovePath(path) },
                    onEdit = if (onEditPath != null) { { onEditPath(path) } } else null,
                    isLoading = isLoading
                )
            }
        }

        if (susPaths.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_paths_configured)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddPath,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_custom_path),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }

            Button(
                onClick = onAddAppPath,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_app_path),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }

        // 重置按钮
        if (onReset != null && susPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_paths_title),
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
 * SUS循环路径内容组件
 */
@Composable
fun SusLoopPathsContent(
    susLoopPaths: Set<String>,
    isLoading: Boolean,
    onAddLoopPath: () -> Unit,
    onRemoveLoopPath: (String) -> Unit,
    onEditLoopPath: ((String) -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            cornerRadius = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.sus_loop_paths_description_title),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.sus_loop_paths_description_text),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
                Text(
                    text = stringResource(R.string.susfs_loop_path_restriction_warning),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (susLoopPaths.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_loop_paths_configured)
            )
        } else {
            SectionHeader(
                title = stringResource(R.string.loop_paths_section),
                subtitle = null,
                icon = Icons.Default.Loop,
                count = susLoopPaths.size
            )

            susLoopPaths.toList().forEach { path ->
                PathItemCard(
                    path = path,
                    icon = Icons.Default.Loop,
                    onDelete = { onRemoveLoopPath(path) },
                    onEdit = if (onEditLoopPath != null) { { onEditLoopPath(path) } } else null,
                    isLoading = isLoading
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddLoopPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_loop_path),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }

        // 重置按钮
        if (onReset != null && susLoopPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_loop_paths_title),
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
 * SUS Maps内容组件
 */
@Composable
fun SusMapsContent(
    susMaps: Set<String>,
    isLoading: Boolean,
    onAddSusMap: () -> Unit,
    onRemoveSusMap: (String) -> Unit,
    onEditSusMap: ((String) -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            cornerRadius = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.sus_maps_description_title),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.sus_maps_description_text),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
                Text(
                    text = stringResource(R.string.sus_maps_warning),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.sus_maps_debug_info),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }

        if (susMaps.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_sus_maps_configured)
            )
        } else {
            SectionHeader(
                title = stringResource(R.string.sus_maps_section),
                subtitle = null,
                icon = Icons.Default.Security,
                count = susMaps.size
            )

            susMaps.toList().forEach { map ->
                PathItemCard(
                    path = map,
                    icon = Icons.Default.Security,
                    onDelete = { onRemoveSusMap(map) },
                    onEdit = if (onEditSusMap != null) { { onEditSusMap(map) } } else null,
                    isLoading = isLoading
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddSusMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }

        // 重置按钮
        if (onReset != null && susMaps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_sus_maps_title),
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
 * SUS挂载内容组件
 */
@Composable
fun SusMountsContent(
    susMounts: Set<String>,
    hideSusMountsForAllProcs: Boolean,
    isLoading: Boolean,
    onAddMount: () -> Unit,
    onRemoveMount: (String) -> Unit,
    onEditMount: ((String) -> Unit)? = null,
    onToggleHideSusMountsForAllProcs: (Boolean) -> Unit,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SusMountHidingControlCard(
            hideSusMountsForAllProcs = hideSusMountsForAllProcs,
            isLoading = isLoading,
            onToggleHiding = onToggleHideSusMountsForAllProcs
        )

        if (susMounts.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_mounts_configured)
            )
        } else {
            susMounts.toList().forEach { mount ->
                PathItemCard(
                    path = mount,
                    icon = Icons.Default.Storage,
                    onDelete = { onRemoveMount(mount) },
                    onEdit = if (onEditMount != null) { { onEditMount(mount) } } else null,
                    isLoading = isLoading
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddMount,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }

        // 重置按钮
        if (onReset != null && susMounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_mounts_title),
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
 * 尝试卸载内容组件
 */
@Composable
fun TryUmountContent(
    tryUmounts: Set<String>,
    umountForZygoteIsoService: Boolean,
    isLoading: Boolean,
    onAddUmount: () -> Unit,
    onRemoveUmount: (String) -> Unit,
    onEditUmount: ((String) -> Unit)? = null,
    onToggleUmountForZygoteIsoService: (Boolean) -> Unit,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.umount_zygote_iso_service),
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.umount_zygote_iso_service_description),
                        style = MiuixTheme.textStyles.body1,
                        color = colorScheme.onSurfaceVariantSummary,
                        lineHeight = 14.sp
                    )
                }
                Switch(
                    checked = umountForZygoteIsoService,
                    onCheckedChange = onToggleUmountForZygoteIsoService,
                    enabled = !isLoading
                )
            }
        }

        if (tryUmounts.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_umounts_configured)
            )
        } else {
            tryUmounts.toList().forEach { umountEntry ->
                val parts = umountEntry.split("|")
                val path = if (parts.isNotEmpty()) parts[0] else umountEntry
                val mode = if (parts.size > 1) parts[1] else "0"
                val modeText = if (mode == "0")
                    stringResource(R.string.susfs_umount_mode_normal_short)
                else
                    stringResource(R.string.susfs_umount_mode_detach_short)

                PathItemCard(
                    path = path,
                    icon = Icons.Default.Storage,
                    additionalInfo = stringResource(R.string.susfs_umount_mode_display, modeText, mode),
                    onDelete = { onRemoveUmount(umountEntry) },
                    onEdit = if (onEditUmount != null) { { onEditUmount(umountEntry) } } else null,
                    isLoading = isLoading
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddUmount,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }

        // 重置按钮
        if (onReset != null && tryUmounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_umounts_title),
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
 * Kstat配置内容组件
 */
@Composable
fun KstatConfigContent(
    kstatConfigs: Set<String>,
    addKstatPaths: Set<String>,
    isLoading: Boolean,
    onAddKstatStatically: () -> Unit,
    onAddKstat: () -> Unit,
    onRemoveKstatConfig: (String) -> Unit,
    onEditKstatConfig: ((String) -> Unit)? = null,
    onRemoveAddKstat: (String) -> Unit,
    onEditAddKstat: ((String) -> Unit)? = null,
    onUpdateKstat: (String) -> Unit,
    onUpdateKstatFullClone: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            cornerRadius = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.kstat_config_description_title),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_add_statically),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_add),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_update),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_update_full_clone),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
            }
        }

        if (kstatConfigs.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.static_kstat_config),
                subtitle = null,
                icon = Icons.Default.Settings,
                count = kstatConfigs.size
            )
            kstatConfigs.toList().forEach { config ->
                KstatConfigItemCard(
                    config = config,
                    onDelete = { onRemoveKstatConfig(config) },
                    onEdit = if (onEditKstatConfig != null) { { onEditKstatConfig(config) } } else null,
                    isLoading = isLoading
                )
            }
        }

        if (addKstatPaths.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.kstat_path_management),
                subtitle = null,
                icon = Icons.Default.Folder,
                count = addKstatPaths.size
            )
            addKstatPaths.toList().forEach { path ->
                AddKstatPathItemCard(
                    path = path,
                    onDelete = { onRemoveAddKstat(path) },
                    onEdit = if (onEditAddKstat != null) { { onEditAddKstat(path) } } else null,
                    onUpdate = { onUpdateKstat(path) },
                    onUpdateFullClone = { onUpdateKstatFullClone(path) },
                    isLoading = isLoading
                )
            }
        }

        if (kstatConfigs.isEmpty() && addKstatPaths.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.no_kstat_config_message)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddKstat,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }

            Button(
                onClick = onAddKstatStatically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * 路径设置内容组件
 */
@SuppressLint("SdCardPath")
@Composable
fun PathSettingsContent(
    androidDataPath: String,
    onAndroidDataPathChange: (String) -> Unit,
    sdcardPath: String,
    onSdcardPathChange: (String) -> Unit,
    isLoading: Boolean,
    onSetAndroidDataPath: () -> Unit,
    onSetSdcardPath: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = androidDataPath,
                    onValueChange = onAndroidDataPathChange,
                    label = stringResource(R.string.susfs_android_data_path_label),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Button(
                    onClick = onSetAndroidDataPath,
                    enabled = !isLoading && androidDataPath.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.susfs_set_android_data_path),
                        maxLines = 2
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = sdcardPath,
                    onValueChange = onSdcardPathChange,
                    label = stringResource(R.string.susfs_sdcard_path_label),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Button(
                    onClick = onSetSdcardPath,
                    enabled = !isLoading && sdcardPath.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.susfs_set_sdcard_path),
                        maxLines = 2
                    )
                }
            }
        }

        // 重置按钮
        if (onReset != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                        text = stringResource(R.string.susfs_reset_path_title),
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
 * 启用功能状态内容组件
 */
@Composable
fun EnabledFeaturesContent(
    enabledFeatures: List<SuSFSManager.EnabledFeature>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            cornerRadius = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.susfs_enabled_features_description),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp
                )
            }
        }

        if (enabledFeatures.isEmpty()) {
            EmptyStateCard(
                message = stringResource(R.string.susfs_no_features_found)
            )
        } else {
            enabledFeatures.forEach { feature ->
                FeatureStatusCard(
                    feature = feature,
                    onRefresh = onRefresh
                )
            }
        }

        // 刷新按钮
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.refresh),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
            }
        }
    }
}