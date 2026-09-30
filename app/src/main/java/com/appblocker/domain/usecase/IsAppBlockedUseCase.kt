package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.repository.BlockedAppRepository

/**
 * Decides whether [invoke]'s package should be blocked right now and why.
 * Returns null when the app may be used.
 *
 * Precedence: a disabled app is never blocked; a focus session blocks every
 * enabled app; otherwise the app's schedules decide.
 */
class IsAppBlockedUseCase(
    private val blockedAppRepository: BlockedAppRepository,
    private val isFocusSessionActive: IsFocusSessionActiveUseCase,
    private val isAppOnActiveSchedule: IsAppOnActiveScheduleUseCase,
) {
    suspend operator fun invoke(packageName: String): BlockReason? {
        if (!blockedAppRepository.isBlockingEnabled(packageName)) return null
        if (isFocusSessionActive()) return BlockReason.FocusSession
        return isAppOnActiveSchedule(packageName)
    }
}
