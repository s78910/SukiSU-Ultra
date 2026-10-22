package org.bakasu.bakasu.ui.screen.main

sealed interface HiddenModuleAction {
    data object ToggleManagement : HiddenModuleAction
    data class Authenticate(val password: String, val confirmation: String) : HiddenModuleAction
    data class Select(val id: String) : HiddenModuleAction
    data object ToggleSelectedVisibility : HiddenModuleAction
    data object ClearSelection : HiddenModuleAction
}

data class HiddenModuleControls(
    val hiddenIds: Set<String> = emptySet(),
    val failed: Boolean = true,
    val hasPassword: Boolean = true,
    val management: Boolean = false,
    val requestPassword: Boolean = false,
    val selected: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: Int? = null,
) {
    fun isVisible(id: String): Boolean = !failed && (management || id !in hiddenIds)
}
