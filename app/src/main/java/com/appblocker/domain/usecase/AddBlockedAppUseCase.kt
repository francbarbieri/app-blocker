package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.repository.BlockedAppRepository

class AddBlockedAppUseCase(private val repository: BlockedAppRepository) {
    suspend operator fun invoke(packageName: String, appName: String) {
        val app = BlockedApp(
            packageName = packageName,
            appName = appName,
            isBlockingEnabled = true,
            createdAt = System.currentTimeMillis()
        )
        repository.addApp(app)
    }
}
