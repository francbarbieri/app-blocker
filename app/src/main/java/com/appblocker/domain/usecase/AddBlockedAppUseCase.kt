package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.util.Clock

class AddBlockedAppUseCase(
    private val repository: BlockedAppRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(packageName: String, appName: String) {
        val app = BlockedApp(
            packageName = packageName,
            appName = appName,
            isBlockingEnabled = true,
            createdAt = clock.nowMs()
        )
        repository.addApp(app)
    }
}
