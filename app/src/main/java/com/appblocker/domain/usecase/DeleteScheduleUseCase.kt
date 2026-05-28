package com.appblocker.domain.usecase

import com.appblocker.domain.model.Schedule
import com.appblocker.domain.repository.BlockedAppRepository

class DeleteScheduleUseCase(
    private val blockedAppRepository: BlockedAppRepository
) {
    suspend operator fun invoke(schedule: Schedule) {
        blockedAppRepository.deleteSchedule(schedule)
    }
}
