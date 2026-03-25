package com.appblocker.domain.usecase

import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.repository.MotivationalMessageRepository

class GetMotivationalMessageUseCase(private val repository: MotivationalMessageRepository) {
    suspend operator fun invoke(packageName: String): MotivationalMessage? {
        return repository.getRandomMessageForApp(packageName)
    }
}
