package com.sukisu.ultra.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import com.sukisu.ultra.ui.theme.wallpaperCardColors

@Composable
fun DynamicManagerCard() {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        colors = wallpaperCardColors(),
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

        var isDynEnabled by rememberSaveable {
            mutableStateOf(
                Natives.getDynamicManager()?.isValid() == true
            )
        }
        var dynSize by rememberSaveable {
            mutableStateOf(
                Natives.getDynamicManager()?.size?.toString() ?: ""
            )
        }
        var dynHash by rememberSaveable {
            mutableStateOf(
                Natives.getDynamicManager()?.hash ?: ""
            )
        }

        val showDynDialog = rememberSaveable { mutableStateOf(false) }

        SuperArrow(
            title = stringResource(R.string.dynamic_manager_title),
            summary = if (isDynEnabled) {
                stringResource(R.string.dynamic_manager_enabled_summary, dynSize)
            } else {
                stringResource(R.string.dynamic_manager_disabled)
            },
            leftAction = {
                Icon(
                    Icons.Rounded.Security,
                    modifier = Modifier.padding(end = 16.dp),
                    contentDescription = stringResource(R.string.dynamic_manager_title),
                    tint = colorScheme.onBackground
                )
            },
            onClick = {
                showDynDialog.value = true
            }
        )

        DynamicManagerDialog(
            show = showDynDialog,
            initialEnabled = isDynEnabled,
            initialSize = dynSize,
            initialHash = dynHash,
            onConfirm = { enabled, size, hash ->
                scope.launch(Dispatchers.IO) {
                    if (enabled) {
                        val newSize = try {
                            when {
                                size.startsWith("0x", true) ->
                                    size.substring(2).toInt(16)
                                else -> size.toInt()
                            }
                        } catch (_: Exception) {
                            -1
                        }
                        if (newSize <= 0 || hash.length != 64) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.invalid_sign_config,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                        val ok = Natives.setDynamicManager(newSize, hash)
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                dynSize = size
                                dynHash = hash
                                isDynEnabled = true
                                prefs.edit().apply {
                                    putBoolean("dm_enabled", true)
                                    putString("dm_size", dynSize)
                                    putString("dm_hash", dynHash)
                                    apply()
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.dynamic_manager_set_success,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.dynamic_manager_set_failed,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        val ok = Natives.clearDynamicManager()
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                isDynEnabled = false
                                prefs.edit().apply {
                                    putBoolean("dm_enabled", false)
                                    apply()
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.dynamic_manager_disabled_success,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    R.string.dynamic_manager_clear_failed,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DynamicManagerDialog(
    show: MutableState<Boolean>,
    initialEnabled: Boolean,
    initialSize: String,
    initialHash: String,
    onConfirm: (enabled: Boolean, size: String, hash: String) -> Unit
) {
    var tempDynEnabled by remember { mutableStateOf(initialEnabled) }
    var tempDynSize by remember { mutableStateOf(initialSize) }
    var tempDynHash by remember { mutableStateOf(initialHash) }

    if (show.value) {
        tempDynEnabled = initialEnabled
        tempDynSize = initialSize
        tempDynHash = initialHash
    }

    SuperDialog(
        title = stringResource(R.string.dynamic_manager_title),
        show = show,
        onDismissRequest = {
            show.value = false
        }
    ) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            SuperSwitch(
                title = stringResource(R.string.enable_dynamic_manager),
                checked = tempDynEnabled,
                onCheckedChange = { tempDynEnabled = it }
            )

            Spacer(Modifier.height(16.dp))

            TextField(
                value = tempDynSize,
                onValueChange = { value ->
                    // 只允许输入十六进制字符
                    if (value.all { it in "0123456789xXaAbBcCdDeEfF" }) {
                        tempDynSize = value
                    }
                },
                label = stringResource(R.string.signature_size),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            TextField(
                value = tempDynHash,
                onValueChange = { value ->
                    // 只允许输入十六进制字符，最多64个
                    if (value.all { it in "0123456789aAbBcCdDeEfF" } && value.length <= 64) {
                        tempDynHash = value
                    }
                },
                label = stringResource(R.string.signature_hash),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Text(
                text = "${tempDynHash.length} / 64",
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                color = colorScheme.onSurfaceVariantSummary
            )
        }

        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = {
                    show.value = false
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    show.value = false
                    onConfirm(tempDynEnabled, tempDynSize.trim(), tempDynHash.trim())
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
