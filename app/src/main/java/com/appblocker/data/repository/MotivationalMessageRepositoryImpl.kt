package com.appblocker.data.repository

import com.appblocker.data.local.dao.MotivationalMessageDao
import com.appblocker.data.local.entity.MotivationalMessageEntity
import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.repository.MotivationalMessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MotivationalMessageRepositoryImpl(
    private val dao: MotivationalMessageDao
) : MotivationalMessageRepository {

    override suspend fun add(message: MotivationalMessage) {
        dao.insert(message.toEntity())
    }

    override suspend fun remove(message: MotivationalMessage) {
        dao.delete(message.toEntity())
    }

    override suspend fun update(message: MotivationalMessage) {
        dao.update(message.toEntity())
    }

    override fun getMessagesForApp(packageName: String): Flow<List<MotivationalMessage>> {
        return dao.getMessagesForApp(packageName).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getGeneralMessages(): Flow<List<MotivationalMessage>> {
        return dao.getGeneralMessages().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getRandomMessageForApp(packageName: String): MotivationalMessage? {
        return dao.getRandomMessageForApp(packageName)?.toDomain()
    }

    private fun MotivationalMessage.toEntity() = MotivationalMessageEntity(
        id = id,
        appPackageName = appPackageName,
        message = message,
        createdAt = createdAt
    )

    private fun MotivationalMessageEntity.toDomain() = MotivationalMessage(
        id = id,
        appPackageName = appPackageName,
        message = message,
        createdAt = createdAt
    )
}
