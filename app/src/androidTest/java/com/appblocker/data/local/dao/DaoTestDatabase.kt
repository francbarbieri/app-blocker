package com.appblocker.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.local.entity.BlockedAppEntity

internal fun inMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
    InstrumentationRegistry.getInstrumentation().targetContext,
    AppDatabase::class.java,
).allowMainThreadQueries().build()

internal suspend fun AppDatabase.addApp(packageName: String, enabled: Boolean = true) {
    blockedAppDao().insert(
        BlockedAppEntity(
            packageName = packageName,
            appName = packageName,
            isBlockingEnabled = enabled,
            createdAt = 0L,
        )
    )
}
