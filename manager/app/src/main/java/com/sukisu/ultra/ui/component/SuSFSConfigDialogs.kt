package com.sukisu.ultra.ui.component

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.SuSFSManager
import com.sukisu.ultra.ui.screen.extensions.AppInfoCache

/**
 * 添加路径对话框
 */
@Composable
fun AddPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean,
    titleRes: Int,
    labelRes: Int,
    placeholderRes: Int,
    initialValue: String = ""
) {
    var newPath by remember { mutableStateOf("") }

    // 当对话框显示时，设置初始值
    LaunchedEffect(showDialog, initialValue) {
        if (showDialog) {
            newPath = initialValue
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    label = { Text(stringResource(labelRes)) },
                    placeholder = { Text(stringResource(placeholderRes)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPath.isNotBlank()) {
                            onConfirm(newPath.trim())
                            newPath = ""
                        }
                    },
                    enabled = newPath.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(if (initialValue.isNotEmpty()) R.string.susfs_save else R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        newPath = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

/**
 * 快捷添加应用路径对话框
 */
@Composable
fun AddAppPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isLoading: Boolean,
    apps: List<SuSFSManager.AppInfo> = emptyList(),
    onLoadApps: () -> Unit,
    existingSusPaths: Set<String> = emptySet()
) {
    var searchText by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(setOf<SuSFSManager.AppInfo>()) }

    // 获取已添加的包名
    val addedPackageNames = remember(existingSusPaths) {
        existingSusPaths.mapNotNull { path ->
            val regex = Regex(".*/Android/data/([^/]+)/?.*")
            regex.find(path)?.groupValues?.get(1)
        }.toSet()
    }

    // 过滤掉已添加的应用
    val availableApps = remember(apps, addedPackageNames) {
        apps.filter { app ->
            !addedPackageNames.contains(app.packageName)
        }
    }

    val filteredApps = remember(availableApps, searchText) {
        if (searchText.isBlank()) {
            availableApps
        } else {
            availableApps.filter { app ->
                app.appName.contains(searchText, ignoreCase = true) ||
                        app.packageName.contains(searchText, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(showDialog) {
        if (showDialog && apps.isEmpty()) {
            onLoadApps()
        }
        // 当对话框显示时清空选择
        if (showDialog) {
            selectedApps = setOf()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.susfs_add_app_path),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // 显示统计信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (selectedApps.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.selected_apps_count, selectedApps.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (addedPackageNames.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.already_added_apps_count, addedPackageNames.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (availableApps.isEmpty()) {
                                    stringResource(R.string.all_apps_already_added)
                                } else {
                                    stringResource(R.string.no_apps_found)
                                },
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps) { app ->
                                val isSelected = selectedApps.contains(app)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    onClick = {
                                        selectedApps = if (isSelected) {
                                            selectedApps - app
                                        } else {
                                            selectedApps + app
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 应用图标
                                        AppIcon(
                                            packageName = app.packageName,
                                            packageInfo = app.packageInfo,
                                            modifier = Modifier.size(40.dp)
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp)
                                        ) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }

                                        // 选择指示器
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedApps.isNotEmpty()) {
                            onConfirm(selectedApps.map { it.packageName })
                        }
                        selectedApps = setOf()
                        searchText = ""
                    },
                    enabled = selectedApps.isNotEmpty() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.add)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        selectedApps = setOf()
                        searchText = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}


/**
 * 应用图标组件
 */
@Composable
fun AppIcon(
    packageName: String,
    packageInfo: PackageInfo? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (packageInfo != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(packageInfo)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        var appIcon by remember(packageName) {
            mutableStateOf(
                AppInfoCache.getAppInfo(packageName)?.drawable
            )
        }

        LaunchedEffect(packageName) {
            if (appIcon == null && !AppInfoCache.hasCache(packageName)) {
                try {
                    val packageManager = context.packageManager
                    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                    val drawable = packageManager.getApplicationIcon(applicationInfo)
                    appIcon = drawable
                    val cachedInfo = AppInfoCache.CachedAppInfo(
                        appName = packageName,
                        packageInfo = null,
                        drawable = drawable
                    )
                    AppInfoCache.putAppInfo(packageName, cachedInfo)
                } catch (_: Exception) {
                    Log.d("获取应用图标失败", packageName)
                }
            }
        }
        Image(
            painter = rememberDrawablePainter(appIcon),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    }
}


/**
 * 添加尝试卸载对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTryUmountDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
    isLoading: Boolean,
    initialPath: String = "",
    initialMode: Int = 0
) {
    var newUmountPath by remember { mutableStateOf("") }
    var newUmountMode by remember { mutableIntStateOf(0) }
    var umountModeExpanded by remember { mutableStateOf(false) }

    // 当对话框显示时，设置初始值
    LaunchedEffect(showDialog, initialPath, initialMode) {
        if (showDialog) {
            newUmountPath = initialPath
            newUmountMode = initialMode
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(if (initialPath.isNotEmpty()) R.string.susfs_edit_try_umount else R.string.susfs_add_try_umount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newUmountPath,
                        onValueChange = { newUmountPath = it },
                        label = { Text(stringResource(R.string.susfs_path_label)) },
                        placeholder = { Text(stringResource(R.string.susfs_path_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = umountModeExpanded,
                        onExpandedChange = { umountModeExpanded = !umountModeExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (newUmountMode == 0)
                                stringResource(R.string.susfs_umount_mode_normal)
                            else
                                stringResource(R.string.susfs_umount_mode_detach),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(stringResource(R.string.susfs_umount_mode_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = umountModeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = umountModeExpanded,
                            onDismissRequest = { umountModeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.susfs_umount_mode_normal)) },
                                onClick = {
                                    newUmountMode = 0
                                    umountModeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.susfs_umount_mode_detach)) },
                                onClick = {
                                    newUmountMode = 1
                                    umountModeExpanded = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUmountPath.isNotBlank()) {
                            onConfirm(newUmountPath.trim(), newUmountMode)
                            newUmountPath = ""
                            newUmountMode = 0
                        }
                    },
                    enabled = newUmountPath.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(if (initialPath.isNotEmpty()) R.string.susfs_save else R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        newUmountPath = ""
                        newUmountMode = 0
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

/**
 * 添加Kstat静态配置对话框
 */
@Composable
fun AddKstatStaticallyDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, String, String, String, String, String, String, String) -> Unit,
    isLoading: Boolean,
    initialConfig: String = ""
) {
    var newKstatPath by remember { mutableStateOf("") }
    var newKstatIno by remember { mutableStateOf("") }
    var newKstatDev by remember { mutableStateOf("") }
    var newKstatNlink by remember { mutableStateOf("") }
    var newKstatSize by remember { mutableStateOf("") }
    var newKstatAtime by remember { mutableStateOf("") }
    var newKstatAtimeNsec by remember { mutableStateOf("") }
    var newKstatMtime by remember { mutableStateOf("") }
    var newKstatMtimeNsec by remember { mutableStateOf("") }
    var newKstatCtime by remember { mutableStateOf("") }
    var newKstatCtimeNsec by remember { mutableStateOf("") }
    var newKstatBlocks by remember { mutableStateOf("") }
    var newKstatBlksize by remember { mutableStateOf("") }

    // 当对话框显示时，解析初始配置
    LaunchedEffect(showDialog, initialConfig) {
        if (showDialog && initialConfig.isNotEmpty()) {
            val parts = initialConfig.split("|")
            if (parts.size >= 13) {
                newKstatPath = parts[0]
                newKstatIno = if (parts[1] == "default") "" else parts[1]
                newKstatDev = if (parts[2] == "default") "" else parts[2]
                newKstatNlink = if (parts[3] == "default") "" else parts[3]
                newKstatSize = if (parts[4] == "default") "" else parts[4]
                newKstatAtime = if (parts[5] == "default") "" else parts[5]
                newKstatAtimeNsec = if (parts[6] == "default") "" else parts[6]
                newKstatMtime = if (parts[7] == "default") "" else parts[7]
                newKstatMtimeNsec = if (parts[8] == "default") "" else parts[8]
                newKstatCtime = if (parts[9] == "default") "" else parts[9]
                newKstatCtimeNsec = if (parts[10] == "default") "" else parts[10]
                newKstatBlocks = if (parts[11] == "default") "" else parts[11]
                newKstatBlksize = if (parts[12] == "default") "" else parts[12]
            }
        } else if (showDialog && initialConfig.isEmpty()) {
            // 清空所有字段
            newKstatPath = ""
            newKstatIno = ""
            newKstatDev = ""
            newKstatNlink = ""
            newKstatSize = ""
            newKstatAtime = ""
            newKstatAtimeNsec = ""
            newKstatMtime = ""
            newKstatMtimeNsec = ""
            newKstatCtime = ""
            newKstatCtimeNsec = ""
            newKstatBlocks = ""
            newKstatBlksize = ""
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(if (initialConfig.isNotEmpty()) R.string.edit_kstat_statically_title else R.string.add_kstat_statically_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKstatPath,
                        onValueChange = { newKstatPath = it },
                        label = { Text(stringResource(R.string.file_or_directory_path_label)) },
                        placeholder = { Text("/path/to/file_or_directory") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatIno,
                            onValueChange = { newKstatIno = it },
                            label = { Text("ino") },
                            placeholder = { Text("1234") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatDev,
                            onValueChange = { newKstatDev = it },
                            label = { Text("dev") },
                            placeholder = { Text("1234") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatNlink,
                            onValueChange = { newKstatNlink = it },
                            label = { Text("nlink") },
                            placeholder = { Text("2") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatSize,
                            onValueChange = { newKstatSize = it },
                            label = { Text("size") },
                            placeholder = { Text("223344") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatAtime,
                            onValueChange = { newKstatAtime = it },
                            label = { Text("atime") },
                            placeholder = { Text("1712592355") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatAtimeNsec,
                            onValueChange = { newKstatAtimeNsec = it },
                            label = { Text("atime_nsec") },
                            placeholder = { Text("0") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatMtime,
                            onValueChange = { newKstatMtime = it },
                            label = { Text("mtime") },
                            placeholder = { Text("1712592355") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatMtimeNsec,
                            onValueChange = { newKstatMtimeNsec = it },
                            label = { Text("mtime_nsec") },
                            placeholder = { Text("0") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatCtime,
                            onValueChange = { newKstatCtime = it },
                            label = { Text("ctime") },
                            placeholder = { Text("1712592355") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatCtimeNsec,
                            onValueChange = { newKstatCtimeNsec = it },
                            label = { Text("ctime_nsec") },
                            placeholder = { Text("0") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newKstatBlocks,
                            onValueChange = { newKstatBlocks = it },
                            label = { Text("blocks") },
                            placeholder = { Text("16") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newKstatBlksize,
                            onValueChange = { newKstatBlksize = it },
                            label = { Text("blksize") },
                            placeholder = { Text("512") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.hint_use_default_value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKstatPath.isNotBlank()) {
                            onConfirm(
                                newKstatPath.trim(),
                                newKstatIno.trim().ifBlank { "default" },
                                newKstatDev.trim().ifBlank { "default" },
                                newKstatNlink.trim().ifBlank { "default" },
                                newKstatSize.trim().ifBlank { "default" },
                                newKstatAtime.trim().ifBlank { "default" },
                                newKstatAtimeNsec.trim().ifBlank { "default" },
                                newKstatMtime.trim().ifBlank { "default" },
                                newKstatMtimeNsec.trim().ifBlank { "default" },
                                newKstatCtime.trim().ifBlank { "default" },
                                newKstatCtimeNsec.trim().ifBlank { "default" },
                                newKstatBlocks.trim().ifBlank { "default" },
                                newKstatBlksize.trim().ifBlank { "default" }
                            )
                            // 清空所有字段
                            newKstatPath = ""
                            newKstatIno = ""
                            newKstatDev = ""
                            newKstatNlink = ""
                            newKstatSize = ""
                            newKstatAtime = ""
                            newKstatAtimeNsec = ""
                            newKstatMtime = ""
                            newKstatMtimeNsec = ""
                            newKstatCtime = ""
                            newKstatCtimeNsec = ""
                            newKstatBlocks = ""
                            newKstatBlksize = ""
                        }
                    },
                    enabled = newKstatPath.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(if (initialConfig.isNotEmpty()) R.string.susfs_save else R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        // 清空所有字段
                        newKstatPath = ""
                        newKstatIno = ""
                        newKstatDev = ""
                        newKstatNlink = ""
                        newKstatSize = ""
                        newKstatAtime = ""
                        newKstatAtimeNsec = ""
                        newKstatMtime = ""
                        newKstatMtimeNsec = ""
                        newKstatCtime = ""
                        newKstatCtimeNsec = ""
                        newKstatBlocks = ""
                        newKstatBlksize = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

/**
 * 确认对话框
 */
@Composable
fun ConfirmDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    titleRes: Int,
    messageRes: Int,
    isLoading: Boolean = false,
    isDestructive: Boolean = false
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading,
                    colors = if (isDestructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}