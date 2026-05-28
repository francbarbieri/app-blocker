package com.appblocker.domain.usecase

import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow

class ObserveEnabledBlockedPackagesUseCase(
    private val blockedAppRepository: BlockedAppRepository
) {
    operator fun invoke(): Flow<Set<String>> =
        blockedAppRepository.observeEnabledBlockedPackageNames()
}
