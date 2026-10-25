package org.bakasu.bakasu.ui.viewmodel

import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.HiddenModuleUseCases
import org.bakasu.bakasu.ui.screen.main.HiddenModuleAction
import org.bakasu.bakasu.ui.screen.main.HiddenModuleControls
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bakasu.bakasu.data.module.ModulePreferencesRepository
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.domain.model.MetaModuleStatus
import org.bakasu.bakasu.domain.model.ModuleUpdateVerification
import org.bakasu.bakasu.domain.usecase.CalculateInstalledModuleSizeUseCase
import org.bakasu.bakasu.domain.usecase.GetBooleanPreferenceUseCase
import org.bakasu.bakasu.domain.usecase.GetModuleUpdateChangelogUseCase
import org.bakasu.bakasu.domain.usecase.IsSoftRebootPreferredUseCase
import org.bakasu.bakasu.domain.usecase.ObserveInstalledModulesUseCase
import org.bakasu.bakasu.domain.usecase.RebootUseCase
import org.bakasu.bakasu.domain.usecase.RefreshInstalledModulesUseCase
import org.bakasu.bakasu.domain.usecase.ResolveModuleUpdateUseCase
import org.bakasu.bakasu.domain.usecase.SetModuleEnabledUseCase
import org.bakasu.bakasu.domain.usecase.SetModuleRemovedUseCase
import org.bakasu.bakasu.domain.usecase.TransliterateTextUseCase
import org.bakasu.bakasu.domain.usecase.UpdateCachedModuleEnabledUseCase

data class ModuleUiState(
    val hidden: HiddenModuleControls = HiddenModuleControls(),
    val moduleList: List<InstalledModule> = emptyList(),
    val moduleSizes: Map<String, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val search: String = "",
    val sortEnabledFirst: Boolean = false,
    val sortActionFirst: Boolean = false,
    val hasModuleRequireMount: Boolean = false,
    val hasMagisk: Boolean = false,
    val metaModuleStatus: MetaModuleStatus = MetaModuleStatus.MISSING,
    val isNeedRefresh: Boolean = false,
    val showMoreModuleInfo: Boolean = false,
)

sealed interface ModuleUiAction {
    data class Refresh(val manual: Boolean = false) : ModuleUiAction
    data object ReloadSettings : ModuleUiAction
    data class Search(val query: String) : ModuleUiAction
    data class Sort(val enabledFirst: Boolean, val actionFirst: Boolean) : ModuleUiAction
    data class SetShowMoreInfo(val enabled: Boolean) : ModuleUiAction
    data class LoadSize(val moduleId: String) : ModuleUiAction
    data object MarkNeedRefresh : ModuleUiAction
    data class UpdateCachedEnabled(val moduleId: String, val enabled: Boolean) : ModuleUiAction
    data class SetEnabled(val moduleId: String, val enabled: Boolean) : ModuleUiAction
    data class SetRemoved(val moduleId: String, val removed: Boolean) : ModuleUiAction
    data object Reboot : ModuleUiAction
}

sealed interface ModuleUiEvent {
    data class Error(val message: String) : ModuleUiEvent
    data object RefreshCompleted : ModuleUiEvent
    data class EnabledChanged(
        val moduleId: String,
        val enabled: Boolean,
        val successful: Boolean,
    ) : ModuleUiEvent

    data class RemovedChanged(
        val moduleId: String,
        val removed: Boolean,
        val successful: Boolean,
    ) : ModuleUiEvent
}

private data class ModuleControls(
    val search: String = "",
    val isNeedRefresh: Boolean = false,
    val moduleSizes: Map<String, String> = emptyMap(),
)

class ModuleViewModel(
    private val hiddenModules: HiddenModuleUseCases,
    private val observeInstalledModules: ObserveInstalledModulesUseCase,
    private val modulePreferences: ModulePreferencesRepository,
    private val refreshInstalledModules: RefreshInstalledModulesUseCase,
    private val calculateModuleSize: CalculateInstalledModuleSizeUseCase,
    private val updateCachedModuleEnabledUseCase: UpdateCachedModuleEnabledUseCase,
    private val getBooleanPreference: GetBooleanPreferenceUseCase,
    private val transliterateText: TransliterateTextUseCase,
    private val setModuleEnabled: SetModuleEnabledUseCase,
    private val setModuleRemoved: SetModuleRemovedUseCase,
    private val reboot: RebootUseCase,
    private val isSoftRebootPreferred: IsSoftRebootPreferredUseCase,
    private val resolveModuleUpdate: ResolveModuleUpdateUseCase,
    private val getModuleUpdateChangelog: GetModuleUpdateChangelogUseCase,
) : ViewModel() {
    private val hiddenControls = MutableStateFlow(HiddenModuleControls())
    private val hiddenMutex = Mutex()
    init {
        viewModelScope.launch { reloadHiddenModules() }
    }

    private suspend fun reloadHiddenModules() {
        hiddenMutex.withLock {
            val snapshot = hiddenModules.load()
            hiddenControls.update {
                it.copy(
                    hiddenIds = snapshot.hiddenModuleIds,
                    failed = snapshot.shouldHideAllModules,
                    hasPassword = snapshot.hasPassword,
                    management = it.management && !snapshot.shouldHideAllModules,
                    selected = emptySet(),
                )
            }
        }
    }

    fun dispatchHidden(action: HiddenModuleAction) {
        viewModelScope.launch {
            hiddenMutex.withLock {
                val current = hiddenControls.value
                when (action) {
                    HiddenModuleAction.ToggleManagement -> hiddenControls.value =
                        if (current.management) current.copy(management = false, selected = emptySet(), error = null)
                        else current.copy(requestPassword = !current.requestPassword, error = null)
                    is HiddenModuleAction.Authenticate -> {
                        hiddenControls.value = current.copy(busy = true)
                        val success = hiddenModules.authenticate(action.password, action.confirmation)
                        val snapshot = hiddenModules.load()
                        hiddenControls.value = current.copy(
                            hiddenIds = snapshot.hiddenModuleIds,
                            failed = snapshot.shouldHideAllModules,
                            hasPassword = snapshot.hasPassword,
                            management = success && !snapshot.shouldHideAllModules,
                            requestPassword = !success,
                            selected = emptySet(),
                            busy = false,
                            error = if (success) null else R.string.module_hidden_password_invalid,
                        )
                    }
                    is HiddenModuleAction.Select -> if (current.management && !current.failed) {
                        hiddenControls.value = current.copy(selected = current.selected.let {
                            if (action.id in it) it - action.id else it + action.id
                        })
                    }
                    HiddenModuleAction.ClearSelection -> hiddenControls.value = current.copy(selected = emptySet())
                    HiddenModuleAction.ToggleSelectedVisibility -> if (current.management && !current.failed && current.selected.isNotEmpty()) {
                        hiddenControls.value = current.copy(busy = true)
                        val updated = current.hiddenIds.toMutableSet().apply {
                            current.selected.forEach { if (!remove(it)) add(it) }
                        }
                        val snapshot = hiddenModules.save(updated)
                        val success = !snapshot.shouldHideAllModules && snapshot.hiddenModuleIds == updated
                        hiddenControls.value = current.copy(
                            hiddenIds = snapshot.hiddenModuleIds,
                            failed = !success,
                            management = success,
                            selected = emptySet(),
                            busy = false,
                            error = if (success) null else R.string.module_hidden_storage_failed,
                        )
                    }
                }
            }
        }
    }

    private val controls = MutableStateFlow(ModuleControls())
    private val mutableEvents = MutableSharedFlow<ModuleUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ModuleUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<ModuleUiState> = combine(
        observeInstalledModules(),
        controls,
        modulePreferences.preferences,
        hiddenControls,
    ) { source, local, preferences, hidden ->
        ModuleUiState(
            hidden = hidden,
            moduleList = buildModuleList(
                modules = source.modules.filter { hidden.isVisible(it.id) },
                search = local.search,
                sortEnabledFirst = preferences.sortEnabledFirst,
                sortActionFirst = preferences.sortActionFirst,
            ),
            moduleSizes = local.moduleSizes,
            isRefreshing = source.refreshing,
            search = local.search,
            sortEnabledFirst = preferences.sortEnabledFirst,
            sortActionFirst = preferences.sortActionFirst,
            hasModuleRequireMount = source.hasModuleRequireMount,
            hasMagisk = source.hasMagisk,
            metaModuleStatus = source.metaModuleStatus,
            isNeedRefresh = local.isNeedRefresh,
            showMoreModuleInfo = preferences.showMoreModuleInfo,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = state

    suspend fun resolveUpdate(module: InstalledModule): ModuleUpdateVerification {
        val update = module.moduleUpdate ?: return ModuleUpdateVerification.Rejected
        return resolveModuleUpdate(module.id, module.updateJson, update.zipUrl, update.versionCode)
    }

    suspend fun loadUpdateChangelog(url: String, configuredSource: String) = getModuleUpdateChangelog(url, configuredSource)

    fun isUpdateCurrent(module: InstalledModule, update: ModuleUpdateVerification.Registry): Boolean = resolveModuleUpdate.isCurrent(update) && observeInstalledModules().value.modules.any {
        it.id == module.id && !it.remove && it.versionCode == module.versionCode &&
            it.updateJson == module.updateJson && it.moduleUpdate == module.moduleUpdate
    }

    fun dispatch(action: ModuleUiAction) {
        when (action) {
            is ModuleUiAction.Refresh -> refresh(action.manual)

            ModuleUiAction.ReloadSettings -> modulePreferences.reload()

            is ModuleUiAction.Search -> controls.update { it.copy(search = action.query) }

            is ModuleUiAction.Sort -> {
                modulePreferences.setSort(action.enabledFirst, action.actionFirst)
            }

            is ModuleUiAction.SetShowMoreInfo -> {
                modulePreferences.setShowMoreInfo(action.enabled)
            }

            is ModuleUiAction.LoadSize -> viewModelScope.launch {
                val size = formatFileSize(calculateModuleSize(action.moduleId))
                controls.update { current ->
                    current.copy(moduleSizes = current.moduleSizes + (action.moduleId to size))
                }
            }

            ModuleUiAction.MarkNeedRefresh -> controls.update { it.copy(isNeedRefresh = true) }

            is ModuleUiAction.UpdateCachedEnabled ->
                updateCachedModuleEnabledUseCase(action.moduleId, action.enabled)

            is ModuleUiAction.SetEnabled -> viewModelScope.launch {
                val successful = setModuleEnabled(action.moduleId, action.enabled).isSuccess
                mutableEvents.emit(
                    ModuleUiEvent.EnabledChanged(action.moduleId, action.enabled, successful),
                )
            }

            is ModuleUiAction.SetRemoved -> viewModelScope.launch {
                val successful = setModuleRemoved(action.moduleId, action.removed).isSuccess
                mutableEvents.emit(
                    ModuleUiEvent.RemovedChanged(action.moduleId, action.removed, successful),
                )
            }

            ModuleUiAction.Reboot -> viewModelScope.launch {
                val reason = if (isSoftRebootPreferred()) "soft_reboot" else ""
                reboot(reason).onFailure { mutableEvents.tryEmit(ModuleUiEvent.Error(it.message.orEmpty())) }
            }
        }
    }
    private fun refresh(manual: Boolean) {
        viewModelScope.launch { refreshNow(manual) }
    }

    private suspend fun refreshNow(manual: Boolean) {
        val checkUpdates = getBooleanPreference(
            PREF_CHECK_MODULE_UPDATE,
            getBooleanPreference(PREF_CHECK_UPDATE, true),
        )
        reloadHiddenModules()
        refreshInstalledModules(manual, checkUpdates)
            .onSuccess {
                controls.update { it.copy(isNeedRefresh = false) }
                mutableEvents.tryEmit(ModuleUiEvent.RefreshCompleted)
            }
            .onFailure { error ->
                mutableEvents.tryEmit(ModuleUiEvent.Error(error.message.orEmpty()))
            }
    }

    private fun buildModuleList(
        modules: List<InstalledModule>,
        search: String,
        sortEnabledFirst: Boolean,
        sortActionFirst: Boolean,
    ): List<InstalledModule> {
        val comparator = compareBy<InstalledModule>(
            {
                val executable = it.hasWebUi || it.hasActionScript
                when {
                    it.metamodule && it.enabled -> 0

                    sortEnabledFirst && sortActionFirst -> when {
                        it.enabled && executable -> 1
                        it.enabled -> 2
                        executable -> 3
                        else -> 4
                    }

                    sortEnabledFirst -> if (it.enabled) 1 else 2

                    sortActionFirst -> if (executable) 1 else 2

                    else -> 1
                }
            },
            { if (sortEnabledFirst) !it.enabled else false },
            { if (sortActionFirst) !(it.hasWebUi || it.hasActionScript) else false },
        ).thenBy(Collator.getInstance(Locale.getDefault()), InstalledModule::id)

        return modules.filter { module ->
            module.id.contains(search, ignoreCase = true) ||
                module.name.contains(search, ignoreCase = true) ||
                transliterateText(module.name).contains(search, ignoreCase = true)
        }.sortedWith(comparator)
    }

    private companion object {
        const val PREF_CHECK_MODULE_UPDATE = "check_module_update"
        const val PREF_CHECK_UPDATE = "check_update"
    }
}

fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024
    return when {
        bytes >= tb -> "%.2f TB".format(bytes / tb)
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.2f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
