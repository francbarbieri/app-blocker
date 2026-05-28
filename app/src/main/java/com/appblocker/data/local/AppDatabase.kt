package com.appblocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.appblocker.data.local.dao.BlockedAppDao
import com.appblocker.data.local.dao.FocusSessionDao
import com.appblocker.data.local.dao.MotivationalMessageDao
import com.appblocker.data.local.dao.ScheduleDao
import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.BlockedAppEntity
import com.appblocker.data.local.entity.FocusSessionEntity
import com.appblocker.data.local.entity.MotivationalMessageEntity
import com.appblocker.data.local.entity.ScheduleAppEntity
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity

@Database(
    entities = [
        BlockedAppEntity::class,
        ScheduleEntity::class,
        ScheduleDayEntity::class,
        ScheduleAppEntity::class,
        UsageSessionEntity::class,
        UnblockEventEntity::class,
        MotivationalMessageEntity::class,
        FocusSessionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun unblockEventDao(): UnblockEventDao
    abstract fun motivationalMessageDao(): MotivationalMessageDao
    abstract fun focusSessionDao(): FocusSessionDao

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
                        FOREIGN KEY(app_package_name) REFERENCES blocked_apps(package_name) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_unblock_events_app_package_name ON unblock_events(app_package_name)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS schedule_apps (
                        schedule_id INTEGER NOT NULL,
                        app_package_name TEXT NOT NULL,
                        PRIMARY KEY(schedule_id, app_package_name),
                        FOREIGN KEY(schedule_id) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(app_package_name) REFERENCES blocked_apps(package_name) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_apps_app_package_name ON schedule_apps(app_package_name)")

                db.execSQL(
                    "INSERT INTO schedule_apps (schedule_id, app_package_name) " +
                        "SELECT id, app_package_name FROM schedules"
                )

                db.execSQL(
                    """
                    CREATE TABLE schedules_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        schedule_type TEXT NOT NULL,
                        start_time TEXT,
                        end_time TEXT,
                        daily_limit_minutes INTEGER,
                        is_active INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO schedules_new (id, schedule_type, start_time, end_time, daily_limit_minutes, is_active)
                    SELECT id, schedule_type, start_time, end_time, daily_limit_minutes, is_active FROM schedules
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE schedules")
                db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        started_at INTEGER NOT NULL,
                        expires_at INTEGER,
                        ended_at INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Replace single-column index with composite to cover the
                // `WHERE app_package_name = ? ORDER BY timestamp DESC` and
                // `WHERE app_package_name = ? AND timestamp >= ?` query paths.
                db.execSQL("DROP INDEX IF EXISTS index_unblock_events_app_package_name")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_unblock_events_app_package_name_timestamp " +
                        "ON unblock_events(app_package_name, timestamp)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_blocker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
