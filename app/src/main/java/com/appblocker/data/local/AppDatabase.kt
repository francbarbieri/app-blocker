package com.appblocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS unblock_events")
                db.execSQL(
                    """
                    CREATE TABLE unblock_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        app_package_name TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        FOREIGN KEY(app_package_name) REFERENCES blocked_apps(package_name) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_unblock_events_app_package_name ON unblock_events(app_package_name)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_blocker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
