package com.appblocker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_dropsAndRecreatesUnblockEvents() {
        // Create v1 schema and seed an unblock_events row using the old column
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO blocked_apps (package_name, app_name, is_blocking_enabled, created_at) " +
                "VALUES ('com.example.app', 'Example', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, user_proceeded) " +
                "VALUES ('com.example.app', 100, 1)"
            )
        }

        // Run migration; helper validates resulting schema against the v2 export
        helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        ).use { db ->
            // Table was recreated, so it's empty
            db.query("SELECT COUNT(*) FROM unblock_events").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 0) { "Expected unblock_events to be empty after migration" }
            }
            // And it accepts the new schema
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, outcome) " +
                "VALUES ('com.example.app', 200, 'LEGITIMATE')"
            )
        }
    }

    @Test
    fun migrate2To3_movesAppFkIntoScheduleAppsJunction() {
        // Seed a v2 database: one blocked app + one schedule bound to it via the old FK
        helper.createDatabase(testDbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO blocked_apps (package_name, app_name, is_blocking_enabled, created_at) " +
                "VALUES ('com.example.app', 'Example', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO schedules (id, app_package_name, schedule_type, start_time, end_time, is_active) " +
                "VALUES (1, 'com.example.app', 'TIME_WINDOW', '09:00', '17:00', 1)"
            )
            db.execSQL("INSERT INTO schedule_days (schedule_id, day_of_week) VALUES (1, 1)")
        }

        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        ).use { db ->
            // schedules row is preserved without the app_package_name column
            db.query(
                "SELECT id, schedule_type, start_time, end_time, daily_limit_minutes, is_active FROM schedules WHERE id = 1"
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "Schedule row should still exist after migration" }
                assert(cursor.getString(1) == "TIME_WINDOW")
                assert(cursor.getString(2) == "09:00")
                assert(cursor.getString(3) == "17:00")
                assert(cursor.getInt(5) == 1)
            }

            // The old app FK is now a row in schedule_apps
            db.query(
                "SELECT schedule_id, app_package_name FROM schedule_apps WHERE schedule_id = 1"
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "schedule_apps should have a row for the migrated schedule" }
                assert(cursor.getLong(0) == 1L)
                assert(cursor.getString(1) == "com.example.app")
            }

            // schedule_days references still resolve to the recreated schedules table
            db.query("SELECT COUNT(*) FROM schedule_days WHERE schedule_id = 1").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 1) { "Expected the schedule_days row to survive migration" }
            }

            // Multi-app insert works on the new schema
            db.execSQL(
                "INSERT INTO blocked_apps (package_name, app_name, is_blocking_enabled, created_at) " +
                "VALUES ('com.example.other', 'Other', 1, 0)"
            )
            db.execSQL("INSERT INTO schedule_apps (schedule_id, app_package_name) VALUES (1, 'com.example.other')")
            db.query("SELECT COUNT(*) FROM schedule_apps WHERE schedule_id = 1").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 2) { "Schedule should now apply to two apps" }
            }
        }
    }
}
