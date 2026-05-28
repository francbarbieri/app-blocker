package com.appblocker.domain.usecase

import com.appblocker.domain.model.Schedule
import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow

class ObserveSchedulesUseCase(
    private val blockedAppRepository: BlockedAppRepository
) {
    operator fun invoke(): Flow<List<Schedule>> =
        blockedAppRepository.getAllSchedules()
}
