package com.appblocker

import android.content.Context
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.BlockedAppRepositoryImpl
import com.appblocker.data.repository.FocusSessionRepositoryImpl
import com.appblocker.data.repository.MotivationalMessageRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.repository.FocusSessionRepository
import com.appblocker.domain.repository.MotivationalMessageRepository
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.usecase.AddBlockedAppUseCase
import com.appblocker.domain.usecase.DeleteScheduleUseCase
import com.appblocker.domain.usecase.EndFocusSessionUseCase
import com.appblocker.domain.usecase.GetAllBlockedAppsUseCase
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.IsAppBlockedUseCase
import com.appblocker.domain.usecase.IsAppOnActiveScheduleUseCase
import com.appblocker.domain.usecase.IsFocusSessionActiveUseCase
import com.appblocker.domain.usecase.ObserveEnabledBlockedPackagesUseCase
import com.appblocker.domain.usecase.ObserveLatestFocusSessionUseCase
import com.appblocker.domain.usecase.ObserveSchedulesUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.domain.usecase.RemoveBlockedAppUseCase
import com.appblocker.domain.usecase.SaveScheduleUseCase
import com.appblocker.domain.usecase.StartFocusSessionUseCase
import com.appblocker.domain.usecase.ToggleBlockingUseCase
import com.appblocker.domain.util.Clock

/**
 * Single composition root. ViewModels, Activities, and the AccessibilityService
 * fetch use cases from here via [Context.appContainer]; nothing outside this
 * file should import classes from [com.appblocker.data].
 */
class AppContainer(context: Context) {

    val clock: Clock = Clock.System

    private val database = AppDatabase.getInstance(context)

    private val blockedAppRepository: BlockedAppRepository = BlockedAppRepositoryImpl(
        database.blockedAppDao(),
        database.scheduleDao(),
    )
    private val usageRepository: UsageRepository = UsageRepositoryImpl(
        database.usageSessionDao(),
        database.unblockEventDao(),
    )
    private val focusSessionRepository: FocusSessionRepository = FocusSessionRepositoryImpl(
        database.focusSessionDao(),
        clock,
    )
    private val motivationalMessageRepository: MotivationalMessageRepository =
        MotivationalMessageRepositoryImpl(database.motivationalMessageDao())

    val isFocusSessionActiveUseCase = IsFocusSessionActiveUseCase(focusSessionRepository)
    val isAppOnActiveScheduleUseCase = IsAppOnActiveScheduleUseCase(
        blockedAppRepository,
        usageRepository,
        clock,
    )
    val isAppBlockedUseCase = IsAppBlockedUseCase(
        blockedAppRepository,
        isFocusSessionActiveUseCase,
        isAppOnActiveScheduleUseCase,
    )

    val getAllBlockedAppsUseCase = GetAllBlockedAppsUseCase(blockedAppRepository)
    val observeEnabledBlockedPackagesUseCase =
        ObserveEnabledBlockedPackagesUseCase(blockedAppRepository)
    val addBlockedAppUseCase = AddBlockedAppUseCase(blockedAppRepository, clock)
    val removeBlockedAppUseCase = RemoveBlockedAppUseCase(blockedAppRepository)
    val toggleBlockingUseCase = ToggleBlockingUseCase(blockedAppRepository)

    val recordUsageUseCase = RecordUsageUseCase(usageRepository, clock)
    val getMotivationalMessageUseCase = GetMotivationalMessageUseCase(motivationalMessageRepository)

    val observeLatestFocusSessionUseCase =
        ObserveLatestFocusSessionUseCase(focusSessionRepository)
    val startFocusSessionUseCase = StartFocusSessionUseCase(focusSessionRepository)
    val endFocusSessionUseCase = EndFocusSessionUseCase(focusSessionRepository)

    val observeSchedulesUseCase = ObserveSchedulesUseCase(blockedAppRepository)
    val saveScheduleUseCase = SaveScheduleUseCase(blockedAppRepository)
    val deleteScheduleUseCase = DeleteScheduleUseCase(blockedAppRepository)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AppBlockerApplication).container
