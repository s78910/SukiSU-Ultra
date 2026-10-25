package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.module.ModuleRepository

class HiddenModuleUseCases(private val repository: ModuleRepository) {
    suspend fun load() = repository.loadHiddenModules()
    suspend fun authenticate(password: String, confirmation: String) = repository.authenticateHiddenModules(password, confirmation)
    suspend fun save(ids: Set<String>) = repository.saveHiddenModules(ids)
}
