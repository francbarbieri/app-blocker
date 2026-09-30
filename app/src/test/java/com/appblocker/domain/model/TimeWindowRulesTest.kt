package com.appblocker.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeWindowRulesTest {

    // 2026-09-25 is a Friday (ISO 5); the 26th a Saturday (6).
    private val friday = LocalDate.of(2026, 9, 25)
    private val saturday = friday.plusDays(1)

    private fun t(value: String) = LocalTime.parse(value)

    private fun active(
        start: String,
        end: String,
        days: Set<Int> = emptySet(),
        date: LocalDate = friday,
        now: String,
    ) = TimeWindowRules.isActive(t(start), t(end), days, date, t(now))

    @Test
    fun `same-day window is active inside and inactive outside`() {
        assertTrue(active("09:00", "17:00", now = "12:30"))
        assertFalse(active("09:00", "17:00", now = "08:59"))
        assertFalse(active("09:00", "17:00", now = "17:30"))
    }

    @Test
    fun `window start is inclusive and end is exclusive`() {
        assertTrue(active("09:00", "17:00", now = "09:00"))
        assertFalse(active("09:00", "17:00", now = "17:00"))
    }

    @Test
    fun `overnight window is active late evening and early morning`() {
        assertTrue(active("22:00", "06:00", now = "23:15"))
        assertTrue(active("22:00", "06:00", now = "00:00"))
        assertTrue(active("22:00", "06:00", now = "05:59"))
        assertFalse(active("22:00", "06:00", now = "06:00"))
        assertFalse(active("22:00", "06:00", now = "12:00"))
        assertFalse(active("22:00", "06:00", now = "21:59"))
    }

    @Test
    fun `after-midnight part of an overnight window belongs to the day it started`() {
        val fridayOnly = setOf(5)
        // Saturday 01:00 is still Friday night's window.
        assertTrue(active("22:00", "06:00", days = fridayOnly, date = saturday, now = "01:00"))
        // Saturday 23:00 starts a Saturday window, which is not selected.
        assertFalse(active("22:00", "06:00", days = fridayOnly, date = saturday, now = "23:00"))
        // Friday 01:00 belongs to Thursday night, which is not selected.
        assertFalse(active("22:00", "06:00", days = fridayOnly, date = friday, now = "01:00"))
        assertTrue(active("22:00", "06:00", days = fridayOnly, date = friday, now = "22:30"))
    }

    @Test
    fun `equal start and end covers the whole selected day`() {
        assertTrue(active("00:00", "00:00", now = "00:00"))
        assertTrue(active("09:00", "09:00", now = "23:59"))
        assertTrue(active("09:00", "09:00", days = setOf(5), date = friday, now = "03:00"))
        assertFalse(active("09:00", "09:00", days = setOf(5), date = saturday, now = "03:00"))
    }

    @Test
    fun `days filter applies to same-day windows`() {
        val weekdays = setOf(1, 2, 3, 4, 5)
        assertTrue(active("09:00", "17:00", days = weekdays, date = friday, now = "10:00"))
        assertFalse(active("09:00", "17:00", days = weekdays, date = saturday, now = "10:00"))
    }

    @Test
    fun `empty days means every day`() {
        assertTrue(active("09:00", "17:00", days = emptySet(), date = saturday, now = "10:00"))
    }
}
