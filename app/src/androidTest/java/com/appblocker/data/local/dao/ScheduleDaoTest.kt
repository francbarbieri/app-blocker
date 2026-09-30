package com.appblocker.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.scheduleDao()
        db.addApp("a")
        db.addApp("b")
        db.addApp("c")
    }

    @After
    fun tearDown() = db.close()

    private fun window(id: Long = 0, active: Boolean = true) = ScheduleEntity(
        id = id,
        scheduleType = "TIME_WINDOW",
        startTime = "09:00",
        endTime = "17:00",
        dailyLimitMinutes = null,
        isActive = active,
    )

    @Test
    fun insert_writesDaysAndAppsWithTheSchedule() = runTest {
        val id = dao.insertScheduleWithDaysAndApps(window(), listOf(1, 3), listOf("a", "b"))

        val stored = dao.getAllSchedulesWithDaysAndApps().first().single()
        assertEquals(id, stored.schedule.id)
        assertEquals(setOf(1, 3), stored.days.map { it.dayOfWeek }.toSet())
        assertEquals(setOf("a", "b"), stored.apps.map { it.appPackageName }.toSet())
    }

    @Test
    fun update_replacesDaysAndAppsInsteadOfAppending() = runTest {
        val id = dao.insertScheduleWithDaysAndApps(window(), listOf(1, 3), listOf("a", "b"))

        dao.updateScheduleWithDaysAndApps(window(id = id), listOf(5), listOf("c"))

        val stored = dao.getAllSchedulesWithDaysAndApps().first().single()
        assertEquals(listOf(5), stored.days.map { it.dayOfWeek })
        assertEquals(listOf("c"), stored.apps.map { it.appPackageName })
    }

    @Test
    fun getAllSchedulesForApp_includesPausedSchedules() = runTest {
        dao.insertScheduleWithDaysAndApps(window(active = true), emptyList(), listOf("a"))
        dao.insertScheduleWithDaysAndApps(window(active = false), emptyList(), listOf("a"))
        dao.insertScheduleWithDaysAndApps(window(active = true), emptyList(), listOf("b"))

        val forA = dao.getAllSchedulesWithDaysAndAppsForApp("a")
        assertEquals(2, forA.size)
        assertTrue(forA.any { it.schedule.isActive })
        assertTrue(forA.any { !it.schedule.isActive })
    }

    @Test
    fun deletingASchedule_cascadesToItsDaysAndApps() = runTest {
        val id = dao.insertScheduleWithDaysAndApps(window(), listOf(2), listOf("a"))
        dao.deleteSchedule(window(id = id))

        assertTrue(dao.getAllSchedulesWithDaysAndApps().first().isEmpty())
        assertFalse(dao.getAllSchedulesWithDaysAndAppsForApp("a").isNotEmpty())
    }
}
