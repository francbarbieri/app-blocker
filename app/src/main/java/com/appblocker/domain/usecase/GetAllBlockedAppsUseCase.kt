package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow

class GetAllBlockedAppsUseCase(private val repository: BlockedAppRepository) {
    operator fun invoke(): Flow<List<BlockedApp>> {
        return repository.getAllBlockedApps()
    }
}
