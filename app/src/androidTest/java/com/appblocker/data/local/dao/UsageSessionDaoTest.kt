package com.appblocker.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.local.entity.UsageSessionEntity
import com.appblocker.data.repository.UsageRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UsageSessionDao

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.usageSessionDao()
        db.addApp("a")
        db.addApp("b")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(pkg: String, start: Long, end: Long?) = dao.insert(
        UsageSessionEntity(appPackageName = pkg, startTime = start, endTime = end, durationMs = end?.minus(start))
    )

    @Test
    fun getUsageMs_clipsSessionsToTheRange() = runTest {
        session("a", start = 50, end = 150)   // straddles the start: 50 counted
        session("a", start = 200, end = 300)  // fully inside: 100
        session("a", start = 950, end = 1_100) // straddles the end: 50
        session("a", start = 0, end = 90)     // entirely before: 0
        session("b", start = 200, end = 300)  // other app: ignored

        assertEquals(200L, dao.getUsageMs(listOf("a"), fromMs = 100, toMs = 1_000))
    }

    @Test
    fun getUsageMs_addsUpSeveralApps() = runTest {
        session("a", start = 0, end = 100)
        session("b", start = 50, end = 250)
        assertEquals(300L, dao.getUsageMs(listOf("a", "b"), fromMs = 0, toMs = 1_000))
    }

    @Test
    fun getUsageMs_ignoresOpenSessionsAndEmptyRanges() = runTest {
        session("a", start = 100, end = null)
        assertEquals(0L, dao.getUsageMs(listOf("a"), fromMs = 0, toMs = 1_000))
        assertEquals(0L, dao.getUsageMs(listOf("b"), fromMs = 0, toMs = 1_000))
    }

    @Test
    fun repositoryHeartbeat_extendsTheSessionThatIsCounted() = runTest {
        val repo = UsageRepositoryImpl(dao, db.unblockEventDao())
        val id = repo.startSession("a", startTime = 1_000)
        assertEquals("a fresh session counts as zero", 0L, repo.getUsageMs(listOf("a"), 0, 10_000))

        repo.updateSessionEnd(id, endTime = 4_000)
        assertEquals(3_000L, repo.getUsageMs(listOf("a"), 0, 10_000))

        repo.updateSessionEnd(id, endTime = 6_000)
        assertEquals(5_000L, repo.getUsageMs(listOf("a"), 0, 10_000))
        assertEquals(5_000L, dao.observeSessionsOverlapping(0, 10_000).first().single().durationMs)
    }

    @Test
    fun observeSessionsOverlapping_returnsOnlyOverlappingSessions() = runTest {
        session("a", start = 0, end = 100)     // ends exactly at range start: excluded
        session("a", start = 50, end = 150)    // overlaps
        session("b", start = 900, end = 1_200) // overlaps
        session("b", start = 1_000, end = 1_100) // starts at range end: excluded

        val overlapping = dao.observeSessionsOverlapping(fromMs = 100, toMs = 1_000).first()
        assertEquals(listOf(50L, 900L), overlapping.map { it.startTime })
    }

    @Test
    fun deletingAnApp_cascadesToItsSessions() = runTest {
        session("a", start = 0, end = 100)
        db.blockedAppDao().delete(db.blockedAppDao().getByPackageName("a")!!)
        assertEquals(0L, dao.getUsageMs(listOf("a"), 0, 1_000))
    }
}
