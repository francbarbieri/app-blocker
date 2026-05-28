package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveEnabledBlockedPackagesUseCaseTest {

    @Test
    fun `returns empty set when no blocked apps exist`() = runTest {
        val repo = FakeBlockedAppRepository(initial = emptyList())
        val useCase = ObserveEnabledBlockedPackagesUseCase(repo)

        assertEquals(emptySet<String>(), useCase().first())
    }

    @Test
    fun `excludes apps with isBlockingEnabled = false`() = runTest {
        val repo = FakeBlockedAppRepository(
            initial = listOf(
                blockedApp("com.example.a", enabled = true),
                blockedApp("com.example.b", enabled = false),
                blockedApp("com.example.c", enabled = true),
            )
        )
        val useCase = ObserveEnabledBlockedPackagesUseCase(repo)

        assertEquals(setOf("com.example.a", "com.example.c"), useCase().first())
    }

    @Test
    fun `re-emits when underlying flow changes`() = runTest {
        val repo = FakeBlockedAppRepository(
            initial = listOf(blockedApp("com.example.a", enabled = true))
        )
        val useCase = ObserveEnabledBlockedPackagesUseCase(repo)

        assertEquals(setOf("com.example.a"), useCase().first())

        repo.setApps(
            listOf(
                blockedApp("com.example.a", enabled = true),
                blockedApp("com.example.b", enabled = true),
            )
        )

        assertEquals(setOf("com.example.a", "com.example.b"), useCase().first())
    }

    private fun blockedApp(packageName: String, enabled: Boolean) = BlockedApp(
        packageName = packageName,
        appName = packageName,
        isBlockingEnabled = enabled,
        createdAt = 0L
    )

    private class FakeBlockedAppRepository(initial: List<BlockedApp>) : BlockedAppRepository {
        private val apps = MutableStateFlow(initial)

        fun setApps(value: List<BlockedApp>) {
            apps.value = value
        }

        override fun observeEnabledBlockedPackageNames(): Flow<Set<String>> =
            apps.map { list ->
                list.asSequence()
                    .filter { it.isBlockingEnabled }
                    .map { it.packageName }
                    .toSet()
            }

        override fun getAllBlockedApps(): Flow<List<BlockedApp>> = apps

        override suspend fun addApp(app: BlockedApp) = unsupported()
        override suspend fun removeApp(app: BlockedApp) = unsupported()
        override suspend fun getByPackageName(packageName: String) = unsupported()
        override suspend fun isBlockingEnabled(packageName: String) = unsupported()
        override suspend fun setBlockingEnabled(packageName: String, enabled: Boolean) = unsupported()
        override suspend fun addSchedule(schedule: Schedule): Long = unsupported()
        override suspend fun updateSchedule(schedule: Schedule) = unsupported()
        override suspend fun deleteSchedule(schedule: Schedule) = unsupported()
        override fun getAllSchedules(): Flow<List<Schedule>> = unsupported()
        override fun getSchedulesForApp(packageName: String): Flow<List<Schedule>> = unsupported()
        override suspend fun getActiveSchedulesForApp(packageName: String): List<Schedule> = unsupported()

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("Not used by this test")
    }
}
