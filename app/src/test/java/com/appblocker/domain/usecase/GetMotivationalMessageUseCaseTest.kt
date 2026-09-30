package com.appblocker.domain.usecase

import com.appblocker.domain.model.DefaultMotivationalMessages
import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.presentation.fakes.FakeMotivationalMessageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GetMotivationalMessageUseCaseTest {

    @Test
    fun `user-written message wins over the built-in ones`() = runTest {
        val repo = FakeMotivationalMessageRepository(
            nextMessage = MotivationalMessage(message = "Read a chapter instead", createdAt = 0L),
        )
        assertEquals("Read a chapter instead", GetMotivationalMessageUseCase(repo)("pkg"))
    }

    @Test
    fun `falls back to a built-in message when the user has none`() = runTest {
        val message = GetMotivationalMessageUseCase(FakeMotivationalMessageRepository(), Random(7))("pkg")
        assertTrue(message in DefaultMotivationalMessages.all)
    }

    @Test
    fun `blank user message is treated as missing`() = runTest {
        val repo = FakeMotivationalMessageRepository(
            nextMessage = MotivationalMessage(message = "   ", createdAt = 0L),
        )
        assertTrue(GetMotivationalMessageUseCase(repo)("pkg") in DefaultMotivationalMessages.all)
    }

    @Test
    fun `built-in messages rotate`() = runTest {
        val useCase = GetMotivationalMessageUseCase(FakeMotivationalMessageRepository(), Random(42))
        val seen = (1..50).map { useCase("pkg") }.toSet()
        assertTrue("expected several distinct messages, got ${seen.size}", seen.size > 3)
    }
}
