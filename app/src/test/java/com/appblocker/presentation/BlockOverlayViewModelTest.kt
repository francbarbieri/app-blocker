package com.appblocker.presentation

import app.cash.turbine.test
import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.fakes.FakeMotivationalMessageRepository
import com.appblocker.presentation.fakes.FakeUsageRepository
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class BlockOverlayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val packageName = "com.example.app"
    private val appName = "Example"

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
        val vm = BlockOverlayViewModel(
            packageName = packageName,
            appName = appName,
            recordUsage = RecordUsageUseCase(usageRepo),
            getMotivationalMessage = GetMotivationalMessageUseCase(motivRepo)
        )
        return vm to usageRepo
    }

    @Test
    fun `initial state is BreathingPause with 5 seconds remaining`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertEquals(BlockOverlayUiState.BreathingPause(appName, 5), vm.state.value)
    }

    @Test
    fun `countdown decrements secondsRemaining each second`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BlockOverlayUiState.BreathingPause(appName, 4), vm.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BlockOverlayUiState.BreathingPause(appName, 3), vm.state.value)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(BlockOverlayUiState.BreathingPause(appName, 1), vm.state.value)
    }

    @Test
    fun `countdown reaching zero transitions to Confirmation`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(BlockOverlayUiState.Confirmation(appName), vm.state.value)
    }

    @Test
    fun `onSkipBreath transitions immediately to Confirmation and stops the countdown`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel()
            vm.onSkipBreath()
            assertEquals(BlockOverlayUiState.Confirmation(appName), vm.state.value)
            advanceTimeBy(10_000)
            assertEquals(BlockOverlayUiState.Confirmation(appName), vm.state.value)
        }

    @Test
    fun `onBreakingPlan after skip transitions to Motivational with the loaded message`() =
        runTest(dispatcher) {
            val (vm, _) = viewModel(
                message = MotivationalMessage(
                    id = 1,
                    appPackageName = packageName,
                    message = "Keep going",
                    createdAt = 0L
                )
            )
            vm.onSkipBreath()
            vm.onBreakingPlan()
            advanceUntilIdle()
            assertEquals(
                BlockOverlayUiState.Motivational(appName, "Keep going"),
                vm.state.value
            )
        }

    @Test
    fun `onBreakingPlan with no message uses the fallback string`() = runTest(dispatcher) {
        val (vm, _) = viewModel(message = null)
        vm.onSkipBreath()
        vm.onBreakingPlan()
        advanceUntilIdle()
        val state = vm.state.value as BlockOverlayUiState.Motivational
        assertEquals("You've got this! Stay focused.", state.message)
    }

    @Test
    fun `onLegitimate records LEGITIMATE and emits GrantGraceAndClose`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.onSkipBreath()
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
        vm.onSkipBreath()
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
            vm.onSkipBreath()
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
    fun `onBackPressedFromMotivational returns state to Confirmation`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.onSkipBreath()
        vm.onBreakingPlan()
        advanceUntilIdle()
        assertTrue(vm.state.value is BlockOverlayUiState.Motivational)

        vm.onBackPressedFromMotivational()

        assertEquals(BlockOverlayUiState.Confirmation(appName), vm.state.value)
        assertTrue(
            "No event should be recorded on back-to-confirmation",
            usageRepo.recordedEvents.isEmpty()
        )
    }
}
