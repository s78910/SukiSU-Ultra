package com.sukisu.ultra.ui.screen

import android.content.Context
import android.os.Process.myUid
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.*
import java.time.format.DateTimeFormatter

private val SPACING_SMALL = 4.dp
private val SPACING_MEDIUM = 8.dp
private val SPACING_LARGE = 16.dp

private const val PAGE_SIZE = 10000
private const val MAX_TOTAL_LOGS = 100000

private const val LOGS_PATCH = "/data/adb/ksu/log/sulog.log"

data class LogEntry(
    val timestamp: String,
    val type: LogType,
    val uid: String,
    val comm: String,
    val details: String,
    val pid: String,
    val rawLine: String
)

data class LogPageInfo(
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalLogs: Int = 0,
    val hasMore: Boolean = false
)

enum class LogType(val displayName: String, val color: Color) {
    SU_GRANT("SU_GRANT", Color(0xFF4CAF50)),
    SU_EXEC("SU_EXEC", Color(0xFF2196F3)),
    PERM_CHECK("PERM_CHECK", Color(0xFFFF9800)),
    SYSCALL("SYSCALL", Color(0xFF00BCD4)),
    MANAGER_OP("MANAGER_OP", Color(0xFF9C27B0)),
    UNKNOWN("UNKNOWN", Color(0xFF757575))
}

enum class LogExclType(val displayName: String, val color: Color) {
    CURRENT_APP("Current app", Color(0xFF9E9E9E)),
    PRCTL_STAR("prctl_*", Color(0xFF00BCD4)),
    PRCTL_UNKNOWN("prctl_unknown", Color(0xFF00BCD4)),
    SETUID("setuid", Color(0xFF00BCD4))
}

private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val localFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun saveExcludedSubTypes(context: Context, types: Set<LogExclType>) {
    val prefs = context.getSharedPreferences("sulog", Context.MODE_PRIVATE)
    val nameSet = types.map { it.name }.toSet()
    prefs.edit { putStringSet("excluded_subtypes", nameSet) }
}

private fun loadExcludedSubTypes(context: Context): Set<LogExclType> {
    val prefs = context.getSharedPreferences("sulog", Context.MODE_PRIVATE)
    val nameSet = prefs.getStringSet("excluded_subtypes", emptySet()) ?: emptySet()
    return nameSet.mapNotNull { name ->
        LogExclType.entries.firstOrNull { it.name == name }
    }.toSet()
}

@Destination<RootGraph>
@Composable
fun LogViewer(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf<LogType?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var pageInfo by remember { mutableStateOf(LogPageInfo()) }
    var lastLogFileHash by remember { mutableStateOf("") }
    val currentUid = remember { myUid().toString() }

    val initialExcluded = remember {
        loadExcludedSubTypes(context)
    }

    var excludedSubTypes by rememberSaveable { mutableStateOf(initialExcluded) }

    LaunchedEffect(excludedSubTypes) {
        saveExcludedSubTypes(context, excludedSubTypes)
    }

    val filteredEntries = remember(
        logEntries, filterType, searchQuery, excludedSubTypes
    ) {
        logEntries.filter { entry ->
            val matchesSearch = searchQuery.isEmpty() ||
                    entry.comm.contains(searchQuery, ignoreCase = true) ||
                    entry.details.contains(searchQuery, ignoreCase = true) ||
                    entry.uid.contains(searchQuery, ignoreCase = true)

            if (LogExclType.CURRENT_APP in excludedSubTypes && entry.uid == currentUid) return@filter false

            if (entry.type == LogType.SYSCALL) {
                val detail = entry.details
                if (LogExclType.PRCTL_STAR in excludedSubTypes && detail.startsWith("Syscall: prctl") && !detail.startsWith("Syscall: prctl_unknown")) return@filter false
                if (LogExclType.PRCTL_UNKNOWN in excludedSubTypes && detail.startsWith("Syscall: prctl_unknown")) return@filter false
                if (LogExclType.SETUID in excludedSubTypes && detail.startsWith("Syscall: setuid")) return@filter false
            }

            val matchesFilter = filterType == null || entry.type == filterType
            matchesFilter && matchesSearch
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }

    val loadPage: (Int, Boolean) -> Unit = { page, forceRefresh ->
        scope.launch {
            if (isLoading) return@launch

            isLoading = true
            try {
                loadLogsWithPagination(
                    page,
                    forceRefresh,
                    lastLogFileHash
                ) { entries, newPageInfo, newHash ->
                    logEntries = if (page == 0 || forceRefresh) {
                        entries
                    } else {
                        logEntries + entries
                    }
                    pageInfo = newPageInfo
                    lastLogFileHash = newHash
                }
            } finally {
                isLoading = false
            }
        }
    }

    val onManualRefresh: () -> Unit = {
        loadPage(0, true)
    }

    val loadNextPage: () -> Unit = {
        if (pageInfo.hasMore && !isLoading) {
            loadPage(pageInfo.currentPage + 1, false)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (!isLoading) {
                scope.launch {
                    val hasNewLogs = checkForNewLogs(lastLogFileHash)
                    if (hasNewLogs) {
                        loadPage(0, true)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPage(0, true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.log_viewer_title),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.navigateUp() },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = stringResource(R.string.log_viewer_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.log_viewer_search)
                        )
                    }
                    IconButton(onClick = onManualRefresh) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Refresh,
                            contentDescription = stringResource(R.string.log_viewer_refresh)
                        )
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Delete,
                            contentDescription = stringResource(R.string.log_viewer_clear_logs)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_LARGE, vertical = SPACING_MEDIUM),
                    label = stringResource(R.string.log_viewer_search_placeholder)
                )
            }

            LogControlPanel(
                filterType = filterType,
                onFilterTypeSelected = { filterType = it },
                logCount = filteredEntries.size,
                totalCount = logEntries.size,
                pageInfo = pageInfo,
                excludedSubTypes = excludedSubTypes,
                onExcludeToggle = { excl ->
                    excludedSubTypes = if (excl in excludedSubTypes)
                        excludedSubTypes - excl
                    else
                        excludedSubTypes + excl
                }
            )

            if (isLoading && logEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredEntries.isEmpty()) {
                EmptyLogState(
                    hasLogs = logEntries.isNotEmpty(),
                    onRefresh = onManualRefresh
                )
            } else {
                LogList(
                    entries = filteredEntries,
                    pageInfo = pageInfo,
                    isLoading = isLoading,
                    onLoadMore = loadNextPage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    val showClearDialogState = remember { mutableStateOf(showClearDialog) }

    LaunchedEffect(showClearDialog) {
        showClearDialogState.value = showClearDialog
    }

    LaunchedEffect(showClearDialogState.value) {
        showClearDialog = showClearDialogState.value
    }

    SuperDialog(
        show = showClearDialogState,
        onDismissRequest = { showClearDialog = false },
        content = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                text = stringResource(R.string.log_viewer_clear_logs),
                style = MiuixTheme.textStyles.title4,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                text = stringResource(R.string.log_viewer_clear_logs_confirm),
                style = MiuixTheme.textStyles.body2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showClearDialog = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            clearLogs()
                            loadPage(0, true)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

private val CONTROL_PANEL_SPACING_SMALL = 4.dp
private val CONTROL_PANEL_SPACING_MEDIUM = 8.dp
private val CONTROL_PANEL_SPACING_LARGE = 12.dp

@Composable
private fun LogControlPanel(
    filterType: LogType?,
    onFilterTypeSelected: (LogType?) -> Unit,
    logCount: Int,
    totalCount: Int,
    pageInfo: LogPageInfo,
    excludedSubTypes: Set<LogExclType>,
    onExcludeToggle: (LogExclType) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_LARGE, vertical = SPACING_MEDIUM)
    ) {
        Column {
            SuperArrow(
                title = stringResource(R.string.log_viewer_settings),
                onClick = { isExpanded = !isExpanded },
                summary = if (isExpanded)
                    stringResource(R.string.log_viewer_collapse)
                else
                    stringResource(R.string.log_viewer_expand)
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = SPACING_MEDIUM)
                ) {
                    Text(
                        text = stringResource(R.string.log_viewer_filter_type),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Spacer(modifier = Modifier.height(CONTROL_PANEL_SPACING_MEDIUM))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(CONTROL_PANEL_SPACING_MEDIUM)) {
                        item {
                            FilterChip(
                                text = stringResource(R.string.log_viewer_all_types),
                                selected = filterType == null,
                                onClick = { onFilterTypeSelected(null) }
                            )
                        }
                        items(LogType.entries.toTypedArray()) { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(type.color, RoundedCornerShape(3.dp))
                                )
                                FilterChip(
                                    text = type.displayName,
                                    selected = filterType == type,
                                    onClick = { onFilterTypeSelected(if (filterType == type) null else type) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CONTROL_PANEL_SPACING_LARGE))

                    Text(
                        text = stringResource(R.string.log_viewer_exclude_subtypes),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Spacer(modifier = Modifier.height(CONTROL_PANEL_SPACING_MEDIUM))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(CONTROL_PANEL_SPACING_MEDIUM)) {
                        items(LogExclType.entries.toTypedArray()) { excl ->
                            val label = if (excl == LogExclType.CURRENT_APP)
                                stringResource(R.string.log_viewer_exclude_current_app)
                            else excl.displayName

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(excl.color, RoundedCornerShape(3.dp))
                                )
                                FilterChip(
                                    text = label,
                                    selected = excl in excludedSubTypes,
                                    onClick = { onExcludeToggle(excl) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CONTROL_PANEL_SPACING_LARGE))

                    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_PANEL_SPACING_SMALL)) {
                        Text(
                            text = stringResource(R.string.log_viewer_showing_entries, logCount, totalCount),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        if (pageInfo.totalPages > 0) {
                            Text(
                                text = stringResource(
                                    R.string.log_viewer_page_info,
                                    pageInfo.currentPage + 1,
                                    pageInfo.totalPages,
                                    pageInfo.totalLogs
                                ),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (pageInfo.totalLogs >= MAX_TOTAL_LOGS) {
                            Text(
                                text = stringResource(R.string.log_viewer_too_many_logs, MAX_TOTAL_LOGS),
                                style = MiuixTheme.textStyles.body2,
                                color = Color(0xFFE53935)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CONTROL_PANEL_SPACING_LARGE))
                }
            }
        }
    }
}

@Composable
private fun LogList(
    entries: List<LogEntry>,
    pageInfo: LogPageInfo,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SPACING_LARGE, vertical = SPACING_MEDIUM),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM)
    ) {
        items(entries) { entry ->
            LogEntryCard(entry = entry)
        }

        if (pageInfo.hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SPACING_LARGE),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.log_viewer_load_more))
                        }
                    }
                }
            }
        } else if (entries.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SPACING_LARGE),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.log_viewer_all_logs_loaded),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(SPACING_LARGE)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(entry.type.color, RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = entry.type.displayName,
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = entry.timestamp,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Spacer(modifier = Modifier.height(SPACING_SMALL))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UID: ${entry.uid}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = "PID: ${entry.pid}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Text(
                text = entry.comm,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(SPACING_SMALL))
                Text(
                    text = entry.details,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(SPACING_MEDIUM))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(SPACING_MEDIUM))
                    Text(
                        text = stringResource(R.string.log_viewer_raw_log),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(SPACING_SMALL))
                    Text(
                        text = entry.rawLine,
                        style = MiuixTheme.textStyles.body2,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLogState(
    hasLogs: Boolean,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SPACING_LARGE)
        ) {
            Text(
                text = stringResource(
                    if (hasLogs) R.string.log_viewer_no_matching_logs
                    else R.string.log_viewer_no_logs
                ),
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Button(
                onClick = onRefresh
            ) {
                Text(stringResource(R.string.log_viewer_refresh))
            }
        }
    }
}

private suspend fun checkForNewLogs(lastHash: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val logPath = "/data/adb/ksu/log/sulog.log"
            val result = runCmd(shell, "stat -c '%Y %s' $logPath 2>/dev/null || echo '0 0'")
            val currentHash = result.trim()
            currentHash != lastHash && currentHash != "0 0"
        } catch (_: Exception) {
            false
        }
    }
}

private suspend fun loadLogsWithPagination(
    page: Int,
    forceRefresh: Boolean,
    lastHash: String,
    onLoaded: (List<LogEntry>, LogPageInfo, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val statResult = runCmd(shell, "stat -c '%Y %s' $LOGS_PATCH 2>/dev/null || echo '0 0'")
            val currentHash = statResult.trim()

            if (!forceRefresh && currentHash == lastHash && currentHash != "0 0") {
                withContext(Dispatchers.Main) {
                    onLoaded(emptyList(), LogPageInfo(), currentHash)
                }
                return@withContext
            }

            val totalLinesResult = runCmd(shell, "wc -l < $LOGS_PATCH 2>/dev/null || echo '0'")
            val totalLines = totalLinesResult.trim().toIntOrNull() ?: 0

            if (totalLines == 0) {
                withContext(Dispatchers.Main) {
                    onLoaded(emptyList(), LogPageInfo(), currentHash)
                }
                return@withContext
            }

            val effectiveTotal = minOf(totalLines, MAX_TOTAL_LOGS)
            val totalPages = (effectiveTotal + PAGE_SIZE - 1) / PAGE_SIZE

            val startLine = if (page == 0) {
                maxOf(1, totalLines - effectiveTotal + 1)
            } else {
                val skipLines = page * PAGE_SIZE
                maxOf(1, totalLines - effectiveTotal + 1 + skipLines)
            }

            val endLine = minOf(startLine + PAGE_SIZE - 1, totalLines)

            if (startLine > totalLines) {
                withContext(Dispatchers.Main) {
                    onLoaded(emptyList(), LogPageInfo(page, totalPages, effectiveTotal, false), currentHash)
                }
                return@withContext
            }

            val result = runCmd(shell, "sed -n '${startLine},${endLine}p' $LOGS_PATCH 2>/dev/null || echo ''")
            val entries = parseLogEntries(result)

            val hasMore = endLine < totalLines
            val pageInfo = LogPageInfo(page, totalPages, effectiveTotal, hasMore)

            withContext(Dispatchers.Main) {
                onLoaded(entries, pageInfo, currentHash)
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                onLoaded(emptyList(), LogPageInfo(), lastHash)
            }
        }
    }
}

private suspend fun clearLogs() {
    withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            runCmd(shell, "echo '' > $LOGS_PATCH")
        } catch (_: Exception) {
        }
    }
}

private fun parseLogEntries(logContent: String): List<LogEntry> {
    if (logContent.isBlank()) return emptyList()

    val entries = logContent.lines()
        .filter { it.isNotBlank() && it.startsWith("[") }
        .mapNotNull { line ->
            try {
                parseLogLine(line)
            } catch (_: Exception) {
                null
            }
        }

    return entries.reversed()
}

private fun utcToLocal(utc: String): String {
    return try {
        val instant = LocalDateTime.parse(utc, utcFormatter).atOffset(ZoneOffset.UTC).toInstant()
        val local = instant.atZone(ZoneId.systemDefault())
        local.format(localFormatter)
    } catch (_: Exception) {
        utc
    }
}

private fun parseLogLine(line: String): LogEntry? {
    val timestampRegex = """\[(.*?)]""".toRegex()
    val timestampMatch = timestampRegex.find(line) ?: return null
    val timestamp = utcToLocal(timestampMatch.groupValues[1])

    val afterTimestamp = line.substring(timestampMatch.range.last + 1).trim()
    val parts = afterTimestamp.split(":")
    if (parts.size < 2) return null

    val typeStr = parts[0].trim()
    val type = when (typeStr) {
        "SU_GRANT" -> LogType.SU_GRANT
        "SU_EXEC" -> LogType.SU_EXEC
        "PERM_CHECK" -> LogType.PERM_CHECK
        "SYSCALL" -> LogType.SYSCALL
        "MANAGER_OP" -> LogType.MANAGER_OP
        else -> LogType.UNKNOWN
    }

    val details = parts[1].trim()
    val uid: String = extractValue(details, "UID") ?: ""
    val comm: String = extractValue(details, "COMM") ?: ""
    val pid: String = extractValue(details, "PID") ?: ""

    val detailsStr = when (type) {
        LogType.SU_GRANT -> {
            val method: String = extractValue(details, "METHOD") ?: ""
            "Method: $method"
        }
        LogType.SU_EXEC -> {
            val target: String = extractValue(details, "TARGET") ?: ""
            val result: String = extractValue(details, "RESULT") ?: ""
            "Target: $target, Result: $result"
        }
        LogType.PERM_CHECK -> {
            val result: String = extractValue(details, "RESULT") ?: ""
            "Result: $result"
        }
        LogType.SYSCALL -> {
            val syscall = extractValue(details, "SYSCALL") ?: ""
            val args = extractValue(details, "ARGS") ?: ""
            "Syscall: $syscall, Args: $args"
        }
        LogType.MANAGER_OP -> {
            val op: String = extractValue(details, "OP") ?: ""
            val managerUid: String = extractValue(details, "MANAGER_UID") ?: ""
            val targetUid: String = extractValue(details, "TARGET_UID") ?: ""
            "Operation: $op, Manager UID: $managerUid, Target UID: $targetUid"
        }
        else -> details
    }

    return LogEntry(
        timestamp = timestamp,
        type = type,
        uid = uid,
        comm = comm,
        details = detailsStr,
        pid = pid,
        rawLine = line
    )
}

private fun extractValue(text: String, key: String): String? {
    val regex = """$key=(\S+)""".toRegex()
    return regex.find(text)?.groupValues?.get(1)
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.textButtonColorsPrimary()
        } else {
            ButtonDefaults.textButtonColors()
        }
    )
}
