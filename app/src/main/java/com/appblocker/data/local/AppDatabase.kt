package com.appblocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.appblocker.data.local.dao.BlockedAppDao
import com.appblocker.data.local.dao.MotivationalMessageDao
import com.appblocker.data.local.dao.ScheduleDao
import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.BlockedAppEntity
import com.appblocker.data.local.entity.MotivationalMessageEntity
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity

@Database(
    entities = [
        BlockedAppEntity::class,
        ScheduleEntity::class,
        ScheduleDayEntity::class,
        UsageSessionEntity::class,
        UnblockEventEntity::class,
        MotivationalMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun unblockEventDao(): UnblockEventDao
    abstract fun motivationalMessageDao(): MotivationalMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_blocker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
