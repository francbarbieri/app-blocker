package com.appblocker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Ignore
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

    @Ignore(
        "v1 schema JSON was never exported (export was added at v2). " +
            "MigrationTestHelper.createDatabase(name, 1) requires schemas/1.json to seed the " +
            "room_master_table identity hash, so this test cannot run today. The production " +
            "MIGRATION_1_2 is still registered and applied to legacy installs by Room's " +
            "migration chain; this is a test-infrastructure gap, not a production-correctness gap. " +
            "DataInvariantsTest guarantees future versions ship with an exported schema."
    )
    @Test
    fun migrate1To2_dropsAndRecreatesUnblockEvents() {
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

        helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        ).use { db ->
            db.query("SELECT COUNT(*) FROM unblock_events").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 0) { "Expected unblock_events to be empty after migration" }
            }
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, outcome) " +
                    "VALUES ('com.example.app', 200, 'LEGITIMATE')"
            )
        }
    }

    @Test
    fun migrate2To3_movesAppFkIntoScheduleAppsJunction() {
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
            db.query(
                "SELECT id, schedule_type, start_time, end_time, daily_limit_minutes, is_active FROM schedules WHERE id = 1"
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "Schedule row should still exist after migration" }
                assert(cursor.getString(1) == "TIME_WINDOW")
                assert(cursor.getString(2) == "09:00")
                assert(cursor.getString(3) == "17:00")
                assert(cursor.getInt(5) == 1)
            }

            db.query(
                "SELECT schedule_id, app_package_name FROM schedule_apps WHERE schedule_id = 1"
            ).use { cursor ->
                assert(cursor.moveToFirst()) { "schedule_apps should have a row for the migrated schedule" }
                assert(cursor.getLong(0) == 1L)
                assert(cursor.getString(1) == "com.example.app")
            }

            db.query("SELECT COUNT(*) FROM schedule_days WHERE schedule_id = 1").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 1) { "Expected the schedule_days row to survive migration" }
            }

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

    @Test
    fun migrate3To4_createsFocusSessionsTable() {
        helper.createDatabase(testDbName, 3).use {
            // No v3 data needed; the new table is independent.
        }

        helper.runMigrationsAndValidate(
            testDbName,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        ).use { db ->
            db.execSQL(
                "INSERT INTO focus_sessions (started_at, expires_at, ended_at) " +
                    "VALUES (100, 200, NULL)"
            )
            db.query("SELECT id, started_at, expires_at, ended_at FROM focus_sessions").use { cursor ->
                assert(cursor.moveToFirst())
                assert(cursor.getLong(1) == 100L)
                assert(cursor.getLong(2) == 200L)
                assert(cursor.isNull(3))
            }
            db.execSQL("INSERT INTO focus_sessions (started_at, expires_at) VALUES (300, NULL)")
            db.query("SELECT COUNT(*) FROM focus_sessions").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 2)
            }
        }
    }

    @Test
    fun migrate4To5_replacesUnblockEventsIndexWithComposite() {
        helper.createDatabase(testDbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO blocked_apps (package_name, app_name, is_blocking_enabled, created_at) " +
                    "VALUES ('com.example.app', 'Example', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, outcome) " +
                    "VALUES ('com.example.app', 100, 'LEGITIMATE')"
            )

            // Pre-condition: v4 single-column index is present.
            db.query("PRAGMA index_list('unblock_events')").use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(1)
                assert("index_unblock_events_app_package_name" in names) {
                    "v4 should declare the single-column index; got $names"
                }
            }
        }

        helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        ).use { db ->
            // Data survives the index swap.
            db.query("SELECT COUNT(*) FROM unblock_events").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 1) { "unblock_events row should survive the migration" }
            }

            // Post-condition: composite index is present, single-column index is gone.
            db.query("PRAGMA index_list('unblock_events')").use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(1)
                assert("index_unblock_events_app_package_name_timestamp" in names) {
                    "v5 should declare the composite index; got $names"
                }
                assert("index_unblock_events_app_package_name" !in names) {
                    "v5 must drop the single-column index; got $names"
                }
            }
        }
    }
}
