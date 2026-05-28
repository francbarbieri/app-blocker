package com.appblocker.data.repository

import com.appblocker.data.local.dao.FocusSessionDao
import com.appblocker.data.local.entity.FocusSessionEntity
import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.repository.FocusSessionRepository
import com.appblocker.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusSessionRepositoryImpl(
    private val dao: FocusSessionDao,
    private val clock: Clock,
) : FocusSessionRepository {

    override suspend fun startSession(durationMs: Long?): FocusSession {
        val now = clock.nowMs()
        val expiresAt = durationMs?.let { now + it }
        val id = dao.insert(
            FocusSessionEntity(
                startedAt = now,
                expiresAt = expiresAt,
            )
        )
        return FocusSession(id = id, startedAt = now, expiresAt = expiresAt)
    }

    override suspend fun endActiveSession() {
        val now = clock.nowMs()
        val active = dao.getActiveSession(now) ?: return
        dao.markEnded(active.id, now)
    }

    override suspend fun getActiveSession(): FocusSession? {
        val now = clock.nowMs()
        return dao.getActiveSession(now)?.toDomain()
    }

    override fun observeLatestOpenSession(): Flow<FocusSession?> =
        dao.observeLatestOpenSession().map { it?.toDomain() }

    private fun FocusSessionEntity.toDomain() = FocusSession(
        id = id,
        startedAt = startedAt,
        expiresAt = expiresAt,
        endedAt = endedAt,
    )
}
