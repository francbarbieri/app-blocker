package com.appblocker.domain.repository

import com.appblocker.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    /**
     * Starts a new focus session. [durationMs] of null means indefinite.
     */
    suspend fun startSession(durationMs: Long?): FocusSession

    /**
     * Marks the current active session as ended (no-op if none).
     */
    suspend fun endActiveSession()

    /**
     * Reads the active session right now, or null if none.
     */
    suspend fun getActiveSession(): FocusSession?

    /**
     * Observes the latest open (not yet manually ended) session. The consumer
     * is responsible for checking [FocusSession.isActive] against the current
     * time, since a row with an expired expires_at is no longer active even
     * though it's still in the table.
     */
    fun observeLatestOpenSession(): Flow<FocusSession?>
}
