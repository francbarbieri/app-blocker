package com.appblocker.domain.usecase

import com.appblocker.domain.model.DefaultMotivationalMessages
import com.appblocker.domain.repository.MotivationalMessageRepository
import kotlin.random.Random

class GetMotivationalMessageUseCase(
    private val repository: MotivationalMessageRepository,
    private val random: Random = Random.Default,
) {
    /**
     * A user-written message for [packageName] (or a general one) when any
     * exist, otherwise one of the built-in [DefaultMotivationalMessages].
     */
    suspend operator fun invoke(packageName: String): String =
        repository.getRandomMessageForApp(packageName)?.message?.takeIf { it.isNotBlank() }
            ?: DefaultMotivationalMessages.all.random(random)
}
