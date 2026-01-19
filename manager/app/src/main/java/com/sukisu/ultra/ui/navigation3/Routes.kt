package com.sukisu.ultra.ui.navigation3

import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import com.sukisu.ultra.ui.screen.FlashIt
import com.sukisu.ultra.ui.screen.RepoModuleArg
import com.sukisu.ultra.ui.viewmodel.KpmViewModel
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import com.sukisu.ultra.ui.viewmodel.TemplateViewModel

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object SuperUser : Route

    @Serializable
    data object Module : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route

    @Serializable
    data object AppProfileTemplate : Route

    @Serializable
    data class TemplateEditor(val template: @Contextual TemplateViewModel.TemplateInfo, val readOnly: Boolean) : Route

    @Serializable
    data class AppProfile(val appInfo: @Contextual SuperUserViewModel.AppInfo) : Route

    @Serializable
    data object Install : Route

    @Serializable
    data class ModuleRepoDetail(val module: @Contextual RepoModuleArg) : Route

    @Serializable
    data object ModuleRepo : Route

    @Serializable
    data class Flash(val flashIt: @Contextual FlashIt) : Route

    @Serializable
    data class ExecuteModuleAction(val moduleId: String) : Route

    @Serializable
    data class KernelFlash(val kernelUri: @Contextual Uri, val selectedSlot: String?, val kpmPatchEnabled: Boolean, val kpmUndoPatch: Boolean) : Route

    @Serializable
    data object Kpm: Route

    @Serializable
    data object Personalization: Route

    @Serializable
    data object Tool: Route

    @Serializable
    data object UmountManager: Route

    @Serializable
    data object Sulog: Route

    @Serializable
    data object SuSFS: Route
}
