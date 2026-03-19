package com.appblocker.domain.repository

import com.appblocker.domain.model.MotivationalMessage
import kotlinx.coroutines.flow.Flow

interface MotivationalMessageRepository {
    suspend fun add(message: MotivationalMessage)
    suspend fun remove(message: MotivationalMessage)
    suspend fun update(message: MotivationalMessage)
    fun getMessagesForApp(packageName: String): Flow<List<MotivationalMessage>>
    fun getGeneralMessages(): Flow<List<MotivationalMessage>>
    suspend fun getRandomMessageForApp(packageName: String): MotivationalMessage?
}
