package com.appblocker.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FocusSessionDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.focusSessionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun boundedSession_isActiveUntilItExpires() = runTest {
        val id = dao.insert(FocusSessionEntity(startedAt = 1_000, expiresAt = 5_000))
        assertEquals(id, dao.getActiveSession(nowEpochMs = 4_999)?.id)
        assertNull(dao.getActiveSession(nowEpochMs = 5_000))
    }

    @Test
    fun indefiniteSession_staysActiveUntilEnded() = runTest {
        val id = dao.insert(FocusSessionEntity(startedAt = 1_000, expiresAt = null))
        assertEquals(id, dao.getActiveSession(nowEpochMs = Long.MAX_VALUE / 2)?.id)

        dao.markEnded(id, nowEpochMs = 2_000)
        assertNull(dao.getActiveSession(nowEpochMs = 2_001))
    }
}
