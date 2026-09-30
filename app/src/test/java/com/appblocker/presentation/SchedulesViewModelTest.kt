package com.appblocker.presentation

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.usecase.DeleteScheduleUseCase
import com.appblocker.domain.usecase.GetAllBlockedAppsUseCase
import com.appblocker.domain.usecase.ObserveSchedulesUseCase
import com.appblocker.domain.usecase.SaveScheduleUseCase
import com.appblocker.presentation.fakes.FakeBlockedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app = BlockedApp("com.example.social", "Social", isBlockingEnabled = true, createdAt = 0L)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeBlockedAppRepository) = SchedulesViewModel(
        observeSchedules = ObserveSchedulesUseCase(repo),
        getAllBlockedApps = GetAllBlockedAppsUseCase(repo),
        saveSchedule = SaveScheduleUseCase(repo),
        deleteSchedule = DeleteScheduleUseCase(repo),
    )

    @Test
    fun `loads schedules and apps`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(app), vm.state.value.blockedApps)
    }

    @Test
    fun `saving a daily limit stores the limit and no times`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        val vm = viewModel(repo)
        vm.startCreating()
        vm.updateType(ScheduleType.DAILY_LIMIT)
        vm.updateDailyLimit(45)
        vm.toggleDay(6)
        vm.toggleApp(app.packageName)
        vm.saveEditor()
        advanceUntilIdle()

        val saved = repo.schedules.value.single()
        assertEquals(ScheduleType.DAILY_LIMIT, saved.scheduleType)
        assertEquals(45, saved.dailyLimitMinutes)
        assertNull(saved.startTime)
        assertNull(saved.endTime)
        assertEquals(listOf(6), saved.days)
        assertEquals(listOf(app.packageName), saved.appPackageNames)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `saving a time window stores times and no limit`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        val vm = viewModel(repo)
        vm.startCreating()
        vm.updateStartTime("22:00")
        vm.updateEndTime("06:00")
        vm.toggleApp(app.packageName)
        vm.saveEditor()
        advanceUntilIdle()

        val saved = repo.schedules.value.single()
        assertEquals(ScheduleType.TIME_WINDOW, saved.scheduleType)
        assertEquals("22:00", saved.startTime)
        assertEquals("06:00", saved.endTime)
        assertNull(saved.dailyLimitMinutes)
    }

    @Test
    fun `cannot save without apps`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        val vm = viewModel(repo)
        vm.startCreating()
        assertFalse(vm.state.value.editor!!.canSave)
        vm.saveEditor()
        advanceUntilIdle()
        assertTrue(repo.schedules.value.isEmpty())
        assertTrue("editor stays open", vm.state.value.editor != null)
    }

    @Test
    fun `daily limit is clamped to the allowed range`() = runTest(dispatcher) {
        val vm = viewModel(FakeBlockedAppRepository())
        vm.startCreating()
        vm.updateDailyLimit(0)
        assertEquals(EditorState.DAILY_LIMIT_RANGE.first, vm.state.value.editor!!.dailyLimitMinutes)
        vm.updateDailyLimit(10_000)
        assertEquals(EditorState.DAILY_LIMIT_RANGE.last, vm.state.value.editor!!.dailyLimitMinutes)
    }

    @Test
    fun `switching type keeps the other type's input`() = runTest(dispatcher) {
        val vm = viewModel(FakeBlockedAppRepository())
        vm.startCreating()
        vm.updateStartTime("07:30")
        vm.updateType(ScheduleType.DAILY_LIMIT)
        vm.updateDailyLimit(90)
        vm.updateType(ScheduleType.TIME_WINDOW)
        val editor = vm.state.value.editor!!
        assertEquals("07:30", editor.startTime)
        assertEquals(90, editor.dailyLimitMinutes)
    }

    @Test
    fun `editing keeps id and paused state`() = runTest(dispatcher) {
        val existing = Schedule(
            id = 0,
            appPackageNames = listOf(app.packageName),
            scheduleType = ScheduleType.DAILY_LIMIT,
            dailyLimitMinutes = 20,
            isActive = false,
        )
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        repo.addSchedule(existing)
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.startEditing(repo.schedules.value.single())
        vm.updateDailyLimit(25)
        vm.saveEditor()
        advanceUntilIdle()

        val saved = repo.schedules.value.single()
        assertEquals(1L, saved.id)
        assertEquals(25, saved.dailyLimitMinutes)
        assertFalse("editing must not silently resume a paused schedule", saved.isActive)
    }

    @Test
    fun `setScheduleActive pauses and resumes`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        repo.addSchedule(
            Schedule(appPackageNames = listOf(app.packageName), scheduleType = ScheduleType.TIME_WINDOW,
                startTime = "09:00", endTime = "17:00"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.setScheduleActive(repo.schedules.value.single(), false)
        advanceUntilIdle()
        assertFalse(repo.schedules.value.single().isActive)

        vm.setScheduleActive(repo.schedules.value.single(), true)
        advanceUntilIdle()
        assertTrue(repo.schedules.value.single().isActive)
    }

    @Test
    fun `deleteEditing removes the schedule and closes the editor`() = runTest(dispatcher) {
        val repo = FakeBlockedAppRepository(apps = listOf(app))
        repo.addSchedule(
            Schedule(appPackageNames = listOf(app.packageName), scheduleType = ScheduleType.DAILY_LIMIT,
                dailyLimitMinutes = 30),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.startEditing(repo.schedules.value.single())
        vm.deleteEditing()
        advanceUntilIdle()

        assertTrue(repo.schedules.value.isEmpty())
        assertNull(vm.state.value.editor)
    }
}
