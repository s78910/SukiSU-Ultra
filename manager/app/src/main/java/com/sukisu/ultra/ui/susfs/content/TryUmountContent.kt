package com.sukisu.ultra.ui.susfs.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.susfs.component.BottomActionButtons
import com.sukisu.ultra.ui.susfs.component.EmptyStateCard
import com.sukisu.ultra.ui.susfs.component.PathItemCard
import com.sukisu.ultra.ui.susfs.component.ResetButton
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

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
        ) {
            SuperSwitch(
                title = stringResource(R.string.umount_zygote_iso_service),
                summary = stringResource(R.string.umount_zygote_iso_service_description),
                leftAction = {
                    Icon(
                        Icons.Default.Security,
                        modifier = Modifier.padding(end = 16.dp),
                        contentDescription = stringResource(R.string.umount_zygote_iso_service),
                        tint = colorScheme.onBackground
                    )
                },
                checked = umountForZygoteIsoService,
                onCheckedChange = onToggleUmountForZygoteIsoService,
                enabled = !isLoading
            )
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
                    additionalInfo = stringResource(
                        R.string.susfs_umount_mode_display,
                        modeText,
                        mode
                    ),
                    onDelete = { onRemoveUmount(umountEntry) },
                    onEdit = if (onEditUmount != null) {
                        { onEditUmount(umountEntry) }
                    } else null,
                    isLoading = isLoading
                )
            }
        }
    }

    BottomActionButtons(
        primaryButtonText = stringResource(R.string.add),
        onPrimaryClick = onAddUmount,
        isLoading = isLoading
    )

    if (onReset != null && tryUmounts.isNotEmpty()) {
        ResetButton(
            title = stringResource(R.string.susfs_reset_umounts_title),
            onClick = onReset
        )
    }
}

