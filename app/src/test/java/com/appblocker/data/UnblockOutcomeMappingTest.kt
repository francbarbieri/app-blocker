package com.appblocker.data

import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnblockOutcomeMappingTest {

    @Test
    fun `each outcome round-trips through entity and back`() = runTest {
        UnblockOutcome.values().forEach { outcome ->
            val dao = InMemoryUnblockEventDao()
            val repo = UsageRepositoryImpl(NoopUsageSessionDao(), dao)

            repo.recordUnblockEvent(
                UnblockEvent(
                    appPackageName = "com.example.app",
                    timestamp = 1_700_000_000_000L,
                    outcome = outcome
                )
            )

            val stored = repo.getUnblockEventsForApp("com.example.app").first()
            assertEquals(1, stored.size)
            assertEquals(outcome, stored[0].outcome)
        }
    }

    @Test
    fun `entity stores outcome as enum name string`() = runTest {
        val dao = InMemoryUnblockEventDao()
        val repo = UsageRepositoryImpl(NoopUsageSessionDao(), dao)

        repo.recordUnblockEvent(
            UnblockEvent(
                appPackageName = "com.example.app",
                timestamp = 1L,
                outcome = UnblockOutcome.LEGITIMATE
            )
        )

        assertEquals("LEGITIMATE", dao.events.value.first().outcome)
    }
}

private class InMemoryUnblockEventDao : UnblockEventDao {
    val events = MutableStateFlow<List<UnblockEventEntity>>(emptyList())

    override suspend fun insert(event: UnblockEventEntity) {
        events.value = events.value + event.copy(id = events.value.size + 1L)
    }

    override fun getEventsForApp(packageName: String): Flow<List<UnblockEventEntity>> =
        events.map { list -> list.filter { it.appPackageName == packageName } }

    override suspend fun getEventCountSince(packageName: String, sinceEpochMs: Long): Int =
        events.value.count { it.appPackageName == packageName && it.timestamp >= sinceEpochMs }
}

private class NoopUsageSessionDao : UsageSessionDao {
    override suspend fun insert(session: UsageSessionEntity): Long = 0
    override suspend fun updateEndTime(sessionId: Long, endTime: Long) = Unit
    override fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long):
        Flow<List<UsageSessionEntity>> = MutableStateFlow(emptyList())
    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long = 0L
    override suspend fun getTotalDurationForRange(packageName: String, startEpochMs: Long, endEpochMs: Long): Long = 0L
    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int = 0
}
