package com.appblocker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflectionPauseTest {

    @Test
    fun `first attempts get the minimum pause`() {
        assertEquals(5, reflectionPauseSeconds(1))
        assertEquals(6, reflectionPauseSeconds(2))
    }

    @Test
    fun `pause grows with each attempt`() {
        assertEquals(9, reflectionPauseSeconds(3))
        assertEquals(15, reflectionPauseSeconds(5))
    }

    @Test
    fun `pause is capped`() {
        assertEquals(30, reflectionPauseSeconds(10))
        assertEquals(30, reflectionPauseSeconds(500))
    }
}
