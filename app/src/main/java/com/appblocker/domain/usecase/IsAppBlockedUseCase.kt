package com.appblocker.domain.usecase

import com.appblocker.domain.repository.BlockedAppRepository

class IsAppBlockedUseCase(
    private val blockedAppRepository: BlockedAppRepository,
    private val isFocusSessionActive: IsFocusSessionActiveUseCase,
    private val isAppOnActiveSchedule: IsAppOnActiveScheduleUseCase,
) {
    suspend operator fun invoke(packageName: String): Boolean {
        if (!blockedAppRepository.isBlockingEnabled(packageName)) return false
        if (isFocusSessionActive()) return true
        return isAppOnActiveSchedule(packageName)
    }
}
