package com.appblocker.domain.usecase

import com.appblocker.domain.model.Schedule
import com.appblocker.domain.repository.BlockedAppRepository

class SaveScheduleUseCase(
    private val blockedAppRepository: BlockedAppRepository
) {
    suspend operator fun invoke(schedule: Schedule) {
        if (schedule.id == 0L) {
            blockedAppRepository.addSchedule(schedule)
        } else {
            blockedAppRepository.updateSchedule(schedule)
        }
    }
}
