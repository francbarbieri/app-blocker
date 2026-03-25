package com.appblocker.domain.usecase

import com.appblocker.domain.repository.BlockedAppRepository

class ToggleBlockingUseCase(private val repository: BlockedAppRepository) {
    suspend operator fun invoke(packageName: String, enabled: Boolean) {
        repository.setBlockingEnabled(packageName, enabled)
    }
}
