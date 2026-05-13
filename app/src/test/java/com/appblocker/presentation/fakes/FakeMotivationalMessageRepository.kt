package com.appblocker.presentation.fakes

import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.repository.MotivationalMessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMotivationalMessageRepository(
    var nextMessage: MotivationalMessage? = null
) : MotivationalMessageRepository {

    override suspend fun add(message: MotivationalMessage) = Unit
    override suspend fun remove(message: MotivationalMessage) = Unit
    override suspend fun update(message: MotivationalMessage) = Unit
    override fun getMessagesForApp(packageName: String): Flow<List<MotivationalMessage>> =
        MutableStateFlow(emptyList())
    override fun getGeneralMessages(): Flow<List<MotivationalMessage>> =
        MutableStateFlow(emptyList())
    override suspend fun getRandomMessageForApp(packageName: String): MotivationalMessage? =
        nextMessage
}
