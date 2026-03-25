package com.appblocker.domain.usecase

import com.appblocker.domain.repository.BlockedAppRepository

class RemoveBlockedAppUseCase(private val repository: BlockedAppRepository) {
    suspend operator fun invoke(packageName: String) {
        val app = repository.getByPackageName(packageName) ?: return
        repository.removeApp(app)
    }
}
