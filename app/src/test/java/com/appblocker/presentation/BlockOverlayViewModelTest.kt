package com.appblocker.presentation

import app.cash.turbine.test
import com.appblocker.domain.model.DefaultMotivationalMessages
import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.model.TodayAppStats
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.GetTodayAppStatsUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.domain.util.Clock
import com.appblocker.presentation.fakes.FakeMotivationalMessageRepository
import com.appblocker.presentation.fakes.FakeUsageRepository
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import com.appblocker.presentation.screen.ReflectionStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BlockOverlayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val packageName = "com.example.app"
    private val appName = "Example"
    private val now = 10 * 3_600_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        message: MotivationalMessage? = null,
        usageRepo: FakeUsageRepository = FakeUsageRepository()
    ): Pair<BlockOverlayViewModel, FakeUsageRepository> {
        val motivRepo = FakeMotivationalMessageRepository(nextMessage = message)
        val clock = Clock { now }
        val vm = BlockOverlayViewModel(
            packageName = packageName,
            appName = appName,
            recordUsage = RecordUsageUseCase(usageRepo, clock),
            getMotivationalMessage = GetMotivationalMessageUseCase(motivRepo),
            getTodayAppStats = GetTodayAppStatsUseCase(usageRepo, clock, zoneProvider = { ZoneOffset.UTC }),
        )
        return vm to usageRepo
    }

    private val BlockOverlayViewModel.reflection get() = state.value as BlockOverlayUiState.Reflection

    /** Runs the whole reveal so the choices are unlocked. */
    private fun TestScope.unlock() {
        advanceTimeBy(60_000); runCurrent()
    }

    @Test
    fun `starts loading with nothing revealed`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertEquals(ReflectionStep.LOADING, vm.reflection.step)
    }

    @Test
    fun `reveals attempts, then usage, then the choice after the pause`() = runTest(dispatcher) {
        val usageRepo = FakeUsageRepository().apply { addSession(packageName, now - 120_000L, now) }
        val (vm, _) = viewModel(usageRepo = usageRepo)

        runCurrent()
        assertEquals(ReflectionStep.ATTEMPTS, vm.reflection.step)
        assertEquals(TodayAppStats(attempt = 1, usageMs = 120_000L), vm.reflection.stats)
        assertEquals(5, vm.reflection.pauseSeconds)

        advanceTimeBy(BlockOverlayViewModel.USAGE_LINE_DELAY_MS); runCurrent()
        assertEquals(ReflectionStep.USAGE, vm.reflection.step)

        advanceTimeBy(5_000 - BlockOverlayViewModel.USAGE_LINE_DELAY_MS - 1); runCurrent()
        assertEquals(ReflectionStep.USAGE, vm.reflection.step)

        advanceTimeBy(1); runCurrent()
        assertEquals(ReflectionStep.DECIDE, vm.reflection.step)
    }

    @Test
    fun `pause grows with today's attempts`() = runTest(dispatcher) {
        val usageRepo = FakeUsageRepository()
        repeat(4) {
            usageRepo.recordUnblockEvent(UnblockEvent(appPackageName = packageName, timestamp = now - 1_000L, outcome = UnblockOutcome.LEGITIMATE))
        }
        val (vm, _) = viewModel(usageRepo = usageRepo)
        runCurrent()

        assertEquals(5, vm.reflection.stats?.attempt)
        assertEquals(15, vm.reflection.pauseSeconds)

        advanceTimeBy(14_999); runCurrent()
        assertEquals(ReflectionStep.USAGE, vm.reflection.step)
        advanceTimeBy(1); runCurrent()
        assertEquals(ReflectionStep.DECIDE, vm.reflection.step)
    }

    @Test
    fun `choices are ignored until the pause is over`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.events.test {
            runCurrent()
            vm.onLegitimate()
            vm.onBreakingPlan()
            advanceTimeBy(2_000); runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(vm.state.value is BlockOverlayUiState.Reflection)
        assertTrue(usageRepo.recordedEvents.isEmpty())
    }

    @Test
    fun `onBreakingPlan transitions to Motivational with the loaded message`() = runTest(dispatcher) {
        val (vm, _) = viewModel(
            message = MotivationalMessage(
                id = 1,
                appPackageName = packageName,
                message = "Keep going",
                createdAt = 0L
            )
        )
        unlock()
        vm.onBreakingPlan()
        advanceUntilIdle()
        assertEquals(
            BlockOverlayUiState.Motivational(appName, "Keep going", TodayAppStats(attempt = 1, usageMs = 0L)),
            vm.state.value
        )
    }

    @Test
    fun `onBreakingPlan with no user message shows a built-in one`() = runTest(dispatcher) {
        val (vm, _) = viewModel(message = null)
        unlock()
        vm.onBreakingPlan()
        advanceUntilIdle()
        val state = vm.state.value as BlockOverlayUiState.Motivational
        assertTrue(state.message in DefaultMotivationalMessages.all)
    }

    @Test
    fun `back during the reveal records BACKED_OFF and goes home`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.events.test {
            runCurrent()
            vm.onDismissToHome()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GoHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(UnblockOutcome.BACKED_OFF), usageRepo.recordedEvents.map { it.outcome })
    }

    @Test
    fun `only the first decision is recorded`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        unlock()
        vm.events.test {
            vm.onLegitimate()
            vm.onLegitimate()
            vm.onDismissToHome()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GrantGraceAndClose, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(UnblockOutcome.LEGITIMATE), usageRepo.recordedEvents.map { it.outcome })
    }

    @Test
    fun `onLegitimate records LEGITIMATE and emits GrantGraceAndClose`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        unlock()
        vm.events.test {
            vm.onLegitimate()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GrantGraceAndClose, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, usageRepo.recordedEvents.size)
        assertEquals(UnblockOutcome.LEGITIMATE, usageRepo.recordedEvents[0].outcome)
        assertEquals(packageName, usageRepo.recordedEvents[0].appPackageName)
    }

    @Test
    fun `onGoBack from Motivational records BACKED_OFF and emits GoHome`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        unlock()
        vm.onBreakingPlan()
        advanceUntilIdle()
        vm.events.test {
            vm.onGoBack()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GoHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, usageRepo.recordedEvents.size)
        assertEquals(UnblockOutcome.BACKED_OFF, usageRepo.recordedEvents[0].outcome)
    }

    @Test
    fun `onProceed from Motivational records BROKE_PLAN_PROCEEDED and emits GrantGraceAndClose`() =
        runTest(dispatcher) {
            val (vm, usageRepo) = viewModel()
            unlock()
            vm.onBreakingPlan()
            advanceUntilIdle()
            vm.events.test {
                vm.onProceed()
                advanceUntilIdle()
                assertEquals(BlockOverlayEvent.GrantGraceAndClose, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, usageRepo.recordedEvents.size)
            assertEquals(UnblockOutcome.BROKE_PLAN_PROCEEDED, usageRepo.recordedEvents[0].outcome)
        }

    @Test
    fun `onProceed outside Motivational does nothing`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        unlock()
        vm.onProceed()
        advanceUntilIdle()
        assertTrue(usageRepo.recordedEvents.isEmpty())
    }

    @Test
    fun `onBackPressedFromMotivational returns to the unlocked reflection`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        unlock()
        vm.onBreakingPlan()
        advanceUntilIdle()
        assertTrue(vm.state.value is BlockOverlayUiState.Motivational)

        vm.onBackPressedFromMotivational()

        assertEquals(ReflectionStep.DECIDE, vm.reflection.step)
        assertTrue(
            "No event should be recorded on back-to-reflection",
            usageRepo.recordedEvents.isEmpty()
        )
    }
}
