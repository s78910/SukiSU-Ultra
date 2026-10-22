package com.sukisu.ultra.ui.screen.susfs.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ui.screen.susfs.SuSFSTab
import com.sukisu.ultra.ui.screen.susfs.SuSFSUiState
import com.sukisu.ultra.ui.screen.susfs.SuSFSRuntimeInfo
import com.sukisu.ultra.ui.util.getSuSFSVersion
import com.sukisu.ultra.ui.util.getSuSFSVariant
import com.sukisu.ultra.ui.util.getSuSFSImplementation
import com.sukisu.ultra.ui.screen.susfs.repository.SuSFSRepositoryInterface
import com.sukisu.ultra.ui.screen.susfs.repository.SuSFSRepositoryImpl
import com.sukisu.ultra.ui.screen.susfs.util.SuSFSManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SuSFSViewModel(
    private val repo: SuSFSRepositoryInterface = SuSFSRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuSFSUiState())
    val uiState: StateFlow<SuSFSUiState> = _uiState.asStateFlow()

    fun setSelectedTab(tab: SuSFSTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    private var loaded = false
    private var appContext: Context? = null

    fun loadInitial(context: Context) {
        if (loaded) return
        loaded = true
        appContext = context.applicationContext
        loadRuntimeInfo()
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
            val canEnableAutoStart = withContext(Dispatchers.IO) {
                SuSFSManager.hasConfigurationForAutoStart(context)
            }

            repo.loadInitialConfig(context).onSuccess { config ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        unameValue = config.unameValue,
                        buildTimeValue = config.buildTimeValue,
                        autoStartEnabled = SuSFSManager.isAutoStartEnabled(),
                        executeInPostFsData = config.executeInPostFsData,
                        susPaths = config.susPaths,
                        susLoopPaths = config.susLoopPaths,
                        susMaps = config.susMaps,
                        kstatConfigs = config.kstatConfigs,
                        addKstatPaths = config.addKstatPaths,
                        hideSusMountsForAllProcs = config.hideSusMountsForAllProcs,
                        enableHideBl = config.enableHideBl,
                        enableLog = config.enableLog,
                        enableCleanupResidue = config.enableCleanupResidue,
                        enableAvcLogSpoofing = config.enableAvcLogSpoofing,
                        cmdlineOrBootconfigPath = config.cmdlineOrBootconfigPath,
                        canEnableAutoStart = canEnableAutoStart
                    )
                }
            }.onFailure { e ->
                loaded = false
                _uiState.update { it.copy(error = e) }
            }
            } catch (e: CancellationException) {
                loaded = false
                throw e
            } catch (e: Exception) {
                loaded = false
                _uiState.update { it.copy(error = e) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadRuntimeInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            fun readValue(query: () -> String): String? {
                return try {
                    query().trim().takeIf {
                        it.isNotBlank() && !it.equals("unsupport", true) &&
                            !it.equals("unsupported", true) && !it.equals("unknown", true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            // Read independently: a missing optional query must not hide version/variant.
            val version = readValue(::getSuSFSVersion)
            val variant = readValue(::getSuSFSVariant)
            val implementation = when (readValue(::getSuSFSImplementation)?.lowercase()) {
                "lkm" -> "LKM"
                "builtin", "built-in" -> "Built-in"
                else -> null
            }
            _uiState.update { it.copy(runtimeInfo = SuSFSRuntimeInfo(version, variant, implementation)) }
        }
    }

    fun loadEnabledFeatures(context: Context) {
        if (_uiState.value.isLoadingFeatures) return
        _uiState.update { it.copy(isLoadingFeatures = true, error = null) }
        viewModelScope.launch {
            repo.getEnabledFeatures(context).onSuccess { features ->
                _uiState.update { it.copy(enabledFeatures = features, isLoadingFeatures = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingFeatures = false, error = e) }
            }
        }
    }

    private var loadingApps = false

    fun loadInstalledApps() {
        if (loadingApps || _uiState.value.installedApps.isNotEmpty()) return
        loadingApps = true
        _uiState.update { it.copy(isLoadingApps = true) }
        viewModelScope.launch {
            repo.getInstalledApps().onSuccess { apps ->
                _uiState.update { it.copy(installedApps = apps) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e) }
            }
            loadingApps = false
            _uiState.update { it.copy(isLoadingApps = false) }
        }
    }

    fun loadSlotInfo(context: Context) {
        if (_uiState.value.isLoadingSlotInfo) return
        _uiState.update { it.copy(isLoadingSlotInfo = true, slotInfoError = null) }
        viewModelScope.launch {
            repo.getSlotInfo(context = context).onSuccess { (list, current) ->
                _uiState.update {
                    it.copy(
                        slotInfoList = list,
                        currentActiveSlot = current,
                        isLoadingSlotInfo = false
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingSlotInfo = false, slotInfoError = e.localizedMessage) }
            }
        }
    }

    fun updateUname(value: String) {
        _uiState.update { it.copy(unameValue = value) }
    }

    fun updateBuildTime(value: String) {
        _uiState.update { it.copy(buildTimeValue = value) }
    }

    fun setExecuteInPostFsData(value: Boolean) {
        _uiState.update { it.copy(executeInPostFsData = value) }
    }

    fun toggleConfirmReset(show: Boolean) {
        _uiState.update { it.copy(showConfirmReset = show) }
    }

    fun showSlotInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showSlotInfoDialog = show) }
    }

    fun openAddPathDialog(editing: String? = null) {
        _uiState.update { it.copy(showAddPathDialog = true, editingPath = editing) }
    }

    fun closeAddPathDialog() {
        _uiState.update { it.copy(showAddPathDialog = false, editingPath = null) }
    }

    fun openAddLoopPathDialog(editing: String? = null) {
        _uiState.update { it.copy(showAddLoopPathDialog = true, editingLoopPath = editing) }
    }

    fun closeAddLoopPathDialog() {
        _uiState.update { it.copy(showAddLoopPathDialog = false, editingLoopPath = null) }
    }

    fun openAddSusMapDialog(editing: String? = null) {
        _uiState.update { it.copy(showAddSusMapDialog = true, editingSusMap = editing) }
    }

    fun closeAddSusMapDialog() {
        _uiState.update { it.copy(showAddSusMapDialog = false, editingSusMap = null) }
    }

    fun openAddAppPathDialog() {
        _uiState.update { it.copy(showAddAppPathDialog = true) }
    }

    fun closeAddAppPathDialog() {
        _uiState.update { it.copy(showAddAppPathDialog = false) }
    }

    fun openAddKstatStaticallyDialog(editing: String? = null) {
        _uiState.update {
            it.copy(
                showAddKstatStaticallyDialog = true,
                editingKstatConfig = editing
            )
        }
    }

    fun closeAddKstatStaticallyDialog() {
        _uiState.update {
            it.copy(
                showAddKstatStaticallyDialog = false,
                editingKstatConfig = null
            )
        }
    }

    fun openAddKstatDialog(editing: String? = null) {
        _uiState.update {
            it.copy(
                showAddKstatDialog = true,
                editingKstatPath = editing
            )
        }
    }

    fun closeAddKstatDialog() {
        _uiState.update {
            it.copy(
                showAddKstatDialog = false,
                editingKstatPath = null
            )
        }
    }

    fun toggleResetPathsDialog(show: Boolean) {
        _uiState.update { it.copy(showResetPathsDialog = show) }
    }

    fun toggleResetLoopPathsDialog(show: Boolean) {
        _uiState.update { it.copy(showResetLoopPathsDialog = show) }
    }

    fun toggleResetSusMapsDialog(show: Boolean) {
        _uiState.update { it.copy(showResetSusMapsDialog = show) }
    }

    fun toggleResetKstatDialog(show: Boolean) {
        _uiState.update { it.copy(showResetKstatDialog = show) }
    }

    fun applyBasicSettings(context: Context) = performOperation {
        val current = _uiState.value
        check(SuSFSManager.setUname(context, current.unameValue.trim(), current.buildTimeValue.trim()))
        SuSFSManager.saveExecuteInPostFsData(current.executeInPostFsData)
        if (SuSFSManager.isAutoStartEnabled()) check(SuSFSManager.configureAutoStart(context, true))
        refreshConfig()
    }

    fun performOperation(operation: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetAll(context: Context) = performOperation {
        check(SuSFSManager.resetToDefault(context))
        _uiState.update { it.copy(showConfirmReset = false) }
        refreshConfig()
    }

    fun configureAutoStart(context: Context, enabled: Boolean) = performOperation {
        if (enabled && !_uiState.value.canEnableAutoStart) return@performOperation
        check(SuSFSManager.configureAutoStart(context, enabled))
        _uiState.update { it.copy(autoStartEnabled = enabled) }
    }

    fun setEnableLog(context: Context, enabled: Boolean) = performOperation {
        check(SuSFSManager.setEnableLog(context, enabled))
        _uiState.update { it.copy(enableLog = enabled) }
    }

    fun setEnableHideBl(context: Context, enabled: Boolean) = performOperation {
        SuSFSManager.saveEnableHideBl(enabled)
        if (SuSFSManager.isAutoStartEnabled()) check(SuSFSManager.configureAutoStart(context, true))
        refreshConfig()
    }

    fun setEnableCleanupResidue(context: Context, enabled: Boolean) = performOperation {
        SuSFSManager.saveEnableCleanupResidue(enabled)
        if (SuSFSManager.isAutoStartEnabled()) check(SuSFSManager.configureAutoStart(context, true))
        refreshConfig()
    }

    fun setEnableAvcLogSpoofing(context: Context, enabled: Boolean) = performOperation {
        check(SuSFSManager.setEnableAvcLogSpoofing(context, enabled))
        _uiState.update { it.copy(enableAvcLogSpoofing = enabled) }
    }

    fun setHideSusMountsForAllProcs(context: Context, hideForAll: Boolean) = performOperation {
        check(SuSFSManager.setHideSusMountsForAllProcs(context, hideForAll))
        _uiState.update { it.copy(hideSusMountsForAllProcs = hideForAll) }
    }

    fun reloadConfig() {
        viewModelScope.launch {
            try {
                refreshConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e) }
            }
        }
    }

    internal suspend fun refreshConfig() {
        val config = withContext(Dispatchers.IO) { SuSFSManager.getCurrentModuleConfig() }
        val canEnable = appContext?.let { SuSFSManager.hasConfigurationForAutoStart(it) } ?: false
        _uiState.update {
            it.copy(
                unameValue = config.unameValue,
                buildTimeValue = config.buildTimeValue,
                autoStartEnabled = SuSFSManager.isAutoStartEnabled(),
                executeInPostFsData = config.executeInPostFsData,
                susPaths = config.susPaths,
                susLoopPaths = config.susLoopPaths,
                susMaps = config.susMaps,
                kstatConfigs = config.kstatConfigs,
                addKstatPaths = config.addKstatPaths,
                hideSusMountsForAllProcs = config.hideSusMountsForAllProcs,
                enableHideBl = config.enableHideBl,
                        enableLog = config.enableLog,
                enableCleanupResidue = config.enableCleanupResidue,
                enableAvcLogSpoofing = config.enableAvcLogSpoofing,
                cmdlineOrBootconfigPath = config.cmdlineOrBootconfigPath,
                canEnableAutoStart = canEnable
            )
        }
    }


    fun applyCmdlineOrBootconfig(context: Context, sourceUri: String) = performOperation {
        check(SuSFSManager.setCmdlineOrBootconfigFile(context, sourceUri))
        val path = SuSFSManager.getCmdlineOrBootconfigPath()
        _uiState.update { it.copy(cmdlineOrBootconfigPath = path, cmdlineOrBootconfigError = null) }
    }
}
