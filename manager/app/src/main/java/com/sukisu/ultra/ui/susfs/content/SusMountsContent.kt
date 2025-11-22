package com.sukisu.ultra.ui.susfs.content

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.susfs.component.BottomActionButtons
import com.sukisu.ultra.ui.susfs.component.EmptyStateCard
import com.sukisu.ultra.ui.susfs.component.PathItemCard
import com.sukisu.ultra.ui.susfs.component.ResetButton
import com.sukisu.ultra.ui.susfs.component.SusMountHidingControlCard

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
                    onEdit = if (onEditMount != null) {
                        { onEditMount(mount) }
                    } else null,
                    isLoading = isLoading
                )
            }
        }
    }

    BottomActionButtons(
        primaryButtonText = stringResource(R.string.add),
        onPrimaryClick = onAddMount,
        isLoading = isLoading
    )

    if (onReset != null && susMounts.isNotEmpty()) {
        ResetButton(
            title = stringResource(R.string.susfs_reset_mounts_title),
            onClick = onReset
        )
    }
}

